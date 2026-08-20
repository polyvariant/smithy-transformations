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

package smithytransformations;

import software.amazon.smithy.build.ProjectionTransformer;
import software.amazon.smithy.build.TransformContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.transform.ModelTransformer;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Appends error shapes to the operations and services carrying {@code @addErrors}.
 *
 * <p>Both shape types have an {@code errors} property, so one trait covers both: on an operation the
 * errors are specific to that operation, on a service they apply to every operation it contains.
 * Errors already present on the shape are left alone, so applying the transformation twice is a
 * no-op the second time.
 */
public final class AddErrors implements ProjectionTransformer {

    @Override
    public String getName() {
        return "addErrors";
    }

    @Override
    public Model transform(TransformContext context) {
        Model model = context.getModel();
        Set<Shape> updated = new HashSet<>();

        for (Shape shape : model.getShapesWithTrait(AddErrorsTrait.class)) {
            List<ShapeId> toAdd = shape.expectTrait(AddErrorsTrait.class).getValues();
            if (toAdd.isEmpty()) {
                continue;
            }

            if (shape.isOperationShape()) {
                OperationShape operation = shape.asOperationShape().get();
                Set<ShapeId> existing = operation.getErrorsSet();
                OperationShape.Builder builder = operation.toBuilder();
                for (ShapeId error : toAdd) {
                    if (!existing.contains(error)) {
                        builder.addError(error);
                    }
                }
                updated.add(builder.build());
            } else if (shape.isServiceShape()) {
                ServiceShape service = shape.asServiceShape().get();
                Set<ShapeId> existing = service.getErrorsSet();
                ServiceShape.Builder builder = service.toBuilder();
                for (ShapeId error : toAdd) {
                    if (!existing.contains(error)) {
                        builder.addError(error);
                    }
                }
                updated.add(builder.build());
            }
        }

        if (updated.isEmpty()) {
            return model;
        }

        return ModelTransformer.create().replaceShapes(model, updated);
    }
}
