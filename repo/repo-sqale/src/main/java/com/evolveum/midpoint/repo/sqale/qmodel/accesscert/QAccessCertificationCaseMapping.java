/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.repo.sqale.qmodel.accesscert;

import static com.evolveum.midpoint.xml.ns._public.common.common_3.AccessCertificationCaseType.*;
import static com.evolveum.midpoint.xml.ns._public.common.common_3.FocusType.F_ACTIVATION;

import java.util.Objects;

import com.evolveum.midpoint.prism.PrismContainer;

import com.evolveum.midpoint.prism.PrismContainerValue;

import org.jetbrains.annotations.NotNull;

import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.repo.sqale.SqaleRepoContext;
import com.evolveum.midpoint.repo.sqale.qmodel.common.QContainerMapping;
import com.evolveum.midpoint.repo.sqale.update.SqaleUpdateContext;
import com.evolveum.midpoint.repo.sqlbase.JdbcSession;
import com.evolveum.midpoint.util.MiscUtil;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AccessCertificationCampaignType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AccessCertificationCaseType;

import com.evolveum.midpoint.xml.ns._public.common.common_3.AccessCertificationWorkItemType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ActivationType;

import java.util.List;

/**
 * Mapping between {@link QAccessCertificationCase} and {@link AccessCertificationCaseType}.
 */
