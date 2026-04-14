/*
 * Copyright © 2026 James Carman
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jwcarman.specular;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.lang3.reflect.TypeUtils;

/**
 * A super-type token that captures a generic {@link Type} at compile time via an anonymous
 * subclass. Useful for passing a parameterized type as an argument when {@code Class<T>} would
 * erase the type parameters.
 *
 * <pre>{@code
 * TypeRef<Map<String, Integer>> ref = new TypeRef<>() {};
 * Type type = ref.getType(); // ParameterizedType: Map<String, Integer>
 * }</pre>
 *
 * @param <T> the captured type
 */
public abstract class TypeRef<T> {

  private final Type type;

  protected TypeRef() {
    Type superclass = getClass().getGenericSuperclass();
    if (!(superclass instanceof ParameterizedType parameterized)) {
      throw new IllegalArgumentException(
          "TypeRef must be created as a parameterized anonymous subclass");
    }
    this.type = parameterized.getActualTypeArguments()[0];
  }

  private TypeRef(Type type) {
    this.type = type;
  }

  /**
   * Creates a {@link TypeRef} for a non-generic class.
   *
   * @param type the class
   * @param <T> the type
   * @return a type reference wrapping the class
   */
  public static <T> TypeRef<T> of(Class<T> type) {
    Objects.requireNonNull(type, "type must not be null");
    return new TypeRef<>(type) {};
  }

  /**
   * Creates a {@link TypeRef} wrapping an arbitrary {@link Type}. The returned reference has an
   * unknown compile-time parameter since {@code Type} does not carry generic information at the JVM
   * level.
   *
   * @param type the type
   * @return a type reference wrapping the type
   */
  public static TypeRef<?> of(Type type) {
    Objects.requireNonNull(type, "type must not be null");
    return new TypeRef<>(type) {};
  }

  /**
   * Creates a {@link TypeRef} for a method parameter, with type variables resolved against the
   * parameter's declaring class. If the parameter's type involves type variables inherited from a
   * generic supertype, use {@link #parameterType(Parameter, Class)} to supply a concrete subtype
   * context.
   *
   * @param parameter the parameter
   * @return a type reference for the (possibly unresolved) parameter type
   */
  public static TypeRef<?> parameterType(Parameter parameter) {
    Objects.requireNonNull(parameter, "parameter must not be null");
    return parameterType(parameter, parameter.getDeclaringExecutable().getDeclaringClass());
  }

  /**
   * Creates a {@link TypeRef} for a method parameter, with type variables resolved against {@code
   * context}'s class hierarchy. Use this when the parameter's declaring class has type variables
   * bound by a concrete subtype.
   *
   * @param parameter the parameter
   * @param context the concrete class whose bindings should be applied
   * @return a type reference with variables substituted as far as {@code context} allows
   */
  public static TypeRef<?> parameterType(Parameter parameter, Class<?> context) {
    Objects.requireNonNull(parameter, "parameter must not be null");
    Objects.requireNonNull(context, "context must not be null");
    return resolveAgainst(
        parameter.getParameterizedType(),
        parameter.getDeclaringExecutable().getDeclaringClass(),
        context);
  }

  /**
   * Creates a {@link TypeRef} for a method's return type, with type variables resolved against the
   * method's declaring class.
   *
   * @param method the method
   * @return a type reference for the (possibly unresolved) return type
   */
  public static TypeRef<?> returnType(Method method) {
    Objects.requireNonNull(method, "method must not be null");
    return returnType(method, method.getDeclaringClass());
  }

  /**
   * Creates a {@link TypeRef} for a method's return type, with type variables resolved against
   * {@code context}'s class hierarchy.
   *
   * @param method the method
   * @param context the concrete class whose bindings should be applied
   * @return a type reference with variables substituted as far as {@code context} allows
   */
  public static TypeRef<?> returnType(Method method, Class<?> context) {
    Objects.requireNonNull(method, "method must not be null");
    Objects.requireNonNull(context, "context must not be null");
    return resolveAgainst(method.getGenericReturnType(), method.getDeclaringClass(), context);
  }

