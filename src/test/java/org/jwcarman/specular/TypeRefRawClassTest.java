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

import java.lang.reflect.Parameter;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class TypeRefRawClassTest {

  interface Handler<T> {
    void handle(T value);
  }

  @Nested
  class Raw_class {

    @Test
    void is_typed_so_callers_need_no_unchecked_cast_of_their_own() {
      TypeRef<Map<String, Integer>> ref = new TypeRef<>() {};

      Class<Map<String, Integer>> raw = ref.rawClass();

      assertThat(raw).isEqualTo(Map.class);
    }

    @Test
    void casts_a_value_with_a_checked_cast() {
      TypeRef<String> ref = TypeRef.of(String.class);

      String value = ref.rawClass().cast("hello");

      assertThat(value).isEqualTo("hello");
    }

    @Test
    void throws_when_the_type_has_no_single_erased_class() throws NoSuchMethodException {
      Parameter parameter = Handler.class.getMethod("handle", Object.class).getParameters()[0];
      TypeRef<?> unresolved = TypeRef.parameterType(parameter);

      assertThatThrownBy(unresolved::rawClass)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("T");
    }
  }
}
