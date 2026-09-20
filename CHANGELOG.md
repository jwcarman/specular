# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Breaking changes

- `TypeRef.parameterType(Parameter, Class<?>)` and `TypeRef.returnType(Method, Class<?>)` now throw `IllegalArgumentException` when the context class is not a subtype of the declaring class. Previously this silently returned the unresolved type, masking caller bugs.
- `TypeRef.getType()` renamed to `TypeRef.type()` for consistency with the other accessors.
- `TypeRef.getRawType()` replaced by `TypeRef.rawClass()`, which returns `Class<T>` rather than `Class<?>`. Callers who know the captured type no longer need an unchecked cast of their own; the single unchecked cast now lives in one documented, isolated method in the library. `rawClass()` also throws `IllegalArgumentException` where `getRawType()` returned `null` — for a type with no single erased class, such as an unresolved type variable.
- `TypeRef.rawClass()`'s javadoc no longer claims the unchecked cast is "sound by construction" unconditionally. It is sound for every reference whose `T` the compiler established, and explicitly not for one from `parameterized`, whose `T` is asserted by the caller. `parameterized` also regains the warning, carried over from the original in jwcarman/codec, about `T` naming a subtype of `raw`.
- The `protected TypeRef()` constructor now throws `IllegalArgumentException` when the captured type argument is a type variable. `new TypeRef<T>() {}` inside a generic method or class captured `T` itself, producing a reference whose raw class was `null` and whose assignability answers were meaningless. Resolution factories such as `parameterType(Parameter)` still return unresolved type variables by design; only capture through the constructor is rejected.

### Added

- `TypeRef.parameterType(Parameter, TypeRef)`, `returnType(Method, TypeRef)` and `fieldType(Field, TypeRef)` — resolve against a *parameterized* context. A `Class` context carries only what a class literal carries, so for `class Sub<X> extends Base<X>` the literal `Sub.class` has already discarded `X` and the member's variable stays unresolved. A reference keeps the argument: `returnType(get, new TypeRef<Sub<String>>() {})` resolves to `String`. This is the resolution `typeArgument` and `supertype` have always done for a parameterized receiver, now available to the member factories. Note that a bare `null` context is now ambiguous between the two overloads and needs a cast.
- `TypeRef.as()` — asserts a type reflection cannot know. `TypeRef<String> name = TypeRef.fieldType(field).as();` The library cannot prove what a field holds, but the caller often can; this makes that claim explicit and greppable instead of an unchecked cast at the call site. Where the compiler *can* establish the type, `where` proves it instead.
- `TypeRef.isAssignableTo(Type|Class|TypeRef)` — the mirror of `isAssignableFrom`, for when the value's type is what you hold and the target is what you are checking against.
- `TypeRef.fieldType(Field)` and `fieldType(Field, Class<?> context)` — the third reflection source alongside parameters and return types, with the same context-resolution behaviour.
- `TypeRef.arrayOf(TypeRef<E>)` — builds `E[]`. A `GenericArrayType` could not otherwise be constructed without JDK internals. An array of a non-generic type comes back as the array `Class`, matching how the JDK models it, so a built array type equals a captured one.
- `TypeRef.componentType()` — the component of an array, covering both the `Class` form (`String[]`) and the `GenericArrayType` form (`List<String>[]`).
- `TypeRef.typeArguments()` — this type's own arguments in order, empty when it is not parameterized.
- `TypeRef.typeArgument(TypeVariable<?>)` — resolve a variable by name rather than by position; the index-based overload now delegates to it.

- `TypeRef.where(TypeParameter, TypeRef)` and the new `TypeParameter<T>` class — substitute a type variable in a template capture to build a fully parameterized reference whose static type the compiler *proves* rather than the caller asserting. This is the only construction path with no unchecked cast and no unprovable claim:

  ```java
  static <E> TypeRef<Envelope<E>> envelopeOf(TypeRef<E> element) {
      return new TypeRef<Envelope<E>>() {}.where(new TypeParameter<>() {}, element).resolved();
  }
  ```
