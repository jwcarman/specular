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
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class TypeRefBuildingTest {

  static class Envelope<T> {}

  @Nested
  class List_of {

    @Test
    void equals_the_same_type_captured_by_an_anonymous_subclass() {
      assertThat(TypeRef.listOf(TypeRef.of(String.class)))
          .isEqualTo(new TypeRef<List<String>>() {});
    }

    @Test
    void nests_to_build_a_list_of_lists() {
      TypeRef<List<List<String>>> built = TypeRef.listOf(TypeRef.listOf(TypeRef.of(String.class)));
      assertThat(built).isEqualTo(new TypeRef<List<List<String>>>() {});
    }

    @Test
    void rejects_a_null_element_type() {
      assertThatNullPointerException().isThrownBy(() -> TypeRef.listOf(null));
    }
  }

  @Nested
  class Set_of {

    @Test
    void equals_the_same_type_captured_by_an_anonymous_subclass() {
      assertThat(TypeRef.setOf(TypeRef.of(String.class))).isEqualTo(new TypeRef<Set<String>>() {});
    }
  }

  @Nested
  class Optional_of {

    @Test
    void equals_the_same_type_captured_by_an_anonymous_subclass() {
      assertThat(TypeRef.optionalOf(TypeRef.of(String.class)))
          .isEqualTo(new TypeRef<Optional<String>>() {});
    }
  }

  @Nested
  class Map_of {

    @Test
    void equals_the_same_type_captured_by_an_anonymous_subclass() {
      assertThat(TypeRef.mapOf(TypeRef.of(String.class), TypeRef.of(Integer.class)))
          .isEqualTo(new TypeRef<Map<String, Integer>>() {});
    }
  }

  @Nested
  class Parameterized {

    @Test
    void builds_any_generic_class_from_its_arguments() {
      TypeRef<Envelope<String>> built =
          TypeRef.parameterized(Envelope.class, TypeRef.of(String.class));

      assertThat(built).isEqualTo(new TypeRef<Envelope<String>>() {});
    }

    @Test
    void rejects_a_class_with_no_type_parameters() {
      TypeRef<String> argument = TypeRef.of(String.class);

      assertThatThrownBy(() -> TypeRef.parameterized(String.class, argument))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("not a generic class");
    }

    @Test
    void rejects_the_wrong_number_of_arguments() {
      TypeRef<String> argument = TypeRef.of(String.class);

      assertThatThrownBy(() -> TypeRef.parameterized(Map.class, argument))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("2 type parameter(s) but 1 argument(s)");
    }

    @Test
    void rejects_a_primitive_type_argument() {
      TypeRef<Integer> primitive = TypeRef.of(int.class);

      assertThatThrownBy(() -> TypeRef.listOf(primitive))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("cannot be primitive");
    }
  }

  @Nested
  class A_built_type {

    @Test
    void reports_the_raw_class_of_its_generic_class() {
      assertThat(TypeRef.listOf(TypeRef.of(String.class)).rawClass()).isEqualTo(List.class);
    }

    @Test
    void is_assignable_from_the_same_type_captured_by_an_anonymous_subclass() {
      assertThat(TypeRef.listOf(TypeRef.of(String.class)))
          .matches(ref -> ref.isAssignableFrom(new TypeRef<List<String>>() {}));
    }
  }
}
