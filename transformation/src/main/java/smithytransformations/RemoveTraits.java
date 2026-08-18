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
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.StringNode;
import software.amazon.smithy.model.selector.Selector;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.Trait;
import software.amazon.smithy.model.transform.ModelTransformer;

import java.util.HashSet;
import java.util.Set;

/**
 * Strips traits off every shape in the model.
 *
 * <p>Configured by the {@code removeTraits} metadata key, whose shape (and therefore whose
 * validation) is declared with {@code @metadata} in {@code smithytransformations.smithy}. Metadata
 * rather than a trait, because the transformation is model-wide and has no shape to attach itself
 * to.
 *
 * <p>Each entry is a {@link Selector} matching the <em>trait definition shapes</em> to strip, so the
 * full selector grammar is available: {@code [trait|trait][id|namespace = 'smithy.rules']} for a
 * whole namespace, {@code [id = 'smithy.api#deprecated']} for a single trait.
 */
public final class RemoveTraits implements ProjectionTransformer {

    /** Metadata key holding the selectors. Kept in sync with the {@code @metadata} declaration. */
    static final String METADATA_KEY = "removeTraits";

    @Override
    public String getName() {
        return "removeTraits";
    }

    @Override
    public Model transform(TransformContext context) {
        Model model = context.getModel();

        Node selectorsNode = model.getMetadataProperty(METADATA_KEY).orElse(null);
        if (selectorsNode == null) {
            return model;
        }

        // Resolve the selectors up front: every match is a trait definition shape whose
        // id we then strip wherever it is applied.
        Set<ShapeId> ids = new HashSet<>();
        for (String selector : selectorsNode.expectArrayNode().getElementsAs(StringNode::getValue)) {
            for (Shape match : Selector.parse(selector).select(model)) {
                ids.add(match.getId());
            }
        }

        if (ids.isEmpty()) {
            return model;
        }

        return ModelTransformer.create().removeTraitsIf(
            model,
            (Shape shape, Trait trait) -> ids.contains(trait.toShapeId()));
    }
}
