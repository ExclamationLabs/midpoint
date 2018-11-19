/*
 * Copyright (c) 2015-2017 Evolveum
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
package com.evolveum.midpoint.model.impl.lens;

import com.evolveum.midpoint.model.api.context.EvaluatedAssignmentTarget;
import com.evolveum.midpoint.model.api.context.EvaluationOrder;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.schema.RelationRegistry;
import com.evolveum.midpoint.util.DebugUtil;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import org.jetbrains.annotations.NotNull;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.Collection;

/**
 * @author semancik
 *
 */
public class EvaluatedAssignmentTargetImpl implements EvaluatedAssignmentTarget {

	final PrismObject<? extends FocusType> target;
	private final boolean evaluateConstructions;
	@NotNull private final AssignmentPathImpl assignmentPath;	 // TODO reconsider (maybe we should store only some lightweight information here)
	private final AssignmentType assignment;
	private Collection<ExclusionPolicyConstraintType> exclusions = null;
	private final boolean isValid;

	EvaluatedAssignmentTargetImpl(
			PrismObject<? extends FocusType> target, boolean evaluateConstructions,
			@NotNull AssignmentPathImpl assignmentPath, AssignmentType assignment,
			boolean isValid) {
		this.target = target;
		this.evaluateConstructions = evaluateConstructions;
		this.assignmentPath = assignmentPath;
		this.assignment = assignment;
		this.isValid = isValid;
	}

	@Override
	public PrismObject<? extends FocusType> getTarget() {
		return target;
	}

	@Override
	public boolean isDirectlyAssigned() {
		return assignmentPath.size() == 1;
	}

	@Override
	public boolean appliesToFocus() {
		return assignmentPath.last().isMatchingOrder();
	}

	@Override
	public boolean appliesToFocusWithAnyRelation(RelationRegistry relationRegistry) {
		// TODO clean up this method
		if (appliesToFocus()) {
			// This covers any indirectly assigned targets, like user -> org -> parent-org -> root-org -(I)-> role
			// And directly assigned membership relations as well.
			return true;
		}
		// And this covers any directly assigned non-membership relations (like approver or owner).
		// Actually I think these should be also covered by appliesToFocus() i.e. their isMatchingOrder should be true.
		// But for some reason it is currently not so.
		EvaluationOrder order = assignmentPath.last().getEvaluationOrder();
		if (order.getSummaryOrder() == 1) {
			return true;
		}
		if (order.getSummaryOrder() != 0) {
			return false;
		}
		int delegationCount = 0;
		for (QName delegationRelation : relationRegistry.getAllRelationsFor(RelationKindType.DELEGATION)) {
			delegationCount += order.getMatchingRelationOrder(delegationRelation);
		}
		return delegationCount > 0;
	}

	@Override
	public boolean isEvaluateConstructions() {
		return evaluateConstructions;
	}

	@Override
	public AssignmentType getAssignment() {
		return assignment;
	}

	@Override
	@NotNull
	public AssignmentPathImpl getAssignmentPath() {
		return assignmentPath;
	}

	public String getOid() {
		return target.getOid();
	}

	@Override
	public boolean isValid() {
		return isValid;
	}

	/**
	 * Only for legacy exclusions. Not reliable. Do not use if you can avoid it.
	 * It will get deprecated eventually.
	 */
	public Collection<ExclusionPolicyConstraintType> getExclusions() {
		if (exclusions == null) {
			exclusions = new ArrayList<>();

			FocusType focusType = target.asObjectable();
			if (focusType instanceof AbstractRoleType) {
				AbstractRoleType roleType = (AbstractRoleType)focusType;

				// legacy (very old)
				for (ExclusionPolicyConstraintType exclusionType: roleType.getExclusion()) {
					exclusions.add(exclusionType);
				}

				// legacy
				PolicyConstraintsType constraints = roleType.getPolicyConstraints();
				if (constraints != null) {
					for (ExclusionPolicyConstraintType exclusionType: constraints.getExclusion()) {
						exclusions.add(exclusionType);
					}
				}
			}
		}
		return exclusions;
	}

	@Override
	public String debugDump() {
		return debugDump(0);
	}

	@Override
	public String debugDump(int indent) {
		StringBuilder sb = new StringBuilder();
		DebugUtil.debugDumpLabel(sb, "EvaluatedAssignmentTarget", indent);
		sb.append("\n");
		DebugUtil.debugDumpWithLabel(sb, "Target", target, indent + 1);
		sb.append("\n");
		DebugUtil.debugDumpWithLabel(sb, "Assignment", String.valueOf(assignment), indent + 1);
		sb.append("\n");
		DebugUtil.debugDumpWithLabel(sb, "EvaluateConstructions", evaluateConstructions, indent + 1);
		sb.append("\n");
		DebugUtil.debugDumpWithLabel(sb, "Valid", isValid, indent + 1);
		sb.append("\n");
		DebugUtil.debugDumpWithLabel(sb, "Path", assignmentPath, indent + 1);
		return sb.toString();
	}

}
