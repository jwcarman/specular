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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class TypeRefAssignableTest {

  @Nested
  class raw_types {

    @Test
    void identity_is_accepted() {
      assertThat(TypeRef.of(String.class).isAssignableFrom(String.class)).isTrue();
    }

    @Test
    void subtype_is_accepted() {
      assertThat(TypeRef.of(CharSequence.class).isAssignableFrom(String.class)).isTrue();
    }

    @Test
    void supertype_is_rejected() {
      assertThat(TypeRef.of(String.class).isAssignableFrom(Object.class)).isFalse();
    }

    @Test
    void unrelated_types_are_rejected() {
      assertThat(TypeRef.of(String.class).isAssignableFrom(Integer.class)).isFalse();
    }
  }

  @Nested
  class parameterized_invariance {

    @Test
    void identical_parameterization_is_accepted() {
      TypeRef<Map<String, String>> target = new TypeRef<>() {};
      TypeRef<Map<String, String>> source = new TypeRef<>() {};
      assertThat(target.isAssignableFrom(source)).isTrue();
    }

    @Test
    void narrower_value_type_into_wider_slot_is_rejected() {
      TypeRef<Map<String, Object>> target = new TypeRef<>() {};
      TypeRef<Map<String, String>> source = new TypeRef<>() {};
      assertThat(target.isAssignableFrom(source)).isFalse();
    }

    @Test
    void wider_value_type_into_narrower_slot_is_rejected() {
      TypeRef<Map<String, String>> target = new TypeRef<>() {};
      TypeRef<Map<String, Object>> source = new TypeRef<>() {};
      assertThat(target.isAssignableFrom(source)).isFalse();
    }
  }

  @Nested
  class wildcards {

    @Test
    void unbounded_wildcard_accepts_any_parameterization() {
      TypeRef<Map<?, ?>> target = new TypeRef<>() {};
      assertThat(target.isAssignableFrom(new TypeRef<Map<String, Integer>>() {})).isTrue();
    }

    @Test
    void upper_bounded_wildcard_accepts_in_bound_value() {
      TypeRef<Map<String, ? extends CharSequence>> target = new TypeRef<>() {};
      assertThat(target.isAssignableFrom(new TypeRef<Map<String, String>>() {})).isTrue();
    }

    @Test
    void upper_bounded_wildcard_rejects_out_of_bound_value() {
      TypeRef<Map<String, ? extends Number>> target = new TypeRef<>() {};
      assertThat(target.isAssignableFrom(new TypeRef<Map<String, String>>() {})).isFalse();
    }

    @Test
    void lower_bounded_wildcard_accepts_supertype_value() {
      TypeRef<List<? super Integer>> target = new TypeRef<>() {};
      assertThat(target.isAssignableFrom(new TypeRef<List<Number>>() {})).isTrue();
    }
  }

  @Nested
  class nested_generics {

    @Test
    void identical_nested_parameterization_is_accepted() {
      TypeRef<List<Map<String, String>>> target = new TypeRef<>() {};
      TypeRef<List<Map<String, String>>> source = new TypeRef<>() {};
      assertThat(target.isAssignableFrom(source)).isTrue();
    }

    @Test
    void mismatched_inner_parameterization_is_rejected() {
      TypeRef<List<Map<String, String>>> target = new TypeRef<>() {};
      TypeRef<List<Map<String, Integer>>> source = new TypeRef<>() {};
      assertThat(target.isAssignableFrom(source)).isFalse();
    }
  }

  @Nested
  class cross_kind {

    @Test
    void raw_subclass_of_parameterized_target_is_accepted() {
      TypeRef<Map<String, String>> target = new TypeRef<>() {};
      TypeRef<HashMap<String, String>> source = new TypeRef<>() {};
      assertThat(target.isAssignableFrom(source)).isTrue();
    }

    @Test
    void raw_target_accepts_parameterized_value() {
      TypeRef<?> target = TypeRef.of(Map.class);
      TypeRef<Map<String, String>> source = new TypeRef<>() {};
      assertThat(target.isAssignableFrom(source)).isTrue();
    }

    @Test
    void concrete_implementation_is_accepted_by_interface_type() {
      TypeRef<List<String>> target = new TypeRef<>() {};
      TypeRef<ArrayList<String>> source = new TypeRef<>() {};
      assertThat(target.isAssignableFrom(source)).isTrue();
    }
  }
}
