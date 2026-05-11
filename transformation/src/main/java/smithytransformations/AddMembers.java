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
import software.amazon.smithy.model.shapes.AbstractShapeBuilder;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.AbstractTrait;
import software.amazon.smithy.model.traits.Trait;
import software.amazon.smithy.model.traits.TraitFactory;
import software.amazon.smithy.model.transform.ModelTransformer;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class AddMembers implements ProjectionTransformer {

    @Override
    public String getName() {
        return "addMembers";
    }

    @Override
    public Model transform(TransformContext context) {
        Model model = context.getModel();
        TraitFactory traitFactory = TraitFactory.createServiceFactory();
        Set<Shape> updated = new HashSet<>();

        for (Shape container : model.getShapesWithTrait(AddMembersTrait.class)) {
            AddMembersTrait trait = container.expectTrait(AddMembersTrait.class);
            AbstractShapeBuilder<?, ?> builder = Shape.shapeToBuilder(container);

            Set<String> existingLower = new HashSet<>();
            for (MemberShape m : container.members()) {
                existingLower.add(m.getMemberName().toLowerCase(Locale.ROOT));
            }
            Set<String> seenLower = new LinkedHashSet<>();
            for (AddMembersEntry entry : trait.getValues()) {
                String lower = entry.getName().toLowerCase(Locale.ROOT);
                if (!seenLower.add(lower)) {
                    throw new IllegalStateException(
                        "addMembers on " + container.getId()
                            + ": member `" + entry.getName()
                            + "` is listed more than once (member names are case-insensitive)");
                }
                if (existingLower.contains(lower)) {
                    throw new IllegalStateException(
                        "addMembers on " + container.getId()
                            + ": member `" + entry.getName()
                            + "` already exists (member names are case-insensitive)");
                }

                MemberShape.Builder memberBuilder = MemberShape.builder()
                    .id(container.getId().withMember(entry.getName()))
                    .target(entry.getTarget());

                entry.getTraitsOrEmpty().forEach((traitId, traitNode) -> {
                    ShapeId tid = ShapeId.from(traitId);
                    Trait memberTrait = traitFactory
                        .createTrait(tid, container.getId(), traitNode)
                        .orElseGet(() -> new GenericTrait(tid, traitNode));
                    memberBuilder.addTrait(memberTrait);
                });

                builder.addMember(memberBuilder.build());
            }

            updated.add(builder.build());
        }

        if (updated.isEmpty()) {
            return model;
        }

        return ModelTransformer.create().replaceShapes(model, updated);
    }

    private static final class GenericTrait extends AbstractTrait {
        private final Node value;

        GenericTrait(ShapeId id, Node value) {
            super(id, value.getSourceLocation());
            this.value = value;
        }

        @Override
        protected Node createNode() {
            return value;
        }
    }
}
