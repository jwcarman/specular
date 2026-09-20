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
import java.util.Arrays;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * A {@link ParameterizedType} fabricated at run time, as opposed to one reflected from a class
 * file. Equality, hashing and the type name follow the JDK's own implementation, so a type built
 * here and the same type captured by an anonymous subclass are interchangeable — including as keys
 * in a hash-based collection.
 *
 * <p>Package-private: it is an implementation detail of {@link TypeRef}, which hands callers the
 * {@link ParameterizedType} interface rather than this type.
 */
final class SyntheticParameterizedType implements ParameterizedType {

  private final Class<?> raw;
  private final Type[] arguments;

  SyntheticParameterizedType(Class<?> raw, Type[] arguments) {
    this.raw = raw;
    this.arguments = arguments;
  }

  @Override
  public Type[] getActualTypeArguments() {
    return arguments.clone();
  }

  @Override
  public Type getRawType() {
    return raw;
  }

  @Override
  public Type getOwnerType() {
    return raw.getDeclaringClass();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ParameterizedType other)) return false;
    return Objects.equals(getOwnerType(), other.getOwnerType())
        && raw.equals(other.getRawType())
        && Arrays.equals(arguments, other.getActualTypeArguments());
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(arguments) ^ Objects.hashCode(getOwnerType()) ^ raw.hashCode();
  }

  @Override
  public String getTypeName() {
    StringJoiner args = new StringJoiner(", ", "<", ">");
    for (Type argument : arguments) {
      args.add(argument.getTypeName());
    }
    Type owner = getOwnerType();
    String name = owner == null ? raw.getName() : owner.getTypeName() + "$" + raw.getSimpleName();
    return name + args;
  }

  @Override
  public String toString() {
    return getTypeName();
  }
}
