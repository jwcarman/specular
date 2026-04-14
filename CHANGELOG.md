# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.0] - 2026-04-14

### Added

- Initial scaffold.
- `TypeRef<T>`: super-type token for capturing generic types at compile time via an anonymous subclass. Includes a static `of(Class<T>)` factory for non-generic types.
- `Types`: reflection helpers for resolving generic type parameters (`typeParamFromClass`, `typeParamFromType`) and method parameter types in a class hierarchy (`resolveParameterType`).

### Requirements

- Java 25+
- Apache Commons Lang 3 (runtime)

[Unreleased]: https://github.com/jwcarman/specular/compare/0.1.0...HEAD
[0.1.0]: https://github.com/jwcarman/specular/releases/tag/0.1.0
