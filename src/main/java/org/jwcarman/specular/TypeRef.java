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

import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
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
 * <p>References are value-based: two that name the same type are equal and hash alike whichever
 * {@link Type} implementation each holds, so they are safe as keys in a hash-based collection. One
 * exception: a type built by {@link #parameterized(Class, TypeRef...)} for an inner class of a
 * <em>generic</em> outer class cannot record the outer class's own type arguments, so {@code
 * Outer<String>.Inner<Integer>} built here is not equal to the same type captured by an anonymous
 * subclass.
 *
 * <p>Operations that walk a type — equality, hashing, {@code toString} and assignability — recurse
 * over its structure without a depth limit, so a pathologically nested type (thousands of levels)
 * can exhaust the stack. Types originate from compiled class files or from code already running in
 * the process, so this is not reachable from untrusted input.
 *
 * @param <T> the captured type
 */
public abstract class TypeRef<T> {

  private static final String OTHER_MUST_NOT_BE_NULL = "other must not be null";
  private static final String CONTEXT_MUST_NOT_BE_NULL = "context must not be null";
  private static final String PARAMETER_MUST_NOT_BE_NULL = "parameter must not be null";
  private static final String FIELD_MUST_NOT_BE_NULL = "field must not be null";
  private static final String METHOD_MUST_NOT_BE_NULL = "method must not be null";

  private final Type type;

