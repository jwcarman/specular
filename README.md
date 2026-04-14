# Specular

[![CI](https://github.com/jwcarman/specular/actions/workflows/maven.yml/badge.svg)](https://github.com/jwcarman/specular/actions/workflows/maven.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/Java-25%2B-orange)](https://openjdk.org/)
[![Maven Central](https://img.shields.io/maven-central/v/org.jwcarman/specular)](https://central.sonatype.com/artifact/org.jwcarman/specular)

[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=jwcarman_specular&metric=sqale_rating)](https://sonarcloud.io/summary/new_code?id=jwcarman_specular)
[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=jwcarman_specular&metric=reliability_rating)](https://sonarcloud.io/summary/new_code?id=jwcarman_specular)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=jwcarman_specular&metric=security_rating)](https://sonarcloud.io/summary/new_code?id=jwcarman_specular)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=jwcarman_specular&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=jwcarman_specular)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=jwcarman_specular&metric=coverage)](https://sonarcloud.io/summary/new_code?id=jwcarman_specular)

General-purpose reflection utilities for Java. A single class — `TypeRef<T>` — wraps `java.lang.reflect.Type` with a super-type-token constructor, generic-aware assignability, full type-variable resolution against a subtype hierarchy, and ergonomic type-argument extraction.

## Installation

```xml
<dependency>
    <groupId>org.jwcarman</groupId>
    <artifactId>specular</artifactId>
    <version>0.3.0</version>
</dependency>
```

## Quick Start

**Capture a parameterized type:**
```java
TypeRef<Map<String, Integer>> ref = new TypeRef<>() {};
ref.getType();     // ParameterizedType: Map<String, Integer>
ref.getRawType();  // Map.class
```

**Check generic-aware assignability:**
```java
TypeRef<Map<String, Object>> target = new TypeRef<>() {};
target.isAssignableFrom(new TypeRef<Map<String, String>>() {});  // false — Java invariance
target.isAssignableFrom(new TypeRef<Map<String, Object>>() {});  // true
```

**Resolve a method parameter's type against a concrete subtype:**
```java
abstract class Handler<T> { void handle(T value); }
class StringHandler extends Handler<String> {}

Parameter p = Handler.class.getMethod("handle", Object.class).getParameters()[0];
TypeRef<?> resolved = TypeRef.parameterType(p, StringHandler.class);
resolved.getType();  // String.class (not the raw TypeVariable T)
```

## API

### Construction

```java
new TypeRef<List<String>>() {}          // super-type token (captures generic)
TypeRef.of(String.class)                // from a Class
TypeRef.of(someType)                    // from any java.lang.reflect.Type (returns TypeRef<?>)
```

### Type introspection

```java
Type    getType()      // the captured Type (may be Class or ParameterizedType)
Class<?> getRawType()  // erased raw class — e.g. Map.class for Map<String,Integer>
```

### Reflection-driven factories

```java
TypeRef<?> parameterType(Parameter p)                    // wraps p.getParameterizedType()
TypeRef<?> parameterType(Parameter p, Class<?> context)  // resolves T against context's hierarchy
TypeRef<?> returnType(Method m)
TypeRef<?> returnType(Method m, Class<?> context)
```

The `context`-aware overloads substitute type variables inherited from generic supertypes. Passing an unrelated context throws `IllegalArgumentException`.

### Type-variable resolution

```java
Optional<TypeRef<?>> typeArgument(Class<? super T> definingClass, int index)
TypeRef<?>           supertype(Class<? super T> supertype)
```

`typeArgument` pulls out a single bound type argument. `supertype` returns the fully-parameterized form of a supertype in this type's hierarchy, transitively. The `<? super T>` bound is enforced at compile time — you can't ask about a class unrelated to your TypeRef.

```java
TypeRef.of(StringHandler.class).typeArgument(Handler.class, 0);
// Optional[TypeRef<String>]

TypeRef.of(StringHandler.class).supertype(Handler.class);
// TypeRef<Handler<String>>
```

### Assignability

```java
boolean isAssignableFrom(Type other)
boolean isAssignableFrom(Class<?> other)
boolean isAssignableFrom(TypeRef<?> other)
```

Delegates to Apache Commons Lang `TypeUtils.isAssignable`, honoring Java's invariance and wildcard bounds.

## When to use

| Need | Use |
|------|-----|
| Pass a generic type at runtime | `TypeRef<T>` |
| Check if value of type X fits parameter of type Y (generic-aware) | `ref.isAssignableFrom(other)` |
| Pull `String` out of `StringHandler implements Handler<String>` | `TypeRef.of(StringHandler.class).typeArgument(Handler.class, 0)` |
| Get the full `Handler<String>` view, not just `String` | `TypeRef.of(StringHandler.class).supertype(Handler.class)` |
| Resolve a method parameter's type in a concrete subtype context | `TypeRef.parameterType(parameter, subtype)` |

### Comparison to alternatives

- **Jackson `TypeReference<T>`** — captures generic types but framework-specific, no assignability / type-argument helpers.
- **Guava `TypeToken<T>`** — closest peer. Bigger API surface. Requires all of Guava as a dep.
- **Spring `ResolvableType`** — Spring-only, tightly coupled to the framework.
- **CDI `TypeLiteral<T>`** — CDI-only.

Specular aims to be the minimal, framework-neutral option: one class, commons-lang3 as the only runtime dep.

## Requirements

- Java 25+
- Apache Commons Lang 3 (runtime)

## License

Apache License 2.0
