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
import software.amazon.smithy.model.loader.IdlTokenizer
import software.amazon.smithy.model.loader.ModelAssembler
import software.amazon.smithy.model.shapes.SmithyIdlModelSerializer
import software.amazon.smithy.syntax
import software.amazon.smithy.syntax.TokenTree

import java.nio.file.Paths
import scala.jdk.CollectionConverters.*

class AddOperationsTest extends FunSuite {

  test("basic: adds @addOperations targets to the service's operations") {
    transformationComparisonTest(os.sub / "addOperations" / "basic")
  }

  test("apply: a second `apply ... @addOperations(...)` is concatenated with the original") {
    transformationComparisonTest(os.sub / "addOperations" / "apply")
  }

  private def transformationComparisonTest(directory: os.SubPath) = {
    val result = new AddOperations().transform(
      TransformContext
        .builder()
        .model(loadModel(os.resource / "smithy" / directory / "input.smithy"))
        .build()
    )

    val expected = loadModel(os.resource / "smithy" / directory / "expected.smithy")
    val diff =
      ModelDiff
        .builder()
        .oldModel(expected)
        .newModel(result)
        .compare()
        .getDiffEvents
        .asScala
        .toList

    val actualFile =
      os.pwd / "tests" / "src" / "test" / "resources" / "smithy" / directory / "actual.smithy"

    if (diff.nonEmpty) {
      os.write
        .over(
          actualFile,
          format(
            SmithyIdlModelSerializer
              .builder()
              .build()
              .serialize(result)
              .get(Paths.get("sample.smithy"))
          ),
        )
      println(s"wrote actual contents to $actualFile")
    } else if (os.exists(actualFile)) {
      os.remove(actualFile): Unit
    }

    assert(diff.isEmpty, diff.map(_.toString).mkString("\n"))
  }

  private def format(string: String): String = {
    val tokenizer = IdlTokenizer.create(string)
    val tree = TokenTree.of(tokenizer)
    syntax.Formatter.format(tree)
  }

  private def loadModel(resources: os.ResourcePath*): Model = {
    val assembler = Model
      .assembler()
      .discoverModels()
      .putProperty(ModelAssembler.DISABLE_JAR_CACHE, true)

    resources.foreach { res =>
      assembler.addImport(this.getClass.getClassLoader.getResource(res.segments.mkString("/")))
    }

    assembler.assemble().unwrap()
  }

}
