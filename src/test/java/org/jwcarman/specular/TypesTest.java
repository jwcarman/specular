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

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class TypesTest {

  interface Handler<T> {
    void handle(T value);
  }

  static class StringHandler implements Handler<String> {
    @Override
    public void handle(String value) {
      // no-op for test fixture
    }
  }

  static class Box<T> {}

  static class IntBox extends Box<Integer> {}

  @Nested
  class resolve_parameter_type {

    @Test
    void resolves_type_variable_bound_in_concrete_subclass() throws Exception {
      Method method = Handler.class.getMethod("handle", Object.class);
      Parameter parameter = method.getParameters()[0];

      Class<?> resolved = Types.resolveParameterType(parameter, StringHandler.class);

      assertThat(resolved).isEqualTo(String.class);
    }

    @Test
    void falls_back_to_declared_type_when_unbound() throws Exception {
      Method method = Handler.class.getMethod("handle", Object.class);
      Parameter parameter = method.getParameters()[0];

      Class<?> resolved = Types.resolveParameterType(parameter, Handler.class);

      assertThat(resolved).isEqualTo(Object.class);
    }
  }

  @Nested
  class type_param_from_class {

    @Test
    void resolves_superclass_type_argument() {
      Class<?> resolved = Types.typeParamFromClass(IntBox.class, Box.class, 0);
      assertThat(resolved).isEqualTo(Integer.class);
    }

    @Test
    void resolves_interface_type_argument() {
      Class<?> resolved = Types.typeParamFromClass(StringHandler.class, Handler.class, 0);
      assertThat(resolved).isEqualTo(String.class);
    }
  }

  @Nested
  class type_param_from_type {

    @Test
    void returns_null_when_types_are_unrelated() {
      java.lang.reflect.Type resolved = Types.typeParamFromType(String.class, Handler.class, 0);
      assertThat(resolved).isNull();
    }
  }
}
