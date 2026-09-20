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

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Type;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SyntheticGenericArrayTypeTest {

  private static GenericArrayType captured() {
    return (GenericArrayType) new TypeRef<List<String>[]>() {}.type();
  }

  private static SyntheticGenericArrayType built() {
    return new SyntheticGenericArrayType(TypeRef.listOf(TypeRef.of(String.class)).type());
  }

  @Nested
  class The_built_array_type {

    @Test
    void reports_its_component() {
      assertThat(built().getGenericComponentType()).isEqualTo(captured().getGenericComponentType());
    }

    @Test
    void equals_the_same_type_reflected_by_the_jdk() {
      assertThat(built()).isEqualTo(captured());
    }

    @Test
    void is_symmetric_with_the_jdk_implementation() {
      assertThat(captured()).isEqualTo(built());
    }

    @Test
    void hashes_like_the_jdk_implementation() {
      assertThat(built()).hasSameHashCodeAs(captured());
    }

    @Test
    void equals_itself() {
      // Two references, one object: the identity short-circuit is what is under test.
      Type type = built();
      Type sameObject = type;

      assertThat(type).isEqualTo(sameObject);
    }

    @Test
    void does_not_equal_a_type_that_is_not_an_array() {
      Type notAnArray = String.class;

      assertThat(built()).isNotEqualTo(notAnArray);
    }

    @Test
    void does_not_equal_an_array_of_a_different_component() {
      Type other = new SyntheticGenericArrayType(TypeRef.setOf(TypeRef.of(String.class)).type());

      assertThat(built()).isNotEqualTo(other);
    }

    @Test
    void names_itself_as_the_jdk_does() {
      assertThat(built().getTypeName()).isEqualTo(captured().getTypeName());
    }

    @Test
    void is_what_to_string_reports() {
      SyntheticGenericArrayType type = built();

      assertThat(type).hasToString(type.getTypeName());
    }
  }
}
