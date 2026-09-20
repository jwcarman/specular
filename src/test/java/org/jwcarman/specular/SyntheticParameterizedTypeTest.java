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

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SyntheticParameterizedTypeTest {

  /** A generic type nested inside another class, so the built type has an owner. */
  static class Owner {
    static class Held<T> {}
  }

  private static ParameterizedType captured(TypeRef<?> ref) {
    return (ParameterizedType) ref.type();
  }

  private static SyntheticParameterizedType listOfString() {
    return new SyntheticParameterizedType(List.class, new Type[] {String.class});
  }

  @Nested
  class Equality {

    @Test
    void holds_against_itself() {
      SyntheticParameterizedType type = listOfString();
      assertThat(type.equals(type)).isTrue();
    }

    @Test
    void holds_against_the_same_type_reflected_by_the_jdk() {
      assertThat(listOfString()).isEqualTo(captured(new TypeRef<List<String>>() {}));
    }

    @Test
    void is_symmetric_with_the_jdk_implementation() {
      assertThat(captured(new TypeRef<List<String>>() {})).isEqualTo(listOfString());
    }

    @Test
    void fails_against_a_type_that_is_not_parameterized() {
      assertThat(listOfString().equals(String.class)).isFalse();
    }

    @Test
    void fails_against_null() {
      assertThat(listOfString().equals(null)).isFalse();
    }

    @Test
    void fails_when_the_raw_type_differs() {
      Type set = new SyntheticParameterizedType(java.util.Set.class, new Type[] {String.class});
      assertThat(listOfString().equals(set)).isFalse();
    }

    @Test
    void fails_when_the_type_arguments_differ() {
      Type listOfInteger = new SyntheticParameterizedType(List.class, new Type[] {Integer.class});
      assertThat(listOfString().equals(listOfInteger)).isFalse();
    }

    @Test
    void fails_when_the_owner_type_differs() {
      Type held = new SyntheticParameterizedType(Owner.Held.class, new Type[] {String.class});
      assertThat(held.equals(listOfString())).isFalse();
    }
  }

  @Nested
  class Hashing {

    @Test
    void matches_the_jdk_for_the_same_type() {
      assertThat(listOfString()).hasSameHashCodeAs(captured(new TypeRef<List<String>>() {}));
    }

    @Test
    void matches_the_jdk_for_a_type_with_an_owner() {
      Type held = new SyntheticParameterizedType(Owner.Held.class, new Type[] {String.class});
      assertThat(held).hasSameHashCodeAs(captured(new TypeRef<Owner.Held<String>>() {}));
    }
  }

  @Nested
  class The_type_name {

    @Test
    void matches_the_jdk_for_a_top_level_class() {
      assertThat(listOfString().getTypeName())
          .isEqualTo(captured(new TypeRef<List<String>>() {}).getTypeName());
    }

    @Test
    void matches_the_jdk_for_a_nested_class() {
      Type held = new SyntheticParameterizedType(Owner.Held.class, new Type[] {String.class});
      assertThat(held.getTypeName())
          .isEqualTo(captured(new TypeRef<Owner.Held<String>>() {}).getTypeName());
    }

    @Test
    void renders_every_type_argument() {
      Type map =
          new SyntheticParameterizedType(Map.class, new Type[] {String.class, Integer.class});
      assertThat(map.getTypeName()).isEqualTo("java.util.Map<java.lang.String, java.lang.Integer>");
    }

    @Test
    void is_what_to_string_reports() {
      SyntheticParameterizedType type = listOfString();
      assertThat(type).hasToString(type.getTypeName());
    }
  }

  @Nested
  class The_parts {

    @Test
    void include_the_raw_type() {
      assertThat(listOfString().getRawType()).isEqualTo(List.class);
    }

    @Test
    void include_the_type_arguments() {
      assertThat(listOfString().getActualTypeArguments()).containsExactly(String.class);
    }

    @Test
    void have_no_owner_for_a_top_level_class() {
      assertThat(listOfString().getOwnerType()).isNull();
    }

    @Test
    void name_the_declaring_class_as_the_owner_of_a_nested_class() {
      Type held = new SyntheticParameterizedType(Owner.Held.class, new Type[] {String.class});
      assertThat(((ParameterizedType) held).getOwnerType()).isEqualTo(Owner.class);
    }

    @Test
    void do_not_expose_the_backing_argument_array() {
      SyntheticParameterizedType type = listOfString();

      type.getActualTypeArguments()[0] = Integer.class;

      assertThat(type.getActualTypeArguments()).containsExactly(String.class);
    }
  }
}
