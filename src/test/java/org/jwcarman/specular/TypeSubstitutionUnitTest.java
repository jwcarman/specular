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
    void is_reported_rather_than_silently_producing_a_wrong_type() {
      Map<TypeVariable<?>, Type> bindings = new HashMap<>();
      bindings.put(A, B);
      bindings.put(B, A);

      assertThatThrownBy(() -> TypeSubstitution.substitute(A, bindings))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("cycle");
    }

    @Test
    void is_reported_when_a_variable_is_bound_to_a_type_naming_itself() {
      Map<TypeVariable<?>, Type> bindings = new HashMap<>();
      bindings.put(A, new SyntheticParameterizedType(List.class, new Type[] {A}));

      assertThatThrownBy(() -> TypeSubstitution.substitute(A, bindings))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("cycle");
    }
  }

  @Nested
  class A_deeply_nested_type {

    @Test
    void is_substituted_all_the_way_down() {
      // Structural depth is not a cycle. Nesting far past any guard must still resolve.
      Type deep = A;
      for (int i = 0; i < 100; i++) {
        deep = new SyntheticParameterizedType(List.class, new Type[] {deep});
      }

      Type substituted = TypeSubstitution.substitute(deep, Map.of(A, String.class));

      assertThat(TypeRef.of(substituted).unresolvedVariables()).isEmpty();
    }

    @Test
    void keeps_a_variable_that_has_no_binding_however_deep_it_sits() {
      Type deep = A;
      for (int i = 0; i < 100; i++) {
        deep = new SyntheticParameterizedType(List.class, new Type[] {deep});
      }

      Type substituted = TypeSubstitution.substitute(deep, Map.of());

      assertThat(TypeRef.of(substituted).unresolvedVariables()).containsExactly(A);
    }

    @Test
    void may_mention_the_same_variable_in_sibling_positions() {
      // Two occurrences side by side are not a cycle.
      Type pair = new SyntheticParameterizedType(Map.class, new Type[] {A, A});

      Type substituted = TypeSubstitution.substitute(pair, Map.of(A, String.class));

      assertThat(TypeRef.of(substituted)).isEqualTo(new TypeRef<Map<String, String>>() {});
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
      TypeRef<String[]> arrayContext = TypeRef.of(String[].class);

      assertThatThrownBy(() -> TypeRef.returnType(method, arrayContext))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("not a subtype");
    }
  }

  @Nested
  class A_wildcard_whose_bound_becomes_a_wildcard {

    @Test
    void widens_to_unbounded_when_an_upper_bound_becomes_lower_bounded() {
      // `? extends (? super Integer)` is not a legal type; the honest widening is `?`.
      Map<TypeVariable<?>, Type> bindings =
          Map.of(A, new SyntheticWildcardType(new Type[0], new Type[] {Integer.class}));
      Type upperBoundedByA = new SyntheticWildcardType(new Type[] {A}, new Type[0]);

      Type substituted = TypeSubstitution.substitute(upperBoundedByA, bindings);

      assertThat(substituted.getTypeName()).isEqualTo("?");
    }

    @Test
    void widens_to_unbounded_when_a_lower_bound_becomes_upper_bounded() {
      Map<TypeVariable<?>, Type> bindings =
          Map.of(A, new SyntheticWildcardType(new Type[] {Number.class}, new Type[0]));
      Type lowerBoundedByA = new SyntheticWildcardType(new Type[0], new Type[] {A});

      Type substituted = TypeSubstitution.substitute(lowerBoundedByA, bindings);

      assertThat(substituted.getTypeName()).isEqualTo("?");
    }

    @Test
    void keeps_every_bound_of_an_intersection_rather_than_the_first() {
      Map<TypeVariable<?>, Type> bindings =
          Map.of(
              A,
              new SyntheticWildcardType(new Type[] {Number.class, Comparable.class}, new Type[0]));
      Type upperBoundedByA = new SyntheticWildcardType(new Type[] {A}, new Type[0]);

      Type substituted = TypeSubstitution.substitute(upperBoundedByA, bindings);

      assertThat(substituted.getTypeName())
          .isEqualTo("? extends java.lang.Number & java.lang.Comparable");
    }
  }
}
