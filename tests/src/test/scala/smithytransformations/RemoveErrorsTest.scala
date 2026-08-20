/*
 * Copyright 2026 Polyvariant
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package smithytransformations

class RemoveErrorsTest extends TransformationSuite(() => new RemoveErrors) {

  test("operation: removes @removeErrors targets from the operation's errors") {
    transformationComparisonTest(
      input =
        """|$version: "2"
           |
           |namespace example
           |
           |use smithytransformations#removeErrors
           |
           |@removeErrors([BoomError])
           |operation A {
           |    errors: [KeepError, BoomError]
           |}
           |
           |@error("client")
           |structure KeepError {}
           |
           |@error("server")
           |structure BoomError {}
           |""".stripMargin,
      expected =
        """|$version: "2"
           |
           |namespace example
           |
           |use smithytransformations#removeErrors
           |
           |@removeErrors([BoomError])
           |operation A {
           |    errors: [KeepError]
           |}
           |
           |@error("client")
           |structure KeepError {}
           |
           |@error("server")
           |structure BoomError {}
           |""".stripMargin,
    )
  }

  test("service: removes @removeErrors targets from the service's errors") {
    transformationComparisonTest(
      input =
        """|$version: "2"
           |
           |namespace example
           |
           |use smithytransformations#removeErrors
           |
           |@removeErrors([BoomError])
           |service MyService {
           |    operations: [A]
           |    errors: [KeepError, BoomError]
           |}
           |
           |operation A {}
           |
           |@error("client")
           |structure KeepError {}
           |
           |@error("server")
           |structure BoomError {}
           |""".stripMargin,
      expected =
        """|$version: "2"
           |
           |namespace example
           |
           |use smithytransformations#removeErrors
           |
           |@removeErrors([BoomError])
           |service MyService {
           |    operations: [A]
           |    errors: [KeepError]
           |}
           |
           |operation A {}
           |
           |@error("client")
           |structure KeepError {}
           |
           |@error("server")
           |structure BoomError {}
           |""".stripMargin,
    )
  }

  test("removing the only error leaves the operation with no errors") {
    transformationComparisonTest(
      input =
        """|$version: "2"
           |
           |namespace example
           |
           |use smithytransformations#removeErrors
           |
           |@removeErrors([BoomError])
           |operation A {
           |    errors: [BoomError]
           |}
           |
           |@error("server")
           |structure BoomError {}
           |""".stripMargin,
      expected =
        """|$version: "2"
           |
           |namespace example
           |
           |use smithytransformations#removeErrors
           |
           |@removeErrors([BoomError])
           |operation A {}
           |
           |@error("server")
           |structure BoomError {}
           |""".stripMargin,
    )
  }

  test("the error structure itself stays in the model") {
    val result = new RemoveErrors().transform(
      software
        .amazon
        .smithy
        .build
        .TransformContext
        .builder()
        .model(
          loadModel(
            """|$version: "2"
               |
               |namespace example
               |
               |use smithytransformations#removeErrors
               |
               |@removeErrors([BoomError])
               |operation A {
               |    errors: [BoomError]
               |}
               |
               |@error("server")
               |structure BoomError {}
               |""".stripMargin
          )
        )
        .build()
    )
    assert(
      result
        .getShape(software.amazon.smithy.model.shapes.ShapeId.from("example#BoomError"))
        .isPresent
    )
  }

  test("not attached: naming an error the shape doesn't have is a no-op") {
    val model =
      """|$version: "2"
         |
         |namespace example
         |
         |use smithytransformations#removeErrors
         |
         |@removeErrors([NotAttachedError])
         |operation A {
         |    errors: [KeepError]
         |}
         |
         |@error("client")
         |structure KeepError {}
         |
         |@error("server")
         |structure NotAttachedError {}
         |""".stripMargin
    transformationComparisonTest(input = model, expected = model)
  }

  test("empty: @removeErrors([]) is a no-op") {
    val model =
      """|$version: "2"
         |
         |namespace example
         |
         |use smithytransformations#removeErrors
         |
         |@removeErrors([])
         |operation A {
         |    errors: [KeepError]
         |}
         |
         |@error("client")
         |structure KeepError {}
         |""".stripMargin
    transformationComparisonTest(input = model, expected = model)
  }

  test("apply: a second `apply ... @removeErrors(...)` is concatenated with the original") {
    transformationComparisonTest(
      input =
        """|$version: "2"
           |
           |namespace example
           |
           |use smithytransformations#removeErrors
           |
           |@removeErrors([BoomError])
           |operation A {
           |    errors: [KeepError, BoomError, OtherError]
           |}
           |
           |apply A @removeErrors([OtherError])
           |
           |@error("client")
           |structure KeepError {}
           |
           |@error("server")
           |structure BoomError {}
           |
           |@error("client")
           |structure OtherError {}
           |""".stripMargin,
      expected =
        """|$version: "2"
           |
           |namespace example
           |
           |use smithytransformations#removeErrors
           |
           |@removeErrors([BoomError, OtherError])
           |operation A {
           |    errors: [KeepError]
           |}
           |
           |@error("client")
           |structure KeepError {}
           |
           |@error("server")
           |structure BoomError {}
           |
           |@error("client")
           |structure OtherError {}
           |""".stripMargin,
    )
  }

  test("validation: @removeErrors cannot be applied to a structure") {
    val errors = validationErrorsFor(
      """|$version: "2"
         |
         |namespace example
         |
         |use smithytransformations#removeErrors
         |
         |@removeErrors([BoomError])
         |structure NotAnOperation {}
         |
         |@error("server")
         |structure BoomError {}
         |""".stripMargin
    )
    assert(errors.exists(_.contains("removeErrors")), errors.mkString("\n"))
  }

  test("validation: target shape must exist") {
    val errors = validationErrorsFor(
      """|$version: "2"
         |
         |namespace example
         |
         |use smithytransformations#removeErrors
         |
         |@removeErrors([DoesNotExist])
         |operation A {}
         |""".stripMargin
    )
    assert(errors.exists(_.contains("DoesNotExist")), errors.mkString("\n"))
  }

}
