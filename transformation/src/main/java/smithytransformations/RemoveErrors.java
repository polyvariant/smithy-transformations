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
 * Detaches error shapes from the operations and services carrying {@code @removeErrors}.
 *
 * <p>Only the {@code errors} property is touched — the error structures themselves stay in the
 * model, since other shapes may still reference them. Listing an error that isn't attached is
 * ignored rather than an error, which keeps the trait usable against upstream models that may or
 * may not declare it.
 *
 * <p>Note that removing an error from an operation cannot cancel out one inherited from the
 * enclosing service: on a service, {@code errors} applies to every operation it contains, and
 * Smithy has no per-operation opt-out. Remove it from the service instead.
 */
public final class RemoveErrors implements ProjectionTransformer {

    @Override
    public String getName() {
        return "removeErrors";
    }

    @Override
    public Model transform(TransformContext context) {
        Model model = context.getModel();
        Set<Shape> updated = new HashSet<>();

        for (Shape shape : model.getShapesWithTrait(RemoveErrorsTrait.class)) {
            List<ShapeId> toRemove = shape.expectTrait(RemoveErrorsTrait.class).getValues();
            if (toRemove.isEmpty()) {
                continue;
            }

            if (shape.isOperationShape()) {
                OperationShape operation = shape.asOperationShape().get();
                OperationShape.Builder builder = operation.toBuilder();
                toRemove.forEach(builder::removeError);
                updated.add(builder.build());
            } else if (shape.isServiceShape()) {
                ServiceShape service = shape.asServiceShape().get();
                ServiceShape.Builder builder = service.toBuilder();
                toRemove.forEach(builder::removeError);
                updated.add(builder.build());
            }
        }

        if (updated.isEmpty()) {
            return model;
        }

        return ModelTransformer.create().replaceShapes(model, updated);
    }
}
