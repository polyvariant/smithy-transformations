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
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.transform.ModelTransformer;

import java.util.HashSet;
import java.util.Set;

/**
 * Detaches operations from the services carrying {@code @removeOperations}.
 *
 * <p>Only the service's {@code operations} list is touched — the operation shapes themselves stay in
 * the model, so another service can still bind them. Combine with smithy-build's own {@code
 * removeUnusedShapes} if you want the now-orphaned operations gone as well.
 *
 * <p>Listing an operation the service doesn't have is ignored rather than an error.
 */
public final class RemoveOperations implements ProjectionTransformer {

    @Override
    public String getName() {
        return "removeOperations";
    }

    @Override
    public Model transform(TransformContext context) {
        Model model = context.getModel();
        Set<Shape> updated = new HashSet<>();

        for (ServiceShape service : model.getServiceShapesWithTrait(RemoveOperationsTrait.class)) {
            RemoveOperationsTrait trait = service.expectTrait(RemoveOperationsTrait.class);
            if (trait.getValues().isEmpty()) {
                continue;
            }
            ServiceShape.Builder builder = service.toBuilder();
            trait.getValues().forEach(builder::removeOperation);
            updated.add(builder.build());
        }

        if (updated.isEmpty()) {
            return model;
        }

        return ModelTransformer.create().replaceShapes(model, updated);
    }
}
