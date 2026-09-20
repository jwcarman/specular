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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.TypeVariable;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class TypeRefApiTest {

  public static class Holder<T> {
    public List<T> items;
    public T value;
    public String name;

    public <M> M describe() {
      return null;
    }
  }

  public static class StringHolder extends Holder<String> {}

  private static Field field(String name) throws NoSuchFieldException {
    return Holder.class.getField(name);
  }

  @Nested
  class Assignability_in_the_other_direction {

    @Test
    void holds_for_a_subtype_target() {
      assertThat(TypeRef.of(String.class).isAssignableTo(CharSequence.class)).isTrue();
    }

    @Test
    void fails_for_an_unrelated_target() {
      assertThat(TypeRef.of(String.class).isAssignableTo(Integer.class)).isFalse();
    }

    @Test
    void honours_generic_invariance() {
      TypeRef<List<String>> source = new TypeRef<>() {};

      assertThat(source.isAssignableTo(new TypeRef<List<Object>>() {})).isFalse();
      assertThat(source.isAssignableTo(new TypeRef<List<String>>() {})).isTrue();
    }

    @Test
    void mirrors_is_assignable_from() {
      TypeRef<CharSequence> target = TypeRef.of(CharSequence.class);
      TypeRef<String> source = TypeRef.of(String.class);

      assertThat(source.isAssignableTo(target)).isEqualTo(target.isAssignableFrom(source));
    }

    @Test
    void rejects_null() {
      TypeRef<String> ref = TypeRef.of(String.class);

      assertThatThrownBy(() -> ref.isAssignableTo((TypeRef<?>) null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  class A_field_type {

    @Test
    void is_read_from_the_field() throws NoSuchFieldException {
      assertThat(TypeRef.fieldType(field("name")).type()).isEqualTo(String.class);
    }

    @Test
    void keeps_the_declared_variable_without_a_context() throws NoSuchFieldException {
      assertThat(TypeRef.fieldType(field("value")).type().getTypeName()).isEqualTo("T");
    }

    @Test
    void resolves_against_a_context() throws NoSuchFieldException {
      assertThat(TypeRef.fieldType(field("value"), StringHolder.class).type())
          .isEqualTo(String.class);
    }

    @Test
    void resolves_a_parameterized_field_against_a_context() throws NoSuchFieldException {
      assertThat(TypeRef.fieldType(field("items"), StringHolder.class))
          .isEqualTo(new TypeRef<List<String>>() {});
    }

    @Test
    void rejects_an_unrelated_context() throws NoSuchFieldException {
      Field value = field("value");

      assertThatThrownBy(() -> TypeRef.fieldType(value, Integer.class))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("not a subtype");
    }

    @Test
    void rejects_null() {
      assertThatThrownBy(() -> TypeRef.fieldType(null)).isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  class An_array_reference {

    @Test
    void is_built_from_its_component() {
      assertThat(TypeRef.arrayOf(TypeRef.of(String.class))).isEqualTo(new TypeRef<String[]>() {});
    }

    @Test
    void is_built_from_a_parameterized_component() {
      assertThat(TypeRef.arrayOf(TypeRef.listOf(TypeRef.of(String.class))))
          .isEqualTo(new TypeRef<List<String>[]>() {});
    }

    @Test
    void reports_its_component() {
      assertThat(TypeRef.arrayOf(TypeRef.of(String.class)).componentType())
          .contains(TypeRef.of(String.class));
    }

    @Test
    void reports_the_component_of_a_captured_array() {
      assertThat(new TypeRef<String[]>() {}.componentType()).contains(TypeRef.of(String.class));
    }

    @Test
    void reports_the_component_of_a_generic_array() {
      assertThat(new TypeRef<List<String>[]>() {}.componentType())
          .contains(TypeRef.listOf(TypeRef.of(String.class)));
    }

    @Test
    void has_the_array_class_as_its_raw_class() {
      assertThat(TypeRef.arrayOf(TypeRef.of(String.class)).rawClass()).isEqualTo(String[].class);
    }

    @Test
    void rejects_a_null_component() {
      assertThatThrownBy(() -> TypeRef.arrayOf(null)).isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  class A_non_array_reference {

    @Test
    void has_no_component_type() {
      assertThat(TypeRef.of(String.class).componentType()).isEmpty();
    }
  }

  @Nested
  class The_type_arguments {

    @Test
    void are_reported_in_order() {
      assertThat(new TypeRef<Map<String, Integer>>() {}.typeArguments())
          .containsExactly(TypeRef.of(String.class), TypeRef.of(Integer.class));
    }

    @Test
    void are_empty_for_a_non_parameterized_type() {
      assertThat(TypeRef.of(String.class).typeArguments()).isEmpty();
    }

    @Test
    void are_empty_for_a_raw_class() {
      assertThat(TypeRef.of(List.class).typeArguments()).isEmpty();
    }
  }

  @Nested
  class Resolving_a_named_variable {

    @Test
    void finds_the_argument_bound_to_it() {
      assertThat(TypeRef.of(StringHolder.class).typeArgument(Holder.class.getTypeParameters()[0]))
          .contains(TypeRef.of(String.class));
    }

    @Test
    void is_empty_for_a_variable_of_an_unrelated_class() {
      assertThat(TypeRef.of(String.class).typeArgument(Holder.class.getTypeParameters()[0]))
          .isEmpty();
    }

    @Test
    void is_empty_for_a_method_level_variable() throws NoSuchMethodException {
      TypeVariable<?> methodVariable = Holder.class.getMethod("describe").getTypeParameters()[0];

      assertThat(TypeRef.of(StringHolder.class).typeArgument(methodVariable)).isEmpty();
    }

    @Test
    void rejects_null() {
      TypeRef<String> ref = TypeRef.of(String.class);

      assertThatThrownBy(() -> ref.typeArgument(null)).isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  class An_array_context {

    @Test
    void is_rejected_rather_than_treated_as_its_component() throws NoSuchMethodException {
      Method describe = Holder.class.getMethod("describe");
      TypeRef<Holder<String>[]> context = TypeRef.arrayOf(new TypeRef<Holder<String>>() {});

      assertThatThrownBy(() -> TypeRef.returnType(describe, context))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("not a subtype");
    }
  }

  @Nested
  class An_array_of_void {

    @Test
    void is_rejected_with_a_clear_message() {
      TypeRef<Void> voidRef = TypeRef.of(void.class);

      assertThatThrownBy(() -> TypeRef.arrayOf(voidRef))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("void");
    }
  }

  @Nested
  class Asserting_a_type {

    @Test
    void lets_the_caller_name_what_reflection_could_not() throws NoSuchFieldException {
      Field name = Holder.class.getField("name");

      TypeRef<String> asserted = TypeRef.fieldType(name).as();

      assertThat(asserted.rawClass()).isEqualTo(String.class);
    }

    @Test
    void returns_the_same_captured_type() throws NoSuchFieldException {
      Field name = Holder.class.getField("name");

      assertThat(TypeRef.fieldType(name).as()).isEqualTo(TypeRef.of(String.class));
    }
  }
}
