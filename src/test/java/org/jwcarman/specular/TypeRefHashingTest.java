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

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Every shape of {@link Type} a reference can hold must hash consistently with equality, whichever
 * implementation produced it.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class TypeRefHashingTest {

  /** A {@link Type} from outside the JDK and outside this library. */
  private record Foreign(String name) implements Type {
    @Override
    public String getTypeName() {
      return name;
    }
  }

  private static <E> TypeRef<E[]> arrayOfVariable() {
    return new TypeRef<E[]>() {};
  }

  private static <E> TypeRef<List<? extends E>> wildcardOfVariable() {
    return new TypeRef<List<? extends E>>() {};
  }

  private static <E> TypeRef<List<? super E>> lowerWildcardOfVariable() {
    return new TypeRef<List<? super E>>() {};
  }

  private static <E> TypeRef<List<E>> listOfVariable() {
    return new TypeRef<List<E>>() {};
  }

  @Nested
  class Equal_references {

    @Test
    void hash_alike_when_both_hold_an_array_type() {
      assertThat(new TypeRef<String[]>() {}).hasSameHashCodeAs(new TypeRef<String[]>() {});
    }

    @Test
    void hash_alike_when_both_hold_a_wildcard() {
      assertThat(new TypeRef<List<? extends Number>>() {})
          .hasSameHashCodeAs(new TypeRef<List<? extends Number>>() {});
    }

    @Test
    void hash_alike_when_both_hold_a_lower_bounded_wildcard() {
      assertThat(new TypeRef<List<? super Integer>>() {})
          .hasSameHashCodeAs(new TypeRef<List<? super Integer>>() {});
    }

    @Test
    void hash_alike_when_both_hold_a_type_variable() {
      assertThat(listOfVariable()).hasSameHashCodeAs(listOfVariable());
    }

    @Test
    void hash_alike_when_both_hold_an_array_of_a_variable() {
      assertThat(arrayOfVariable()).hasSameHashCodeAs(arrayOfVariable());
    }

    @Test
    void hash_alike_when_both_hold_a_wildcard_over_a_variable() {
      assertThat(wildcardOfVariable()).hasSameHashCodeAs(wildcardOfVariable());
    }

    @Test
    void hash_alike_when_both_hold_a_nested_owner_type() {
      assertThat(new TypeRef<Map.Entry<String, Integer>>() {})
          .hasSameHashCodeAs(new TypeRef<Map.Entry<String, Integer>>() {});
    }

    @Test
    void hash_alike_when_both_hold_a_foreign_type_implementation() {
      assertThat(TypeRef.of(new Foreign("x"))).hasSameHashCodeAs(TypeRef.of(new Foreign("x")));
    }
  }

  @Nested
  class Different_references {

    @Test
    void do_not_hash_alike() {
      assertThat(new TypeRef<List<String>>() {}.hashCode())
          .isNotEqualTo(new TypeRef<List<Integer>>() {}.hashCode());
    }

    @Test
    void are_not_equal_to_a_non_reference() {
      assertThat(new TypeRef<List<String>>() {}).isNotEqualTo("not a reference");
    }
  }

  @Nested
  class A_foreign_type {

    @Test
    void a_lower_bounded_wildcard_over_a_variable_is_unresolved() {
      assertThat(lowerWildcardOfVariable().unresolvedVariables())
          .extracting(java.lang.reflect.TypeVariable::getName)
          .containsExactly("E");
    }

    @Test
    void has_no_unresolved_variables() {
      assertThat(TypeRef.of(new Foreign("x")).unresolvedVariables()).isEmpty();
    }

    @Test
    void has_no_single_erased_class() {
      TypeRef<?> ref = TypeRef.of(new Foreign("x"));

      assertThatThrownBy(ref::rawClass).isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  class A_type_parameter {

    @Test
    void equals_another_capture_of_the_same_variable() {
      assertThat(parameterOfList()).isEqualTo(parameterOfList());
    }

    @Test
    void hashes_like_another_capture_of_the_same_variable() {
      assertThat(parameterOfList()).hasSameHashCodeAs(parameterOfList());
    }

    @Test
    void equals_itself() {
      TypeParameter<?> parameter = parameterOfList();

      assertThat(parameter.equals(parameter)).isTrue();
    }

    @Test
    void does_not_equal_a_different_variable() {
      assertThat(parameterOfList()).isNotEqualTo(parameterOfMap());
    }

    @Test
    void does_not_equal_a_non_parameter() {
      assertThat(parameterOfList()).isNotEqualTo("not a parameter");
    }

    @Test
    void names_the_variable_it_captured() {
      assertThat(parameterOfList()).hasToString("TypeParameter<E>");
    }

    private <E> TypeParameter<E> parameterOfList() {
      return new TypeParameter<>() {};
    }

    private <K> TypeParameter<K> parameterOfMap() {
      return new TypeParameter<>() {};
    }
  }

  @Nested
  class Substituting_into_a_type_with_other_variables {

    @Test
    void reports_the_variables_that_are_available() {
      assertThatThrownBy(() -> mismatchedSubstitution(TypeRef.of(String.class)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("no type variable")
          .hasMessageContaining("it has");
    }
  }

  private static <K, V, Z> TypeRef<Map<K, V>> mismatchedSubstitution(TypeRef<Z> other) {
    return new TypeRef<Map<K, V>>() {}.where(new TypeParameter<Z>() {}, other);
  }
}
