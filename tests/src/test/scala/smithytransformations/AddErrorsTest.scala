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

class AddErrorsTest extends TransformationSuite(() => new AddErrors) {

  test("operation: adds @addErrors targets to the operation's errors") {
    transformationComparisonTest(
      input =
        """|$version: "2"
           |
           |namespace example
           |
           |use smithytransformations#addErrors
           |
           |@addErrors([BoomError])
           |operation A {
           |    errors: [ExistingError]
           |}
           |
           |@error("client")
           |structure ExistingError {}
           |
           |@error("server")
           |structure BoomError {}
           |""".stripMargin,
      expected =
        """|$version: "2"
           |
           |namespace example
           |
           |use smithytransformations#addErrors
           |
           |@addErrors([BoomError])
           |operation A {
           |    errors: [ExistingError, BoomError]
           |}
           |
           |@error("client")
           |structure ExistingError {}
           |
           |@error("server")
           |structure BoomError {}
           |""".stripMargin,
    )
  }

  test("service: adds @addErrors targets to the service's errors") {
    transformationComparisonTest(
      input =
        """|$version: "2"
           |
           |namespace example
           |
           |use smithytransformations#addErrors
           |
           |@addErrors([BoomError])
           |service MyService {
           |    operations: [A]
           |    errors: [ExistingError]
           |}
           |
           |operation A {}
           |
           |@error("client")
           |structure ExistingError {}
           |
           |@error("server")
           |structure BoomError {}
           |""".stripMargin,
      expected =
        """|$version: "2"
           |
           |namespace example
           |
           |use smithytransformations#addErrors
           |
           |@addErrors([BoomError])
           |service MyService {
           |    operations: [A]
           |    errors: [ExistingError, BoomError]
           |}
           |
           |operation A {}
           |
           |@error("client")
           |structure ExistingError {}
           |
           |@error("server")
           |structure BoomError {}
           |""".stripMargin,
    )
  }

  test("operation without errors: @addErrors introduces the errors list") {
    transformationComparisonTest(
      input =
        """|$version: "2"
           |
           |namespace example
           |
           |use smithytransformations#addErrors
           |
           |@addErrors([BoomError])
           |operation A {}
           |
           |@error("server")
           |structure BoomError {}
           |""".stripMargin,
      expected =
        """|$version: "2"
           |
           |namespace example
           |
           |use smithytransformations#addErrors
           |
           |@addErrors([BoomError])
           |operation A {
           |    errors: [BoomError]
           |}
           |
           |@error("server")
           |structure BoomError {}
           |""".stripMargin,
    )
  }

  test("idempotent: an error already on the shape is not duplicated") {
    val model =
      """|$version: "2"
         |
         |namespace example
         |
         |use smithytransformations#addErrors
         |
         |@addErrors([BoomError])
         |operation A {
         |    errors: [BoomError]
         |}
         |
         |@error("server")
         |structure BoomError {}
         |""".stripMargin
    transformationComparisonTest(input = model, expected = model)
  }

  test("cross-namespace: @addErrors resolves errors from a different namespace") {
    val otherNs =
      """|$version: "2"
         |
         |namespace other
         |
         |@error("server")
         |structure BoomError {}
         |""".stripMargin
    transformationComparisonTestMulti(
      input = Seq(
        """|$version: "2"
           |
           |namespace example
           |
           |use smithytransformations#addErrors
           |use other#BoomError
           |
           |@addErrors([BoomError])
           |operation A {}
           |""".stripMargin,
        otherNs,
      ),
      expected = Seq(
        """|$version: "2"
           |
           |namespace example
           |
           |use smithytransformations#addErrors
           |use other#BoomError
           |
           |@addErrors([BoomError])
           |operation A {
           |    errors: [BoomError]
           |}
           |""".stripMargin,
        otherNs,
      ),
    )
  }

  test("empty: @addErrors([]) is a no-op") {
    val model =
      """|$version: "2"
         |
         |namespace example
         |
         |use smithytransformations#addErrors
         |
         |@addErrors([])
         |operation A {
         |    errors: [BoomError]
         |}
         |
         |@error("server")
         |structure BoomError {}
         |""".stripMargin
    transformationComparisonTest(input = model, expected = model)
  }

  test("apply: a second `apply ... @addErrors(...)` is concatenated with the original") {
    transformationComparisonTest(
      input =
        """|$version: "2"
           |
           |namespace example
           |
           |use smithytransformations#addErrors
           |
           |@addErrors([BoomError])
           |operation A {}
           |
           |apply A @addErrors([OtherError])
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
           |use smithytransformations#addErrors
           |
           |@addErrors([BoomError, OtherError])
           |operation A {
           |    errors: [BoomError, OtherError]
           |}
           |
           |@error("server")
           |structure BoomError {}
           |
           |@error("client")
           |structure OtherError {}
           |""".stripMargin,
    )
  }

  test("validation: target shape must be an @error structure") {
    val errors = validationErrorsFor(
      """|$version: "2"
         |
         |namespace example
         |
         |use smithytransformations#addErrors
         |
         |@addErrors([NotAnError])
         |operation A {}
         |
         |structure NotAnError {}
         |""".stripMargin
    )
    assert(errors.exists(_.contains("NotAnError")), errors.mkString("\n"))
  }

  test("validation: @addErrors cannot be applied to a structure") {
    val errors = validationErrorsFor(
      """|$version: "2"
         |
         |namespace example
         |
         |use smithytransformations#addErrors
         |
         |@addErrors([BoomError])
         |structure NotAnOperation {}
         |
         |@error("server")
         |structure BoomError {}
         |""".stripMargin
    )
    assert(errors.exists(_.contains("addErrors")), errors.mkString("\n"))
  }

  test("validation: target shape must exist") {
    val errors = validationErrorsFor(
      """|$version: "2"
         |
         |namespace example
         |
         |use smithytransformations#addErrors
         |
         |@addErrors([DoesNotExist])
         |operation A {}
         |""".stripMargin
    )
    assert(errors.exists(_.contains("DoesNotExist")), errors.mkString("\n"))
  }

}
