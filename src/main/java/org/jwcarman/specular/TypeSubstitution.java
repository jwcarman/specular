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
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Replaces type variables with the types bound to them, throughout a type.
 *
 * <p>This exists because Commons Lang's {@code TypeUtils.unrollVariables} is not a substitution
 * function: it does not descend into a {@link GenericArrayType}, it discards the bounds of a
 * wildcard whose variable it cannot resolve, it does not rewrite an owner type, and it does not
 * unroll a binding that leads to another binding. Every one of those left a variable sitting in a
 * type that claimed to be resolved.
 *
 * <p>The rules here are the ones the language specifies: a variable becomes whatever it is bound to
 * (following the chain), and a variable with no binding stays exactly as it was rather than
 * vanishing. Every other shape is rebuilt from its substituted parts.
 */
final class TypeSubstitution {

  private TypeSubstitution() {}

  /**
   * Substitutes {@code bindings} throughout {@code type}.
   *
   * @param type the type to rewrite
   * @param bindings the variables to replace and what to replace them with
   * @return the rewritten type, or {@code type} itself when nothing in it is bound
   */
  static Type substitute(Type type, Map<TypeVariable<?>, Type> bindings) {
    return substitute(type, bindings, new HashSet<>());
  }

  /**
   * @param expanding the variables currently being expanded. Only a binding can lead back to a
   *     variable already in flight, so this catches a cycle exactly; descending into the structure
   *     of a type cannot loop, however deeply it nests.
   */
  private static Type substitute(
      Type type, Map<TypeVariable<?>, Type> bindings, Set<TypeVariable<?>> expanding) {
    return switch (type) {
      case TypeVariable<?> variable -> substituteVariable(variable, bindings, expanding);
      case ParameterizedType parameterized ->
          substituteParameterized(parameterized, bindings, expanding);
      case GenericArrayType array -> substituteArray(array, bindings, expanding);
      case WildcardType wildcard -> substituteWildcard(wildcard, bindings, expanding);
      case null, default -> type;
    };
  }

  private static Type substituteVariable(
      TypeVariable<?> variable,
      Map<TypeVariable<?>, Type> bindings,
      Set<TypeVariable<?>> expanding) {
    Type bound = bindings.get(variable);
    // An unbound variable stays as it is: it is still an honest part of the type.
    if (bound == null || bound.equals(variable)) {
      return variable;
    }
    if (!expanding.add(variable)) {
      throw new IllegalStateException(
          "type variable " + variable.getName() + " is bound through a cycle");
    }
    try {
      // The binding may itself name a variable that is bound — Sub<X> extends Base<List<X>>.
      return substitute(bound, bindings, expanding);
    } finally {
      expanding.remove(variable);
    }
  }

  private static Type substituteParameterized(
      ParameterizedType parameterized,
      Map<TypeVariable<?>, Type> bindings,
      Set<TypeVariable<?>> expanding) {
    Type[] arguments = parameterized.getActualTypeArguments();
    Type[] substituted = new Type[arguments.length];
    for (int i = 0; i < arguments.length; i++) {
      substituted[i] = substitute(arguments[i], bindings, expanding);
    }
    Type owner = substitute(parameterized.getOwnerType(), bindings, expanding);
    return new SyntheticParameterizedType(
        owner, (Class<?>) parameterized.getRawType(), substituted);
  }

  private static Type substituteArray(
      GenericArrayType array, Map<TypeVariable<?>, Type> bindings, Set<TypeVariable<?>> expanding) {
    Type component = substitute(array.getGenericComponentType(), bindings, expanding);
    // An array of a non-generic type is a Class in the JDK's model; match that so a substituted
    // array equals the same array captured from source.
    return component instanceof Class<?> clazz
        ? clazz.arrayType()
        : new SyntheticGenericArrayType(component);
  }

  private static Type substituteWildcard(
      WildcardType wildcard, Map<TypeVariable<?>, Type> bindings, Set<TypeVariable<?>> expanding) {
    return new SyntheticWildcardType(
        substituteBounds(wildcard.getUpperBounds(), bindings, expanding, true),
        substituteBounds(wildcard.getLowerBounds(), bindings, expanding, false));
  }

  /**
   * Substitutes a wildcard's bounds, flattening any bound that becomes a wildcard itself.
   *
   * <p>{@code ? extends (? super Integer)} is not a legal type. A nested wildcard contributes its
   * own bounds in the same position — which, because {@link SyntheticWildcardType} normalises an
   * absent upper bound to {@code Object}, widens such a case to plain {@code ?} rather than
   * inventing a bound that was never there.
   *
   * @param upper whether these are the upper bounds, which decides which side of a nested wildcard
   *     is the honest contribution
   */
  private static Type[] substituteBounds(
      Type[] types,
      Map<TypeVariable<?>, Type> bindings,
      Set<TypeVariable<?>> expanding,
      boolean upper) {
    List<Type> substituted = new ArrayList<>(types.length);
    for (Type type : types) {
      Type result = substitute(type, bindings, expanding);
      if (result instanceof WildcardType nested) {
        // Keep every bound, not just the first: `? extends Number & Comparable` has two.
        substituted.addAll(List.of(upper ? nested.getUpperBounds() : nested.getLowerBounds()));
      } else {
        substituted.add(result);
      }
    }
    return substituted.toArray(Type[]::new);
  }
}
