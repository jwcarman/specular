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

/**
 * Specular: general-purpose reflection utilities for Java.
 *
 * <p>The module exports a single package containing a single public type, {@link
 * org.jwcarman.specular.TypeRef}. Apache Commons Lang is required but not re-exported: its types
 * never appear in Specular's public API, so consumers do not inherit a dependency on it.
 */
module org.jwcarman.specular {
  requires org.apache.commons.lang3;

  exports org.jwcarman.specular;

  // Tests are patched into this module, and JUnit instantiates them reflectively. A qualified
  // opens grants that access to JUnit alone: nothing else can reflect into the package, and no
  // runtime dependency on JUnit is created. Without it the build passes while the same tests
  // fail in an IDE, which runs them on the module path with no equivalent of Surefire's
  // package opening.
  opens org.jwcarman.specular to
      org.junit.platform.commons;
}
