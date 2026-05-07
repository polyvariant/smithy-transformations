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

import munit.FunSuite
import software.amazon.smithy.build.TransformContext
import software.amazon.smithy.diff.ModelDiff
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.loader.ModelAssembler

import scala.jdk.CollectionConverters.*

class AddOperationsTest extends FunSuite {

  test("basic: adds @addOperations targets to the service's operations") {
    transformationComparisonTest(
      input =
        """|$version: "2"
           |
           |namespace example
           |
           |use smithytransformations#addOperations
           |
           |@addOperations([Another])
           |service MyService {
           |    operations: [A]
           |}
           |
           |operation A {}
           |operation Another {}
           |""".stripMargin,
      expected =
        """|$version: "2"
           |
           |namespace example
           |
           |use smithytransformations#addOperations
           |
           |@addOperations([Another])
           |service MyService {
           |    operations: [A, Another]
           |}
           |
           |operation A {}
           |operation Another {}
           |""".stripMargin,
    )
  }

  test("cross-namespace: @addOperations resolves operations from a different namespace") {
    val otherNs =
      """|$version: "2"
         |
         |namespace other
         |
         |operation Another {}
         |""".stripMargin
    transformationComparisonTestMulti(
      input = Seq(
        """|$version: "2"
           |
           |namespace example
           |
           |use smithytransformations#addOperations
           |use other#Another
           |
           |@addOperations([Another])
           |service MyService {
           |    operations: [A]
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
           |use smithytransformations#addOperations
           |use other#Another
           |
           |@addOperations([Another])
           |service MyService {
           |    operations: [A, Another]
           |}
           |
           |operation A {}
           |""".stripMargin,
        otherNs,
      ),
    )
  }

  test("empty: @addOperations([]) is a no-op") {
    val model =
      """|$version: "2"
         |
         |namespace example
         |
         |use smithytransformations#addOperations
         |
         |@addOperations([])
         |service MyService {
         |    operations: [A]
         |}
         |
         |operation A {}
         |""".stripMargin
    transformationComparisonTest(input = model, expected = model)
  }

  test("apply: a second `apply ... @addOperations(...)` is concatenated with the original") {
    transformationComparisonTest(
      input =
        """|$version: "2"
           |
           |namespace example
           |
           |use smithytransformations#addOperations
           |
           |@addOperations([Another])
           |service MyService {
           |    operations: [A]
           |}
           |
           |apply MyService @addOperations([Third])
           |
           |operation A {}
           |operation Another {}
           |operation Third {}
           |""".stripMargin,
      expected =
        """|$version: "2"
           |
           |namespace example
           |
           |use smithytransformations#addOperations
           |
           |@addOperations([Another, Third])
           |service MyService {
           |    operations: [A, Another, Third]
           |}
           |
           |operation A {}
           |operation Another {}
           |operation Third {}
           |""".stripMargin,
    )
  }

  private def transformationComparisonTest(input: String, expected: String): Unit =
    transformationComparisonTestMulti(Seq(input), Seq(expected))

  private def transformationComparisonTestMulti(
    input: Seq[String],
    expected: Seq[String],
  ): Unit = {
    val result = new AddOperations().transform(
      TransformContext
        .builder()
        .model(loadModel(input*))
        .build()
    )

    val diff =
      ModelDiff
        .builder()
        .oldModel(loadModel(expected*))
        .newModel(result)
        .compare()
        .getDiffEvents
        .asScala
        .toList

    assert(diff.isEmpty, diff.map(_.toString).mkString("\n"))
  }

  test("validation: target shape must be an operation") {
    val errors = validationErrorsFor(
      """|$version: "2"
         |
         |namespace example
         |
         |use smithytransformations#addOperations
         |
         |@addOperations([NotAnOperation])
         |service MyService {
         |    operations: [A]
         |}
         |
         |operation A {}
         |
         |structure NotAnOperation {}
         |""".stripMargin
    )
    assert(
      errors.exists(_.contains("NotAnOperation")),
      errors.mkString("\n"),
    )
  }

  test("validation: @addOperations cannot be applied to a non-service shape") {
    val errors = validationErrorsFor(
      """|$version: "2"
         |
         |namespace example
         |
         |use smithytransformations#addOperations
         |
         |@addOperations([A])
         |structure NotAService {}
         |
         |operation A {}
         |""".stripMargin
    )
    assert(
      errors.exists(_.contains("addOperations")),
      errors.mkString("\n"),
    )
  }

  test("validation: target shape must exist") {
    val errors = validationErrorsFor(
      """|$version: "2"
         |
         |namespace example
         |
         |use smithytransformations#addOperations
         |
         |@addOperations([DoesNotExist])
         |service MyService {
         |    operations: [A]
         |}
         |
         |operation A {}
         |""".stripMargin
    )
    assert(
      errors.exists(_.contains("DoesNotExist")),
      errors.mkString("\n"),
    )
  }

  private def loadModel(contents: String*): Model = {
    val assembler = Model
      .assembler()
      .discoverModels()
      .putProperty(ModelAssembler.DISABLE_JAR_CACHE, true)
    contents.zipWithIndex.foreach { case (c, i) =>
      assembler.addUnparsedModel(s"test-$i.smithy", c)
    }
    assembler.assemble().unwrap()
  }

  private def validationErrorsFor(content: String): List[String] = {
    import software.amazon.smithy.model.validation.Severity
    val result = Model
      .assembler()
      .discoverModels()
      .putProperty(ModelAssembler.DISABLE_JAR_CACHE, true)
      .addUnparsedModel("test.smithy", content)
      .assemble()
    val errors = result
      .getValidationEvents
      .asScala
      .toList
      .filter(_.getSeverity == Severity.ERROR)
      .map(_.getMessage)
    assert(errors.nonEmpty, "expected validation errors but got none")
    errors
  }

}
