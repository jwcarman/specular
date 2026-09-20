# Using References as Keys

Most things built on type tokens are registries: a map from a type to the thing
that handles it. That only works if two references naming the same type are
interchangeable as keys — and that is harder than it sounds, because a `Type`
can arrive from several implementations that disagree.

## The guarantee

Two references naming the same type are equal and hash alike, whichever
implementation each holds.

```java
Map<TypeRef<?>, String> registry = new HashMap<>();
registry.put(new TypeRef<List<Integer>>() {}, "registered");

registry.get(TypeRef.listOf(TypeRef.of(Integer.class)));          // "registered"
registry.get(TypeRef.returnType(method, IntegerBox.class));       // "registered"
registry.get(TypeRef.of(StringHolder.class).supertype(List.class)); // as applicable
```

All three of those hold a *different* `Type` implementation — one reflected by
the JDK, one built by Specular, one produced by Commons Lang while resolving
variables. They still find each other.

## Why that needs saying

The JDK, Commons Lang and Specular each implement `ParameterizedType`. They
agree on `equals` — the interface's contract is structural — but they compute
`hashCode` differently. Delegating to the held type's `hashCode` therefore
produces two references that are `equals` yet hash apart, which is a violation
of the `hashCode` contract and shows up as a lookup that silently misses:

```java
// What a naive implementation does:
resolved.equals(captured);                    // true
resolved.hashCode() == captured.hashCode();   // false
map.get(resolved);                            // null
```

Specular hashes the captured type **structurally** — over its raw type, owner and
arguments, recursively — rather than delegating. Equality likewise goes through
an implementation-agnostic comparison. The implementation a reference happens to
hold stops being observable.

## A registry

```java
public class HandlerRegistry {

    private final Map<TypeRef<?>, Handler<?>> handlers = new HashMap<>();

    public <T> void register(TypeRef<T> type, Handler<T> handler) {
        handlers.put(type, handler);
    }

    public Optional<Handler<?>> find(TypeRef<?> type) {
        return Optional.ofNullable(handlers.get(type));
    }
}
```

Registration and lookup can come from entirely different routes:

```java
registry.register(new TypeRef<List<Order>>() {}, orderListHandler);

TypeRef<?> wanted = TypeRef.returnType(
        OrderService.class.getMethod("recent"), OrderService.class);

registry.find(wanted);   // the handler
```

## Exact match, not assignability

A map lookup is an exact-type lookup. `List<Order>` will not find a handler
registered for `Collection<Order>` or for a raw `List`. When you want the most
specific *compatible* handler, search with assignability instead:

```java
public Optional<Handler<?>> findCompatible(TypeRef<?> type) {
    return handlers.entrySet().stream()
            .filter(entry -> entry.getKey().isAssignableFrom(type))
            .map(Map.Entry::getValue)
            .findFirst();
}
```

That is a linear scan; keep the exact-match map as the fast path and fall back
to the scan only on a miss.

## The one exception

A type built by `parameterized` for an inner class of a **generic** outer class
cannot record the outer class's own arguments:

```java
class Outer<T> {
    class Inner<U> { }
}
```

`Outer<String>.Inner<Integer>` captured by an anonymous subclass carries the
`String`; the same type built by `parameterized` does not, so the two are not
equal. This affects inner classes of generic outer classes only — static nested
classes such as `Map.Entry` are unaffected.

Every other construction route carries the owner's arguments, including
`supertype`, `where` and the member factories:

```java
TypeRef<Outer<String>.Inner<Integer>> captured = new TypeRef<>() {};

captured.supertype(Outer.Inner.class).equals(captured);   // true
```

If you need such a type as a key, capture it or resolve it rather than building
it with `parameterized`.

## Keys live as long as their classes

A `TypeRef` holds `Class` objects. Keeping a static registry of references to
classes from a redeployable class loader will pin that loader in memory, the
same as any other static map of `Class` keys. In a container that redeploys
applications, scope the registry to the application rather than to a static
field.
