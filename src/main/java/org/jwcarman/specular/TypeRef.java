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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.reflect.TypeUtils;

/**
 * A super-type token that captures a generic {@link Type} at compile time via an anonymous
 * subclass. Useful for passing a parameterized type as an argument when {@code Class<T>} would
 * erase the type parameters.
 *
 * <pre>{@code
 * TypeRef<Map<String, Integer>> ref = new TypeRef<>() {};
 * Type type = ref.type(); // ParameterizedType: Map<String, Integer>
 * }</pre>
 *
 * @param <T> the captured type
 */
public abstract class TypeRef<T> {

  private final Type type;

  /**
   * Captures the type argument supplied by the anonymous subclass.
   *
   * @throws IllegalArgumentException if the subclass is not parameterized, or if the captured
   *     argument is a type variable — {@code new TypeRef<T>() {}} inside a generic method or class
   *     captures {@code T} itself rather than the type it stands for, producing a reference whose
   *     raw class and assignability answers are meaningless
   */
  protected TypeRef() {
    Type superclass = getClass().getGenericSuperclass();
    if (!(superclass instanceof ParameterizedType parameterized)) {
      throw new IllegalArgumentException(
          "TypeRef must be created as a parameterized anonymous subclass");
    }
    Type captured = parameterized.getActualTypeArguments()[0];
    if (captured instanceof TypeVariable<?>) {
      throw new IllegalArgumentException(
          "TypeRef cannot capture the type variable "
              + captured
              + ": the type argument must be concrete where the anonymous subclass is created");
    }
    this.type = captured;
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
   * A reference to {@code List<E>} built from the reference to {@code E}.
   *
   * @param element the element type
   * @param <E> the element type
   * @return a reference to {@code List<E>}, equal to the same type captured by an anonymous
   *     subclass
   * @throws NullPointerException if {@code element} is null
   * @throws IllegalArgumentException if {@code element} is a primitive type
   */
  public static <E> TypeRef<List<E>> listOf(TypeRef<E> element) {
    return new TypeRef<List<E>>(parameterizedType(List.class, element)) {};
  }

  /**
   * A reference to {@code Set<E>} built from the reference to {@code E}.
   *
   * @param element the element type
   * @param <E> the element type
   * @return a reference to {@code Set<E>}
   * @throws NullPointerException if {@code element} is null
   * @throws IllegalArgumentException if {@code element} is a primitive type
   */
  public static <E> TypeRef<Set<E>> setOf(TypeRef<E> element) {
    return new TypeRef<Set<E>>(parameterizedType(Set.class, element)) {};
  }

  /**
   * A reference to {@code Optional<E>} built from the reference to {@code E}.
   *
   * @param element the element type
   * @param <E> the element type
   * @return a reference to {@code Optional<E>}
   * @throws NullPointerException if {@code element} is null
   * @throws IllegalArgumentException if {@code element} is a primitive type
   */
  public static <E> TypeRef<Optional<E>> optionalOf(TypeRef<E> element) {
    return new TypeRef<Optional<E>>(parameterizedType(Optional.class, element)) {};
  }

  /**
   * A reference to {@code Map<K, V>} built from the references to {@code K} and {@code V}.
   *
   * @param key the key type
   * @param value the value type
   * @param <K> the key type
   * @param <V> the value type
   * @return a reference to {@code Map<K, V>}
   * @throws NullPointerException if {@code key} or {@code value} is null
   * @throws IllegalArgumentException if {@code key} or {@code value} is a primitive type
   */
  public static <K, V> TypeRef<Map<K, V>> mapOf(TypeRef<K> key, TypeRef<V> value) {
    return new TypeRef<Map<K, V>>(parameterizedType(Map.class, key, value)) {};
  }

  /**
   * A reference to any generic class applied to the given type arguments — your own {@code
   * Envelope<T>} built from a {@code TypeRef<T>}, for example. The typed combinators ({@link
   * #listOf}, {@link #mapOf} and friends) cover the common JDK types; this covers every other
   * generic class. {@code T} is taken from the assignment context, so declare the reference you
   * mean:
   *
   * <pre>{@code
   * TypeRef<Envelope<String>> ref = TypeRef.parameterized(Envelope.class, TypeRef.of(String.class));
   * }</pre>
   *
   * <p>The class literal is a witness for {@code T}: because a raw type is a supertype of each of
   * its parameterizations, {@code Class<? super T>} lets the compiler reject {@code
   * TypeRef<List<String>> ref = parameterized(Set.class, element)}. The arity of {@code arguments}
   * is checked at construction, and a class with no type parameters is rejected. What remains
   * unchecked is the identity and order of {@code arguments} against {@code T}'s own type
   * arguments.
   *
   * @param raw the generic class, such as {@code Envelope.class}
   * @param arguments one type reference per type parameter of {@code raw}, in declaration order;
   *     each must be a reference type, not a primitive
   * @param <T> {@code raw} applied to {@code arguments}
   * @return a reference to {@code raw} applied to {@code arguments}
   * @throws NullPointerException if {@code raw}, {@code arguments} or any argument is null
   * @throws IllegalArgumentException if {@code raw} declares no type parameters, if the number of
   *     arguments does not match the number it declares, or if an argument is a primitive type
   */
  public static <T> TypeRef<T> parameterized(Class<? super T> raw, TypeRef<?>... arguments) {
    return new TypeRef<T>(parameterizedType(raw, arguments)) {};
  }

  private static ParameterizedType parameterizedType(Class<?> raw, TypeRef<?>... arguments) {
    Objects.requireNonNull(raw, "raw must not be null");
    Objects.requireNonNull(arguments, "arguments must not be null");
    int expected = raw.getTypeParameters().length;
    if (expected == 0) {
      throw new IllegalArgumentException(raw.getName() + " is not a generic class");
    }
    if (arguments.length != expected) {
      throw new IllegalArgumentException(
          raw.getSimpleName()
              + " declares "
              + expected
              + " type parameter(s) but "
              + arguments.length
              + " argument(s) were given");
    }
    Type[] types = new Type[arguments.length];
    for (int i = 0; i < arguments.length; i++) {
      Type argument = Objects.requireNonNull(arguments[i], "arguments must not contain null").type;
      if (argument instanceof Class<?> clazz && clazz.isPrimitive()) {
        throw new IllegalArgumentException(
            "a type argument cannot be primitive: " + clazz.getName() + " (use its wrapper)");
      }
      types[i] = argument;
    }
    return new SyntheticParameterizedType(raw, types);
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
    if (declaringClass.equals(context)) {
      return of(type);
    }
    Map<TypeVariable<?>, Type> typeArgs = TypeUtils.getTypeArguments(context, declaringClass);
    if (typeArgs == null) {
      throw new IllegalArgumentException(
          context.getName() + " is not a subtype of " + declaringClass.getName());
    }
    return of(TypeUtils.unrollVariables(typeArgs, type));
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
  public Type type() {
    return type;
  }

  /**
   * Returns the erased class of the captured type: {@code Map.class} for {@code Map<String,
   * Integer>}, the class itself for a non-generic type.
   *
   * <p>This method contains the only unchecked cast in the codebase. It is sound by construction: a
   * {@code TypeRef<T>} captures {@code T} and nothing else, so the erasure of the captured type is
   * the erasure of {@code T}. Returning {@code Class<T>} rather than {@code Class<?>} means callers
   * can narrow a value with {@link Class#cast} — a checked cast — instead of writing an unchecked
   * cast of their own at every call site.
   *
   * @return the erased class of {@code T}
   * @throws IllegalArgumentException if the captured type has no single erased class, as for an
   *     unresolved type variable or a wildcard
   */
  public Class<T> rawClass() {
    Class<?> raw = TypeUtils.getRawType(type, null);
    if (raw == null) {
      throw new IllegalArgumentException("Type has no single erased class: " + type.getTypeName());
    }
    return uncheckedTypeToken(raw);
  }

  /**
   * The type-token bridge: the one place the erased class is asserted to be {@code Class<T>}.
   * Isolated so the unchecked cast has exactly one line to live on.
   */
  private static <T> Class<T> uncheckedTypeToken(Class<?> raw) {
    return (Class<T>) raw;
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
    return of(new SyntheticParameterizedType(supertype, resolved));
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
