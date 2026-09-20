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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class TypeRefRobustnessTest {

  public static class Base<T> {
    public T get() {
      return null;
    }

    public <M> M generic() {
      return null;
    }
  }

  public static class Sub<X> extends Base<X> {}

  public static class Box<T> {
    public List<T> contents() {
      return null;
    }
  }

  public static class IntegerBox extends Box<Integer> {}

  /** An intermediate subclass whose own parameters are not in TypeRef's order. */
  static class Mid<A, B> extends TypeRef<B> {}

  /** A named subclass that already binds the type argument. */
  static class Concrete extends TypeRef<String> {}

  private static Method method(Class<?> owner, String name) throws NoSuchMethodException {
    return owner.getMethod(name);
  }

  @Nested
  class When_the_class_asked_about_is_unrelated {

    // The `Class<? super T>` bound holds only while the compiler knows T. A raw reference —
    // or any erasure of one — reaches these call sites with an unrelated class.
    @SuppressWarnings("rawtypes") // a raw reference is the only way to reach this call site
    private final TypeRef unrelated = TypeRef.of(String.class);

    @Test
    void type_argument_is_empty_rather_than_throwing() {
      assertThat(unrelated.typeArgument(Set.class, 0)).isEmpty();
    }

    @Test
    void supertype_reports_the_mismatch() {
      assertThatThrownBy(() -> unrelated.supertype(Set.class))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("not a subtype");
    }
  }

  @Nested
  class When_the_type_arguments_cannot_be_resolved {

    @Test
    void supertype_of_a_raw_class_stays_raw_instead_of_holding_a_null() {
      TypeRef<?> projected = TypeRef.of(List.class).supertype(List.class);

      assertThat(projected.type()).isEqualTo(List.class);
      assertThat(projected).hasToString("TypeRef<java.util.List>");
    }

    @Test
    void supertype_of_a_raw_subclass_stays_raw() {
      assertThat(TypeRef.of(ArrayList.class).supertype(List.class).type()).isEqualTo(List.class);
    }

    @Test
    void type_argument_of_a_raw_self_reference_is_empty() {
      assertThat(TypeRef.of(List.class).typeArgument(List.class, 0)).isEmpty();
    }

    @Test
    void type_argument_of_a_raw_subclass_is_empty() {
      assertThat(TypeRef.of(ArrayList.class).typeArgument(List.class, 0)).isEmpty();
    }
  }

  @Nested
  class When_a_type_variable_survives_resolution {

    @Test
    void resolution_goes_as_far_as_the_context_allows() throws NoSuchMethodException {
      // Sub<X> binds Base's T to X. X has no binding of its own, so it stays — the answer is
      // expressed in the context's own variable rather than the declaring class's.
      TypeRef<?> resolved = TypeRef.returnType(method(Base.class, "get"), Sub.class);

      assertThat(resolved.type().getTypeName()).isEqualTo("X");
    }

    @Test
    void a_method_level_variable_keeps_the_variable() throws NoSuchMethodException {
      TypeRef<?> resolved = TypeRef.returnType(method(Base.class, "generic"), Sub.class);

      assertThat(resolved.type().getTypeName()).isEqualTo("M");
    }
  }

  @Nested
  class When_capture_happens_through_an_intermediate_subclass {

    @Test
    void the_argument_bound_to_TypeRef_is_the_one_captured() {
      TypeRef<String> ref = new Mid<Integer, String>() {};

      assertThat(ref.type()).isEqualTo(String.class);
    }

    @Test
    void a_named_subclass_binding_the_argument_is_captured() {
      assertThat(new Concrete() {}.type()).isEqualTo(String.class);
    }
  }

  @Nested
  class A_resolved_type {

    @Test
    void equals_the_same_type_captured_by_an_anonymous_subclass() throws NoSuchMethodException {
      TypeRef<?> resolved = TypeRef.returnType(method(Box.class, "contents"), IntegerBox.class);

      assertThat(resolved).isEqualTo(new TypeRef<List<Integer>>() {});
    }

    @Test
    void hashes_the_same_as_the_captured_type() throws NoSuchMethodException {
      TypeRef<?> resolved = TypeRef.returnType(method(Box.class, "contents"), IntegerBox.class);

      assertThat(resolved).hasSameHashCodeAs(new TypeRef<List<Integer>>() {});
    }

    @Test
    void finds_the_captured_type_as_a_map_key() throws NoSuchMethodException {
      Map<TypeRef<?>, String> cache = new HashMap<>();
      cache.put(new TypeRef<List<Integer>>() {}, "registered");

      TypeRef<?> resolved = TypeRef.returnType(method(Box.class, "contents"), IntegerBox.class);

      assertThat(cache).containsEntry(resolved, "registered");
    }

    @Test
    void finds_a_built_type_as_a_map_key() throws NoSuchMethodException {
      Map<TypeRef<?>, String> cache = new HashMap<>();
      cache.put(TypeRef.listOf(TypeRef.of(Integer.class)), "registered");

      TypeRef<?> resolved = TypeRef.returnType(method(Box.class, "contents"), IntegerBox.class);

      assertThat(cache).containsEntry(resolved, "registered");
    }
  }
}
