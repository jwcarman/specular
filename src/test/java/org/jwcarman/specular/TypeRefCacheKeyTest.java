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

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class TypeRefCacheKeyTest {

  interface Handler<T> {}

  static class StringHandler implements Handler<String> {}

  @Nested
  class A_projected_supertype {

    @Test
    void equals_the_same_type_captured_by_an_anonymous_subclass() {
      TypeRef<?> projected = TypeRef.of(StringHandler.class).supertype(Handler.class);
      assertThat(projected).isEqualTo(new TypeRef<Handler<String>>() {});
    }

    @Test
    void hashes_the_same_as_the_captured_type() {
      TypeRef<?> projected = TypeRef.of(StringHandler.class).supertype(Handler.class);
      assertThat(projected).hasSameHashCodeAs(new TypeRef<Handler<String>>() {});
    }

    @Test
    void finds_the_captured_type_as_a_map_key() {
      Map<TypeRef<?>, String> cache = new HashMap<>();
      cache.put(new TypeRef<Handler<String>>() {}, "registered");

      TypeRef<?> projected = TypeRef.of(StringHandler.class).supertype(Handler.class);

      assertThat(cache).containsEntry(projected, "registered");
    }
  }
}
