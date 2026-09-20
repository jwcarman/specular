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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Substitution has to rewrite every shape a type can take, not just the ones {@code
 * TypeUtils.unrollVariables} happens to handle. Each test here is a shape that was silently left
 * alone, or mangled, before the library did its own walking.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class TypeSubstitutionTest {

  public static class Holder<T> {
    public List<T>[] arrayOfParameterized;
    public T[] arrayOfVariable;
    public List<? extends T> upperBounded;
    public Comparator<? super T> lowerBounded;
    public List<List<T>> nested;
  }

  public static class StringHolder extends Holder<String> {}

  /** Rebinds its parameter on the way up, so resolution has to unroll twice. */
  public static class Outer<T> {}

  public static class Middle<U> extends Outer<List<U>> {}

  public static class Bottom extends Middle<String> {}

  /** A capture chain through the TypeRef hierarchy itself. */
  abstract static class Mid<A, B> extends TypeRef<B> {}

  abstract static class Mid2<X> extends Mid<X, List<X>> {}

  private static Field field(String name) throws NoSuchFieldException {
    return Holder.class.getField(name);
  }

  @Nested
  class An_array_of_a_parameterized_type {

    @Test
    void is_substituted() throws NoSuchFieldException {
      assertThat(TypeRef.fieldType(field("arrayOfParameterized"), StringHolder.class))
          .isEqualTo(new TypeRef<List<String>[]>() {});
    }

    @Test
    void has_no_unresolved_variables_left() throws NoSuchFieldException {
      assertThat(
              TypeRef.fieldType(field("arrayOfParameterized"), StringHolder.class)
                  .unresolvedVariables())
          .isEmpty();
    }
  }

  @Nested
  class An_array_of_a_variable {

    @Test
    void is_substituted() throws NoSuchFieldException {
      assertThat(TypeRef.fieldType(field("arrayOfVariable"), StringHolder.class))
          .isEqualTo(new TypeRef<String[]>() {});
    }
  }

  @Nested
  class A_bounded_wildcard {

    @Test
    void keeps_its_upper_bound_when_the_bound_resolves() throws NoSuchFieldException {
      assertThat(TypeRef.fieldType(field("upperBounded"), StringHolder.class))
          .isEqualTo(new TypeRef<List<? extends String>>() {});
    }

    @Test
    void keeps_its_lower_bound_when_the_bound_resolves() throws NoSuchFieldException {
      assertThat(TypeRef.fieldType(field("lowerBounded"), StringHolder.class))
          .isEqualTo(new TypeRef<Comparator<? super String>>() {});
    }

    @Test
    void keeps_an_unresolvable_bound_rather_than_dropping_it() throws NoSuchFieldException {
      // A raw context binds nothing. The bound must survive as the variable it was, not
      // silently widen to an unbounded wildcard.
      TypeRef<?> resolved = TypeRef.fieldType(field("upperBounded"), RawHolder.class);

      assertThat(resolved.type().getTypeName()).contains("? extends T");
      assertThat(resolved.unresolvedVariables()).extracting(v -> v.getName()).containsExactly("T");
    }
  }

  @SuppressWarnings("rawtypes") // a raw context is the shape under test
  public static class RawHolder extends Holder {}

  @Nested
  class A_nested_parameterized_type {

    @Test
    void is_substituted_at_every_level() throws NoSuchFieldException {
      assertThat(TypeRef.fieldType(field("nested"), StringHolder.class))
          .isEqualTo(new TypeRef<List<List<String>>>() {});
    }
  }

  @Nested
  class A_hierarchy_that_rebinds_on_the_way_up {

    @Test
    void resolves_the_type_argument_transitively() {
      assertThat(TypeRef.of(Bottom.class).typeArgument(Outer.class, 0))
          .contains(new TypeRef<List<String>>() {});
    }

    @Test
    void resolves_the_supertype_transitively() {
      assertThat(TypeRef.of(Bottom.class).supertype(Outer.class))
          .isEqualTo(new TypeRef<Outer<List<String>>>() {});
    }

    @Test
    void agrees_with_the_member_factories() {
      TypeRef<?> viaSupertype = TypeRef.of(Bottom.class).supertype(Outer.class);
      TypeRef<?> viaArgument = TypeRef.of(Bottom.class).typeArgument(Outer.class, 0).orElseThrow();

      assertThat(viaSupertype.typeArguments()).containsExactly(viaArgument);
    }
  }

  @Nested
  class Capture_through_two_levels_of_subclass {

    @Test
    void unrolls_the_rebinding() {
      TypeRef<List<String>> captured = new Mid2<>() {};

      assertThat(captured).isEqualTo(new TypeRef<List<String>>() {});
      assertThat(captured.unresolvedVariables()).isEmpty();
    }
  }

  @Nested
  class Substituting_with_where {

    @Test
    void rewrites_an_array_component() {
      assertThat(arrayTemplate(TypeRef.of(String.class))).isEqualTo(new TypeRef<String[]>() {});
    }

    @Test
    void rewrites_inside_a_wildcard_bound() {
      assertThat(wildcardTemplate(TypeRef.of(String.class)))
          .isEqualTo(new TypeRef<List<? extends String>>() {});
    }

    @Test
    void rejects_a_primitive_argument() {
      TypeRef<Integer> primitive = TypeRef.of(int.class);

      assertThatThrownBy(() -> listTemplate(primitive))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("primitive");
    }
  }

  private static <E> TypeRef<E[]> arrayTemplate(TypeRef<E> element) {
    return new TypeRef<E[]>() {}.where(new TypeParameter<E>() {}, element).resolved();
  }

  private static <E> TypeRef<List<? extends E>> wildcardTemplate(TypeRef<E> element) {
    return new TypeRef<List<? extends E>>() {}.where(new TypeParameter<E>() {}, element).resolved();
  }

  private static <E> TypeRef<List<E>> listTemplate(TypeRef<E> element) {
    return new TypeRef<List<E>>() {}.where(new TypeParameter<E>() {}, element);
  }

  @Nested
  class A_wildcard_with_a_dropped_bound {

    @Test
    void still_hashes_like_an_equal_reference() throws NoSuchFieldException {
      TypeRef<?> fromContext = TypeRef.fieldType(field("upperBounded"), StringHolder.class);
      TypeRef<List<? extends String>> captured = new TypeRef<>() {};

      assertThat(fromContext).isEqualTo(captured).hasSameHashCodeAs(captured);
    }

    @Test
    void is_found_in_a_hash_map() throws NoSuchFieldException {
      Map<TypeRef<?>, String> cache = new HashMap<>();
      cache.put(new TypeRef<List<? extends String>>() {}, "registered");

      TypeRef<?> fromContext = TypeRef.fieldType(field("upperBounded"), StringHolder.class);

      assertThat(cache).containsEntry(fromContext, "registered");
    }
  }
}
