# smithy-transformations

A collection of reusable [Smithy](https://smithy.io/) `ProjectionTransformer`s, packaged together with the Smithy traits that drive them.

Currently provides:

- [`@addOperations`](#addoperations) — append operations to an existing service.
- [`@addMembers`](#addmembers) — append members to an existing aggregate shape (structure or union).

This transformation exists as a workaround / replacement for [smithy-lang/smithy#3105](https://github.com/smithy-lang/smithy/issues/3105).

## Installation

```scala
// sbt
"org.polyvariant.smithy-transformations" % "transformation" % "<version>"
```

```scala
// mill/scala-cli/gradle
org.polyvariant.smithy-transformations:transformation:<version>
```

## Use with smithy4s

Add the transformation jar to your smithy4s codegen classpath and enable the transformer by name:

```scala
// build.sbt
lazy val myProject = project
  .enablePlugins(Smithy4sCodegenPlugin)
  .settings(
    libraryDependencies +=
      "org.polyvariant.smithy-transformations" % "transformation" % "<version>" % Smithy4s,
    Compile / smithy4sModelTransformers += "addOperations",
  )
```

`smithy4sModelTransformers` is processed in order — list `addOperations` before any transformer that depends on the final `operations` list.

A working example lives in [`smithy4sExample/`](smithy4sExample/).

## Use with the Smithy CLI / `smithy-build`

The transformation is registered as a `software.amazon.smithy.build.ProjectionTransformer` SPI, so adding the jar to your build classpath is enough — reference it by name from `smithy-build.json`:

```json
{
  "version": "1.0",
  "projections": {
    "default": {
      "transforms": [
        { "name": "addOperations" },
        { "name": "addMembers" }
      ]
    }
  }
}
```

## `addOperations`

Given an upstream service you don't own:

```smithy
// upstream.smithy — provided by someone else
$version: "2"

namespace example

service MyService {
    operations: [A]
}

operation A {}
```

attach `@addOperations` from your own file to append operations to it:

```smithy
// ours.smithy
$version: "2"

namespace example

use smithytransformations#addOperations

apply MyService @addOperations([Another])

operation Another {}
```

After the `addOperations` transformer runs, `MyService.operations` becomes `[A, Another]`. You can have multiple `apply MyService @addOperations(...)` blocks across files — Smithy's loader [concatenates](https://smithy.io/2.0/spec/model.html#trait-conflict-resolution) them, so several consumers can each contribute their own operations.

## `addMembers`

Given an upstream aggregate shape (structure or union) you don't own:

```smithy
// upstream.smithy — provided by someone else
$version: "2"

namespace example

structure MyStruct {
    original: String
}
```

attach `@addMembers` from your own file to append members to it:

```smithy
// ours.smithy
$version: "2"

namespace example

use smithytransformations#addMembers

apply MyStruct @addMembers([
    { name: "extra", target: String }
    {
        name: "withTraits"
        target: Integer
        traits: {
            "smithy.api#required": {}
            "smithy.api#documentation": "added by addMembers"
        }
    }
])
```

After the `addMembers` transformer runs, `MyStruct` has the original `original` member plus `extra: String` and a required `withTraits: Integer` with documentation. Each entry is `{ name, target, traits? }`; `traits` is an optional map from trait shape id to that trait's node value. The selector accepts both structures and unions, and multiple `apply ... @addMembers(...)` blocks are concatenated like `@addOperations`.
