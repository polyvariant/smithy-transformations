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

import software.amazon.smithy.build.TransformContext
import software.amazon.smithy.model.shapes.ShapeId

import scala.jdk.CollectionConverters.*

class RemoveOperationsTest extends TransformationSuite(() => new RemoveOperations) {

  test("basic: removes @removeOperations targets from the service's operations") {
    transformationComparisonTest(
      input =
        """|$version: "2"
           |
           |namespace example
           |
           |use smithytransformations#removeOperations
           |
           |@removeOperations([Gone])
           |service MyService {
           |    operations: [A, Gone]
           |}
           |
           |operation A {}
           |operation Gone {}
           |""".stripMargin,
      expected =
        """|$version: "2"
           |
           |namespace example
           |
           |use smithytransformations#removeOperations
           |
           |@removeOperations([Gone])
           |service MyService {
           |    operations: [A]
           |}
           |
           |operation A {}
           |operation Gone {}
           |""".stripMargin,
    )
  }

  test("the operation shape itself stays in the model") {
    val result = new RemoveOperations().transform(
      TransformContext
        .builder()
        .model(
          loadModel(
            """|$version: "2"
               |
               |namespace example
               |
               |use smithytransformations#removeOperations
               |
               |@removeOperations([Gone])
               |service MyService {
               |    operations: [A, Gone]
               |}
               |
               |operation A {}
               |operation Gone {}
               |""".stripMargin
          )
        )
        .build()
    )
    assert(result.getShape(ShapeId.from("example#Gone")).isPresent)
  }

  test("cross-namespace: @removeOperations resolves operations from a different namespace") {
    val otherNs =
      """|$version: "2"
         |
         |namespace other
         |
         |operation Gone {}
         |""".stripMargin
    transformationComparisonTestMulti(
      input = Seq(
        """|$version: "2"
           |
           |namespace example
           |
           |use smithytransformations#removeOperations
           |use other#Gone
           |
           |@removeOperations([Gone])
           |service MyService {
           |    operations: [A, Gone]
           |}
           |
           |operation A {}
           |""".stripMargin,
        otherNs,
      ),
      expected = Seq(
        """|$version: "2"
           |
           |namespace example
           |
           |use smithytransformations#removeOperations
           |use other#Gone
           |
           |@removeOperations([Gone])
           |service MyService {
           |    operations: [A]
           |}
           |
           |operation A {}
           |""".stripMargin,
        otherNs,
      ),
    )
  }

  test("not attached: naming an operation the service doesn't have is a no-op") {
    val model =
      """|$version: "2"
         |
         |namespace example
         |
         |use smithytransformations#removeOperations
         |
         |@removeOperations([Unbound])
         |service MyService {
         |    operations: [A]
         |}
         |
         |operation A {}
         |operation Unbound {}
         |""".stripMargin
    transformationComparisonTest(input = model, expected = model)
  }

  test("empty: @removeOperations([]) is a no-op") {
    val model =
      """|$version: "2"
         |
         |namespace example
         |
         |use smithytransformations#removeOperations
         |
         |@removeOperations([])
         |service MyService {
         |    operations: [A]
         |}
         |
         |operation A {}
         |""".stripMargin
    transformationComparisonTest(input = model, expected = model)
  }

  test("removing every operation leaves an empty service") {
    transformationComparisonTest(
      input =
        """|$version: "2"
           |
           |namespace example
           |
           |use smithytransformations#removeOperations
           |
           |@removeOperations([A])
           |service MyService {
           |    operations: [A]
           |}
           |
           |operation A {}
           |""".stripMargin,
      expected =
        """|$version: "2"
           |
           |namespace example
           |
           |use smithytransformations#removeOperations
           |
           |@removeOperations([A])
           |service MyService {}
           |
           |operation A {}
           |""".stripMargin,
    )
  }

  test("apply: a second `apply ... @removeOperations(...)` is concatenated with the original") {
    transformationComparisonTest(
      input =
        """|$version: "2"
           |
           |namespace example
           |
           |use smithytransformations#removeOperations
           |
           |@removeOperations([Gone])
           |service MyService {
           |    operations: [A, Gone, AlsoGone]
           |}
           |
           |apply MyService @removeOperations([AlsoGone])
           |
           |operation A {}
           |operation Gone {}
           |operation AlsoGone {}
           |""".stripMargin,
      expected =
        """|$version: "2"
           |
           |namespace example
           |
           |use smithytransformations#removeOperations
           |
           |@removeOperations([Gone, AlsoGone])
           |service MyService {
           |    operations: [A]
           |}
           |
           |operation A {}
           |operation Gone {}
           |operation AlsoGone {}
           |""".stripMargin,
    )
  }

  test("interaction: addOperations then removeOperations cancel out") {
    val input =
      """|$version: "2"
         |
         |namespace example
         |
         |use smithytransformations#addOperations
         |use smithytransformations#removeOperations
         |
         |@addOperations([Another])
         |@removeOperations([Another])
         |service MyService {
         |    operations: [A]
         |}
         |
         |operation A {}
         |operation Another {}
         |""".stripMargin

    val added = new AddOperations().transform(
      TransformContext.builder().model(loadModel(input)).build()
    )
    val removed = new RemoveOperations().transform(
      TransformContext.builder().model(added).build()
    )

    val service = removed
      .expectShape(ShapeId.from("example#MyService"))
      .asServiceShape()
      .get()

    assertEquals(
      service.getOperations().asScala.toList,
      List(ShapeId.from("example#A")),
    )
  }

  test("validation: target shape must be an operation") {
    val errors = validationErrorsFor(
      """|$version: "2"
         |
         |namespace example
         |
         |use smithytransformations#removeOperations
         |
         |@removeOperations([NotAnOperation])
         |service MyService {
         |    operations: [A]
         |}
         |
         |operation A {}
         |
         |structure NotAnOperation {}
         |""".stripMargin
    )
    assert(errors.exists(_.contains("NotAnOperation")), errors.mkString("\n"))
  }

  test("validation: @removeOperations cannot be applied to a non-service shape") {
    val errors = validationErrorsFor(
      """|$version: "2"
         |
         |namespace example
         |
         |use smithytransformations#removeOperations
         |
         |@removeOperations([A])
         |structure NotAService {}
         |
         |operation A {}
         |""".stripMargin
    )
    assert(errors.exists(_.contains("removeOperations")), errors.mkString("\n"))
  }

  test("validation: target shape must exist") {
    val errors = validationErrorsFor(
      """|$version: "2"
         |
         |namespace example
         |
         |use smithytransformations#removeOperations
         |
         |@removeOperations([DoesNotExist])
         |service MyService {
         |    operations: [A]
         |}
         |
         |operation A {}
         |""".stripMargin
    )
    assert(errors.exists(_.contains("DoesNotExist")), errors.mkString("\n"))
  }

}
