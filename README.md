# smithy-transformations

A collection of reusable [Smithy](https://smithy.io/) `ProjectionTransformer`s, packaged together with the Smithy traits that drive them.

Currently provides:

- [`@addOperations`](#addoperations) — append operations to an existing service.
- [`@removeOperations`](#removeoperations) — detach operations from an existing service.
- [`@addMembers`](#addmembers) — append members to an existing aggregate shape (structure or union).
- [`@removeMembers`](#removemembers) — detach members from an existing aggregate shape.
- [`@addErrors`](#adderrors) — append errors to an existing operation or service.
- [`@removeErrors`](#removeerrors) — detach errors from an existing operation or service.
- [`removeTraits`](#removetraits) — strip traits matching a selector off every shape in the model.

These exist as a workaround / replacement for [smithy-lang/smithy#3105](https://github.com/smithy-lang/smithy/issues/3105).

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

`smithy4sModelTransformers` is processed in order — list each transformer before any that depends on the shape of the model it produces. If you use both an `add*` and the matching `remove*` transformer on the same shape, the later one wins for anything they both name.

A working example lives in [`smithy4sExample/`](smithy4sExample/).

## Use with the Smithy CLI / `smithy-build`

Each transformation is registered as a `software.amazon.smithy.build.ProjectionTransformer` SPI, so adding the jar to your build classpath is enough — reference them by name from `smithy-build.json`:

```json
{
  "version": "1.0",
  "projections": {
    "default": {
      "transforms": [
        { "name": "addOperations" },
        { "name": "removeOperations" },
        { "name": "addMembers" },
        { "name": "removeMembers" },
        { "name": "addErrors" },
        { "name": "removeErrors" },
        { "name": "removeTraits" }
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

## `removeOperations`

The inverse of `@addOperations` — detach operations from a service you don't own:

```smithy
// ours.smithy
$version: "2"

namespace example

use smithytransformations#removeOperations

apply MyService @removeOperations([A])
```

After the `removeOperations` transformer runs, `A` is gone from `MyService.operations`. Only the service's `operations` list is touched — the `A` shape itself stays in the model, so another service can still bind it. Pair it with smithy-build's own [`removeUnusedShapes`](https://smithy.io/2.0/guides/smithy-build-json.html#removeunusedshapes) if you want the now-orphaned operation gone too.

Naming an operation the service doesn't have is ignored rather than an error, which keeps the trait usable against an upstream model that may or may not bind it.

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

## `removeMembers`

The inverse of `@addMembers` — detach members from an aggregate shape you don't own:

```smithy
// ours.smithy
$version: "2"

namespace example

use smithytransformations#removeMembers

apply MyStruct @removeMembers(["original"])
```

After the `removeMembers` transformer runs, `MyStruct` no longer has an `original` member. Entries are plain member names, not shape ids, and they're matched **case-insensitively** — matching how Smithy itself treats member name uniqueness. Naming a member the shape doesn't have is ignored rather than an error.

Only the container is rewritten, so removing a member that something else still depends on is your responsibility to avoid — dropping a member referenced by an `@httpLabel` binding or a `@required` contract elsewhere will surface as a validation failure downstream, not here.

## `addErrors`

Both operations and services have an `errors` property, so one trait covers both. On an operation the errors are specific to that operation; on a service they apply to every operation it contains.

Given an upstream operation you don't own:

```smithy
// upstream.smithy — provided by someone else
$version: "2"

namespace example

operation A {
    errors: [ExistingError]
}

@error("client")
structure ExistingError {}
```

attach `@addErrors` from your own file to append errors to it:

```smithy
// ours.smithy
$version: "2"

namespace example

use smithytransformations#addErrors

apply A @addErrors([BoomError])

@error("server")
structure BoomError {}
```

After the `addErrors` transformer runs, `A.errors` becomes `[ExistingError, BoomError]`. Apply it to a service instead to add an error to every operation at once:

```smithy
apply MyService @addErrors([BoomError])
```

Targets must be structures marked with [`@error`](https://smithy.io/2.0/spec/type-refinement-traits.html#error-trait) — the trait's `@idRef` selector enforces that at model-load time, so a typo or a non-error target fails before the transformation runs. Errors already present on the shape are skipped rather than duplicated, so running the transformation twice is a no-op the second time. Multiple `apply ... @addErrors(...)` blocks are concatenated like `@addOperations`.

## `removeErrors`

The inverse of `@addErrors`, on operations and services alike:

```smithy
// ours.smithy
$version: "2"

namespace example

use smithytransformations#removeErrors

apply A @removeErrors([ExistingError])
```

After the `removeErrors` transformer runs, `ExistingError` is gone from `A.errors`. The error structure itself stays in the model — other operations may still declare it. Naming an error the shape doesn't declare is ignored rather than an error.

One asymmetry worth knowing: removing an error from an *operation* cannot cancel out one inherited from the enclosing *service*. Service-level `errors` apply to every operation the service contains and Smithy offers no per-operation opt-out, so if the error comes from the service, remove it from the service.

## `removeTraits`

Unlike the others, this one isn't driven by a trait — it's model-wide, so there's no shape to attach it to. It's configured by the `smithytransformations#removeTraits` metadata key, whose type is declared with Smithy's [`@metadata` trait](https://smithy.io/2.0/spec/model.html#metadata-trait), so the value is validated as part of loading the model.

Metadata keys are plain strings — Smithy attaches no namespace meaning to the `#`. It's qualified here purely to avoid collisions: metadata is merged across every model loaded together, so a bare `removeTraits` would clash with any other library that picked the same word. (The transformer's *name*, used in `smithy4sModelTransformers` and `smithy-build.json`, is just `removeTraits`.)

Each entry is a [selector](https://smithy.io/2.0/spec/selectors.html) matching the **trait definition shapes** to strip:

```smithy
$version: "2"

metadata "smithytransformations#removeTraits" = [
    "[trait|trait][id|namespace = 'smithy.rules']"  // every trait in the namespace
    "[id = 'smithy.api#deprecated']"                // just this one trait
]

namespace example
```

After the `removeTraits` transformer runs, no shape in the model carries a `smithy.rules` trait, nor `@deprecated`. Because entries are selectors rather than plain names, you get the whole grammar — `[trait|trait][trait|smithy.api#unstable]` strips every trait that is itself marked `@unstable`, and so on.

The motivating case: trimming a large AWS model with `@only`/`exclude` removes operations, but the service still carries `smithy.rules#endpointTests` cases referencing them — an ERROR-severity validation failure that suppressions can't downgrade. If you don't generate endpoint rules anyway, dropping the whole `smithy.rules` namespace makes the dangling references disappear.

Note that removal is genuinely model-wide: it applies to the prelude and to every dependency in the model, not just your own shapes. A selector matching `smithy.api#documentation` will strip it from prelude shapes too. Prefer namespaces you own or that you're deliberately discarding.

Remember `[trait|trait]` in the namespace form — without it, `[id|namespace = 'custom']` would also match non-trait shapes in that namespace. That's harmless (only applied traits are ever removed) but the intent reads better with it.
