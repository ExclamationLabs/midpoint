/*
 * Copyright (C) 2010-2023 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.security.enforcer.impl;

import static com.evolveum.midpoint.security.enforcer.impl.PhaseSelector.*;
import static com.evolveum.midpoint.security.enforcer.impl.SecurityEnforcerImpl.FILTER_TRACE_ENABLED;
import static com.evolveum.midpoint.xml.ns._public.common.common_3.AuthorizationPhaseType.EXECUTION;
import static com.evolveum.midpoint.xml.ns._public.common.common_3.AuthorizationPhaseType.REQUEST;

import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.query.ObjectFilter;
import com.evolveum.midpoint.repo.common.query.SelectorToFilterTranslator;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.ObjectQueryUtil;
import com.evolveum.midpoint.security.api.Authorization;
import com.evolveum.midpoint.security.api.MidPointPrincipal;
import com.evolveum.midpoint.security.api.OwnerResolver;
import com.evolveum.midpoint.security.enforcer.api.FilterGizmo;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.DebugUtil;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AuthorizationPhaseType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.OrderConstraintsType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.OwnedObjectSelectorType;

/**
 * Operation that computes a "security filter", i.e. additional restrictions to be applied in a given filtering/searching
 * situation.
 *
 * @param <T> type of the objects being filtered
 * @param <F> type of the filter being produced, see {@link FilterGizmo}
 */
class EnforcerFilterOperation<T, F> extends EnforcerOperation {

    private final String[] operationUrls;
    private final Class<T> searchResultType;
    private final AuthorizationPreProcessor preProcessor;
    private final boolean includeSpecial;
    private final ObjectFilter origFilter;
    private final String limitAuthorizationAction;
    private final List<OrderConstraintsType> paramOrderConstraints;
    private final FilterGizmo<F> gizmo;
    private final String desc;

    EnforcerFilterOperation(
            String[] operationUrls,
            Class<T> searchResultType,
            AuthorizationPreProcessor preProcessor,
            boolean includeSpecial,
            ObjectFilter origFilter,
            String limitAuthorizationAction,
            List<OrderConstraintsType> paramOrderConstraints,
            FilterGizmo<F> gizmo,
            String desc,
            @Nullable MidPointPrincipal principal,
            @Nullable OwnerResolver ownerResolver,
            @NotNull Beans beans,
            @NotNull Task task) {
        super(principal, ownerResolver, beans, task);
        this.operationUrls = operationUrls;
        this.searchResultType = searchResultType;
        this.preProcessor = preProcessor;
        this.includeSpecial = includeSpecial;
        this.origFilter = origFilter;
        this.limitAuthorizationAction = limitAuthorizationAction;
        this.paramOrderConstraints = paramOrderConstraints;
        this.gizmo = gizmo;
        this.desc = desc;
    }

    F computeSecurityFilter(@Nullable AuthorizationPhaseType phase, OperationResult result)
            throws SchemaException, ObjectNotFoundException, ExpressionEvaluationException,
            CommunicationException, ConfigurationException, SecurityViolationException {
        traceOperationStart();
        F securityFilter;
        if (phase != null) {
            securityFilter = new Partial(nonStrict(phase)).computeFilter(result);
        } else {
            F filterBoth = new Partial(both()).computeFilter(result);
            F filterRequest = new Partial(strict(REQUEST)).computeFilter(result);
            F filterExecution = new Partial(strict(EXECUTION)).computeFilter(result);
            securityFilter =
                    gizmo.or(
                            filterBoth,
                            gizmo.and(filterRequest, filterExecution));
        }
        traceOperationEnd(securityFilter);
        return securityFilter;
    }

    /** TODO */
    private class Partial {

        private final @NotNull PhaseSelector phaseSelector;

        /** TODO */
        @NotNull private final QueryAutzItemPaths queryItemsSpec = new QueryAutzItemPaths();

        /** TODO */
        private F securityFilterAllow = null;

        /** TODO */
        private F securityFilterDeny = null;

        Partial(@NotNull PhaseSelector phaseSelector) {
            this.phaseSelector = phaseSelector;
        }

