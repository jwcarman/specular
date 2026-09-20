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

import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.StringJoiner;

/**
 * A {@link WildcardType} fabricated at run time, so a wildcard's bounds can be rewritten by
 * substitution rather than discarded.
 *
 * <p>Bounds are normalised the way the JDK models them: the upper bounds of an unbounded or
 * lower-bounded wildcard are {@code [Object.class]}, never empty. Commons Lang can produce a
 * wildcard with an empty upper-bound array, which is equal to {@code ? extends Object} but does not
 * look like it; normalising here keeps equality and hashing in step.
 *
 * <p>Package-private: an implementation detail of substitution, handed to callers as the {@link
 * WildcardType} interface.
 */
@SuppressWarnings("java:S6206") // A record cannot express this type. Its generated equals and
// hashCode compare array *references*, where a WildcardType must compare bounds by value and
// normalise an absent upper bound to Object before doing so; its generated accessors would hand
// out the backing arrays, where these must copy; and its components would have to be named
// getUpperBounds/getLowerBounds to satisfy the interface. Every member a record provides would
// need overriding, leaving a record in name only.
final class SyntheticWildcardType implements WildcardType {

  private static final Type[] OBJECT_BOUND = {Object.class};

  private final Type[] upperBounds;
  private final Type[] lowerBounds;

  SyntheticWildcardType(Type[] upperBounds, Type[] lowerBounds) {
    this.lowerBounds = lowerBounds.clone();
    this.upperBounds = upperBounds.length == 0 ? OBJECT_BOUND.clone() : upperBounds.clone();
  }

  @Override
  public Type[] getUpperBounds() {
    return upperBounds.clone();
  }

  @Override
  public Type[] getLowerBounds() {
    return lowerBounds.clone();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof WildcardType other)) return false;
    return Arrays.equals(upperBounds, normalizedUpperBounds(other))
        && Arrays.equals(lowerBounds, other.getLowerBounds());
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(upperBounds) ^ Arrays.hashCode(lowerBounds);
  }

  @Override
  public String getTypeName() {
    if (lowerBounds.length > 0) {
      return bounded("? super ", lowerBounds);
    }
    if (upperBounds.length == 1 && Object.class.equals(upperBounds[0])) {
      return "?";
    }
    return bounded("? extends ", upperBounds);
  }

  private static String bounded(String prefix, Type[] bounds) {
    StringJoiner joined = new StringJoiner(" & ", prefix, "");
    for (Type bound : bounds) {
      joined.add(bound.getTypeName());
    }
    return joined.toString();
  }

  /** An empty upper-bound array means {@code Object}, which is what the JDK would report. */
  private static Type[] normalizedUpperBounds(WildcardType wildcard) {
    Type[] bounds = wildcard.getUpperBounds();
    return bounds.length == 0 ? OBJECT_BOUND : bounds;
  }

  @Override
  public String toString() {
    return getTypeName();
  }
}
