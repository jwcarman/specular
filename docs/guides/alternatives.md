# Choosing a Library

Specular is not the only super-type token in Java, and it is not the right
choice for every project. Here is where it fits.

## At a glance

| | Specular | Guava `TypeToken` | Spring `ResolvableType` | Jackson `TypeReference` | CDI `TypeLiteral` |
|---|---|---|---|---|---|
| Captures a generic type | ✅ | ✅ | ✅ | ✅ | ✅ |
| Resolves variables against a hierarchy | ✅ | ✅ | ✅ | ❌ | ❌ |
| Generic-aware assignability | ✅ | ✅ | ✅ | ❌ | ❌ |
| Builds types at run time | ✅ | ✅ | ✅ | ❌ | ❌ |
| Usable as a map key | ✅ | ✅ | ✅ | ❌ (no `equals`) | ✅ |
| Dependency weight | Commons Lang | all of Guava | Spring Core | Jackson | CDI API |
| Surface area | one class | large | large | one class | one class |

## Use Guava's `TypeToken` if

You already depend on Guava. It is the closest peer, it is excellent, and
duplicating it would be silly. Its API is larger than Specular's — `getSubtype`,
`getTypes`, `isSubtypeOf`, method and constructor resolution — and if you need
those, use them.

Two differences worth knowing. Guava's `getRawType()` returns `Class<? super T>`
where Specular returns `Class<T>`; Guava's signature is sound in every case
while Specular's is sound for every reference the compiler established (see
[Naming a Type](naming-a-type.md#any-generic-class-parameterized)). And Guava,
like Jackson, accepts capturing a bare type variable and fails later, where
Specular rejects it at construction.

## Use Spring's `ResolvableType` if

You are in a Spring application. It is the most capable of the group —
`getGeneric(int...)`, `as()`, `getComponentType()`, field and method sources,
`forClassWithGenerics` — and it resolves capture through an indirect subclass
correctly. It is also thoroughly tied to Spring Core.

## Use Jackson's `TypeReference` if

You are passing a type to Jackson and nothing else. It captures and stops there:
no assignability, no resolution, and no `equals`, so it cannot be a map key.

## Use CDI's `TypeLiteral` if

You are passing a type to CDI. Same shape as Jackson's: capture only.

## Use Specular if

You are writing a **library** that needs to reason about generic types and does
not want to hand its users a framework dependency. That is the case it was built
for: one class, one small dependency, no opinion about how your application is
assembled.

It is also a reasonable pick in an application that needs type resolution
without already having Guava or Spring on the classpath.

## Being straight about the trade

Specular is young and small. Guava and Spring have been exercised by millions of
applications; Specular has a thorough test suite and an audit behind it, which
is not the same thing. If you already have one of them on the classpath, the
honest advice is to use it.

What Specular offers in exchange is a surface small enough to read in one
sitting, failure modes that surface where the mistake is made rather than three
frames later, and no transitive dependency beyond Commons Lang.