        /**
         * Returns additional security filter. This filter is supposed to be merged with the original filter.
         *
         * See also {@link SelectorToFilterTranslator}
         */
        private F computeFilter(OperationResult result)
                throws SchemaException, ObjectNotFoundException, ExpressionEvaluationException, CommunicationException,
                ConfigurationException, SecurityViolationException {

            queryItemsSpec.addRequiredItems(origFilter); // MID-3916
            tracePhaseOperationStart();

            for (Authorization authorization : getAuthorizations()) {

                AuthorizationFilterEvaluation<T> autzEvaluation;
                autzEvaluation = new AuthorizationFilterEvaluation<>(
                        searchResultType,
                        origFilter,
                        authorization,
                        preProcessor.getSelectors(authorization),
                        preProcessor.getSelectorLabel(),
                        includeSpecial,
                        EnforcerFilterOperation.this,
                        result);

                if (!autzEvaluation.isApplicableToActions(operationUrls)
                        || !autzEvaluation.isApplicableToPhase(phaseSelector)
                        || !autzEvaluation.isApplicableToLimitations(limitAuthorizationAction, operationUrls)
                        || !autzEvaluation.isApplicableToOrderConstraints(paramOrderConstraints)
                        || !preProcessor.isApplicable(autzEvaluation)) {
                    continue;
                }

                var applicable = autzEvaluation.computeFilter();

                if (applicable) {
                    F autzSecurityFilter =
                            gizmo.adopt(
                                    ObjectQueryUtil.simplify(autzEvaluation.getAutzFilter()),
                                    authorization);
                    // The authorization is applicable to this situation. Now we can process the decision.
                    if (authorization.isAllow()) {
                        securityFilterAllow = gizmo.or(securityFilterAllow, autzSecurityFilter);
                        traceFilter(EnforcerFilterOperation.this, "after 'allow' authorization", authorization, securityFilterAllow, gizmo);
                        if (!gizmo.isNone(autzSecurityFilter)) {
                            queryItemsSpec.collectItems(authorization);
                        }
                    } else { // "deny" type authorization
                        if (authorization.hasItemSpecification()) {
                            // This is a tricky situation. We have deny authorization, but it only denies access to
                            // some items. Therefore we need to find the objects and then filter out the items.
                            // Therefore do not add this authorization into the filter.
                        } else if (gizmo.isAll(autzSecurityFilter)) {
                            // This is "deny all". We cannot have anything stronger than that.
                            // There is no point in continuing the evaluation.
                            F secFilter = gizmo.createDenyAll();
                            tracePhaseOperationEnd(secFilter, "deny all");
                            return secFilter;
                        } else {
                            securityFilterDeny = gizmo.or(securityFilterDeny, autzSecurityFilter);
                        }
                    }
                }
                trace(authorization);
            }

            traceDetails();

            List<ItemPath> unsatisfiedItems = queryItemsSpec.evaluateUnsatisfiedItems();
            if (!unsatisfiedItems.isEmpty()) {
                F secFilter = gizmo.createDenyAll();
                // TODO lazy string concatenation?
                tracePhaseOperationEnd(secFilter, "deny because items " + unsatisfiedItems + " are not allowed");
                return secFilter;
            }
            securityFilterAllow = gizmo.simplify(securityFilterAllow);
            if (securityFilterAllow == null) {
                // Nothing has been allowed. This means default deny.
                F secFilter = gizmo.createDenyAll();
                tracePhaseOperationEnd(secFilter, "default deny");
                return secFilter;
            } else if (securityFilterDeny == null) {
                // Nothing has been denied. We have "allow" filter only.
                tracePhaseOperationEnd(securityFilterAllow, "allow");
                return securityFilterAllow;
            } else {
                // Both "allow" and "deny" filters
                F secFilter = gizmo.and(securityFilterAllow, gizmo.not(securityFilterDeny));
                tracePhaseOperationEnd(secFilter, "allow with deny clauses");
                return secFilter;
            }
        }

        void trace(Authorization autz) {
            traceFilter(EnforcerFilterOperation.this, "(total 'allow') after authorization", autz, securityFilterAllow, gizmo);
            traceFilter(EnforcerFilterOperation.this, "(total 'deny') after authorization", autz, securityFilterDeny, gizmo);
            tracePaths("after authorization", queryItemsSpec);
        }

        void traceDetails() {
            traceFilter(EnforcerFilterOperation.this, "(total 'allow') after all authorizations", "", securityFilterAllow, gizmo);
            traceFilter(EnforcerFilterOperation.this, "(total 'deny') after all authorizations", "", securityFilterDeny, gizmo);
            tracePaths("after all authorizations", queryItemsSpec);
        }

