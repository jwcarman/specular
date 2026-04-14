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

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class TypeRefResolutionTest {

  interface Handler<T> {
    T handle(T value);
  }

  static class StringHandler implements Handler<String> {
    @Override
    public String handle(String value) {
      return value;
    }
  }

  interface Box<T> {
    List<T> contents();

    void fill(List<T> items);
  }

  static class IntegerBox implements Box<Integer> {
    @Override
    public List<Integer> contents() {
      return List.of();
    }

    @Override
    public void fill(List<Integer> items) {
      // no-op
    }
  }

  static class Concrete {
    public String greet(Map<String, Integer> env) {
      return String.valueOf(env.size());
    }
  }

  private static Parameter parameterOf(Class<?> owner, String methodName) {
    for (Method m : owner.getMethods()) {
      if (m.getName().equals(methodName)) {
        return m.getParameters()[0];
      }
    }
    throw new AssertionError("method not found: " + methodName);
  }

  private static Method methodOf(Class<?> owner, String name) {
    for (Method m : owner.getMethods()) {
      if (m.getName().equals(name)) {
        return m;
      }
    }
    throw new AssertionError("method not found: " + name);
  }

  @Nested
  class parameter_type {

    @Test
    void resolves_type_variable_against_concrete_subtype() {
      Parameter p = parameterOf(Handler.class, "handle");
      TypeRef<?> resolved = TypeRef.parameterType(p, StringHandler.class);
      assertThat(resolved.getType()).isEqualTo(String.class);
    }

    @Test
    void resolves_nested_type_variable_against_concrete_subtype() {
      Parameter p = parameterOf(Box.class, "fill");
      TypeRef<?> resolved = TypeRef.parameterType(p, IntegerBox.class);
      assertThat(resolved.getType()).isInstanceOf(ParameterizedType.class);
      assertThat(resolved.getRawType()).isEqualTo(List.class);
      ParameterizedType pt = (ParameterizedType) resolved.getType();
      assertThat(pt.getActualTypeArguments()).containsExactly(Integer.class);
    }

    @Test
    void no_context_leaves_type_variable_unresolved() {
      Parameter p = parameterOf(Handler.class, "handle");
      TypeRef<?> resolved = TypeRef.parameterType(p);
      // Without a subtype context, T stays as a TypeVariable.
      assertThat(resolved.getType().getTypeName()).isEqualTo("T");
    }

    @Test
    void concrete_method_is_unaffected_by_no_context() {
      Parameter p = parameterOf(Concrete.class, "greet");
      TypeRef<?> resolved = TypeRef.parameterType(p);
      assertThat(resolved.getRawType()).isEqualTo(Map.class);
    }

    @Test
    void null_parameter_is_rejected() {
      assertThatThrownBy(() -> TypeRef.parameterType(null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    void null_context_is_rejected() {
      Parameter p = parameterOf(Handler.class, "handle");
      assertThatThrownBy(() -> TypeRef.parameterType(p, null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  class return_type {

    @Test
    void resolves_type_variable_against_concrete_subtype() {
      Method m = methodOf(Handler.class, "handle");
      TypeRef<?> resolved = TypeRef.returnType(m, StringHandler.class);
      assertThat(resolved.getType()).isEqualTo(String.class);
    }

    @Test
    void resolves_nested_type_variable_against_concrete_subtype() {
      Method m = methodOf(Box.class, "contents");
      TypeRef<?> resolved = TypeRef.returnType(m, IntegerBox.class);
      assertThat(resolved.getRawType()).isEqualTo(List.class);
      ParameterizedType pt = (ParameterizedType) resolved.getType();
      assertThat(pt.getActualTypeArguments()).containsExactly(Integer.class);
    }

    @Test
    void no_context_leaves_type_variable_unresolved() {
      Method m = methodOf(Handler.class, "handle");
      TypeRef<?> resolved = TypeRef.returnType(m);
      assertThat(resolved.getType().getTypeName()).isEqualTo("T");
    }

    @Test
    void null_method_is_rejected() {
      assertThatThrownBy(() -> TypeRef.returnType(null)).isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  class supertype {

    @Test
    void returns_parameterized_form_of_direct_interface() {
      TypeRef<?> ref = TypeRef.of(StringHandler.class).supertype(Handler.class);
      assertThat(ref.getRawType()).isEqualTo(Handler.class);
      assertThat(ref.getType()).isInstanceOf(ParameterizedType.class);
      ParameterizedType pt = (ParameterizedType) ref.getType();
      assertThat(pt.getActualTypeArguments()).containsExactly(String.class);
    }

    @Test
    void returns_parameterized_form_through_transitive_hierarchy() {
      abstract class AbstractBox<T> implements Box<T> {}
      class NamedIntegerBox extends AbstractBox<Integer> {
        @Override
        public List<Integer> contents() {
          return List.of();
        }

        @Override
        public void fill(List<Integer> items) {
          // no-op for test fixture
        }
      }
      TypeRef<?> ref = TypeRef.of(NamedIntegerBox.class).supertype(Box.class);
      assertThat(ref.getRawType()).isEqualTo(Box.class);
      ParameterizedType pt = (ParameterizedType) ref.getType();
      assertThat(pt.getActualTypeArguments()).containsExactly(Integer.class);
    }

    @Test
    void returns_raw_class_for_non_generic_supertype() {
      TypeRef<?> ref = TypeRef.of(String.class).supertype(Object.class);
      assertThat(ref.getType()).isEqualTo(Object.class);
    }

    @Test
    void null_supertype_is_rejected() {
      var ref = TypeRef.of(String.class);
      assertThatThrownBy(() -> ref.supertype(null)).isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  class type_argument {

    @Test
    void resolves_from_interface_binding() {
      Optional<TypeRef<?>> arg = TypeRef.of(StringHandler.class).typeArgument(Handler.class, 0);
      assertThat(arg).isPresent();
      assertThat(arg.orElseThrow().getType()).isEqualTo(String.class);
    }

    @Test
    void resolves_from_interface_binding_with_parameterized_argument() {
      Optional<TypeRef<?>> arg = TypeRef.of(IntegerBox.class).typeArgument(Box.class, 0);
      assertThat(arg.orElseThrow().getType()).isEqualTo(Integer.class);
    }

    @Test
    void returns_empty_for_out_of_range_index() {
      assertThat(TypeRef.of(StringHandler.class).typeArgument(Handler.class, 5)).isEmpty();
    }

    @Test
    void returns_empty_for_negative_index() {
      assertThat(TypeRef.of(StringHandler.class).typeArgument(Handler.class, -1)).isEmpty();
    }

    @Test
    void null_defining_class_is_rejected() {
      var ref = TypeRef.of(String.class);
      assertThatThrownBy(() -> ref.typeArgument(null, 0)).isInstanceOf(NullPointerException.class);
    }
  }
}
