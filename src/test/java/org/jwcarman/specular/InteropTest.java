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

import com.google.gson.Gson;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

/**
 * Specular resolves and builds types; other libraries consume them. The hand-off is {@link
 * java.lang.reflect.Type}, so no adapter is needed — but the types Specular hands over are often
 * its <em>own</em> implementations, fabricated rather than reflected, and each of these libraries
 * has to interpret them correctly.
 *
 * <p>These tests are the evidence for that claim. They assert the consuming library's own view of
 * the type, and where possible they deserialize through it: a library that quietly fails to see a
 * type argument produces {@code List<Object>}, which no assertion about the type alone would catch.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class InteropTest {

  private static final Gson GSON = new Gson();
  private static final ObjectMapper MAPPER = new ObjectMapper();

  public static class Box<T> {
    public List<T> items;
    public Map<String, T> keyed;
    public List<T>[] grouped;
    public List<? extends T> bounded;
  }

  public static class StringBox extends Box<String> {}

  public static class Outer<A> {
    public class Inner<B> {}
  }

  private static Field field(String name) throws NoSuchFieldException {
    return Box.class.getField(name);
  }

  /** The work Specular does that none of these libraries can: resolve T against a context. */
  private static TypeRef<?> resolvedItems() throws NoSuchFieldException {
    return TypeRef.fieldType(field("items"), StringBox.class);
  }

  @Nested
  class Gson_consuming_a_specular_type {

    @Test
    void deserializes_a_resolved_field_type() throws NoSuchFieldException {
      List<String> parsed = GSON.fromJson("[\"a\",\"b\"]", resolvedItems().type());

      // The point of the assertion: without the String argument this would hold something else.
      assertThat(parsed).containsExactly("a", "b");
    }

    @Test
    void deserializes_a_built_parameterized_type() {
      TypeRef<Map<String, Integer>> built =
          TypeRef.mapOf(TypeRef.of(String.class), TypeRef.of(Integer.class));

      Map<String, Integer> parsed = GSON.fromJson("{\"a\":1}", built.type());

      assertThat(parsed).containsEntry("a", 1);
    }

    @Test
    void accepts_a_built_generic_array_type() {
      TypeRef<List<String>[]> built = TypeRef.arrayOf(TypeRef.listOf(TypeRef.of(String.class)));

      com.google.gson.reflect.TypeToken<?> token =
          com.google.gson.reflect.TypeToken.get(built.type());

      assertThat(token.getRawType()).isEqualTo(List[].class);
    }

    @Test
    void canonicalises_a_substituted_wildcard() throws NoSuchFieldException {
      TypeRef<?> bounded = TypeRef.fieldType(field("bounded"), StringBox.class);

      com.google.gson.reflect.TypeToken<?> token =
          com.google.gson.reflect.TypeToken.get(bounded.type());

      assertThat(token.getType().getTypeName()).contains("String");
    }

    @Test
    void round_trips_a_value_through_a_resolved_type() throws NoSuchFieldException {
      TypeRef<?> keyed = TypeRef.fieldType(field("keyed"), StringBox.class);

      String json = GSON.toJson(Map.of("k", "v"), keyed.type());
      Map<String, String> parsed = GSON.fromJson(json, keyed.type());

      assertThat(parsed).containsEntry("k", "v");
    }
  }

  @Nested
  class Jackson_consuming_a_specular_type {

    @Test
    void constructs_a_java_type_from_a_resolved_field_type() throws NoSuchFieldException {
      JavaType javaType = MAPPER.getTypeFactory().constructType(resolvedItems().type());

      assertThat(javaType.getRawClass()).isEqualTo(List.class);
      assertThat(javaType.getContentType().getRawClass()).isEqualTo(String.class);
    }

    @Test
    void deserializes_through_a_resolved_field_type() throws NoSuchFieldException {
      JavaType javaType = MAPPER.getTypeFactory().constructType(resolvedItems().type());

      List<String> parsed = MAPPER.readValue("[\"a\",\"b\"]", javaType);

      assertThat(parsed).containsExactly("a", "b");
    }

    @Test
    void understands_a_built_generic_array_type() {
      TypeRef<List<String>[]> built = TypeRef.arrayOf(TypeRef.listOf(TypeRef.of(String.class)));

      JavaType javaType = MAPPER.getTypeFactory().constructType(built.type());

      assertThat(javaType.isArrayType()).isTrue();
      assertThat(javaType.getContentType().getRawClass()).isEqualTo(List.class);
    }

    @Test
    void understands_a_type_built_by_where() {
      TypeRef<Map<String, Integer>> built = new TypeRef<Map<String, Integer>>() {}.resolved();

      JavaType javaType = MAPPER.getTypeFactory().constructType(built.type());

      assertThat(javaType.getKeyType().getRawClass()).isEqualTo(String.class);
      assertThat(javaType.getContentType().getRawClass()).isEqualTo(Integer.class);
    }

    @Test
    void understands_a_substituted_wildcard() throws NoSuchFieldException {
      TypeRef<?> bounded = TypeRef.fieldType(field("bounded"), StringBox.class);

      JavaType javaType = MAPPER.getTypeFactory().constructType(bounded.type());

      assertThat(javaType.getContentType().getRawClass()).isEqualTo(String.class);
    }
  }

  @Nested
  class Guava_consuming_a_specular_type {

    @Test
    void accepts_a_resolved_field_type() throws NoSuchFieldException {
      com.google.common.reflect.TypeToken<?> token =
          com.google.common.reflect.TypeToken.of(resolvedItems().type());

      assertThat(token.getRawType()).isEqualTo(List.class);
      assertThat(token.getType().getTypeName()).isEqualTo("java.util.List<java.lang.String>");
    }

    @Test
    void agrees_with_specular_about_assignability() throws NoSuchFieldException {
      com.google.common.reflect.TypeToken<?> token =
          com.google.common.reflect.TypeToken.of(resolvedItems().type());

      assertThat(token.isSupertypeOf(new TypeRef<List<String>>() {}.type()))
          .isEqualTo(resolvedItems().isAssignableFrom(new TypeRef<List<String>>() {}));
    }

    @Test
    void accepts_a_built_generic_array_type() {
      TypeRef<List<String>[]> built = TypeRef.arrayOf(TypeRef.listOf(TypeRef.of(String.class)));

      com.google.common.reflect.TypeToken<?> token =
          com.google.common.reflect.TypeToken.of(built.type());

      assertThat(token.isArray()).isTrue();
      assertThat(token.getComponentType().getRawType()).isEqualTo(List.class);
    }

    @Test
    void accepts_a_type_with_a_parameterized_owner() {
      TypeRef<Outer<String>.Inner<Integer>> captured = new TypeRef<>() {};
      TypeRef<?> projected = captured.supertype(Outer.Inner.class);

      com.google.common.reflect.TypeToken<?> token =
          com.google.common.reflect.TypeToken.of(projected.type());

      assertThat(token.getRawType()).isEqualTo(Outer.Inner.class);
    }
  }

  @Nested
  class A_type_that_made_the_round_trip {

    // Gson and Guava canonicalise into their own ParameterizedType implementations, which
    // Specular reads back structurally. Jackson does not — see below.

    @Test
    void comes_back_equal_through_gson() throws NoSuchFieldException {
      TypeRef<?> original = resolvedItems();

      TypeRef<?> returned =
          TypeRef.of(com.google.gson.reflect.TypeToken.get(original.type()).getType());

      assertThat(returned).isEqualTo(original).hasSameHashCodeAs(original);
    }

    @Test
    void comes_back_equal_through_guava() throws NoSuchFieldException {
      TypeRef<?> original = resolvedItems();

      TypeRef<?> returned =
          TypeRef.of(com.google.common.reflect.TypeToken.of(original.type()).getType());

      assertThat(returned).isEqualTo(original).hasSameHashCodeAs(original);
    }

    @Test
    void cannot_come_back_through_jackson() throws NoSuchFieldException {
      // Jackson's JavaType implements java.lang.reflect.Type, so of(Type) accepts it — but a
      // JavaType is not a ParameterizedType, and nothing can read the arguments back out of it.
      // The conversion to Jackson is one-way; keep the original reference rather than rebuilding
      // one from a JavaType.
      TypeRef<?> original = resolvedItems();

      TypeRef<?> returned = TypeRef.of(MAPPER.getTypeFactory().constructType(original.type()));

      assertThat(returned).isNotEqualTo(original);
      assertThat(returned.type()).isInstanceOf(JavaType.class);
    }

    @Test
    void is_useless_when_rebuilt_from_a_java_type() throws NoSuchFieldException {
      TypeRef<?> fromJackson =
          TypeRef.of(MAPPER.getTypeFactory().constructType(resolvedItems().type()));

      // It cannot answer the questions a reference exists to answer.
      assertThat(fromJackson.typeArguments()).isEmpty();
      assertThatThrownBy(fromJackson::rawClass).isInstanceOf(IllegalArgumentException.class);
    }
  }
}
