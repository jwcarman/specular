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

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Objects;
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
