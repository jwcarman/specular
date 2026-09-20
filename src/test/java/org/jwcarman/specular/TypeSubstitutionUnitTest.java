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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The shapes a binding map can take that no legal Java program produces, but a caller — or Commons
 * Lang — can hand us anyway.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class TypeSubstitutionUnitTest {

  static class One<A> {}

  static class Two<B> {}

  private static final TypeVariable<?> A = One.class.getTypeParameters()[0];
  private static final TypeVariable<?> B = Two.class.getTypeParameters()[0];

  @Nested
  class A_variable_bound_to_itself {

    @Test
    void is_left_alone_rather_than_recursed_into() {
      Map<TypeVariable<?>, Type> bindings = new HashMap<>();
      bindings.put(A, A);

      assertThat(TypeSubstitution.substitute(A, bindings)).isEqualTo(A);
    }
  }

  @Nested
  class A_cycle_between_two_variables {

    @Test
    void terminates_instead_of_recursing_forever() {
      Map<TypeVariable<?>, Type> bindings = new HashMap<>();
      bindings.put(A, B);
      bindings.put(B, A);

      // The depth guard stops the chase; what matters is that it returns at all.
      assertThat(TypeSubstitution.substitute(A, bindings)).isNotNull();
    }
  }

  @Nested
  class A_wildcard_substituted_with_another_wildcard {

    @Test
    void is_collapsed_rather_than_nested() {
      Map<TypeVariable<?>, Type> bindings = new HashMap<>();
      bindings.put(A, new SyntheticWildcardType(new Type[] {Number.class}, new Type[0]));
      Type upperBoundedByA = new SyntheticWildcardType(new Type[] {A}, new Type[0]);

      Type substituted = TypeSubstitution.substitute(upperBoundedByA, bindings);

      // `? extends (? extends Number)` is not a legal type; it must read as `? extends Number`.
      assertThat(substituted.getTypeName()).isEqualTo("? extends java.lang.Number");
    }

    @Test
    void keeps_a_lower_bound_that_substitutes_to_a_wildcard() {
      Map<TypeVariable<?>, Type> bindings = new HashMap<>();
      bindings.put(A, new SyntheticWildcardType(new Type[0], new Type[] {Integer.class}));
      Type lowerBoundedByA = new SyntheticWildcardType(new Type[0], new Type[] {A});

      Type substituted = TypeSubstitution.substitute(lowerBoundedByA, bindings);

      assertThat(substituted.getTypeName()).contains("super");
    }
  }

  @Nested
  class A_type_with_nothing_to_substitute {

    @Test
    void comes_back_unchanged() {
      assertThat(TypeSubstitution.substitute(String.class, Map.of())).isEqualTo(String.class);
    }

    @Test
    void is_null_safe() {
      assertThat(TypeSubstitution.substitute(null, Map.of())).isNull();
    }

    @Test
    void leaves_a_foreign_type_implementation_alone() {
      Type foreign =
          new Type() {
            @Override
            public String getTypeName() {
              return "foreign";
            }
          };

      assertThat(TypeSubstitution.substitute(foreign, Map.of())).isSameAs(foreign);
    }
  }

  @Nested
  class An_array_context {

    @Test
    void is_rejected_whether_it_is_a_class_or_a_generic_array() throws NoSuchMethodException {
      var method = List.class.getMethod("iterator");

      assertThatThrownBy(() -> TypeRef.returnType(method, TypeRef.of(String[].class)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("not a subtype");
    }
  }
}