public class QAccessCertificationCaseMapping
        extends QContainerMapping<AccessCertificationCaseType, QAccessCertificationCase, MAccessCertificationCase, MAccessCertificationCampaign> {

    public static final String DEFAULT_ALIAS_NAME = "acca";

    private static QAccessCertificationCaseMapping instance;

    public static QAccessCertificationCaseMapping init(
            @NotNull SqaleRepoContext repositoryContext) {
        if (instance == null) {
            instance = new QAccessCertificationCaseMapping(repositoryContext);
        }
        return get();
    }

    public static QAccessCertificationCaseMapping get() {
        return Objects.requireNonNull(instance);
    }

    private QAccessCertificationCaseMapping(@NotNull SqaleRepoContext repositoryContext) {
        super(QAccessCertificationCase.TABLE_NAME, DEFAULT_ALIAS_NAME,
                AccessCertificationCaseType.class, QAccessCertificationCase.class, repositoryContext);

        addNestedMapping(F_ACTIVATION, ActivationType.class)
                .addItemMapping(ActivationType.F_ADMINISTRATIVE_STATUS,
                        enumMapper(q -> q.administrativeStatus))
                .addItemMapping(ActivationType.F_EFFECTIVE_STATUS,
                        enumMapper(q -> q.effectiveStatus))
                .addItemMapping(ActivationType.F_ENABLE_TIMESTAMP,
                        timestampMapper(q -> q.enableTimestamp))
                .addItemMapping(ActivationType.F_DISABLE_REASON,
                        timestampMapper(q -> q.disableTimestamp))
                .addItemMapping(ActivationType.F_DISABLE_REASON,
                        stringMapper(q -> q.disableReason))
                .addItemMapping(ActivationType.F_VALIDITY_STATUS,
                        enumMapper(q -> q.validityStatus))
                .addItemMapping(ActivationType.F_VALID_FROM,
                        timestampMapper(q -> q.validFrom))
                .addItemMapping(ActivationType.F_VALID_TO,
                        timestampMapper(q -> q.validTo))
                .addItemMapping(ActivationType.F_VALIDITY_CHANGE_TIMESTAMP,
                        timestampMapper(q -> q.validityChangeTimestamp))
                .addItemMapping(ActivationType.F_ARCHIVE_TIMESTAMP,
                        timestampMapper(q -> q.archiveTimestamp));

        addItemMapping(F_CURRENT_STAGE_OUTCOME, stringMapper(q -> q.currentStageOutcome));

        // TODO: iteration -> campaignIteration
        addItemMapping(F_ITERATION, integerMapper(q -> q.campaignIteration));
        addItemMapping(F_OBJECT_REF, refMapper(
                q -> q.objectRefTargetOid,
                q -> q.objectRefTargetType,
                q -> q.objectRefRelationId));
        addItemMapping(F_ORG_REF, refMapper(
                q -> q.orgRefTargetOid,
                q -> q.orgRefTargetType,
                q -> q.orgRefRelationId));
        addItemMapping(F_OUTCOME, stringMapper(q -> q.outcome));
        addItemMapping(F_REMEDIED_TIMESTAMP, timestampMapper(q -> q.remediedTimestamp));
        addItemMapping(F_CURRENT_STAGE_DEADLINE, timestampMapper(q -> q.currentStageCreateTimestamp));
        addItemMapping(F_CURRENT_STAGE_CREATE_TIMESTAMP, timestampMapper(q -> q.currentStageCreateTimestamp));
        addItemMapping(F_STAGE_NUMBER, integerMapper(q -> q.stageNumber));
        addItemMapping(F_TARGET_REF, refMapper(
                q -> q.targetRefTargetOid,
                q -> q.targetRefTargetType,
                q -> q.targetRefRelationId));
        addItemMapping(F_TENANT_REF, refMapper(
                q -> q.tenantRefTargetOid,
                q -> q.tenantRefTargetType,
                q -> q.tenantRefRelationId));

//        addRefMapping(F_ASSIGNEE_REF,
//                QCaseWorkItemReferenceMapping.initForCaseWorkItemAssignee(repositoryContext));
//        addRefMapping(F_CANDIDATE_REF,
//                QCaseWorkItemReferenceMapping.initForCaseWorkItemCandidate(repositoryContext));

    }

    @Override
    protected QAccessCertificationCase newAliasInstance(String alias) {
        return new QAccessCertificationCase(alias);
    }

    @Override
    public MAccessCertificationCase newRowObject() {
        return new MAccessCertificationCase();
    }

    @Override
    public MAccessCertificationCase newRowObject(MAccessCertificationCampaign ownerRow) {
        MAccessCertificationCase row = newRowObject();
        row.ownerOid = ownerRow.oid;
        return row;
    }

    // about duplication see the comment in QObjectMapping.toRowObjectWithoutFullObject
    @SuppressWarnings("DuplicatedCode")
    @Override
    public MAccessCertificationCase insert(AccessCertificationCaseType acase, MAccessCertificationCampaign ownerRow, JdbcSession jdbcSession) throws SchemaException {
        MAccessCertificationCase row = initRowObject(acase, ownerRow);

        // activation
        ActivationType activation = acase.getActivation();
        if (activation != null) {
            row.administrativeStatus = activation.getAdministrativeStatus();
            row.effectiveStatus = activation.getEffectiveStatus();
            row.enableTimestamp = MiscUtil.asInstant(activation.getEnableTimestamp());
            row.disableTimestamp = MiscUtil.asInstant(activation.getDisableTimestamp());
            row.disableReason = activation.getDisableReason();
            row.validityStatus = activation.getValidityStatus();
            row.validFrom = MiscUtil.asInstant(activation.getValidFrom());
            row.validTo = MiscUtil.asInstant(activation.getValidTo());
            row.validityChangeTimestamp = MiscUtil.asInstant(activation.getValidityChangeTimestamp());
            row.archiveTimestamp = MiscUtil.asInstant(activation.getArchiveTimestamp());
        }

        row.currentStageOutcome = acase.getCurrentStageOutcome();
        row.fullObject = repositoryContext().createFullObject(acase);
        // TODO
        row.campaignIteration = acase.getIteration();
        setReference(acase.getObjectRef(),
                o -> row.objectRefTargetOid = o,
                t -> row.objectRefTargetType = t,
                r -> row.objectRefRelationId = r);
        setReference(acase.getOrgRef(),
                o -> row.orgRefTargetOid = o,
                t -> row.orgRefTargetType = t,
                r -> row.orgRefRelationId = r);
        row.outcome = acase.getOutcome();
        row.remediedTimestamp = MiscUtil.asInstant(acase.getRemediedTimestamp());
        row.currentStageDeadline = MiscUtil.asInstant(acase.getCurrentStageDeadline());
        row.currentStageCreateTimestamp = MiscUtil.asInstant(acase.getCurrentStageCreateTimestamp());
        row.stageNumber = acase.getStageNumber();
        setReference(acase.getTargetRef(),
                o -> row.targetRefTargetOid = o,
                t -> row.targetRefTargetType = t,
                r -> row.targetRefRelationId = r);
        setReference(acase.getTenantRef(),
                o -> row.tenantRefTargetOid = o,
                t -> row.tenantRefTargetType = t,
                r -> row.tenantRefRelationId = r);

        insert(row, jdbcSession);

        storeWorkItems(ownerRow, row, acase, jdbcSession);

//        storeRefs(row, acase.getAssigneeRef(),
//                QCaseWorkItemReferenceMapping.getForCaseWorkItemAssignee(), jdbcSession);
//        storeRefs(row, acase.getCandidateRef(),
//                QCaseWorkItemReferenceMapping.getForCaseWorkItemCandidate(), jdbcSession);

        return row;
    }

    @Override
    public void afterModify(
            SqaleUpdateContext<AccessCertificationCaseType, QAccessCertificationCase, MAccessCertificationCase> updateContext)
            throws SchemaException {

        PrismContainer<AccessCertificationCampaignType> caseContainer = (PrismContainer) updateContext.findItem(AccessCertificationCampaignType.F_CASE);
        // row in context already knows its CID
        PrismContainerValue<AccessCertificationCampaignType> caseContainerValue = caseContainer.findValue(updateContext.row().cid);
        byte[] fullObject = repositoryContext().createFullObject(caseContainerValue.asContainerable());
        updateContext.set(updateContext.entityPath().fullObject, fullObject);
    }

    public void storeWorkItems(@NotNull MAccessCertificationCampaign campaignRow,
            @NotNull MAccessCertificationCase caseRow, @NotNull AccessCertificationCaseType schemaObject, @NotNull JdbcSession jdbcSession) throws SchemaException {

        List<AccessCertificationWorkItemType> wis = schemaObject.getWorkItem();
        if (!wis.isEmpty()) {
            for (AccessCertificationWorkItemType wi : wis) {
                QAccessCertificationWorkItemMapping.get().insert(wi, campaignRow, caseRow, jdbcSession);
            }
        }
    }
}