  /**
   * Captures the type argument supplied by the anonymous subclass.
   *
   * <p>Beware the diamond with {@code var}: {@code var ref = new TypeRef<>() {}} has nothing to
   * infer from and captures {@link Object}, silently. Give the reference an explicit type — {@code
   * TypeRef<List<String>> ref = new TypeRef<>() {}} — or write the argument out.
   *
   * @throws IllegalArgumentException if the subclass is not parameterized, or if the captured
   *     argument is a type variable — {@code new TypeRef<T>() {}} inside a generic method or class
   *     captures {@code T} itself rather than the type it stands for, producing a reference whose
   *     raw class and assignability answers are meaningless
   */
  protected TypeRef() {
    // getClass() always extends TypeRef, so the bindings map is never null.
    Map<TypeVariable<?>, Type> bindings = TypeUtils.getTypeArguments(getClass(), TypeRef.class);
    // A subclass may rebind on the way up — Mid2<X> extends Mid<X, List<X>> — so the argument
    // bound to T can itself name variables this same map resolves.
    Type captured =
        TypeSubstitution.substitute(bindings.get(TypeRef.class.getTypeParameters()[0]), bindings);
    if (captured == null) {
      throw new IllegalArgumentException(
          "TypeRef must be created as a parameterized anonymous subclass");
    }
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
   * Creates a {@link TypeRef} for a class.
   *
   * <p>A generic class is accepted and captured <em>raw</em>: {@code of(List.class)} holds {@code
   * List}, not {@code List<Something>}. A raw reference cannot answer questions about its type
   * arguments — {@link #typeArgument(Class, int)} returns {@link Optional#empty()} and {@link
   * #supertype(Class)} projects to the raw supertype. To name the arguments, capture the type with
   * an anonymous subclass, use a combinator such as {@link #listOf(TypeRef)}, or build one with
   * {@link #where(TypeParameter, TypeRef)}.
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
   * A reference to {@code E[]} built from the reference to {@code E}.
   *
   * <p>A {@link GenericArrayType} cannot otherwise be built without reaching into JDK internals, so
   * this is the only way to name an array of a parameterized type at run time. An array of a
   * non-generic type comes back as the corresponding array {@link Class}, which is how the JDK
   * itself models it.
   *
   * @param component the component type
   * @param <E> the component type
   * @return a reference to {@code E[]}
   * @throws NullPointerException if {@code component} is null
   */
  public static <E> TypeRef<E[]> arrayOf(TypeRef<E> component) {
    Objects.requireNonNull(component, "component must not be null");
    if (void.class.equals(component.type)) {
      throw new IllegalArgumentException("there is no array of void");
    }
    // The JDK models an array of a non-generic type as a Class, not a GenericArrayType; matching
    // that keeps a built array type equal to the same type captured by an anonymous subclass.
    Type arrayType =
        component.type instanceof Class<?> clazz
            ? clazz.arrayType()
            : new SyntheticGenericArrayType(component.type);
    return new TypeRef<E[]>(arrayType) {};
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
   * is checked at construction, and a class with no type parameters is rejected.
   *
   * <p>What remains unchecked is the identity and order of {@code arguments} against {@code T}'s
   * own type arguments, and {@code T} naming a <em>subtype</em> of {@code raw} ({@code
   * TypeRef<ArrayList<String>>} built from {@code List.class}). Neither fails here: the reference
   * is created, {@link #rawClass()} reports the erasure of {@code raw} rather than of {@code T},
   * and the mismatch surfaces as a {@link ClassCastException} wherever a value is finally used.
   * {@link #where(TypeParameter, TypeRef)} has no such gap and should be preferred when the
   * declared type matters; reach for this when it does not, and round-trip the result once in a
   * test.
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
  @SuppressWarnings("java:S1452") // A member's generic type is not known at compile time, so
  // TypeRef<?> is the honest return type. Naming a concrete parameter would assert something the
  // compiler cannot check, and erasing to Class<?> would discard the very type arguments this
  // library exists to preserve.
  public static TypeRef<?> parameterType(Parameter parameter) {
    Objects.requireNonNull(parameter, PARAMETER_MUST_NOT_BE_NULL);
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
  @SuppressWarnings("java:S1452") // A member's generic type is not known at compile time, so
  // TypeRef<?> is the honest return type. Naming a concrete parameter would assert something the
  // compiler cannot check, and erasing to Class<?> would discard the very type arguments this
  // library exists to preserve.
  public static TypeRef<?> parameterType(Parameter parameter, Class<?> context) {
    Objects.requireNonNull(parameter, PARAMETER_MUST_NOT_BE_NULL);
    Objects.requireNonNull(context, CONTEXT_MUST_NOT_BE_NULL);
    return resolveAgainst(
        parameter.getParameterizedType(),
        parameter.getDeclaringExecutable().getDeclaringClass(),
        context);
  }

  /**
   * Creates a {@link TypeRef} for a method parameter, with type variables resolved against a
   * <em>parameterized</em> context.
   *
   * <p>A {@link Class} context can only carry what a class literal carries, so {@code Sub.class}
   * for {@code class Sub<X> extends Base<X>} has already discarded {@code X} and leaves the
   * parameter's type variable unresolved. A reference keeps the argument, and the variable resolves
   * all the way:
   *
   * <pre>{@code
   * TypeRef.parameterType(parameter, Sub.class);                      // TypeRef<T>
   * TypeRef.parameterType(parameter, new TypeRef<Sub<String>>() {});  // TypeRef<String>
   * }</pre>
   *
   * @param parameter the parameter
   * @param context the type whose bindings should be applied
   * @return a type reference with variables substituted as far as {@code context} allows
   * @throws NullPointerException if {@code parameter} or {@code context} is null
   * @throws IllegalArgumentException if {@code context} is not a subtype of the declaring class
   */
  @SuppressWarnings("java:S1452") // A member's generic type is not known at compile time, so
  // TypeRef<?> is the honest return type. Naming a concrete parameter would assert something the
  // compiler cannot check, and erasing to Class<?> would discard the very type arguments this
  // library exists to preserve.
  public static TypeRef<?> parameterType(Parameter parameter, TypeRef<?> context) {
    Objects.requireNonNull(parameter, PARAMETER_MUST_NOT_BE_NULL);
    Objects.requireNonNull(context, CONTEXT_MUST_NOT_BE_NULL);
    return resolveAgainst(
        parameter.getParameterizedType(),
        parameter.getDeclaringExecutable().getDeclaringClass(),
        context.type);
  }

  /**
   * Creates a {@link TypeRef} for a method's return type, with type variables resolved against the
   * method's declaring class.
   *
   * @param method the method
   * @return a type reference for the (possibly unresolved) return type
   */
  @SuppressWarnings("java:S1452") // A member's generic type is not known at compile time, so
  // TypeRef<?> is the honest return type. Naming a concrete parameter would assert something the
  // compiler cannot check, and erasing to Class<?> would discard the very type arguments this
  // library exists to preserve.
  public static TypeRef<?> returnType(Method method) {
    Objects.requireNonNull(method, METHOD_MUST_NOT_BE_NULL);
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
  @SuppressWarnings("java:S1452") // A member's generic type is not known at compile time, so
  // TypeRef<?> is the honest return type. Naming a concrete parameter would assert something the
  // compiler cannot check, and erasing to Class<?> would discard the very type arguments this
  // library exists to preserve.
  public static TypeRef<?> returnType(Method method, Class<?> context) {
    Objects.requireNonNull(method, METHOD_MUST_NOT_BE_NULL);
    Objects.requireNonNull(context, CONTEXT_MUST_NOT_BE_NULL);
    return resolveAgainst(method.getGenericReturnType(), method.getDeclaringClass(), context);
  }

  /**
   * Creates a {@link TypeRef} for a field, with type variables resolved against the field's
   * declaring class.
   *
   * @param field the field
   * @return a type reference for the (possibly unresolved) field type
   * @throws NullPointerException if {@code field} is null
   */
  @SuppressWarnings("java:S1452") // A member's generic type is not known at compile time, so
  // TypeRef<?> is the honest return type. Naming a concrete parameter would assert something the
  // compiler cannot check, and erasing to Class<?> would discard the very type arguments this
  // library exists to preserve.
  public static TypeRef<?> fieldType(Field field) {
    Objects.requireNonNull(field, FIELD_MUST_NOT_BE_NULL);
    return fieldType(field, field.getDeclaringClass());
  }

  /**
   * Creates a {@link TypeRef} for a field, with type variables resolved against {@code context}'s
   * class hierarchy. Use this when the declaring class has type variables bound by a concrete
   * subtype.
   *
   * @param field the field
   * @param context the concrete class whose bindings should be applied
   * @return a type reference with variables substituted as far as {@code context} allows
   * @throws NullPointerException if {@code field} or {@code context} is null
   * @throws IllegalArgumentException if {@code context} is not a subtype of the declaring class
   */
  @SuppressWarnings("java:S1452") // A member's generic type is not known at compile time, so
  // TypeRef<?> is the honest return type. Naming a concrete parameter would assert something the
  // compiler cannot check, and erasing to Class<?> would discard the very type arguments this
  // library exists to preserve.
  public static TypeRef<?> fieldType(Field field, Class<?> context) {
    Objects.requireNonNull(field, FIELD_MUST_NOT_BE_NULL);
    Objects.requireNonNull(context, CONTEXT_MUST_NOT_BE_NULL);
    return resolveAgainst(field.getGenericType(), field.getDeclaringClass(), context);
  }

  /**
   * Creates a {@link TypeRef} for a method's return type, with type variables resolved against a
   * <em>parameterized</em> context. See {@link #parameterType(Parameter, TypeRef)} for what a
   * reference context can resolve that a {@link Class} context cannot.
   *
   * @param method the method
   * @param context the type whose bindings should be applied
   * @return a type reference with variables substituted as far as {@code context} allows
   * @throws NullPointerException if {@code method} or {@code context} is null
   * @throws IllegalArgumentException if {@code context} is not a subtype of the declaring class
   */
  @SuppressWarnings("java:S1452") // A member's generic type is not known at compile time, so
  // TypeRef<?> is the honest return type. Naming a concrete parameter would assert something the
  // compiler cannot check, and erasing to Class<?> would discard the very type arguments this
  // library exists to preserve.
  public static TypeRef<?> returnType(Method method, TypeRef<?> context) {
    Objects.requireNonNull(method, METHOD_MUST_NOT_BE_NULL);
    Objects.requireNonNull(context, CONTEXT_MUST_NOT_BE_NULL);
    return resolveAgainst(method.getGenericReturnType(), method.getDeclaringClass(), context.type);
  }

  /**
   * Creates a {@link TypeRef} for a field, with type variables resolved against a
   * <em>parameterized</em> context. See {@link #parameterType(Parameter, TypeRef)} for what a
   * reference context can resolve that a {@link Class} context cannot.
   *
   * @param field the field
   * @param context the type whose bindings should be applied
   * @return a type reference with variables substituted as far as {@code context} allows
   * @throws NullPointerException if {@code field} or {@code context} is null
   * @throws IllegalArgumentException if {@code context} is not a subtype of the declaring class
   */
  @SuppressWarnings("java:S1452") // A member's generic type is not known at compile time, so
  // TypeRef<?> is the honest return type. Naming a concrete parameter would assert something the
  // compiler cannot check, and erasing to Class<?> would discard the very type arguments this
  // library exists to preserve.
  public static TypeRef<?> fieldType(Field field, TypeRef<?> context) {
    Objects.requireNonNull(field, FIELD_MUST_NOT_BE_NULL);
    Objects.requireNonNull(context, CONTEXT_MUST_NOT_BE_NULL);
    return resolveAgainst(field.getGenericType(), field.getDeclaringClass(), context.type);
  }

  private static boolean isArray(Type type) {
    return type instanceof GenericArrayType || (type instanceof Class<?> clazz && clazz.isArray());
  }

  /**
   * The bindings {@code context} supplies for {@code declaringClass}'s variables, empty when {@code
   * context} is not a subtype of it.
   */
  private static Optional<Map<TypeVariable<?>, Type>> bindings(
      Type context, Class<?> declaringClass) {
    return Optional.ofNullable(TypeUtils.getTypeArguments(context, declaringClass));
  }

  /** Resolves every variable or none: empty if any is unbound or bound only to another variable. */
  private static Optional<Type[]> resolveAll(
      TypeVariable<?>[] variables, Map<TypeVariable<?>, Type> typeArgs) {
    Type[] resolved =
        Arrays.stream(variables)
            .map(variable -> TypeSubstitution.substitute(typeArgs.get(variable), typeArgs))
            .filter(Objects::nonNull)
            .filter(argument -> !(argument instanceof TypeVariable<?>))
            .toArray(Type[]::new);
    return resolved.length == variables.length ? Optional.of(resolved) : Optional.empty();
  }

  private static TypeRef<?> resolveAgainst(Type type, Class<?> declaringClass, Type context) {
    if (declaringClass.equals(context)) {
      return of(type);
    }
    // getTypeArguments() strips an array context down to its component, which would quietly
    // accept `Sub<String>[]` as a context for a member of Sub.
    if (isArray(context)) {
      throw new IllegalArgumentException(
          context.getTypeName() + " is not a subtype of " + declaringClass.getName());
    }
    Map<TypeVariable<?>, Type> typeArgs =
        bindings(context, declaringClass)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        context.getTypeName()
                            + " is not a subtype of "
                            + declaringClass.getName()));
    // Unbound variables stay as they are, so the answer is as resolved as the context allows.
    return of(TypeSubstitution.substitute(type, typeArgs));
  }

  /**
   * Substitutes {@code argument} for {@code parameter} throughout this reference's type, returning
   * the reference the compiler expects.
   *
   * <p>This is the one way to build a fully parameterized reference with no unchecked cast and no
   * unproven claim. The static type comes from the template capture, so the compiler — not the
   * caller — establishes it:
   *
   * <pre>{@code
   * static <E> TypeRef<Envelope<E>> envelopeOf(TypeRef<E> element) {
   *   return new TypeRef<Envelope<E>>() {}.where(new TypeParameter<>() {}, element);
   * }
   *
   * envelopeOf(TypeRef.of(String.class));  // TypeRef<Envelope<String>>
   * }</pre>
   *
   * <p>Chain calls to fill more than one slot. Substituting a variable this reference does not
   * mention is rejected: it is always a mistake, and the message names the variables that are
   * available.
   *
   * @param parameter the variable to replace
   * @param argument the type to put in its place
   * @param <X> the type being substituted
   * @return a reference of the same declared type, with {@code parameter} replaced by {@code
   *     argument}
   * @throws NullPointerException if {@code parameter} or {@code argument} is null
   * @throws IllegalArgumentException if this reference does not mention {@code parameter}'s
   *     variable, or if {@code argument} names a primitive type
   */
  public <X> TypeRef<T> where(TypeParameter<X> parameter, TypeRef<X> argument) {
    Objects.requireNonNull(parameter, PARAMETER_MUST_NOT_BE_NULL);
    Objects.requireNonNull(argument, "argument must not be null");
    if (argument.type instanceof Class<?> clazz && clazz.isPrimitive()) {
      throw new IllegalArgumentException(
          "a type argument cannot be primitive: " + clazz.getName() + " (use its wrapper)");
    }
    Set<TypeVariable<?>> unresolved = unresolvedVariables();
    if (!unresolved.contains(parameter.variable)) {
      throw new IllegalArgumentException(
          type.getTypeName()
              + " has no type variable "
              + parameter.variable.getName()
              + " to substitute"
              + (unresolved.isEmpty() ? "" : " (it has " + names(unresolved) + ")"));
    }
    Map<TypeVariable<?>, Type> bindings = new HashMap<>();
    bindings.put(parameter.variable, argument.type);
    return new TypeRef<T>(TypeSubstitution.substitute(type, bindings)) {};
  }

  /**
   * Returns the type variables this reference still carries, in the order they appear.
   *
   * <p>A reference built by substitution, or resolved against a context that could not bind every
   * variable, may still name one. Such a reference cannot describe a concrete type, so {@link
   * #rawClass()} on a bare variable throws and assignability answers are meaningless.
   *
   * @return the unresolved variables, empty if the captured type is fully concrete
   */
  public Set<TypeVariable<?>> unresolvedVariables() {
    Set<TypeVariable<?>> found = new LinkedHashSet<>();
    collectVariables(type, found);
    return found;
  }

  /**
   * Returns this reference, having checked that it names no type variable.
   *
   * <p>The terminal call of a {@link #where(TypeParameter, TypeRef)} chain: substituting some of a
   * template's variables and forgetting the rest yields a reference whose declared type claims to
   * be concrete while its captured type is not. This turns that into an error at the point it is
   * made rather than a puzzle at the point it is used.
   *
   * <pre>{@code
   * return new TypeRef<Map<K, V>>() {}
   *     .where(new TypeParameter<K>() {}, key)
   *     .where(new TypeParameter<V>() {}, value)
   *     .resolved();
   * }</pre>
   *
   * @return this reference
   * @throws IllegalStateException if any type variable remains unresolved
   */
  public TypeRef<T> resolved() {
    Set<TypeVariable<?>> unresolved = unresolvedVariables();
    if (!unresolved.isEmpty()) {
      throw new IllegalStateException(
          type.getTypeName() + " still has unresolved type variable(s): " + names(unresolved));
    }
    return this;
  }

  private static String names(Set<TypeVariable<?>> variables) {
    StringJoiner joined = new StringJoiner(", ");
    for (TypeVariable<?> variable : variables) {
      joined.add(variable.getName());
    }
    return joined.toString();
  }

  private static void collectVariables(Type type, Set<TypeVariable<?>> into) {
    switch (type) {
      case null -> {
        // nothing to collect
      }
      case TypeVariable<?> variable -> into.add(variable);
      case ParameterizedType parameterized -> {
        collectVariables(parameterized.getOwnerType(), into);
        for (Type argument : parameterized.getActualTypeArguments()) {
          collectVariables(argument, into);
        }
      }
      case GenericArrayType array -> collectVariables(array.getGenericComponentType(), into);
      case WildcardType wildcard -> {
        for (Type bound : wildcard.getUpperBounds()) {
          collectVariables(bound, into);
        }
        for (Type bound : wildcard.getLowerBounds()) {
          collectVariables(bound, into);
        }
      }
      default -> {
        // a Class, or a Type implementation with no nested structure
      }
    }
  }

  /**
   * Returns {@code true} if a value of {@code other} is assignable to this reference's captured
   * type, honoring Java's generic assignability rules (invariant type arguments, wildcard bounds,
   * raw-class hierarchy).
   *
   * @param other the candidate source type
   * @return {@code true} if assignment-compatible
   * @throws NullPointerException if {@code other} is null. Commons Lang reads a null {@link Type}
   *     as the null type, which is assignable to every reference type; answering {@code true} for a
   *     caller who simply had nothing to pass is worse than saying so.
   */
  public boolean isAssignableFrom(Type other) {
    Objects.requireNonNull(other, OTHER_MUST_NOT_BE_NULL);
    return TypeUtils.isAssignable(other, type);
  }

  /**
   * {@link #isAssignableFrom(Type)} overload for a raw {@link Class}.
   *
   * @param other the candidate source type
   * @return {@code true} if assignment-compatible
   * @throws NullPointerException if {@code other} is null
   */
  public boolean isAssignableFrom(Class<?> other) {
    Objects.requireNonNull(other, OTHER_MUST_NOT_BE_NULL);
    return isAssignableFrom((Type) other);
  }

  /**
   * {@link #isAssignableFrom(Type)} overload for another {@link TypeRef}.
   *
   * @param other the candidate source type
   * @return {@code true} if assignment-compatible
   * @throws NullPointerException if {@code other} is null
   */
  public boolean isAssignableFrom(TypeRef<?> other) {
    Objects.requireNonNull(other, OTHER_MUST_NOT_BE_NULL);
    return isAssignableFrom(other.type);
  }

  /**
   * Returns {@code true} if a value of this reference's captured type is assignable to {@code
   * other} — the mirror of {@link #isAssignableFrom(Type)}, for when the value's type is what you
   * hold and the target is what you are checking against.
   *
   * @param other the candidate target type
   * @return {@code true} if assignment-compatible
   * @throws NullPointerException if {@code other} is null
   */
  public boolean isAssignableTo(Type other) {
    Objects.requireNonNull(other, OTHER_MUST_NOT_BE_NULL);
    return TypeUtils.isAssignable(type, other);
  }

  /**
   * {@link #isAssignableTo(Type)} overload for a raw {@link Class}.
   *
   * @param other the candidate target type
   * @return {@code true} if assignment-compatible
   * @throws NullPointerException if {@code other} is null
   */
  public boolean isAssignableTo(Class<?> other) {
    Objects.requireNonNull(other, OTHER_MUST_NOT_BE_NULL);
    return isAssignableTo((Type) other);
  }

  /**
   * {@link #isAssignableTo(Type)} overload for another {@link TypeRef}.
   *
   * @param other the candidate target type
   * @return {@code true} if assignment-compatible
   * @throws NullPointerException if {@code other} is null
   */
  public boolean isAssignableTo(TypeRef<?> other) {
    Objects.requireNonNull(other, OTHER_MUST_NOT_BE_NULL);
    return isAssignableTo(other.type);
  }

  /**
   * Returns the component type if this reference names an array.
   *
   * <p>Covers both shapes an array takes in reflection: a {@link Class} such as {@code
   * String[].class}, and a {@link GenericArrayType} such as {@code List<String>[]}.
   *
   * @return the component type, or {@link Optional#empty()} if this is not an array
   */
  public Optional<TypeRef<?>> componentType() {
    return switch (type) {
      case Class<?> clazz when clazz.isArray() -> Optional.of(of(clazz.getComponentType()));
      case GenericArrayType array -> Optional.of(of(array.getGenericComponentType()));
      default -> Optional.empty();
    };
  }

  /**
   * Returns this type's own type arguments, in declaration order.
   *
   * <p>These are the arguments of the captured type itself, not of a supertype: use {@link
   * #typeArgument(Class, int)} or {@link #supertype(Class)} to ask about a supertype's arguments.
   *
   * @return the type arguments, empty if the captured type is not parameterized
   */
  public List<TypeRef<?>> typeArguments() {
    if (!(type instanceof ParameterizedType parameterized)) {
      return List.of();
    }
    List<TypeRef<?>> arguments = new ArrayList<>();
    for (Type argument : parameterized.getActualTypeArguments()) {
      arguments.add(of(argument));
    }
    return List.copyOf(arguments);
  }

  /**
   * Returns the concrete type bound to {@code variable} within this reference's hierarchy, if
   * resolvable.
   *
   * <p>Naming the variable is sturdier than naming a position: {@code
   * typeArgument(Map.class.getTypeParameters()[1])} says which parameter is meant even if the
   * declaring class later gains one.
   *
   * @param variable the type variable to resolve
   * @return the resolved argument, or {@link Optional#empty()} if it is not resolvable here
   * @throws NullPointerException if {@code variable} is null
   */
  public Optional<TypeRef<?>> typeArgument(TypeVariable<?> variable) {
    Objects.requireNonNull(variable, "variable must not be null");
    if (!(variable.getGenericDeclaration() instanceof Class<?> definingClass)) {
      return Optional.empty();
    }
    // map() drops a missing binding; filter() drops one that resolved only to another
    // variable. Either way the answer is "not resolvable here".
    return bindings(type, definingClass)
        .map(typeArgs -> TypeSubstitution.substitute(typeArgs.get(variable), typeArgs))
        .filter(argument -> !(argument instanceof TypeVariable<?>))
        .map(TypeRef::of);
  }

  /**
   * Returns this same reference, typed as {@code TypeRef<U>}.
   *
   * <p>Reflection cannot know what a {@link Field} or {@link Method} holds, so the factories that
   * read one return {@code TypeRef<?>}. When <em>you</em> know — the field named {@code firstName}
   * holds a {@code String} — this lets you say so:
   *
   * <pre>{@code
   * TypeRef<String> firstName = TypeRef.fieldType(field).as();
   * }</pre>
   *
   * <p>This is an assertion, not a proof. Nothing checks that {@code U} matches the captured type,
   * and a wrong one surfaces as a {@link ClassCastException} wherever a value is finally used —
   * exactly like a cast you would otherwise write yourself. The difference is that it is written
   * here, visibly, instead of as an unchecked cast at every call site. Where the compiler *can*
   * establish the type, prefer {@link #where(TypeParameter, TypeRef)}, which proves it.
   *
   * @param <U> the type you are claiming this reference names
   * @return this reference, typed as claimed
   */
  @SuppressWarnings("unchecked") // The caller is asserting U; see the contract above.
  public <U> TypeRef<U> as() {
    return (TypeRef<U>) this;
  }

  /**
   * Returns the captured type.
   *
   * <p>Most often a {@link Class} or a {@link ParameterizedType}, but a reference obtained from
   * {@link #of(Type)}, {@link #parameterType(Parameter)} or {@link #typeArgument(Class, int)} may
   * hold any {@link Type}: a {@link java.lang.reflect.GenericArrayType}, a {@link
   * java.lang.reflect.WildcardType}, or an unresolved {@link TypeVariable}. {@link
   * #unresolvedVariables()} reports whether variables remain.
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
   * <p>This method contains the only unchecked cast in the codebase. It is sound for every
   * reference whose {@code T} the compiler established — one captured by an anonymous subclass,
   * returned by {@link #of(Class)} or a combinator, or built by {@link #where(TypeParameter,
   * TypeRef)} — because such a {@code TypeRef<T>} captures {@code T} and nothing else, so the
   * erasure of the captured type is the erasure of {@code T}. Returning {@code Class<T>} rather
   * than {@code Class<?>} means those callers can narrow a value with {@link Class#cast} — a
   * checked cast — instead of writing an unchecked cast of their own at every call site.
   *
   * <p>It is <em>not</em> sound for a reference from {@link #parameterized(Class, TypeRef...)},
   * whose {@code T} is asserted by the caller rather than proved; see that method for what it does
   * and does not check.
   *
   * @return the erased class of {@code T}
   * @throws IllegalArgumentException if the captured type has no single erased class, as for an
   *     unresolved type variable or a wildcard
   */
  public Class<T> rawClass() {
    return Optional.ofNullable(TypeUtils.getRawType(type, null))
        .map(TypeRef::<T>uncheckedTypeToken)
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Type has no single erased class: " + type.getTypeName()));
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
    return typeArgument(typeParameters[index]);
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
    Map<TypeVariable<?>, Type> typeArgs =
        bindings(type, supertype)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        type.getTypeName() + " is not a subtype of " + supertype.getName()));
    // All or nothing: a supertype whose arguments cannot all be resolved is reported raw
    // rather than half-built.
    return resolveAll(vars, typeArgs)
        .<TypeRef<?>>map(resolved -> of(new SyntheticParameterizedType(supertype, resolved)))
        .orElseGet(() -> of(supertype));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TypeRef<?> other)) return false;
    return TypeUtils.equals(type, other.type);
  }

  /**
   * Hashes the captured type structurally rather than delegating to the {@link Type}
   * implementation's own {@code hashCode}.
   *
   * <p>A reference can hold a type from any number of implementations: one reflected by the JDK,
   * one built by this library, one produced by Commons Lang while resolving variables. Those
   * implementations agree on equality but not on hashing, so delegating would leave two equal
   * references hashing differently — and unable to find each other in a hash-based collection.
   *
   * @return a hash derived from the structure of the captured type
   */
  @Override
  public int hashCode() {
    return hash(type);
  }

  private static int hash(Type type) {
    return switch (type) {
      case null -> 0;
      case Class<?> clazz -> clazz.hashCode();
      case ParameterizedType parameterized -> {
        int result = hash(parameterized.getRawType()) ^ hash(parameterized.getOwnerType());
        for (Type argument : parameterized.getActualTypeArguments()) {
          result = result * 31 + hash(argument);
        }
        yield result;
      }
      case GenericArrayType array -> 31 * hash(array.getGenericComponentType());
      case WildcardType wildcard -> {
        int result = 1;
        // An empty upper-bound array means `? extends Object`, and equality reads it that way.
        // Hashing has to agree, or two equal references hash apart.
        for (Type bound : TypeUtils.getImplicitUpperBounds(wildcard)) {
          result = result * 31 + hash(bound);
        }
        for (Type bound : TypeUtils.getImplicitLowerBounds(wildcard)) {
          result = result * 31 + hash(bound);
        }
        yield result;
      }
      case TypeVariable<?> variable ->
          Objects.hash(variable.getName(), variable.getGenericDeclaration());
      default -> type.getTypeName().hashCode();
    };
  }

  @Override
  public String toString() {
    return "TypeRef<" + type.getTypeName() + ">";
  }
}
