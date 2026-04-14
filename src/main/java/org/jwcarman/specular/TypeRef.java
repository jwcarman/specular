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
   * Returns the captured type, which may be a {@link Class} or a {@link ParameterizedType}.
   *
   * @return the captured type
   */
  public Type getType() {
    return type;
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
