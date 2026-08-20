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

class RemoveMembersTest extends TransformationSuite(() => new RemoveMembers) {

  test("structure: removes the named members") {
    transformationComparisonTest(
      input =
        """|$version: "2"
           |
           |namespace example
           |
           |use smithytransformations#removeMembers
           |
           |@removeMembers(["gone"])
           |structure MyStruct {
           |    kept: String
           |    gone: Integer
           |}
           |""".stripMargin,
      expected =
        """|$version: "2"
           |
           |namespace example
           |
           |use smithytransformations#removeMembers
           |
           |@removeMembers(["gone"])
           |structure MyStruct {
           |    kept: String
           |}
           |""".stripMargin,
    )
  }

  test("union: removes the named members") {
    transformationComparisonTest(
      input =
        """|$version: "2"
           |
           |namespace example
           |
           |use smithytransformations#removeMembers
           |
           |@removeMembers(["gone"])
           |union MyUnion {
           |    kept: String
           |    gone: Integer
           |}
           |""".stripMargin,
      expected =
        """|$version: "2"
           |
           |namespace example
           |
           |use smithytransformations#removeMembers
           |
           |@removeMembers(["gone"])
           |union MyUnion {
           |    kept: String
           |}
           |""".stripMargin,
    )
  }

  test("multiple members are removed at once") {
    transformationComparisonTest(
      input =
        """|$version: "2"
           |
           |namespace example
           |
           |use smithytransformations#removeMembers
           |
           |@removeMembers(["gone", "alsoGone"])
           |structure MyStruct {
           |    kept: String
           |    gone: Integer
           |    alsoGone: Boolean
           |}
           |""".stripMargin,
      expected =
        """|$version: "2"
           |
           |namespace example
           |
           |use smithytransformations#removeMembers
           |
           |@removeMembers(["gone", "alsoGone"])
           |structure MyStruct {
           |    kept: String
           |}
           |""".stripMargin,
    )
  }

  test("member traits do not prevent removal") {
    transformationComparisonTest(
      input =
        """|$version: "2"
           |
           |namespace example
           |
           |use smithytransformations#removeMembers
           |
           |@removeMembers(["gone"])
           |structure MyStruct {
           |    kept: String
           |
           |    @required
           |    @documentation("doomed")
           |    gone: Integer
           |}
           |""".stripMargin,
      expected =
        """|$version: "2"
           |
           |namespace example
           |
           |use smithytransformations#removeMembers
           |
           |@removeMembers(["gone"])
           |structure MyStruct {
           |    kept: String
           |}
           |""".stripMargin,
    )
  }

  test("case-insensitive: member names match regardless of case") {
    transformationComparisonTest(
      input =
        """|$version: "2"
           |
           |namespace example
           |
           |use smithytransformations#removeMembers
           |
           |@removeMembers(["GONE"])
           |structure MyStruct {
           |    kept: String
           |    gone: Integer
           |}
           |""".stripMargin,
      expected =
        """|$version: "2"
           |
           |namespace example
           |
           |use smithytransformations#removeMembers
           |
           |@removeMembers(["GONE"])
           |structure MyStruct {
           |    kept: String
           |}
           |""".stripMargin,
    )
  }

  test("nonexistent: naming a member the shape doesn't have is a no-op") {
    val model =
      """|$version: "2"
         |
         |namespace example
         |
         |use smithytransformations#removeMembers
         |
         |@removeMembers(["notThere"])
         |structure MyStruct {
         |    kept: String
         |}
         |""".stripMargin
    transformationComparisonTest(input = model, expected = model)
  }

  test("empty: @removeMembers([]) is a no-op") {
    val model =
      """|$version: "2"
         |
         |namespace example
         |
         |use smithytransformations#removeMembers
         |
         |@removeMembers([])
         |structure MyStruct {
         |    kept: String
         |}
         |""".stripMargin
    transformationComparisonTest(input = model, expected = model)
  }

  test("removing every member leaves an empty structure") {
    transformationComparisonTest(
      input =
        """|$version: "2"
           |
           |namespace example
           |
           |use smithytransformations#removeMembers
           |
           |@removeMembers(["only"])
           |structure MyStruct {
           |    only: String
           |}
           |""".stripMargin,
      expected =
        """|$version: "2"
           |
           |namespace example
           |
           |use smithytransformations#removeMembers
           |
           |@removeMembers(["only"])
           |structure MyStruct {}
           |""".stripMargin,
    )
  }

  test("apply: a second `apply ... @removeMembers(...)` is concatenated with the original") {
    transformationComparisonTest(
      input =
        """|$version: "2"
           |
           |namespace example
           |
           |use smithytransformations#removeMembers
           |
           |@removeMembers(["gone"])
           |structure MyStruct {
           |    kept: String
           |    gone: Integer
           |    alsoGone: Boolean
           |}
           |
           |apply MyStruct @removeMembers(["alsoGone"])
           |""".stripMargin,
      expected =
        """|$version: "2"
           |
           |namespace example
           |
           |use smithytransformations#removeMembers
           |
           |@removeMembers(["gone", "alsoGone"])
           |structure MyStruct {
           |    kept: String
           |}
           |""".stripMargin,
    )
  }

  test("interaction: addMembers then removeMembers cancel out") {
    import software.amazon.smithy.build.TransformContext
    import software.amazon.smithy.model.shapes.ShapeId
    import scala.jdk.CollectionConverters.*

    val input =
      """|$version: "2"
         |
         |namespace example
         |
         |use smithytransformations#addMembers
         |use smithytransformations#removeMembers
         |
         |@addMembers([{ name: "extra", target: String }])
         |@removeMembers(["extra"])
         |structure MyStruct {
         |    kept: String
         |}
         |""".stripMargin

    val added = new AddMembers().transform(
      TransformContext.builder().model(loadModel(input)).build()
    )
    val removed = new RemoveMembers().transform(
      TransformContext.builder().model(added).build()
    )

    val members =
      removed
        .expectShape(ShapeId.from("example#MyStruct"))
        .members()
        .asScala
        .map(_.getMemberName)
        .toList

    assertEquals(members, List("kept"))
  }

  test("validation: @removeMembers cannot be applied to an operation") {
    val errors = validationErrorsFor(
      """|$version: "2"
         |
         |namespace example
         |
         |use smithytransformations#removeMembers
         |
         |@removeMembers(["whatever"])
         |operation NotAnAggregate {}
         |""".stripMargin
    )
    assert(errors.exists(_.contains("removeMembers")), errors.mkString("\n"))
  }

  test("validation: member names must be non-empty") {
    val errors = validationErrorsFor(
      """|$version: "2"
         |
         |namespace example
         |
         |use smithytransformations#removeMembers
         |
         |@removeMembers([""])
         |structure MyStruct {
         |    kept: String
         |}
         |""".stripMargin
    )
    assert(errors.nonEmpty, errors.mkString("\n"))
  }

}
