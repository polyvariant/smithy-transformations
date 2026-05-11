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

class AddMembersTest extends FunSuite {

  test("basic: adds @addMembers entries to a structure") {
    transformationComparisonTest(
      input =
        """|$version: "2"
           |
           |namespace example
           |
           |use smithytransformations#addMembers
           |
           |@addMembers([
           |    { name: "extra", target: String }
           |])
           |structure MyStruct {
           |    original: String
           |}
           |""".stripMargin,
      expected =
        """|$version: "2"
           |
           |namespace example
           |
           |use smithytransformations#addMembers
           |
           |@addMembers([
           |    { name: "extra", target: String }
           |])
           |structure MyStruct {
           |    original: String
           |    extra: String
           |}
           |""".stripMargin,
    )
  }

  test("union: adds @addMembers entries to a union") {
    transformationComparisonTest(
      input =
        """|$version: "2"
           |
           |namespace example
           |
           |use smithytransformations#addMembers
           |
           |@addMembers([
           |    { name: "extra", target: Integer }
           |])
           |union MyUnion {
           |    original: String
           |}
           |""".stripMargin,
      expected =
        """|$version: "2"
           |
           |namespace example
           |
           |use smithytransformations#addMembers
           |
           |@addMembers([
           |    { name: "extra", target: Integer }
           |])
           |union MyUnion {
           |    original: String
           |    extra: Integer
           |}
           |""".stripMargin,
    )
  }

  test("traits: applies traits to the added member") {
    transformationComparisonTest(
      input =
        """|$version: "2"
           |
           |namespace example
           |
           |use smithytransformations#addMembers
           |
           |@addMembers([
           |    {
           |        name: "extra"
           |        target: String
           |        traits: {
           |            "smithy.api#required": {}
           |            "smithy.api#documentation": "hello"
           |        }
           |    }
           |])
           |structure MyStruct {
           |    original: String
           |}
           |""".stripMargin,
      expected =
        """|$version: "2"
           |
           |namespace example
           |
           |use smithytransformations#addMembers
           |
           |@addMembers([
           |    {
           |        name: "extra"
           |        target: String
           |        traits: {
           |            "smithy.api#required": {}
           |            "smithy.api#documentation": "hello"
           |        }
           |    }
           |])
           |structure MyStruct {
           |    original: String
           |
           |    @required
           |    @documentation("hello")
           |    extra: String
           |}
           |""".stripMargin,
    )
  }

  test("cross-namespace: target shape can live in a different namespace") {
    val otherNs =
      """|$version: "2"
         |
         |namespace other
         |
         |structure External {}
         |""".stripMargin
    transformationComparisonTestMulti(
      input = Seq(
        """|$version: "2"
           |
           |namespace example
           |
           |use smithytransformations#addMembers
           |use other#External
           |
           |@addMembers([
           |    { name: "ext", target: External }
           |])
           |structure MyStruct {
           |    original: String
           |}
           |""".stripMargin,
        otherNs,
      ),
      expected = Seq(
        """|$version: "2"
           |
           |namespace example
           |
           |use smithytransformations#addMembers
           |use other#External
           |
           |@addMembers([
           |    { name: "ext", target: External }
           |])
           |structure MyStruct {
           |    original: String
           |    ext: External
           |}
           |""".stripMargin,
        otherNs,
      ),
    )
  }

  test("empty: @addMembers([]) is a no-op") {
    val model =
      """|$version: "2"
         |
         |namespace example
         |
         |use smithytransformations#addMembers
         |
         |@addMembers([])
         |structure MyStruct {
         |    original: String
         |}
         |""".stripMargin
    transformationComparisonTest(input = model, expected = model)
  }

  test("apply: a second `apply ... @addMembers(...)` is concatenated with the original") {
    transformationComparisonTest(
      input =
        """|$version: "2"
           |
           |namespace example
           |
           |use smithytransformations#addMembers
           |
           |@addMembers([
           |    { name: "first", target: String }
           |])
           |structure MyStruct {
           |    original: String
           |}
           |
           |apply MyStruct @addMembers([
           |    { name: "second", target: Integer }
           |])
           |""".stripMargin,
      expected =
        """|$version: "2"
           |
           |namespace example
           |
           |use smithytransformations#addMembers
           |
           |@addMembers([
           |    { name: "first", target: String }
           |    { name: "second", target: Integer }
           |])
           |structure MyStruct {
           |    original: String
           |    first: String
           |    second: Integer
           |}
           |""".stripMargin,
    )
  }

  private def transformationComparisonTest(input: String, expected: String): Unit =
    transformationComparisonTestMulti(Seq(input), Seq(expected))

  private def transformationComparisonTestMulti(
    input: Seq[String],
    expected: Seq[String],
  ): Unit = {
    val result = new AddMembers().transform(
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

  test("validation: target shape must exist") {
    val errors = validationErrorsFor(
      """|$version: "2"
         |
         |namespace example
         |
         |use smithytransformations#addMembers
         |
         |@addMembers([
         |    { name: "extra", target: DoesNotExist }
         |])
         |structure MyStruct {
         |    original: String
         |}
         |""".stripMargin
    )
    assert(
      errors.exists(_.contains("DoesNotExist")),
      errors.mkString("\n"),
    )
  }

  test("validation: @addMembers cannot be applied to a non-aggregate shape") {
    val errors = validationErrorsFor(
      """|$version: "2"
         |
         |namespace example
         |
         |use smithytransformations#addMembers
         |
         |@addMembers([
         |    { name: "extra", target: String }
         |])
         |string NotAggregate
         |""".stripMargin
    )
    assert(
      errors.exists(_.contains("addMembers")),
      errors.mkString("\n"),
    )
  }

  test("validation: cannot add a member that already exists on the target") {
    val model = loadModel(
      """|$version: "2"
         |
         |namespace example
         |
         |use smithytransformations#addMembers
         |
         |@addMembers([
         |    { name: "original", target: Integer }
         |])
         |structure MyStruct {
         |    original: String
         |}
         |""".stripMargin
    )
    val ex = intercept[IllegalStateException] {
      new AddMembers().transform(
        TransformContext.builder().model(model).build()
      )
    }
    assert(ex.getMessage.contains("already exists"), ex.getMessage)
  }

  test("validation: existing-member check is case-insensitive") {
    val model = loadModel(
      """|$version: "2"
         |
         |namespace example
         |
         |use smithytransformations#addMembers
         |
         |@addMembers([
         |    { name: "ORIGINAL", target: Integer }
         |])
         |structure MyStruct {
         |    original: String
         |}
         |""".stripMargin
    )
    val ex = intercept[IllegalStateException] {
      new AddMembers().transform(
        TransformContext.builder().model(model).build()
      )
    }
    assert(ex.getMessage.contains("already exists"), ex.getMessage)
  }

  test("validation: cannot list the same member name twice in a single @addMembers") {
    val model = loadModel(
      """|$version: "2"
         |
         |namespace example
         |
         |use smithytransformations#addMembers
         |
         |@addMembers([
         |    { name: "extra", target: String }
         |    { name: "Extra", target: Integer }
         |])
         |structure MyStruct {
         |    original: String
         |}
         |""".stripMargin
    )
    val ex = intercept[IllegalStateException] {
      new AddMembers().transform(
        TransformContext.builder().model(model).build()
      )
    }
    assert(ex.getMessage.contains("more than once"), ex.getMessage)
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
