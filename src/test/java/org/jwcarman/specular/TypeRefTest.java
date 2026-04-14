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

import java.lang.reflect.ParameterizedType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class TypeRefTest {

  @Nested
  class construction {

    @Test
    void captures_simple_type_from_anonymous_subclass() {
      TypeRef<String> ref = new TypeRef<>() {};
      assertThat(ref.getType()).isEqualTo(String.class);
    }

    @Test
    void captures_parameterized_type_from_anonymous_subclass() {
      TypeRef<Map<String, Integer>> ref = new TypeRef<>() {};
      assertThat(ref.getType()).isInstanceOf(ParameterizedType.class);
      ParameterizedType pt = (ParameterizedType) ref.getType();
      assertThat(pt.getRawType()).isEqualTo(Map.class);
      assertThat(pt.getActualTypeArguments()).containsExactly(String.class, Integer.class);
    }

    @Test
    void captures_nested_parameterized_type_from_anonymous_subclass() {
      TypeRef<List<Map<String, Integer>>> ref = new TypeRef<>() {};
      ParameterizedType pt = (ParameterizedType) ref.getType();
      assertThat(pt.getRawType()).isEqualTo(List.class);
      assertThat(pt.getActualTypeArguments()[0]).isInstanceOf(ParameterizedType.class);
    }

    @Test
    void of_class_wraps_class() {
      TypeRef<String> ref = TypeRef.of(String.class);
      assertThat(ref.getType()).isEqualTo(String.class);
    }

    @Test
    void of_type_wraps_parameterized_type() {
      TypeRef<Map<String, String>> seed = new TypeRef<>() {};
      TypeRef<?> wrapped = TypeRef.of(seed.getType());
      assertThat(wrapped.getType()).isEqualTo(seed.getType());
    }
  }

  @Nested
  class guards {

    @Test
    void of_class_rejects_null() {
      assertThatThrownBy(() -> TypeRef.of((Class<?>) null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    void of_type_rejects_null() {
      assertThatThrownBy(() -> TypeRef.of((java.lang.reflect.Type) null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void raw_anonymous_subclass_is_rejected() {
      assertThatThrownBy(() -> new TypeRef() {})
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("parameterized");
    }
  }

  @Nested
  class raw_type {

    @Test
    void of_class_returns_the_class() {
      assertThat(TypeRef.of(String.class).getRawType()).isEqualTo(String.class);
    }

    @Test
    void parameterized_returns_outer_erasure() {
      TypeRef<Map<String, Integer>> ref = new TypeRef<>() {};
      assertThat(ref.getRawType()).isEqualTo(Map.class);
    }

    @Test
    void nested_parameterized_returns_outer_erasure() {
      TypeRef<List<Map<String, Integer>>> ref = new TypeRef<>() {};
      assertThat(ref.getRawType()).isEqualTo(List.class);
    }
  }

  @Nested
  class value_semantics {

    @Test
    void equal_captured_types_are_equal_with_matching_hash_codes() {
      TypeRef<Map<String, Integer>> a = new TypeRef<>() {};
      TypeRef<Map<String, Integer>> b = new TypeRef<>() {};
      TypeRef<Map<String, Long>> c = new TypeRef<>() {};

      assertThat(a)
          .isEqualTo(a)
          .isEqualTo(b)
          .hasSameHashCodeAs(b)
          .isNotEqualTo(c)
          .isNotEqualTo("string");
    }

    @Test
    void to_string_includes_the_captured_type_name() {
      TypeRef<List<String>> ref = new TypeRef<>() {};
      assertThat(ref).hasToString("TypeRef<java.util.List<java.lang.String>>");
    }
  }
}
