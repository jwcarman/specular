# Working With Other Libraries

Specular is not a serializer. The useful pattern is to let it do the part the
serializers cannot — resolve a type variable against a context, project onto a
supertype, build a parameterized type at run time — and then hand the result to
whichever library is going to act on it.

No adapter is needed, because the hand-off is `java.lang.reflect.Type`, which
every one of these libraries already speaks.

## The conversions

```java
TypeRef<?> resolved = TypeRef.fieldType(field, new TypeRef<Box<String>>() {});

// Gson
gson.fromJson(json, resolved.type());
com.google.gson.reflect.TypeToken.get(resolved.type());

// Jackson
JavaType javaType = mapper.getTypeFactory().constructType(resolved.type());
mapper.readValue(json, javaType);

// Guava
com.google.common.reflect.TypeToken.of(resolved.type());

// Spring
ResolvableType.forType(resolved.type());
```

And back the other way, from anything that exposes its `Type`:

```java
TypeRef.of(guavaToken.getType());
TypeRef.of(gsonToken.getType());
TypeRef.of(jacksonTypeReference.getType());
TypeRef.of(cdiTypeLiteral.getType());
TypeRef.of(resolvableType.getType());
```

`of(Type)` returns `TypeRef<?>`, because a `Type` carries no compile-time
information. When the token you converted from was itself typed — Guava's
`TypeToken<T>`, Jackson's `TypeReference<T>`, CDI's `TypeLiteral<T>` — you can
recover the parameter, and it is sound to do so because that token's own capture
established it:

```java
static <T> TypeRef<T> from(TypeReference<T> token) {
    return TypeRef.of(token.getType()).coerced();
}
```

Three lines, once, in your own code. That is why Specular ships no adapter
modules: there is nothing for them to carry.

## Why this needs testing at all

The types Specular hands over are often its **own** implementations. A reference
built by `listOf`, `arrayOf` or `where`, or produced by resolving a variable,
holds a `ParameterizedType`, `GenericArrayType` or `WildcardType` that Specular
fabricated rather than reflected. Each consuming library has to interpret those
correctly, and a library that quietly fails to see a type argument does not throw
— it deserializes into `List<Object>` and the mistake surfaces somewhere else
entirely.

So the project tests this rather than assuming it. `InteropTest` asserts each
library's own view of a Specular type and, where the library allows, round-trips
a value through it.

Verified against the versions the build pins: **Gson 2.14.0**, **Jackson 3.2.2**,
**Guava 33.6.0-jre**. All three accept every shape Specular produces, including
generic arrays, substituted wildcards and types with a parameterized owner.

!!! note "These are test-scope dependencies"
    Specular's only runtime dependency is still Apache Commons Lang. Gson,
    Jackson and Guava are on the test classpath so the compatibility claim above
    is checked by CI rather than asserted in prose.

## One asymmetry: Jackson is a one-way trip

Jackson's `JavaType` *implements* `java.lang.reflect.Type`, so `TypeRef.of(...)`
will happily accept one — and give you back a reference that cannot answer
anything:

```java
TypeRef<?> fromJackson = TypeRef.of(mapper.getTypeFactory().constructType(type));

fromJackson.typeArguments();   // empty — a JavaType is not a ParameterizedType
fromJackson.rawClass();        // throws IllegalArgumentException
```

Convert *to* Jackson freely; do not try to come back. Keep the original
reference, or rebuild from the original `Type`. Gson and Guava have no such
problem — both canonicalise into their own `ParameterizedType` implementations,
which Specular reads back structurally, so a round trip through either returns a
reference equal to the one you started with.

## A worked example

Resolving what a generic service returns, then deserializing into it:

```java
public <T> List<T> fetchAll(Class<? extends Repository<T>> repositoryType, String json) {
    TypeRef<?> elementType = TypeRef.of(repositoryType)
            .typeArgument(Repository.class, 0)
            .orElseThrow(() -> new IllegalArgumentException("raw repository type"));

    TypeRef<?> listType = TypeRef.listOf(elementType);

    return gson.fromJson(json, listType.type());
}
```

Gson cannot work out `T` from `repositoryType` on its own; Specular can, and
hands Gson a type it understands.
