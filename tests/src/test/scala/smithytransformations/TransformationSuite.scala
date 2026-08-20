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
import software.amazon.smithy.build.ProjectionTransformer
import software.amazon.smithy.build.TransformContext
import software.amazon.smithy.diff.ModelDiff
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.loader.ModelAssembler
import software.amazon.smithy.model.validation.Severity

import scala.jdk.CollectionConverters.*

/** Shared plumbing for transformer tests: run the transformer over a model, then assert the result
  * is model-equivalent (via smithy-diff, so formatting and member order don't matter) to an
  * expected model.
  */
abstract class TransformationSuite(transformer: () => ProjectionTransformer) extends FunSuite {

  protected def transformationComparisonTest(input: String, expected: String): Unit =
    transformationComparisonTestMulti(Seq(input), Seq(expected))

  protected def transformationComparisonTestMulti(
    input: Seq[String],
    expected: Seq[String],
  ): Unit = {
    val result = transformer().transform(
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

  protected def loadModel(contents: String*): Model = {
    val assembler = Model
      .assembler()
      .discoverModels()
      .putProperty(ModelAssembler.DISABLE_JAR_CACHE, true)
    contents.zipWithIndex.foreach { case (c, i) =>
      assembler.addUnparsedModel(s"test-$i.smithy", c)
    }
    assembler.assemble().unwrap()
  }

  protected def validationErrorsFor(content: String): List[String] = {
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
