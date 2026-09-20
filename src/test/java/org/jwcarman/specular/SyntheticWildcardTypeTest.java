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
import java.lang.reflect.WildcardType;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SyntheticWildcardTypeTest {

  private static final Type[] NONE = new Type[0];

  private static WildcardType captured(TypeRef<?> ref) {
    return (WildcardType) ((ParameterizedType) ref.type()).getActualTypeArguments()[0];
  }

  private static SyntheticWildcardType upperBounded(Type bound) {
    return new SyntheticWildcardType(new Type[] {bound}, NONE);
  }

  private static SyntheticWildcardType lowerBounded(Type bound) {
    return new SyntheticWildcardType(NONE, new Type[] {bound});
  }

  private static SyntheticWildcardType unbounded() {
    return new SyntheticWildcardType(NONE, NONE);
  }

  @Nested
  class An_upper_bounded_wildcard {

    @Test
    void equals_the_same_wildcard_reflected_by_the_jdk() {
      assertThat(upperBounded(Number.class))
          .isEqualTo(captured(new TypeRef<List<? extends Number>>() {}));
    }

    @Test
    void is_symmetric_with_the_jdk_implementation() {
      assertThat(captured(new TypeRef<List<? extends Number>>() {}))
          .isEqualTo(upperBounded(Number.class));
    }

    @Test
    void hashes_like_the_jdk_implementation() {
      assertThat(upperBounded(Number.class))
          .hasSameHashCodeAs(captured(new TypeRef<List<? extends Number>>() {}));
    }

    @Test
    void names_itself_as_the_jdk_does() {
      assertThat(upperBounded(Number.class).getTypeName())
          .isEqualTo(captured(new TypeRef<List<? extends Number>>() {}).getTypeName());
    }

    @Test
    void reports_its_bounds() {
      assertThat(upperBounded(Number.class).getUpperBounds()).containsExactly(Number.class);
      assertThat(upperBounded(Number.class).getLowerBounds()).isEmpty();
    }
  }

  @Nested
  class A_lower_bounded_wildcard {

    @Test
    void equals_the_same_wildcard_reflected_by_the_jdk() {
      assertThat(lowerBounded(Integer.class))
          .isEqualTo(captured(new TypeRef<List<? super Integer>>() {}));
    }

    @Test
    void hashes_like_the_jdk_implementation() {
      assertThat(lowerBounded(Integer.class))
          .hasSameHashCodeAs(captured(new TypeRef<List<? super Integer>>() {}));
    }

    @Test
    void names_itself_as_the_jdk_does() {
      assertThat(lowerBounded(Integer.class).getTypeName())
          .isEqualTo(captured(new TypeRef<List<? super Integer>>() {}).getTypeName());
    }
  }

  @Nested
  class An_unbounded_wildcard {

    @Test
    void takes_object_as_its_implicit_upper_bound() {
      assertThat(unbounded().getUpperBounds()).containsExactly(Object.class);
    }

    @Test
    void equals_the_same_wildcard_reflected_by_the_jdk() {
      assertThat(unbounded()).isEqualTo(captured(new TypeRef<List<?>>() {}));
    }

    @Test
    void names_itself_with_a_bare_question_mark() {
      assertThat(unbounded().getTypeName()).isEqualTo("?");
    }

    @Test
    void equals_a_wildcard_whose_upper_bounds_are_absent_rather_than_object() {
      WildcardType sparse =
          new WildcardType() {
            @Override
            public Type[] getUpperBounds() {
              return NONE;
            }

            @Override
            public Type[] getLowerBounds() {
              return NONE;
            }
          };

      assertThat(unbounded()).isEqualTo(sparse);
    }
  }

  @Nested
  class Any_wildcard {

    @Test
    void equals_itself() {
      Type wildcard = unbounded();
      Type sameObject = wildcard;

      assertThat(wildcard).isEqualTo(sameObject);
    }

    @Test
    void does_not_equal_a_type_that_is_not_a_wildcard() {
      Type notAWildcard = String.class;

      assertThat(unbounded()).isNotEqualTo(notAWildcard);
    }

    @Test
    void does_not_equal_a_wildcard_with_different_bounds() {
      assertThat(upperBounded(Number.class)).isNotEqualTo(upperBounded(String.class));
    }

    @Test
    void does_not_equal_a_wildcard_that_differs_only_in_its_lower_bound() {
      // Both have Object as their upper bound, so equality turns on the lower bounds alone.
      assertThat(unbounded()).isNotEqualTo(lowerBounded(Integer.class));
    }

    @Test
    void names_an_intersection_of_upper_bounds() {
      Type intersection =
          new SyntheticWildcardType(new Type[] {Number.class, Comparable.class}, NONE);

      assertThat(intersection.getTypeName())
          .isEqualTo("? extends java.lang.Number & java.lang.Comparable");
    }

    @Test
    void does_not_equal_a_wildcard_bounded_the_other_way() {
      assertThat(upperBounded(Number.class)).isNotEqualTo(lowerBounded(Number.class));
    }

    @Test
    void is_what_to_string_reports() {
      SyntheticWildcardType wildcard = upperBounded(Number.class);

      assertThat(wildcard).hasToString(wildcard.getTypeName());
    }

    @Test
    void does_not_expose_its_backing_arrays() {
      SyntheticWildcardType wildcard = upperBounded(Number.class);

      wildcard.getUpperBounds()[0] = String.class;

      assertThat(wildcard.getUpperBounds()).containsExactly(Number.class);
    }
  }
}
