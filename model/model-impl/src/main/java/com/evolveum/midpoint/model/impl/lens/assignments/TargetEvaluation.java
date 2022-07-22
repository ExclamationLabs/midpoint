/*
 * Copyright (c) 2020 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.model.impl.lens.assignments;

import com.evolveum.midpoint.prism.PrismContainer;
import com.evolveum.midpoint.prism.PrismContainerDefinition;
import com.evolveum.midpoint.prism.PrismContainerValue;

import org.apache.commons.lang3.BooleanUtils;
import org.jetbrains.annotations.NotNull;

import com.evolveum.midpoint.model.common.archetypes.ArchetypeManager;
import com.evolveum.midpoint.model.impl.lens.LensUtil;
import com.evolveum.midpoint.schema.internals.InternalMonitor;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

import org.jetbrains.annotations.Nullable;

/**
 * Evaluates resolved assignment target: its payload (authorizations, GUI config) and assignments/inducements.
 */
public class TargetEvaluation<AH extends AssignmentHolderType> extends AbstractEvaluation<AH> {

    private static final Trace LOGGER = TraceManager.getTrace(TargetEvaluation.class);

    private final OperationResult result;

    /**
     * The resolved target.
     */
    @NotNull private final AssignmentHolderType target;

    /**
     * Overall condition state of this segment. It is composed later with target (role) condition
     * state into targetOverallConditionState.
     */
    private final ConditionState assignmentOverallConditionState;

    /**
     * Condition state after application of target (role) condition.
     */
    private ConditionState targetOverallConditionState;

    /**
     * Aggregated activity of target and the whole path.
     */
    private TargetActivity targetActivity;

    TargetEvaluation(AssignmentPathSegmentImpl segment, EvaluationContext<AH> ctx, OperationResult result) {
        super(segment, ctx);
        this.result = result;

        this.assignmentOverallConditionState = segment.getOverallConditionState();
        this.target = (AssignmentHolderType) segment.getTarget();
    }

    void evaluate() throws SchemaException, ObjectNotFoundException, ExpressionEvaluationException, PolicyViolationException,
            SecurityViolationException, ConfigurationException, CommunicationException {

        assert ctx.assignmentPath.last() == segment;
        assert assignmentOverallConditionState.isNotAllFalse();
        assert segment.isAssignmentActive() || segment.direct;
        checkIfAlreadyEvaluated();

        if (ctx.ae.evaluatedAssignmentTargetCache.canSkip(segment, ctx.primaryAssignmentMode)) {
            LOGGER.trace("Skipping evaluation of segment {} because it is idempotent and we have seen the target before", segment);
            InternalMonitor.recordRoleEvaluationSkip(target, true);
            return;
        }

        LOGGER.debug("Evaluating assignment path segment target: {}", ctx.assignmentPath.shortDumpLazily());
        LOGGER.trace("Evaluating segment target:\n{}", segment.debugDumpLazily(1));

        checkRelationWithTarget(target);
        determineTargetActivity();

        InternalMonitor.recordRoleEvaluation(target, true);

        @Nullable AssignmentTargetEvaluationInformation targetEvaluationInformation;
        if (targetActivity.pathAndTargetActive) {
            // Cache it immediately, even before evaluation. So if there is a cycle in the role path
            // then we can detect it and skip re-evaluation of aggressively idempotent roles.
            targetEvaluationInformation = ctx.ae.evaluatedAssignmentTargetCache.recordProcessing(segment, ctx.primaryAssignmentMode);
        } else {
            targetEvaluationInformation = null;
        }

        int targetPolicyRulesOnEntry = ctx.evalAssignment.getAllTargetsPolicyRulesCount();
        try {
            if (targetActivity.targetActive) {
                // TODO why only for valid targets? This is how it was implemented in original AssignmentEvaluator.
                targetOverallConditionState = ConditionState.merge(
                        assignmentOverallConditionState, determineTargetConditionState());
            } else {
                targetOverallConditionState = assignmentOverallConditionState;
            }

            if (targetOverallConditionState.isNotAllFalse()) {
                evaluateInternal();
            }
        } finally {
            int targetPolicyRulesOnExit = ctx.evalAssignment.getAllTargetsPolicyRulesCount();
            LOGGER.trace("Evaluating segment target done for {}; target policy rules: {} -> {}", segment,
                    targetPolicyRulesOnEntry, targetPolicyRulesOnExit);
            if (targetEvaluationInformation != null) {
                targetEvaluationInformation.setBringsTargetPolicyRules(targetPolicyRulesOnExit > targetPolicyRulesOnEntry);
            }
        }
    }

