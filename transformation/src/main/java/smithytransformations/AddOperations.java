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

public final class AddOperations implements ProjectionTransformer {

    @Override
    public String getName() {
        return "addOperations";
    }

    @Override
    public Model transform(TransformContext context) {
        Model model = context.getModel();
        Set<Shape> updated = new HashSet<>();

        for (ServiceShape service : model.getServiceShapesWithTrait(AddOperationsTrait.class)) {
            AddOperationsTrait trait = service.expectTrait(AddOperationsTrait.class);
            ServiceShape.Builder builder = service.toBuilder();
            trait.getValues().forEach(builder::addOperation);
            updated.add(builder.build());
        }

        if (updated.isEmpty()) {
            return model;
        }

        return ModelTransformer.create().replaceShapes(model, updated);
    }
}