  private static TypeRef<?> resolveAgainst(Type type, Class<?> declaringClass, Class<?> context) {
    Map<TypeVariable<?>, Type> typeArgs = TypeUtils.getTypeArguments(context, declaringClass);
    Type resolved = typeArgs == null ? type : TypeUtils.unrollVariables(typeArgs, type);
    return of(resolved != null ? resolved : type);
  }

  /**
   * Returns {@code true} if a value of {@code other} is assignable to this reference's captured
   * type, honoring Java's generic assignability rules (invariant type arguments, wildcard bounds,
   * raw-class hierarchy).
   *
   * @param other the candidate source type
   * @return {@code true} if assignment-compatible
   */
  public boolean isAssignableFrom(Type other) {
    return TypeUtils.isAssignable(other, type);
  }

  /**
   * {@link #isAssignableFrom(Type)} overload for a raw {@link Class}.
   *
   * @param other the candidate source type
   * @return {@code true} if assignment-compatible
   */
  public boolean isAssignableFrom(Class<?> other) {
    return isAssignableFrom((Type) other);
  }

  /**
   * {@link #isAssignableFrom(Type)} overload for another {@link TypeRef}.
   *
   * @param other the candidate source type
   * @return {@code true} if assignment-compatible
   */
  public boolean isAssignableFrom(TypeRef<?> other) {
    return isAssignableFrom(other.type);
  }

  /**
   * Returns the captured type, which may be a {@link Class} or a {@link ParameterizedType}.
   *
   * @return the captured type
   */
  public Type getType() {
    return type;
  }

  /**
   * Returns the erased raw {@link Class} of this reference's captured type. For a {@link
   * ParameterizedType} like {@code Map<String, Integer>}, returns {@code Map.class}.
   *
   * @return the raw class
   */
  public Class<?> getRawType() {
    return TypeUtils.getRawType(type, null);
  }

  /**
   * Returns the concrete type argument bound at position {@code index} on {@code definingClass}
   * within this reference's type hierarchy, if resolvable.
   *
   * <p>For example, {@code TypeRef.of(StringHandler.class).typeArgument(Handler.class, 0)} returns
   * {@code Optional[TypeRef<String>]} when {@code StringHandler extends Handler<String>}.
   *
   * @param definingClass the generic class or interface declaring the type parameter
   * @param index the zero-based index of the type parameter on {@code definingClass}
   * @return the resolved type argument, or {@link Optional#empty()} if not resolvable
   */
  public Optional<TypeRef<?>> typeArgument(Class<? super T> definingClass, int index) {
    Objects.requireNonNull(definingClass, "definingClass must not be null");
    TypeVariable<?>[] typeParameters = definingClass.getTypeParameters();
    if (index < 0 || index >= typeParameters.length) {
      return Optional.empty();
    }
    Map<TypeVariable<?>, Type> typeArgs = TypeUtils.getTypeArguments(type, definingClass);
    return Optional.ofNullable(typeArgs.get(typeParameters[index])).map(TypeRef::of);
  }

  /**
   * Returns the fully parameterized form of a supertype within this reference's hierarchy.
   *
   * <p>For example, if this reference is {@code TypeRef<StringHandler>} where {@code StringHandler
   * extends AbstractHandler<String> implements Handler<String>}, then {@code
   * supertype(Handler.class)} returns {@code TypeRef<Handler<String>>}.
   *
   * <p>Works transitively across the class and interface hierarchy.
   *
   * @param supertype the supertype to project onto
   * @return a type reference for the parameterized form of {@code supertype}
   */
  public TypeRef<?> supertype(Class<? super T> supertype) {
    Objects.requireNonNull(supertype, "supertype must not be null");
    TypeVariable<?>[] vars = supertype.getTypeParameters();
    if (vars.length == 0) {
      return of(supertype);
    }
    Map<TypeVariable<?>, Type> typeArgs = TypeUtils.getTypeArguments(type, supertype);
    Type[] resolved = new Type[vars.length];
    for (int i = 0; i < vars.length; i++) {
      resolved[i] = typeArgs.get(vars[i]);
    }
    return of(TypeUtils.parameterize(supertype, resolved));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TypeRef<?> other)) return false;
    return type.equals(other.type);
  }

  @Override
  public int hashCode() {
    return type.hashCode();
  }

  @Override
  public String toString() {
    return "TypeRef<" + type.getTypeName() + ">";
  }
}
