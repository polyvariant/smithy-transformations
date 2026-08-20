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
import software.amazon.smithy.model.shapes.AbstractShapeBuilder;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.transform.ModelTransformer;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Removes named members from the aggregate shapes carrying {@code @removeMembers}.
 *
 * <p>Names are matched case-insensitively, matching how Smithy itself treats member name
 * uniqueness. Naming a member the shape doesn't have is ignored rather than an error, so the trait
 * stays usable against an upstream model that may or may not declare it.
 *
 * <p>The container is rebuilt from the members that survive, since shape builders have no
 * {@code removeMember}. Only the container is touched: removing a member that other shapes still
 * reference (through a {@code @required} target elsewhere, say) is the caller's problem to avoid.
 */
public final class RemoveMembers implements ProjectionTransformer {

    @Override
    public String getName() {
        return "removeMembers";
    }

    @Override
    public Model transform(TransformContext context) {
        Model model = context.getModel();
        Set<Shape> updated = new HashSet<>();

        for (Shape container : model.getShapesWithTrait(RemoveMembersTrait.class)) {
            List<String> names = container.expectTrait(RemoveMembersTrait.class).getValues();
            if (names.isEmpty()) {
                continue;
            }

            Set<String> toRemoveLower = new HashSet<>();
            for (String name : names) {
                toRemoveLower.add(name.toLowerCase(Locale.ROOT));
            }

            AbstractShapeBuilder<?, ?> builder = Shape.shapeToBuilder(container);
            builder.clearMembers();
            boolean changed = false;
            for (MemberShape member : container.members()) {
                if (toRemoveLower.contains(member.getMemberName().toLowerCase(Locale.ROOT))) {
                    changed = true;
                } else {
                    builder.addMember(member);
                }
            }

            if (changed) {
                updated.add(builder.build());
            }
        }

        if (updated.isEmpty()) {
            return model;
        }

        return ModelTransformer.create().replaceShapes(model, updated);
    }
}
