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

import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.lang3.reflect.TypeUtils;

/**
 * Captures a type <em>variable</em>, naming the slot in a template type that {@link
 * TypeRef#where(TypeParameter, TypeRef)} fills in.
 *
 * <p>Where a {@link TypeRef} captures a concrete type and refuses a variable, this captures a
 * variable and refuses a concrete type. The pair lets a generic method build a fully parameterized
 * reference with no unchecked cast anywhere — the static type is carried by the template capture
 * and proved by the compiler, rather than asserted:
 *
 * <pre>{@code
 * static <E> TypeRef<Envelope<E>> envelopeOf(TypeRef<E> element) {
 *   return new TypeRef<Envelope<E>>() {}.where(new TypeParameter<>() {}, element);
 * }
 * }</pre>
 *
 * @param <T> the captured type variable
 */
public abstract class TypeParameter<T> {

  final TypeVariable<?> variable;

  /**
   * Captures the type variable supplied by the anonymous subclass.
   *
   * @throws IllegalArgumentException if the captured type argument is not a type variable — {@code
   *     new TypeParameter<String>() {}} names nothing that could be substituted
   */
  protected TypeParameter() {
    // getClass() always extends TypeParameter, so the bindings map is never null.
    Map<TypeVariable<?>, Type> bindings =
        TypeUtils.getTypeArguments(getClass(), TypeParameter.class);
    Type captured = bindings.get(TypeParameter.class.getTypeParameters()[0]);
    if (!(captured instanceof TypeVariable<?> typeVariable)) {
      throw new IllegalArgumentException(
          "TypeParameter must capture a type variable, but captured " + captured);
    }
    this.variable = typeVariable;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TypeParameter<?> other)) return false;
    return variable.equals(other.variable);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(variable);
  }

  @Override
  public String toString() {
    return "TypeParameter<" + variable.getName() + ">";
  }
}
