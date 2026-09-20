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
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * A parameterized context resolves variables a {@link Class} context cannot, because the class
 * literal for a generic class has already thrown its arguments away.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class TypeRefContextTest {

  public static class Base<T> {
    public List<T> items;

    public T get() {
      return null;
    }

    public void accept(T value) {
      // nothing to do
    }
  }

  /** Itself generic: no class anywhere binds X to a concrete type. */
  public static class Sub<X> extends Base<X> {}

  public static class StringSub extends Sub<String> {}

  private static Method get() throws NoSuchMethodException {
    return Base.class.getMethod("get");
  }

  private static Parameter accepted() throws NoSuchMethodException {
    return Base.class.getMethod("accept", Object.class).getParameters()[0];
  }

  private static Field items() throws NoSuchFieldException {
    return Base.class.getField("items");
  }

  @Nested
  class A_parameterized_context {

    @Test
    void resolves_a_return_type_a_class_context_cannot() throws NoSuchMethodException {
      TypeRef<Sub<String>> context = new TypeRef<>() {};

      assertThat(TypeRef.returnType(get(), context).type()).isEqualTo(String.class);
    }

    @Test
    void leaves_the_variable_when_only_the_class_is_known() throws NoSuchMethodException {
      assertThat(TypeRef.returnType(get(), Sub.class).type().getTypeName()).isEqualTo("T");
    }

    @Test
    void resolves_a_parameter_type() throws NoSuchMethodException {
      TypeRef<Sub<String>> context = new TypeRef<>() {};

      assertThat(TypeRef.parameterType(accepted(), context).type()).isEqualTo(String.class);
    }

    @Test
    void resolves_a_field_type() throws NoSuchFieldException {
      TypeRef<Sub<String>> context = new TypeRef<>() {};

      assertThat(TypeRef.fieldType(items(), context)).isEqualTo(new TypeRef<List<String>>() {});
    }

    @Test
    void resolves_through_a_nested_argument() throws NoSuchFieldException {
      TypeRef<Sub<Map<String, Integer>>> context = new TypeRef<>() {};

      assertThat(TypeRef.fieldType(items(), context))
          .isEqualTo(new TypeRef<List<Map<String, Integer>>>() {});
    }

    @Test
    void agrees_with_the_class_overload_for_a_concrete_context() throws NoSuchMethodException {
      TypeRef<StringSub> context = TypeRef.of(StringSub.class);

      assertThat(TypeRef.returnType(get(), context))
          .isEqualTo(TypeRef.returnType(get(), StringSub.class));
    }

    @Test
    void resolves_against_the_declaring_class_itself() throws NoSuchMethodException {
      TypeRef<Base<String>> context = new TypeRef<>() {};

      assertThat(TypeRef.returnType(get(), context).type()).isEqualTo(String.class);
    }
  }

  @Nested
  class An_unrelated_context {

    @Test
    void is_rejected_for_a_return_type() throws NoSuchMethodException {
      Method get = get();
      TypeRef<List<String>> context = new TypeRef<>() {};

      assertThatThrownBy(() -> TypeRef.returnType(get, context))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("not a subtype");
    }

    @Test
    void is_rejected_for_a_parameter_type() throws NoSuchMethodException {
      Parameter accepted = accepted();
      TypeRef<List<String>> context = new TypeRef<>() {};

      assertThatThrownBy(() -> TypeRef.parameterType(accepted, context))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("not a subtype");
    }

    @Test
    void is_rejected_for_a_field_type() throws NoSuchFieldException {
      Field items = items();
      TypeRef<List<String>> context = new TypeRef<>() {};

      assertThatThrownBy(() -> TypeRef.fieldType(items, context))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("not a subtype");
    }
  }

  @Nested
  class A_null_context {

    @Test
    void is_rejected_by_return_type() throws NoSuchMethodException {
      Method get = get();

      assertThatThrownBy(() -> TypeRef.returnType(get, (TypeRef<?>) null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    void is_rejected_by_parameter_type() throws NoSuchMethodException {
      Parameter accepted = accepted();

      assertThatThrownBy(() -> TypeRef.parameterType(accepted, (TypeRef<?>) null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    void is_rejected_by_field_type() throws NoSuchFieldException {
      Field items = items();

      assertThatThrownBy(() -> TypeRef.fieldType(items, (TypeRef<?>) null))
          .isInstanceOf(NullPointerException.class);
    }
  }
}