- `TypeRef.resolved()` — the terminal call of a `where` chain, throwing `IllegalStateException` if any type variable is still unresolved. Substituting some of a template's variables and forgetting the rest produced a reference whose declared type claimed to be concrete while its captured type was not; that is now an error where it is made rather than a puzzle where it is used.
- `TypeRef.unresolvedVariables()` — the type variables a reference still carries, so callers can check before relying on `rawClass()`.
- `module-info.java` declaring the `org.jwcarman.specular` module. Specular set no `Automatic-Module-Name`, so on the module path it was named after the jar file. `requires org.apache.commons.lang3` is non-transitive: commons-lang3 types never appear in the public API.

### Fixed

- **Substitution is now done by the library rather than by `TypeUtils.unrollVariables`,** which is not a substitution function. It does not descend into a `GenericArrayType`, it discards the bounds of a wildcard whose variable it cannot resolve, it does not rewrite an owner type, and it does not follow a binding that leads to another binding. Each of those left a type variable sitting in a type that claimed to be resolved. A second audit found all of them; one walker fixes them together:
    - Capturing through two levels of subclass with rebinding (`class Mid2<X> extends Mid<X, List<X>>`) captured `List<X>` with `X` dangling. The first fix for indirect capture only handled one level.
    - `fieldType`/`parameterType`/`returnType` never substituted into an array: `List<T>[]` came back with `T` intact.
    - A wildcard bounded by an unresolvable variable silently lost its bound — `List<? extends T>` became `List<?>`, and the variable vanished from `unresolvedVariables()` so `resolved()` passed on a type that was not resolved. The context and no-context overloads disagreed about the same field.
    - `typeArgument` and `supertype` did not follow a rebinding (`class B<U> extends A<List<U>>`, `class C extends B<String>` gave `List<U>`), so they disagreed with the member factories on the same hierarchy.
    - `where` silently did nothing when the template was an array or the substitution was nested in one.
- Two references naming the same wildcard could hash differently when one had an absent upper-bound array rather than an explicit `Object`. Equality read it as `? extends Object`; hashing did not. Hashing now normalises the same way.
- `where` rejects a primitive argument, as the combinators already did, instead of fabricating `List<int>`.
- An array context (`Sub<String>[]`) was accepted as if it were its component type, because `getTypeArguments` strips the array. It is now rejected.
- `arrayOf(TypeRef.of(void.class))` threw `UnsupportedOperationException` from deep inside the JDK; it now reports what is wrong.

- `isAssignableFrom` now rejects null consistently across all three overloads. The `Type` and `Class` overloads answered `true` for null (Commons Lang reads a null type as the null type, assignable to any reference type) while the `TypeRef` overload threw `NullPointerException`. All three now throw.

- **Capture through an indirect subclass read the wrong type argument.** The constructor took the immediate superclass's first type argument, which is only `TypeRef`'s `T` when the anonymous subclass extends `TypeRef` directly. `class Mid<A, B> extends TypeRef<B>` captured `A`. Resolution now walks the hierarchy, which also makes `new Concrete() {}` work where `Concrete extends TypeRef<String>` previously threw.
- **`typeArgument` and `supertype` threw `NullPointerException`** when the captured type was not a subtype of the class asked about — reachable with no warning through `parameterized`, and through any raw reference. `typeArgument` now returns `Optional.empty()` and `supertype` throws `IllegalArgumentException` naming the mismatch.
- **`supertype` could build a parameterized type holding a `null` argument** for a raw self-reference such as `of(List.class).supertype(List.class)`, which then threw from `toString()` while `hashCode()` and `rawClass()` silently succeeded. It now returns the raw supertype when an argument cannot be resolved. `typeArgument` likewise returns `Optional.empty()` rather than a reference to a dangling type variable.
- **`parameterType`/`returnType` threw `NullPointerException` with the message "type must not be null"** when a top-level type variable could not be resolved against the context. The declared type now comes back unchanged.
- **Equal references could hash differently, defeating the advertised cache-key contract.** A reference from `parameterType`/`returnType` holds a Commons Lang `ParameterizedType`, which is `equals` to the JDK's but hashes differently — so it could not find a captured key in a `HashMap`. Hashing is now structural and independent of the `Type` implementation, and equality is delegated to an implementation-agnostic comparison. (The earlier fix covered only `supertype`.)


