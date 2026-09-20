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

import java.lang.reflect.TypeVariable;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class TypeRefWhereTest {

  static class Envelope<T> {}

  /** The pattern this API exists for: a typed factory for a caller's own generic type. */
  static <E> TypeRef<Envelope<E>> envelopeOf(TypeRef<E> element) {
    return new TypeRef<Envelope<E>>() {}.where(new TypeParameter<>() {}, element);
  }

  /** Substitutes a variable that the target type does not mention. */
  private static <X> TypeRef<String> substituteUnrelated(TypeRef<X> argument) {
    return TypeRef.of(String.class).where(new TypeParameter<X>() {}, argument);
  }

  static <K, V> TypeRef<Map<K, V>> mapOf(TypeRef<K> key, TypeRef<V> value) {
    return new TypeRef<Map<K, V>>() {}.where(new TypeParameter<K>() {}, key)
        .where(new TypeParameter<V>() {}, value);
  }

  @Nested
  class Substituting_a_type_parameter {

    @Test
    void produces_the_same_type_the_compiler_would_capture() {
      TypeRef<Envelope<String>> built = envelopeOf(TypeRef.of(String.class));

      assertThat(built).isEqualTo(new TypeRef<Envelope<String>>() {});
    }

    @Test
    void produces_a_reference_whose_static_type_needs_no_cast() {
      TypeRef<Envelope<String>> built = envelopeOf(TypeRef.of(String.class));

      assertThat(built.rawClass()).isEqualTo(Envelope.class);
    }

    @Test
    void substitutes_each_parameter_in_turn() {
      TypeRef<Map<String, Integer>> built =
          mapOf(TypeRef.of(String.class), TypeRef.of(Integer.class));

      assertThat(built).isEqualTo(new TypeRef<Map<String, Integer>>() {});
    }

    @Test
    void substitutes_a_nested_argument() {
      TypeRef<Envelope<List<String>>> built = envelopeOf(TypeRef.listOf(TypeRef.of(String.class)));

      assertThat(built).isEqualTo(new TypeRef<Envelope<List<String>>>() {});
    }

    @Test
    void rejects_a_parameter_the_type_does_not_mention() {
      assertThatThrownBy(() -> substituteUnrelated(TypeRef.of(Integer.class)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("no type variable");
    }

    @Test
    void rejects_a_null_parameter() {
      TypeRef<String> ref = TypeRef.of(String.class);

      assertThatThrownBy(() -> ref.where(null, TypeRef.of(Integer.class)))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejects_a_null_argument() {
      assertThatThrownBy(() -> substituteUnrelated(null)).isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  class A_type_parameter {

    @Test
    void must_capture_a_variable_not_a_concrete_type() {
      assertThatThrownBy(() -> new TypeParameter<String>() {})
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("type variable");
    }

    @Test
    @SuppressWarnings("rawtypes") // the raw construction under test cannot be written without it
    void must_be_created_as_a_parameterized_subclass() {
      assertThatThrownBy(() -> new TypeParameter() {})
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("type variable");
    }
  }

  @Nested
  class Completeness {

    @Test
    void a_fully_substituted_reference_is_resolved() {
      TypeRef<Map<String, Integer>> built =
          mapOf(TypeRef.of(String.class), TypeRef.of(Integer.class));

      assertThat(built.resolved()).isSameAs(built);
      assertThat(built.unresolvedVariables()).isEmpty();
    }

    @Test
    void a_partly_substituted_reference_names_what_is_missing() {
      assertThatThrownBy(() -> halfResolved(TypeRef.of(String.class)))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("unresolved type variable")
          .hasMessageContaining("V");
    }

    @Test
    void the_unresolved_variables_are_reported() {
      assertThat(half(TypeRef.of(String.class)).unresolvedVariables())
          .extracting(TypeVariable::getName)
          .containsExactly("V");
    }

    @Test
    void a_concrete_reference_has_no_unresolved_variables() {
      assertThat(TypeRef.of(String.class).unresolvedVariables()).isEmpty();
    }

    @Test
    void an_array_of_a_variable_is_unresolved() {
      assertThat(arrayOf().unresolvedVariables())
          .extracting(TypeVariable::getName)
          .containsExactly("E");
    }

    @Test
    void a_wildcard_bounded_by_a_variable_is_unresolved() {
      assertThat(wildcardOf().unresolvedVariables())
          .extracting(TypeVariable::getName)
          .containsExactly("E");
    }
  }

  /** Substitutes only the first of two parameters, leaving V behind. */
  private static <K, V> TypeRef<Map<K, V>> half(TypeRef<K> key) {
    return new TypeRef<Map<K, V>>() {}.where(new TypeParameter<K>() {}, key);
  }

  private static <K, V> TypeRef<Map<K, V>> halfResolved(TypeRef<K> key) {
    return new TypeRef<Map<K, V>>() {}.where(new TypeParameter<K>() {}, key).resolved();
  }

  private static <E> TypeRef<E[]> arrayOf() {
    return new TypeRef<E[]>() {};
  }

  private static <E> TypeRef<List<? extends E>> wildcardOf() {
    return new TypeRef<List<? extends E>>() {};
  }
}
