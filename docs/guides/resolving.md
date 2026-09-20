# Resolving Type Variables

Capturing a type is the easy half. The useful half is answering questions about
types you did not write down — the parameter of a method on an interface, the
argument a subclass bound, the field of a generic base class.

## What a subclass bound

Given:

```java
interface Handler<T> {
    void handle(T value);
}

class StringHandler implements Handler<String> { ... }
```

ask for the argument at a position:

```java
TypeRef.of(StringHandler.class).typeArgument(Handler.class, 0);
// Optional[TypeRef<String>]
```

or name the variable instead of its position, which survives the declaring class
gaining another parameter later:

```java
TypeRef.of(StringHandler.class)
       .typeArgument(Handler.class.getTypeParameters()[0]);
// Optional[TypeRef<String>]
```

The `Class<? super T>` bound means the compiler rejects asking about a class
that is not a supertype of your reference — as long as it knows `T`. For a raw
or wildcard reference it cannot, and then the answer is `Optional.empty()`
rather than an exception.

## The whole supertype, not just one argument

`typeArgument` gives you one argument. `supertype` gives you the supertype in
its fully parameterized form, walking the hierarchy transitively:

```java
class StringHandler extends AbstractHandler<String> implements Handler<String> { }

TypeRef.of(StringHandler.class).supertype(Handler.class);
// TypeRef<Handler<String>>
```

If the reference is not a subtype of the class you name, this throws
`IllegalArgumentException` — unlike `typeArgument`, because a projection has no
sensible empty answer.

If the arguments cannot be resolved — a raw reference, say — the raw supertype
comes back rather than something half-built:

```java
TypeRef.of(List.class).supertype(List.class).type();   // List.class
```

## Members

Three factories, one shape. Each reads the member's generic type, and each has
an overload that resolves variables against a context class:

```java
TypeRef.parameterType(parameter);
TypeRef.parameterType(parameter, StringHandler.class);

TypeRef.returnType(method);
TypeRef.returnType(method, StringHandler.class);

TypeRef.fieldType(field);
TypeRef.fieldType(field, StringHandler.class);
```

Without a context, the member's own declaring class is used, so a variable it
declares comes back unresolved:

```java
Parameter parameter = Handler.class.getMethod("handle", Object.class).getParameters()[0];

TypeRef.parameterType(parameter).type();   // T — the type variable itself
```

With a context that binds it, you get the concrete answer:

```java
TypeRef.parameterType(parameter, StringHandler.class).type();   // String.class
```

Passing a context that is not a subtype of the declaring class is a mistake, and
throws `IllegalArgumentException` rather than silently handing back the
unresolved type.

## A parameterized context

A `Class` context can only carry what a class literal carries. When the context
class is *itself* generic, the literal has already discarded its arguments:

```java
class Base<T> { public T get() { ... } }
class Sub<X> extends Base<X> { }

TypeRef.returnType(get, Sub.class).type();   // T — Sub.class never held the argument
```

Pass a reference instead and the variable resolves all the way:

```java
TypeRef.returnType(get, new TypeRef<Sub<String>>() {}).type();   // String.class
```

All three factories take a `TypeRef` context, and it nests:

```java
TypeRef.fieldType(items, new TypeRef<Sub<Map<String, Integer>>>() {});
// TypeRef<List<Map<String, Integer>>>
```

This is the same resolution the instance methods have always done — `typeArgument`
and `supertype` accept a parameterized receiver — now available to the member
factories too.

!!! note "Passing null"
    Because the context is overloaded on `Class` and `TypeRef`, a bare `null`
    is ambiguous and will not compile. Cast it — `(Class<?>) null` — if you are
    writing a test that checks the rejection.

## When a variable cannot be resolved

Resolution is best-effort, and it is honest about what it could not do. Two
things can happen.

**The variable survives.** If the context binds a variable to *another* variable,
there is no concrete answer, and the declared type comes back unchanged:

```java
class Base<T> { public T get() { ... } }
class Sub<X> extends Base<X> { }

TypeRef.returnType(Base.class.getMethod("get"), Sub.class).type();
// T — Sub binds it to X, which is itself unresolved
```

The same is true of a method's own type variables, which no class context can
bind.

**The answer is empty.** `typeArgument` reports `Optional.empty()` rather than
handing back a reference to a dangling variable:

```java
TypeRef.of(ArrayList.class).typeArgument(List.class, 0);   // Optional.empty()
```

Either way you can check what a reference still carries:

```java
TypeRef<?> ref = TypeRef.returnType(method, Sub.class);

ref.unresolvedVariables();   // [T]
```

A reference holding an unresolved variable cannot report a raw class —
`rawClass()` throws — and its assignability answers are not meaningful. Check
before you rely on it.

## A worked example

Dispatching to the right handler for a payload, resolving what each handler
handles:

```java
Map<TypeRef<?>, Handler<?>> byType = new HashMap<>();

for (Handler<?> handler : handlers) {
    TypeRef.of(handler.getClass())
           .typeArgument(Handler.class, 0)
           .ifPresent(handled -> byType.put(handled, handler));
}
```

`TypeRef.of(handler.getClass())` reads the *runtime* class, so an anonymous or
lambda-free implementation that binds the parameter resolves cleanly. An
implementation that is itself generic has nothing to bind, `typeArgument`
returns empty, and `ifPresent` skips it — which is the behaviour you want, since
such a handler cannot claim a concrete type.
