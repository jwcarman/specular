# Getting Started

## Install

```xml
<dependency>
    <groupId>org.jwcarman</groupId>
    <artifactId>specular</artifactId>
    <version>0.3.0</version>
</dependency>
```

Java 25 or newer. The only runtime dependency is Apache Commons Lang 3.

On the module path, Specular is `org.jwcarman.specular`:

```java
module my.app {
    requires org.jwcarman.specular;
}
```

## The problem it solves

Erasure means `Class` cannot describe a generic type. These two are the same
class at runtime:

```java
List<String> names = ...;
List<Order> orders = ...;

names.getClass() == orders.getClass();   // true — both java.util.ArrayList
```

So a method that takes `Class<T>` cannot be told the difference. A method that
takes a `TypeRef<T>` can:

```java
TypeRef<List<String>> names = new TypeRef<>() {};
TypeRef<List<Order>> orders = new TypeRef<>() {};

names.equals(orders);   // false
```

## Capture a type

Create an anonymous subclass. The type argument you write is recorded in the
class file, which is what survives erasure:

```java
TypeRef<Map<String, Integer>> ref = new TypeRef<>() {};

ref.type();          // ParameterizedType: Map<String, Integer>
ref.rawClass();      // Map.class
ref.typeArguments(); // [TypeRef<String>, TypeRef<Integer>]
```

The trailing `{}` matters — it is what creates the subclass. Without it the code
will not compile, because `TypeRef` is abstract.

!!! warning "`var` and the diamond"
    `var ref = new TypeRef<>() {}` has nothing to infer from and silently
    captures `Object`. Give the variable an explicit type, or write the argument
    out: `new TypeRef<List<String>>() {}`.

For a type you already hold as a `Class`, skip the subclass:

```java
TypeRef<String> ref = TypeRef.of(String.class);
```

## Ask what it is

```java
TypeRef<List<String>> ref = new TypeRef<>() {};

ref.type();            // the java.lang.reflect.Type
ref.rawClass();        // List.class, typed as Class<List<String>>
ref.typeArguments();   // [TypeRef<String>]
ref.componentType();   // Optional.empty() — not an array
```

`rawClass()` returns `Class<T>` rather than `Class<?>`, so you can narrow a value
with the checked `Class.cast` instead of writing an unchecked cast:

```java
List<String> value = ref.rawClass().cast(something);
```

## Ask what fits

```java
TypeRef<List<Object>> target = new TypeRef<>() {};

target.isAssignableFrom(new TypeRef<List<String>>() {});   // false — invariance
target.isAssignableFrom(new TypeRef<List<Object>>() {});   // true
```

Both directions are available: `isAssignableFrom` when you hold the target, and
`isAssignableTo` when you hold the value's type. See
[Assignability](assignability.md).

## Resolve a type variable

This is the part a plain super-type token cannot do. Given a handler interface
and an implementation that binds its parameter:

```java
interface Handler<T> {
    void handle(T value);
}

class StringHandler implements Handler<String> {
    public void handle(String value) { ... }
}
```

ask what it handles:

```java
TypeRef.of(StringHandler.class).typeArgument(Handler.class, 0);
// Optional[TypeRef<String>]
```

or resolve a method's parameter in the context of a concrete subtype:

```java
Parameter parameter = Handler.class.getMethod("handle", Object.class).getParameters()[0];

TypeRef.parameterType(parameter, StringHandler.class).type();
// String.class — not the raw type variable T
```

See [Resolving Type Variables](resolving.md) for the whole story, including what
happens when a variable cannot be resolved.

## Where to go next

- [Naming a Type](naming-a-type.md) — every way to get a reference, including
  building one when the type is only known at run time.
- [Resolving Type Variables](resolving.md) — parameters, return types, fields
  and hierarchies.
- [Using References as Keys](cache-keys.md) — the registry pattern, and the
  equality contract that makes it work.
- [Choosing a Library](alternatives.md) — honest comparison with Guava, Spring,
  Jackson and CDI.
