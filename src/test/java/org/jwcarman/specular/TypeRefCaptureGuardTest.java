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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class TypeRefCaptureGuardTest {

  private static <X> TypeRef<X> captureInGenericContext() {
    return new TypeRef<>() {};
  }

  @Nested
  class Capturing_a_type_variable {

    @Test
    void throws_instead_of_producing_an_unusable_reference() {
      assertThatThrownBy(TypeRefCaptureGuardTest::captureInGenericContext)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("X");
    }
  }
}
