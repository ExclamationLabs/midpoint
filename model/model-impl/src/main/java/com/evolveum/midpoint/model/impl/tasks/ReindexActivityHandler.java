/*
 * Copyright (C) 2020-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.model.impl.tasks;

import static java.util.Collections.emptyList;

import javax.xml.namespace.QName;

import com.evolveum.midpoint.model.impl.tasks.simple.ExecutionContext;

import com.evolveum.midpoint.schema.util.task.work.ObjectSetUtil;

import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import com.evolveum.midpoint.model.api.ModelPublicConstants;
import com.evolveum.midpoint.model.impl.tasks.simple.SimpleActivityExecution;
import com.evolveum.midpoint.model.impl.tasks.simple.SimpleActivityHandler;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.repo.api.RepoModifyOptions;
import com.evolveum.midpoint.repo.common.activity.ActivityExecutionException;
import com.evolveum.midpoint.repo.common.activity.definition.AbstractWorkDefinition;
import com.evolveum.midpoint.repo.common.activity.definition.ObjectSetSpecificationProvider;
import com.evolveum.midpoint.repo.common.activity.definition.WorkDefinitionFactory.WorkDefinitionSupplier;
import com.evolveum.midpoint.repo.common.task.ActivityReportingOptions;
import com.evolveum.midpoint.repo.common.task.ItemProcessingRequest;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.task.work.LegacyWorkDefinitionSource;
import com.evolveum.midpoint.schema.util.task.work.WorkDefinitionSource;
import com.evolveum.midpoint.schema.util.task.work.WorkDefinitionWrapper;
import com.evolveum.midpoint.task.api.RunningTask;
import com.evolveum.midpoint.util.DebugUtil;
import com.evolveum.midpoint.util.exception.CommonException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ChangeExecutionWorkDefinitionType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectSetType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ReindexingWorkDefinitionType;

/**
 * Task handler for "reindex" task.
 * It simply executes empty modification delta on each repository object.
 * <p>
 * TODO implement also for sub-objects, namely certification cases.
 */
@Component
public class ReindexActivityHandler
        extends SimpleActivityHandler<ObjectType, ReindexActivityHandler.MyWorkDefinition, ExecutionContext> {

    private static final String LEGACY_HANDLER_URI = ModelPublicConstants.REINDEX_TASK_HANDLER_URI;
    private static final Trace LOGGER = TraceManager.getTrace(ReindexActivityHandler.class);

    @Override
    protected @NotNull QName getWorkDefinitionTypeName() {
        return ChangeExecutionWorkDefinitionType.COMPLEX_TYPE;
    }

    @Override
    protected @NotNull Class<MyWorkDefinition> getWorkDefinitionClass() {
        return MyWorkDefinition.class;
    }

    @Override
    protected @NotNull WorkDefinitionSupplier getWorkDefinitionSupplier() {
        return MyWorkDefinition::new;
    }

    @Override
    protected @NotNull String getLegacyHandlerUri() {
        return LEGACY_HANDLER_URI;
    }

    @Override
    protected @NotNull String getShortName() {
        return "Reindexing";
    }

    @Override
    public @NotNull ActivityReportingOptions getDefaultReportingOptions() {
        return new ActivityReportingOptions()
                .enableActionsExecutedStatistics(true)
                .skipWritingOperationExecutionRecords(false); // because of performance
    }

    @Override
    public void beforeExecution(@NotNull SimpleActivityExecution<ObjectType, MyWorkDefinition, ExecutionContext> activityExecution,
            OperationResult opResult) throws ActivityExecutionException, CommonException {
        securityEnforcer.authorizeAll(activityExecution.getRunningTask(), opResult);
    }

    @Override
    public boolean processItem(PrismObject<ObjectType> object, ItemProcessingRequest<PrismObject<ObjectType>> request,
            SimpleActivityExecution<ObjectType, MyWorkDefinition, ExecutionContext> ignored, RunningTask workerTask, OperationResult result) throws CommonException {
        reindexObject(object, result);
        return true;
    }

    private void reindexObject(PrismObject<ObjectType> object, OperationResult result) throws CommonException {
        repositoryService.modifyObject(object.asObjectable().getClass(), object.getOid(), emptyList(),
                RepoModifyOptions.createForceReindex(), result);
    }

    public static class MyWorkDefinition extends AbstractWorkDefinition implements ObjectSetSpecificationProvider {

        private final ObjectSetType objects;

        MyWorkDefinition(WorkDefinitionSource source) {
            if (source instanceof LegacyWorkDefinitionSource) {
                objects = ObjectSetUtil.fromLegacySource((LegacyWorkDefinitionSource) source);
            } else {
                ReindexingWorkDefinitionType typedDefinition = (ReindexingWorkDefinitionType)
                        ((WorkDefinitionWrapper.TypedWorkDefinitionWrapper) source).getTypedDefinition();
                objects = typedDefinition.getObjects();
            }
        }

        @Override
        public ObjectSetType getObjectSetSpecification() {
            return objects;
        }

        @Override
        protected void debugDumpContent(StringBuilder sb, int indent) {
            DebugUtil.debugDumpWithLabelLn(sb, "objects", objects, indent+1);
        }
    }
}
