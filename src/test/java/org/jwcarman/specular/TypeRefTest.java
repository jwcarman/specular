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
import org.junit.jupiter.api.Test;

class TypeRefTest {

  @Test
  void capturesSimpleType() {
    TypeRef<String> ref = new TypeRef<>() {};
    assertThat(ref.getType()).isEqualTo(String.class);
  }

  @Test
  void capturesParameterizedType() {
    TypeRef<Map<String, Integer>> ref = new TypeRef<>() {};
    assertThat(ref.getType()).isInstanceOf(ParameterizedType.class);
    ParameterizedType pt = (ParameterizedType) ref.getType();
    assertThat(pt.getRawType()).isEqualTo(Map.class);
    assertThat(pt.getActualTypeArguments()).containsExactly(String.class, Integer.class);
  }

  @Test
  void capturesNestedParameterizedType() {
    TypeRef<List<Map<String, Integer>>> ref = new TypeRef<>() {};
    ParameterizedType pt = (ParameterizedType) ref.getType();
    assertThat(pt.getRawType()).isEqualTo(List.class);
    assertThat(pt.getActualTypeArguments()[0]).isInstanceOf(ParameterizedType.class);
  }

  @Test
  void ofWrapsClass() {
    TypeRef<String> ref = TypeRef.of(String.class);
    assertThat(ref.getType()).isEqualTo(String.class);
  }

  @Test
  void ofRejectsNull() {
    assertThatThrownBy(() -> TypeRef.of(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void equalsAndHashCodeFollowCapturedType() {
    TypeRef<Map<String, Integer>> a = new TypeRef<>() {};
    TypeRef<Map<String, Integer>> b = new TypeRef<>() {};
    TypeRef<Map<String, Long>> c = new TypeRef<>() {};

    assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    assertThat(a).isNotEqualTo(c);
    assertThat(a).isNotEqualTo("string");
    assertThat(a).isEqualTo(a);
  }

  @Test
  void toStringIncludesTypeName() {
    TypeRef<List<String>> ref = new TypeRef<>() {};
    assertThat(ref.toString()).contains("List").contains("String");
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void rawSubclassRejected() {
    assertThatThrownBy(() -> new TypeRef() {})
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("parameterized");
  }
}