    private void evaluateInternal()
            throws SchemaException, ExpressionEvaluationException, ObjectNotFoundException, SecurityViolationException,
            ConfigurationException, CommunicationException, PolicyViolationException {
        assert targetOverallConditionState.isNotAllFalse();

        collectEvaluatedAssignmentTarget();

        // we need to evaluate assignments also for non-valid targets, because of target policy rules
        // ... but only for direct ones!
        if (targetActivity.targetActive || segment.direct) {
            evaluateAssignments();
        }

        // We need to collect membership also for disabled targets (provided the assignment itself is enabled): MID-4127.
        // It is quite logical: if a user is member of a disabled role, it still needs to have roleMembershipRef
        // pointing to that role.
        if (targetOverallConditionState.isNewTrue() && Util.shouldCollectMembership(segment)) {
            ctx.membershipCollector.collect(target, segment.relation);
        }

        if (targetActivity.targetActive) {

            // TODO In a way analogous to the membership info above: shouldn't we collect tenantRef information
            //  also for disabled tenants?
            if (targetOverallConditionState.isNewTrue()) {
                collectTenantRef(target, ctx);
            }

            // We continue evaluation even if the relation is non-membership and non-delegation.
            // Computation of isMatchingOrder will ensure that we won't collect any unwanted content.
            evaluateInducements();

            // Evaluation Archetype hierarchy. For now, when there is a superArchetypeRef relation,
            // we handle it as the inducement
            evaluateArchetypeHierarchy();

            // Respective conditions are evaluated inside
            evaluateTargetPayload();

        } else {
            LOGGER.trace("Not collecting payload from target of {} as it is not valid", segment);
        }
    }

    private void evaluateAssignments() throws SchemaException, ObjectNotFoundException, ExpressionEvaluationException,
            PolicyViolationException, SecurityViolationException, ConfigurationException, CommunicationException {
        for (AssignmentType assignment : target.getAssignment()) {
            new TargetAssignmentEvaluation<>(segment, targetOverallConditionState, targetActivity, ctx, result, assignment)
                    .evaluate();
        }
    }

    private void evaluateInducements() throws SchemaException, ObjectNotFoundException, ExpressionEvaluationException,
            PolicyViolationException, SecurityViolationException, ConfigurationException, CommunicationException {
        if (target instanceof AbstractRoleType) {
            for (AssignmentType inducement : ((AbstractRoleType) target).getInducement()) {
                new TargetInducementEvaluation<>(segment, targetOverallConditionState, targetActivity, ctx, result, inducement, false)
                        .evaluate();
            }
        }
    }

    private void evaluateArchetypeHierarchy() throws CommunicationException, ObjectNotFoundException, ConfigurationException, SchemaException, SecurityViolationException, PolicyViolationException, ExpressionEvaluationException {
        if (ArchetypeType.class.isAssignableFrom(target.getClass())) {
            ObjectReferenceType superArchetype = ((ArchetypeType) target).getSuperArchetypeRef();
            if (superArchetype == null) {
                return;
            }

            PrismContainerDefinition<AssignmentType> def = target.asPrismObject().getDefinition().findContainerDefinition(ArchetypeType.F_INDUCEMENT);
            PrismContainer<AssignmentType> inducement = def.instantiate();
            PrismContainerValue<AssignmentType> inducementValue = inducement.createNewValue();
            AssignmentType inducementRealValue = inducementValue.asContainerable();
            inducementRealValue.setTargetRef(superArchetype);

            new TargetInducementEvaluation<>(segment, targetOverallConditionState, targetActivity, ctx, result, inducementRealValue, true)
                    .evaluate();
        }
    }