- `TypeRef.listOf`, `setOf`, `optionalOf` and `mapOf` — build a reference to a parameterized JDK collection type from the references to its arguments, keeping the compiler in the loop when the argument type is only known at run time.
- `TypeRef.parameterized(Class<? super T>, TypeRef<?>...)` — the same for any other generic class. Arity and primitive arguments are checked at construction; the raw class literal acts as a compile-time witness for `T`.

### Fixed

- `TypeRef.supertype(Class)` results are now usable as hash-based cache keys. The projected reference was `equals` to the same type captured by an anonymous subclass but hashed differently, so the two could not find each other in a `HashMap`. Parameterized types built by this library now follow the JDK's own equality, hashing and type-name conventions.

### Documentation

- `of(Class)` no longer describes itself as being for "a non-generic class" — it accepts a generic class and captures it raw, with the consequences that follow for `typeArgument` and `supertype`.
- `type()` documents the full set of shapes it can return, including `GenericArrayType`, `WildcardType` and an unresolved `TypeVariable`.
- The capturing constructor warns about `var ref = new TypeRef<>() {}`, which has nothing to infer from and silently captures `Object`.
- The class documentation records two limits: a type built by `parameterized` for an inner class of a *generic* outer class cannot carry the outer's type arguments, so it is not equal to the same type captured by an anonymous subclass; and type-walking operations recurse without a depth limit, which a pathologically nested type could exhaust. Types come from class files or in-process code, so the latter is not reachable from untrusted input.

## [0.3.0] - 2026-04-14

### Breaking changes

- `Types` class removed. All functionality consolidated onto `TypeRef`:
  - `Types.resolveParameterType(Parameter, Class<?>)` → `TypeRef.parameterType(Parameter, Class<?>)` (returns `TypeRef<?>`, carrying full generic form rather than erasing to raw class).
  - `Types.typeParamFromClass(Class, Class, int)` → instance method `TypeRef.of(cls).typeArgument(definingClass, index)` returning `Optional<TypeRef<?>>`.
  - `Types.typeParamFromType(Type, Class, int)` → instance method `TypeRef.of(type).typeArgument(definingClass, index)`.

### Added

- `TypeRef.parameterType(Parameter)` and `parameterType(Parameter, Class<?> context)` — resolves a parameter's type, substituting type variables against the context's class hierarchy.
- `TypeRef.returnType(Method)` and `returnType(Method, Class<?> context)` — same for method return types.
- `TypeRef.supertype(Class<? super T>)` — returns the fully-parameterized form of a supertype in this reference's hierarchy (transitively walks the chain).
- `TypeRef.typeArgument(Class<? super T>, int)` instance method with compile-time bound ensuring the defining class is actually a supertype of the captured type.

## [0.2.0] - 2026-04-14

### Added

- `TypeRef.of(Type)` factory for wrapping arbitrary reflected types.
- `TypeRef.getRawType()` returning the erased raw `Class` of the captured type.
- `TypeRef.isAssignableFrom(Type)` / `isAssignableFrom(Class<?>)` / `isAssignableFrom(TypeRef<?>)` — generic-aware assignability checks honoring Java's invariance and wildcard bounds.

### Changed

- Test suite reorganized with `@Nested` groupings and `@DisplayNameGeneration(ReplaceUnderscores.class)` for readable output.

## [0.1.0] - 2026-04-14

### Added

- Initial scaffold.
- `TypeRef<T>`: super-type token for capturing generic types at compile time via an anonymous subclass. Includes a static `of(Class<T>)` factory for non-generic types.
- `Types`: reflection helpers for resolving generic type parameters (`typeParamFromClass`, `typeParamFromType`) and method parameter types in a class hierarchy (`resolveParameterType`).

### Requirements

- Java 25+
- Apache Commons Lang 3 (runtime)

[Unreleased]: https://github.com/jwcarman/specular/compare/0.3.0...HEAD
[0.3.0]: https://github.com/jwcarman/specular/releases/tag/0.3.0
[0.2.0]: https://github.com/jwcarman/specular/releases/tag/0.2.0
[0.1.0]: https://github.com/jwcarman/specular/releases/tag/0.1.0
