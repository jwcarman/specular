# Naming a Type

There are three ways to get a `TypeRef`, and which one you reach for depends on
when the type is known.

| When you know the type | Use |
|---|---|
| At compile time | `new TypeRef<List<String>>() {}` |
| At run time, and the declared type matters | `where(...)` |
| At run time, and it does not | `listOf`, `mapOf`, `parameterized`, ... |

## Known at compile time

Capture it:

```java
TypeRef<Map<String, List<Order>>> ref = new TypeRef<>() {};
```

Or, for a plain class:

```java
TypeRef<String> ref = TypeRef.of(String.class);
```

`of(Class)` accepts a generic class, but captures it **raw**: `of(List.class)`
holds `List`, not `List<Something>`. A raw reference cannot answer questions
about its arguments — `typeArgument` returns `Optional.empty()` and `supertype`
projects to the raw supertype.

Capturing a bare type variable is rejected:

```java
static <T> TypeRef<T> broken() {
    return new TypeRef<>() {};   // throws IllegalArgumentException
}
```

`T` is not a type; it is a name standing in for one. A reference holding it
could not report a raw class or answer an assignability question, so Specular
refuses it at the point of creation rather than failing later.

## Known only at run time

### The combinators

For the common shapes, pass the argument as a reference:

```java
TypeRef<List<String>> list      = TypeRef.listOf(element);
TypeRef<Set<String>> set        = TypeRef.setOf(element);
TypeRef<Optional<String>> maybe = TypeRef.optionalOf(element);
TypeRef<Map<String, Integer>> m = TypeRef.mapOf(key, value);
TypeRef<String[]> array         = TypeRef.arrayOf(element);
```

These are fully typed: `listOf(TypeRef<E>)` returns `TypeRef<List<E>>`, so the
compiler tracks `E` for you. A built type is equal to — and hashes like — the
same type captured by an anonymous subclass.

!!! note "Arrays"
    `arrayOf` is the only way to name an array of a parameterized type
    (`List<String>[]`) without reaching into JDK internals. An array of a
    non-generic type comes back as the array `Class`, which is how the JDK
    models it, so `arrayOf(of(String.class))` equals `new TypeRef<String[]>() {}`.

### Your own generic types: `where`

For a generic class of your own, `where` substitutes a type variable in a
template you capture. The static type comes from the template, so the compiler
**proves** the result rather than you asserting it:

```java
static <E> TypeRef<Envelope<E>> envelopeOf(TypeRef<E> element) {
    return new TypeRef<Envelope<E>>() {}
        .where(new TypeParameter<>() {}, element)
        .resolved();
}

envelopeOf(TypeRef.of(String.class));   // TypeRef<Envelope<String>>
```

`TypeParameter<T>` is the mirror image of `TypeRef<T>`: where a reference
captures a concrete type and refuses a variable, a parameter captures a variable
and refuses a concrete type.

Chain `where` to fill more than one slot:

```java
static <K, V> TypeRef<Map<K, V>> mapOf(TypeRef<K> key, TypeRef<V> value) {
    return new TypeRef<Map<K, V>>() {}
        .where(new TypeParameter<K>() {}, key)
        .where(new TypeParameter<V>() {}, value)
        .resolved();
}
```

Two guards keep this honest:

- Substituting a variable the template does not mention is rejected outright.
  It is always a mistake, and the message names the variables that *are*
  available.
- `resolved()` throws if any variable is still unresolved. Filling some slots and
  forgetting the rest would otherwise produce a reference whose declared type
  claims to be concrete while the type it holds is not.

```java
new TypeRef<Map<K, V>>() {}
    .where(new TypeParameter<K>() {}, key)
    .resolved();
// IllegalStateException: ... still has unresolved type variable(s): V
```

If you would rather look than throw, `unresolvedVariables()` reports what is
left.

Substitution reaches everywhere in the template, not just its top-level
arguments — an array component, a wildcard's bounds and the owner of an inner
class are all rewritten. A primitive argument is rejected, as it is in the
combinators.

### Any generic class: `parameterized`

When the declared type does not matter, `parameterized` builds a reference from
a raw class and its arguments:

```java
TypeRef<Envelope<String>> ref =
    TypeRef.parameterized(Envelope.class, TypeRef.of(String.class));
```

!!! warning "What `parameterized` cannot check"
    The class literal is a witness for `T`: because a raw type is a supertype of
    each of its parameterizations, the compiler will reject
    `TypeRef<List<String>> r = parameterized(Set.class, element)`. Arity is
    checked, and primitive arguments are rejected.

    It cannot check the **identity and order** of the arguments, or `T` naming a
    *subtype* of the raw class — `TypeRef<ArrayList<String>>` built from
    `List.class` is accepted. Neither fails here. `rawClass()` reports the
    erasure of the raw class rather than of `T`, and the mismatch surfaces as a
    `ClassCastException` wherever a value is finally used.

    `where` has no such gap. Prefer it when the declared type matters, and
    round-trip anything built with `parameterized` once in a test.

## From reflection

A type you found by reflecting over a member:

```java
TypeRef.parameterType(parameter);
TypeRef.returnType(method);
TypeRef.fieldType(field);
```

Each has an overload taking a context class, which resolves variables the member
inherited from a generic supertype. See
[Resolving Type Variables](resolving.md).

## From an arbitrary `Type`

```java
TypeRef<?> ref = TypeRef.of(someType);
```

This accepts any `Type`, including a wildcard or an unresolved variable, and
returns `TypeRef<?>` because a `Type` carries no compile-time information. Check
`unresolvedVariables()` before relying on `rawClass()`, which throws for a type
with no single erased class.

## Claiming what the library cannot prove

Anything read from reflection comes back as `TypeRef<?>`, because a `Field` or
`Method` does not tell the compiler what it holds. `coerced()` lets you say what
you know:

```java
TypeRef<String> firstName = TypeRef.fieldType(field).coerced();
```

It is an assertion, not a proof — nothing checks it, and a wrong claim surfaces
as a `ClassCastException` where the value is used. Reach for it only where the
compiler genuinely cannot establish the type; where it can, `where` proves it
instead.
