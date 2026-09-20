# Assignability

`Class.isAssignableFrom` answers the question after erasure, which means it
cannot tell `List<String>` from `List<Order>`. Specular answers it with the type
arguments intact.

## Both directions

```java
TypeRef<CharSequence> target = TypeRef.of(CharSequence.class);
TypeRef<String> source = TypeRef.of(String.class);

target.isAssignableFrom(source);   // true — a String fits a CharSequence slot
source.isAssignableTo(target);     // true — the same question, asked the other way
```

Use whichever reads better where you are: `isAssignableFrom` when you hold the
target and are vetting candidates, `isAssignableTo` when you hold a value's type
and are looking for somewhere to put it.

Each has three overloads, taking a `Type`, a `Class` or another `TypeRef`.

## Generics are invariant

This is the whole reason to reach for it:

```java
TypeRef<List<Object>> objects = new TypeRef<>() {};
TypeRef<List<String>> strings = new TypeRef<>() {};

objects.isAssignableFrom(strings);   // false
```

A `List<String>` is **not** a `List<Object>`, because you could put an `Integer`
into the latter. Erasure-based checks get this wrong; this one does not.

Wildcards behave as Java says they should:

```java
TypeRef<List<? extends Number>> numbers = new TypeRef<>() {};

numbers.isAssignableFrom(new TypeRef<List<Integer>>() {});   // true
numbers.isAssignableFrom(new TypeRef<List<String>>() {});    // false
```

## Raw types and primitives

A raw type is assignable to a parameterized one — an unchecked conversion, which
is what Java itself permits:

```java
new TypeRef<List<String>>() {}.isAssignableFrom(List.class);   // true
```

Primitive widening and boxing follow the language rules:

```java
TypeRef.of(long.class).isAssignableFrom(int.class);   // true
```

## Null is rejected

All six methods throw `NullPointerException` for a null argument.

This is worth spelling out because the underlying library treats a null `Type`
as *the null type*, which is assignable to every reference type — so the
tempting answer is `true`. For a caller who simply had nothing to pass, a
confident "yes" is the worst possible response, so Specular refuses instead.

## What it delegates to

Assignability is answered by Apache Commons Lang's `TypeUtils.isAssignable`,
which implements the Java Language Specification's rules for subtyping,
wildcards and bounds. Specular adds the null policy and the `TypeRef`-shaped
API around it.

!!! note "Unresolved variables"
    A reference still holding a type variable cannot give a meaningful answer —
    an unresolved variable is treated as unconstrained, so it will appear
    assignable to almost anything. Check `unresolvedVariables()` first if the
    reference came from `parameterType`, `returnType`, `fieldType` or
    `of(Type)`.