    private void evaluateTargetPayload() {
        new TargetPayloadEvaluation<>(segment, targetOverallConditionState, targetActivity, ctx).evaluate();
    }

    private ConditionState determineTargetConditionState()
            throws SchemaException, CommunicationException, ObjectNotFoundException, ConfigurationException,
            SecurityViolationException, ExpressionEvaluationException {
        MappingType condition = target instanceof AbstractRoleType ? ((AbstractRoleType) target).getCondition() : null;
        // TODO why we use "segment.source" as source object for condition evaluation even if
        //  we evaluate condition in target role? We should probably use the role itself as the source here.
        AssignmentHolderType source = segment.source;
        return ctx.conditionEvaluator.computeConditionState(
                condition,
                source,
                "condition in " + segment.getTargetDescription(), target,
                result);
    }

    private void collectEvaluatedAssignmentTarget() {
        EvaluatedAssignmentTargetImpl evalAssignmentTarget = new EvaluatedAssignmentTargetImpl(
                target.asPrismObject(),
                segment.isMatchingOrder, // evaluateConstructions: exact meaning of this is to be revised
                ctx.assignmentPath.clone(),
                segment.assignment,
                targetActivity.pathAndTargetActive);
        ctx.evalAssignment.addRole(evalAssignmentTarget, targetOverallConditionState.getAbsoluteRelativityMode()); // TODO absolute or relative?
    }

    private void determineTargetActivity() throws ConfigurationException {
        // FIXME Target state model does not reflect its archetype!
        LifecycleStateModelType targetStateModel =
                ArchetypeManager.determineLifecycleModel(target.asPrismObject(), ctx.ae.systemConfiguration);
        boolean targetActive = LensUtil.isFocusValid(target, ctx.ae.now, ctx.ae.activationComputer, targetStateModel);
        boolean pathAndTargetActive = segment.isFullPathActive() && targetActive;
        targetActivity = new TargetActivity(targetActive, pathAndTargetActive);
    }

    private void checkRelationWithTarget(AssignmentHolderType target)
            throws SchemaException {
        if (target instanceof AbstractRoleType || target instanceof TaskType) { //TODO:
            // OK, just go on
        } else if (target instanceof UserType) {
            if (!ctx.ae.relationRegistry.isDelegation(segment.relation)) {
                throw new SchemaException("Unsupported relation " + segment.relation + " for assignment of target type " + target + " in " + segment.sourceDescription);
            }
        } else {
            throw new SchemaException("Unknown assignment target type " + target + " in " + segment.sourceDescription);
        }
    }

    private void collectTenantRef(AssignmentHolderType targetToSet, EvaluationContext<AH> ctx) {
        if (targetToSet instanceof OrgType) {
            if (BooleanUtils.isTrue(((OrgType) targetToSet).isTenant()) && ctx.evalAssignment.getTenantOid() == null) {
                if (ctx.assignmentPath.hasOnlyOrgs()) {
                    ctx.evalAssignment.setTenantOid(targetToSet.getOid());
                }
            }
        }
    }

    // We should think of a better word, because "activity" conflicts with activities introduced in 4.4.
    static class TargetActivity {
        final boolean targetActive;
        final boolean pathAndTargetActive;

        private TargetActivity(boolean targetActive, boolean pathAndTargetActive) {
            this.targetActive = targetActive;
            this.pathAndTargetActive = pathAndTargetActive;
        }
    }
}
