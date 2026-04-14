# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Breaking changes

- `TypeRef.parameterType(Parameter, Class<?>)` and `TypeRef.returnType(Method, Class<?>)` now throw `IllegalArgumentException` when the context class is not a subtype of the declaring class. Previously this silently returned the unresolved type, masking caller bugs.

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
