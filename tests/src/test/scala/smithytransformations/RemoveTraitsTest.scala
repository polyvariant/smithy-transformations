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

class RemoveTraitsTest extends FunSuite {

  test("namespace selector: removes every trait in the matched namespace") {
    transformationComparisonTestMulti(
      input = Seq(
        customTraits,
        """|$version: "2"
           |
           |metadata "removeTraits" = ["[trait|trait][id|namespace = 'custom']"]
           |
           |namespace example
           |
           |use custom#alpha
           |use custom#beta
           |
           |@alpha
           |@beta
           |@documentation("kept")
           |structure MyStruct {
           |    @alpha
           |    original: String
           |}
           |""".stripMargin,
      ),
      expected = Seq(
        customTraits,
        """|$version: "2"
           |
           |metadata "removeTraits" = ["[trait|trait][id|namespace = 'custom']"]
           |
           |namespace example
           |
           |@documentation("kept")
           |structure MyStruct {
           |    original: String
           |}
           |""".stripMargin,
      ),
    )
  }

  test("id selector: removes only the single matched trait") {
    transformationComparisonTestMulti(
      input = Seq(
        customTraits,
        """|$version: "2"
           |
           |metadata "removeTraits" = ["[id = 'custom#alpha']"]
           |
           |namespace example
           |
           |use custom#alpha
           |use custom#beta
           |
           |@alpha
           |@beta
           |structure MyStruct {
           |    original: String
           |}
           |""".stripMargin,
      ),
      expected = Seq(
        customTraits,
        """|$version: "2"
           |
           |metadata "removeTraits" = ["[id = 'custom#alpha']"]
           |
           |namespace example
           |
           |use custom#beta
           |
           |@beta
           |structure MyStruct {
           |    original: String
           |}
           |""".stripMargin,
      ),
    )
  }

  test("mixed: several selectors are unioned") {
    transformationComparisonTestMulti(
      input = Seq(
        customTraits,
        otherTraits,
        """|$version: "2"
           |
           |metadata "removeTraits" = ["[trait|trait][id|namespace = 'custom']", "[id = 'other#gamma']"]
           |
           |namespace example
           |
           |use custom#alpha
           |use custom#beta
           |use other#gamma
           |
           |@alpha
           |@beta
           |@gamma
           |@sensitive
           |structure MyStruct {
           |    original: String
           |}
           |""".stripMargin,
      ),
      expected = Seq(
        customTraits,
        otherTraits,
        """|$version: "2"
           |
           |metadata "removeTraits" = ["[trait|trait][id|namespace = 'custom']", "[id = 'other#gamma']"]
           |
           |namespace example
           |
           |@sensitive
           |structure MyStruct {
           |    original: String
           |}
           |""".stripMargin,
      ),
    )
  }

  test("no metadata: the model is left untouched") {
    val model =
      """|$version: "2"
         |
         |namespace example
         |
         |@documentation("kept")
         |structure MyStruct {
         |    original: String
         |}
         |""".stripMargin
    transformationComparisonTest(input = model, expected = model)
  }

  test("empty: an empty list is a no-op") {
    val model =
      """|$version: "2"
         |
         |metadata "removeTraits" = []
         |
         |namespace example
         |
         |@documentation("kept")
         |structure MyStruct {
         |    original: String
         |}
         |""".stripMargin
    transformationComparisonTest(input = model, expected = model)
  }

  test("unmatched: a selector matching nothing is a no-op") {
    val model =
      """|$version: "2"
         |
         |metadata "removeTraits" = ["[trait|trait][id|namespace = 'nope']"]
         |
         |namespace example
         |
         |@documentation("kept")
         |structure MyStruct {
         |    original: String
         |}
         |""".stripMargin
    transformationComparisonTest(input = model, expected = model)
  }

  test("validation: the metadata value must be a list of strings") {
    val errors = validationErrorsFor(
      """|$version: "2"
         |
         |metadata "removeTraits" = "custom"
         |
         |namespace example
         |
         |structure MyStruct {}
         |""".stripMargin
    )
    assert(
      errors.exists(_.contains("removeTraits")),
      errors.mkString("\n"),
    )
  }

  test("validation: entries must not be empty strings") {
    val errors = validationErrorsFor(
      """|$version: "2"
         |
         |metadata "removeTraits" = [""]
         |
         |namespace example
         |
         |structure MyStruct {}
         |""".stripMargin
    )
    assert(
      errors.exists(_.contains("removeTraits")),
      errors.mkString("\n"),
    )
  }

  test("selector grammar: strips every trait marked @unstable") {
    transformationComparisonTestMulti(
      input = Seq(
        """|$version: "2"
           |
           |namespace custom
           |
           |@trait
           |@unstable
           |structure experimental {}
           |
           |@trait
           |structure stable {}
           |""".stripMargin,
        """|$version: "2"
           |
           |metadata "removeTraits" = ["[trait|trait][trait|smithy.api#unstable]"]
           |
           |namespace example
           |
           |use custom#experimental
           |use custom#stable
           |
           |@experimental
           |@stable
           |structure MyStruct {
           |    original: String
           |}
           |""".stripMargin,
      ),
      expected = Seq(
        """|$version: "2"
           |
           |namespace custom
           |
           |@trait
           |@unstable
           |structure experimental {}
           |
           |@trait
           |structure stable {}
           |""".stripMargin,
        """|$version: "2"
           |
           |metadata "removeTraits" = ["[trait|trait][trait|smithy.api#unstable]"]
           |
           |namespace example
           |
           |use custom#stable
           |
           |@stable
           |structure MyStruct {
           |    original: String
           |}
           |""".stripMargin,
      ),
    )
  }

  test("invalid selector: parse failures are surfaced") {
    val model = loadModel(
      """|$version: "2"
         |
         |metadata "removeTraits" = ["[[[not a selector"]
         |
         |namespace example
         |
         |structure MyStruct {}
         |""".stripMargin
    )
    intercept[RuntimeException] {
      new RemoveTraits().transform(TransformContext.builder().model(model).build())
    }
  }

  private val customTraits =
    """|$version: "2"
       |
       |namespace custom
       |
       |@trait
       |structure alpha {}
       |
       |@trait
       |structure beta {}
       |""".stripMargin

  private val otherTraits =
    """|$version: "2"
       |
       |namespace other
       |
       |@trait
       |structure gamma {}
       |
       |@trait
       |structure delta {}
       |""".stripMargin

  private def transformationComparisonTest(input: String, expected: String): Unit =
    transformationComparisonTestMulti(Seq(input), Seq(expected))

  private def transformationComparisonTestMulti(
    input: Seq[String],
    expected: Seq[String],
  ): Unit = {
    val result = new RemoveTraits().transform(
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
