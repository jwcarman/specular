---
hide:
  - navigation
  - toc
---

<div class="jw-hero" markdown>

# Specular

<p class="jw-hero__tagline">General-purpose reflection utilities for Java.</p>

Java 25+ · one class · one dependency

<div class="jw-mirror" role="img" aria-label="TypeRef of List of String, shown with its reflection">
  <span class="jw-mirror__face">TypeRef&lt;List&lt;String&gt;&gt;</span>
  <span class="jw-mirror__glass" aria-hidden="true">TypeRef&lt;List&lt;String&gt;&gt;</span>
</div>

<p class="jw-hero__actions">
  <a class="md-button md-button--primary" href="guides/getting-started/">Get started</a>
  <a class="md-button" href="https://github.com/jwcarman/specular">View on GitHub</a>
</p>

</div>

Erasure throws away the type arguments your code was written against. `TypeRef<T>`
holds on to them, so a generic type survives into runtime as something you can
pass, compare, resolve and use as a key.

```java
TypeRef<Map<String, Integer>> ref = new TypeRef<>() {};

ref.type();       // ParameterizedType: Map<String, Integer>
ref.rawClass();   // Map.class, typed as Class<Map<String, Integer>>
```

For framework and library authors who need to answer "what concrete type does
this handler actually handle?" or "can I hand this value to that parameter?"
without hand-rolling a walk over `ParameterizedType`.

## Why

**It resolves, not just captures.** Jackson's `TypeReference` and CDI's
`TypeLiteral` record a type and stop there. Specular walks a class hierarchy:
given `StringHandler implements Handler<String>`, it will tell you the `String`.

```java
TypeRef.of(StringHandler.class).typeArgument(Handler.class, 0);
// Optional[TypeRef<String>]
```

**It is one class and one dependency.** Guava's `TypeToken` is the closest peer
and brings all of Guava. Spring's `ResolvableType` is excellent and tied to
Spring. Specular is `TypeRef` plus Commons Lang.

**References are values.** Two references naming the same type are equal and
hash alike no matter which `Type` implementation each happens to hold — one
reflected by the JDK, one built here, one produced while resolving variables.
That makes a `TypeRef` safe as a map key, which is how most registries want to
use it.

```java
Map<TypeRef<?>, Handler<?>> registry = new HashMap<>();
registry.put(new TypeRef<List<Order>>() {}, orderListHandler);

TypeRef<?> resolved = TypeRef.returnType(method, OrderService.class);
registry.get(resolved);   // finds it
```

**Mistakes fail where you make them.** Capturing a type variable throws instead
of quietly yielding a useless reference. Asking about an unrelated class returns
`Optional.empty()` instead of throwing a `NullPointerException` three frames
away. A half-substituted template is an error at the point you build it.

## Install

```xml
<dependency>
    <groupId>org.jwcarman</groupId>
    <artifactId>specular</artifactId>
    <version>0.4.0</version>
</dependency>
```

!!! note
    The snippet names the latest release. `main` runs ahead of it — anything the
    [changelog](https://github.com/jwcarman/specular/blob/main/CHANGELOG.md)
    lists under *Unreleased* is not in that release yet.
