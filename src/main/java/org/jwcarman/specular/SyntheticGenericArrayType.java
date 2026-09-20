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

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Type;
import java.util.Objects;

/**
 * A {@link GenericArrayType} fabricated at run time. Equality, hashing and the type name follow the
 * JDK's own implementation, so an array type built here and the same type captured by an anonymous
 * subclass are interchangeable.
 *
 * <p>Package-private: it is an implementation detail of {@link TypeRef#arrayOf(TypeRef)}, which
 * hands callers the {@link GenericArrayType} interface rather than this type.
 */
final class SyntheticGenericArrayType implements GenericArrayType {

  private final Type componentType;

  SyntheticGenericArrayType(Type componentType) {
    this.componentType = componentType;
  }

  @Override
  public Type getGenericComponentType() {
    return componentType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof GenericArrayType other)) return false;
    return componentType.equals(other.getGenericComponentType());
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(componentType);
  }

  @Override
  public String getTypeName() {
    return componentType.getTypeName() + "[]";
  }

  @Override
  public String toString() {
    return getTypeName();
  }
}