        private void tracePhaseOperationStart() {
            if (traceEnabled) {
                LOGGER.trace("  phase={}, initial query items specification: {}", phaseSelector, queryItemsSpec.shortDumpLazily());
            }
        }

        private void tracePhaseOperationEnd(F secFilter, String reason) {
            if (traceEnabled) {
                // TODO message formatting
                traceFilter(EnforcerFilterOperation.this, desc + ": for whole phase (reason: " + reason + ")", phaseSelector, secFilter, gizmo);
            }
        }
    }

    private void traceOperationStart() {
        if (traceEnabled) {
            // TODO desc?
            LOGGER.trace(
                    "AUTZ: computing security filter principal={}, searchResultType={}, searchType={}: orig filter {}",
                    username, TracingUtil.getTypeName(searchResultType), preProcessor, origFilter);
        }
    }

    private void traceOperationEnd(F securityFilter) {
        if (traceEnabled) {
            // TODO desc?
            LOGGER.trace("AUTZ: computed security filter principal={}, searchResultType={}: {}\n{}",
                    username, TracingUtil.getTypeName(searchResultType),
                    securityFilter, DebugUtil.debugDump(securityFilter, 1));
        }
    }

    static void traceFilter(EnforcerOperation ctx, String message, Object forObj, ObjectFilter filter) {
        if (FILTER_TRACE_ENABLED && ctx.traceEnabled) {
            LOGGER.trace("FILTER {} {}:\n{}", message, TracingUtil.describe(forObj), DebugUtil.debugDump(filter, 1));
        }
    }

    static <F> void traceFilter(EnforcerOperation ctx, String message, Object forObj, F filter, FilterGizmo<F> gizmo) {
        if (FILTER_TRACE_ENABLED && ctx.traceEnabled) {
            LOGGER.trace("FILTER {} {}:\n{}", message, TracingUtil.describe(forObj), gizmo.debugDumpFilter(filter, 1));
        }
    }

    private void tracePaths(String message, QueryAutzItemPaths paths) {
        if (FILTER_TRACE_ENABLED && traceEnabled) {
            LOGGER.trace("PATHS {} {}", message, paths.shortDump());
        }
    }

    /**
     * Extracts relevant parts of authorizations for an {@link EnforcerFilterOperation}.
     *
     * TODO better name
     */
    static abstract class AuthorizationPreProcessor {

        static AuthorizationPreProcessor forObject() {
            return new ForObject();
        }

        static AuthorizationPreProcessor forTarget(@NotNull PrismObject<? extends ObjectType> object) {
            return new ForTarget(object);
        }

        abstract List<? extends OwnedObjectSelectorType> getSelectors(Authorization authorization);

        abstract String getSelectorLabel();

        abstract boolean isApplicable(AuthorizationFilterEvaluation<?> autzEvaluation)
                throws SchemaException, ExpressionEvaluationException, CommunicationException, SecurityViolationException,
                ConfigurationException, ObjectNotFoundException;

        static class ForObject extends AuthorizationPreProcessor {

            @Override
            List<? extends OwnedObjectSelectorType> getSelectors(Authorization authorization) {
                return authorization.getObjectSelectors();
            }

            @Override
            String getSelectorLabel() {
                return "object";
            }

            @Override
            boolean isApplicable(AuthorizationFilterEvaluation<?> autzEvaluation) {
                return true;
            }

            @Override
            public String toString() {
                return "object";
            }
        }

        static class ForTarget extends AuthorizationPreProcessor {
            @NotNull private final PrismObject<? extends ObjectType> object;

            ForTarget(@NotNull PrismObject<? extends ObjectType> object) {
                this.object = object;
            }

            @Override
            List<? extends OwnedObjectSelectorType> getSelectors(Authorization authorization) {
                return authorization.getTargetSelectors();
            }

            @Override
            String getSelectorLabel() {
                return "target";
            }

            @Override
            boolean isApplicable(AuthorizationFilterEvaluation<?> autzEvaluation)
                    throws SchemaException, ExpressionEvaluationException, CommunicationException, SecurityViolationException,
                    ConfigurationException, ObjectNotFoundException {
                return autzEvaluation.isApplicableToObject(object);
            }

            @Override
            public String toString() {
                return "target for " + object;
            }
        }
    }
}
