/*
 * Copyright (c) 2010-2019 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.report.impl;

import java.util.*;
import javax.xml.namespace.QName;

import com.evolveum.midpoint.audit.api.AuditService;
import com.evolveum.midpoint.common.Clock;
import com.evolveum.midpoint.common.LocalizationService;
import com.evolveum.midpoint.model.api.*;
import com.evolveum.midpoint.model.api.authentication.CompiledObjectCollectionView;
import com.evolveum.midpoint.model.api.interaction.DashboardService;
import com.evolveum.midpoint.model.api.util.DashboardUtils;
import com.evolveum.midpoint.model.common.util.DefaultColumnUtils;
import com.evolveum.midpoint.repo.api.RepositoryService;
import com.evolveum.midpoint.repo.common.activity.ReportOutputCreatedListener;
import com.evolveum.midpoint.repo.common.commandline.CommandLineScriptExecutor;

import com.evolveum.midpoint.repo.common.expression.ExpressionEnvironment;
import com.evolveum.midpoint.repo.common.expression.ExpressionEnvironmentThreadLocalHolder;
import com.evolveum.midpoint.schema.*;

import com.evolveum.midpoint.util.MiscUtil;
import com.evolveum.midpoint.util.QNameUtil;

import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.evolveum.midpoint.model.common.archetypes.ArchetypeManager;
import com.evolveum.midpoint.model.common.expression.functions.FunctionLibrary;
import com.evolveum.midpoint.model.common.expression.script.ScriptExpression;
import com.evolveum.midpoint.model.common.expression.script.ScriptExpressionEvaluationContext;
import com.evolveum.midpoint.model.common.expression.script.ScriptExpressionEvaluatorFactory;
import com.evolveum.midpoint.model.common.expression.script.ScriptExpressionFactory;
import com.evolveum.midpoint.model.common.expression.script.groovy.GroovyScriptEvaluator;
import com.evolveum.midpoint.prism.*;
import com.evolveum.midpoint.repo.common.ObjectResolver;
import com.evolveum.midpoint.repo.common.expression.ExpressionFactory;
import com.evolveum.midpoint.repo.common.expression.ExpressionUtil;
import com.evolveum.midpoint.schema.expression.VariablesMap;
import com.evolveum.midpoint.report.api.ReportService;
import com.evolveum.midpoint.schema.expression.*;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.security.enforcer.api.AuthorizationParameters;
import com.evolveum.midpoint.security.enforcer.api.SecurityEnforcer;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.task.api.TaskManager;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

import static com.evolveum.midpoint.util.MiscUtil.emptyIfNull;

@Component
public class ReportServiceImpl implements ReportService {

    private static final Trace LOGGER = TraceManager.getTrace(ReportServiceImpl.class);

    @Autowired private ModelService model;
    @Autowired private TaskManager taskManager;
    @Autowired private PrismContext prismContext;
    @Autowired private SchemaService schemaService;
    @Autowired private ExpressionFactory expressionFactory;
    @Autowired @Qualifier("modelObjectResolver") private ObjectResolver objectResolver;
    @Autowired @Qualifier("cacheRepositoryService") private RepositoryService repositoryService;
    @Autowired private AuditService auditService;
    @Autowired private ModelAuditService modelAuditService;
    @Autowired private SecurityEnforcer securityEnforcer;
    @Autowired private ScriptExpressionFactory scriptExpressionFactory;
    @Autowired private ArchetypeManager archetypeManager;

    @Autowired private Clock clock;
    @Autowired private ModelService modelService;
    @Autowired private ModelInteractionService modelInteractionService;
    @Autowired private DashboardService dashboardService;
    @Autowired private LocalizationService localizationService;
    @Autowired private CommandLineScriptExecutor commandLineScriptExecutor;
    @Autowired private ScriptingService scriptingService;

    @Autowired(required = false) private List<ReportOutputCreatedListener> reportOutputCreatedListeners;

    @Override
    public Collection<? extends PrismValue> evaluateScript(PrismObject<ReportType> report, @NotNull ExpressionType expression, VariablesMap variables, String shortDesc, Task task, OperationResult result)
            throws SchemaException, ExpressionEvaluationException, ObjectNotFoundException, CommunicationException, ConfigurationException, SecurityViolationException {

        if (expression.getExpressionEvaluator().size() == 1
                && expression.getExpressionEvaluator().get(0).getValue() instanceof ScriptExpressionEvaluatorType) {
            ScriptExpressionEvaluationContext context = new ScriptExpressionEvaluationContext();
            context.setVariables(variables);
            context.setContextDescription(shortDesc);
            context.setTask(task);
            context.setResult(result);
            setupExpressionProfiles(context, report);

            ScriptExpressionEvaluatorType expressionType = (ScriptExpressionEvaluatorType) expression.getExpressionEvaluator().get(0).getValue();
            if (expressionType.getObjectVariableMode() == null) {
                ScriptExpressionEvaluatorConfigurationType defaultScriptConfiguration = report.asObjectable().getDefaultScriptConfiguration();
                expressionType.setObjectVariableMode(defaultScriptConfiguration == null ? ObjectVariableModeType.OBJECT : defaultScriptConfiguration.getObjectVariableMode());
            }
            context.setExpressionType(expressionType);
            context.setObjectResolver(objectResolver);

            ScriptExpression scriptExpression = scriptExpressionFactory.createScriptExpression(
                    expressionType, context.getOutputDefinition(), context.getExpressionProfile(), expressionFactory, context.getContextDescription(),
                    context.getResult());

            scriptExpression.setFunctions(createFunctionLibraries(scriptExpression.getFunctions()));

            ExpressionEnvironmentThreadLocalHolder.pushExpressionEnvironment(
                    new ExpressionEnvironment(context.getTask(), context.getResult()));
            try {
                return scriptExpression.evaluate(context);
            } finally {
                ExpressionEnvironmentThreadLocalHolder.popExpressionEnvironment();
            }
        } else {
            return ExpressionUtil.evaluateExpressionNative(null, variables, null, expression,
                    determineExpressionProfile(report, result), expressionFactory, shortDesc, task, result);
        }
    }

    private Collection<FunctionLibrary> createFunctionLibraries(Collection<FunctionLibrary> originalFunctions) {
        FunctionLibrary midPointLib = new FunctionLibrary();
        midPointLib.setVariableName("report");
        midPointLib.setNamespace("http://midpoint.evolveum.com/xml/ns/public/function/report-3");
        ReportFunctions reportFunctions = new ReportFunctions(prismContext, schemaService, model, taskManager, modelAuditService);
        midPointLib.setGenericFunctions(reportFunctions);

        Collection<FunctionLibrary> functions = new ArrayList<>();
        functions.addAll(originalFunctions);
        functions.add(midPointLib);
        return functions;
    }

    public PrismContext getPrismContext() {
        return prismContext;
    }

    public ExpressionProfile determineExpressionProfile(PrismObject<ReportType> report, OperationResult result) throws SchemaException, ConfigurationException {
        if (report == null) {
            throw new IllegalArgumentException("No report defined, cannot determine profile");
        }
        return archetypeManager.determineExpressionProfile(report, result);
    }

    private void setupExpressionProfiles(ScriptExpressionEvaluationContext context, PrismObject<ReportType> report) throws SchemaException, ConfigurationException {
        ExpressionProfile expressionProfile = determineExpressionProfile(report, context.getResult());
        LOGGER.trace("Using expression profile '" + (expressionProfile == null ? null : expressionProfile.getIdentifier()) + "' for report evaluation, determined from: {}", report);
        context.setExpressionProfile(expressionProfile);
        context.setScriptExpressionProfile(findScriptExpressionProfile(expressionProfile, report));
    }

    private ScriptExpressionProfile findScriptExpressionProfile(ExpressionProfile expressionProfile, PrismObject<ReportType> report) {
        if (expressionProfile == null) {
            return null;
        }
        ExpressionEvaluatorProfile scriptEvaluatorProfile = expressionProfile.getEvaluatorProfile(ScriptExpressionEvaluatorFactory.ELEMENT_NAME);
        if (scriptEvaluatorProfile == null) {
            return null;
        }
        return scriptEvaluatorProfile.getScriptExpressionProfile(getScriptLanguageName(report));
    }

    private String getScriptLanguageName(PrismObject<ReportType> report) {
        // Hardcoded for now
        return GroovyScriptEvaluator.LANGUAGE_NAME;
    }

    @Override
    public PrismObject<ReportType> getReportDefinition(String reportOid, Task task, OperationResult result) throws ObjectNotFoundException, SchemaException, SecurityViolationException, CommunicationException, ConfigurationException, ExpressionEvaluationException {
        return model.getObject(ReportType.class, reportOid, null, task, result);
    }

    @Override
    public boolean isAuthorizedToRunReport(PrismObject<ReportType> report, Task task, OperationResult result) throws SchemaException, ObjectNotFoundException, ExpressionEvaluationException, CommunicationException, ConfigurationException, SecurityViolationException {
        AuthorizationParameters<ReportType, ObjectType> params = AuthorizationParameters.Builder.buildObject(report);
        return securityEnforcer.isAuthorized(ModelAuthorizationAction.RUN_REPORT.getUrl(), null, params, null, task, result);
    }

    @Override
    public boolean isAuthorizedToImportReport(PrismObject<ReportType> report, Task task, OperationResult result) throws SchemaException, ObjectNotFoundException, ExpressionEvaluationException, CommunicationException, ConfigurationException, SecurityViolationException {
        AuthorizationParameters<ReportType, ObjectType> params = AuthorizationParameters.Builder.buildObject(report);
        return securityEnforcer.isAuthorized(ModelAuthorizationAction.IMPORT_REPORT.getUrl(), null, params, null, task, result);
    }

    public CompiledObjectCollectionView createCompiledView(DashboardReportEngineConfigurationType dashboardConfig,
            DashboardWidgetType widget, Task task, OperationResult result) throws CommonException {
        MiscUtil.stateCheck(dashboardConfig != null, "Dashboard engine in report couldn't be null.");

        DashboardWidgetPresentationType presentation = widget.getPresentation();
        MiscUtil.stateCheck(!DashboardUtils.isDataFieldsOfPresentationNullOrEmpty(presentation),
                "DataField of presentation couldn't be null.");
        DashboardWidgetSourceTypeType sourceType = DashboardUtils.getSourceType(widget);
        MiscUtil.stateCheck(sourceType != null, "No source type specified in " + widget);

        CompiledObjectCollectionView compiledCollection = new CompiledObjectCollectionView();
        if (widget.getPresentation() != null && widget.getPresentation().getView() != null) {
            getModelInteractionService().applyView(compiledCollection, widget.getPresentation().getView());
        }
        CollectionRefSpecificationType collectionRefSpecification =
                getDashboardService().getCollectionRefSpecificationType(widget, task, result);
        if (collectionRefSpecification != null) {
            @NotNull CompiledObjectCollectionView compiledCollectionRefSpec = getModelInteractionService().compileObjectCollectionView(
                    collectionRefSpecification, compiledCollection.getTargetClass(prismContext), task, result);
            getModelInteractionService().applyView(compiledCollectionRefSpec, compiledCollection.toGuiObjectListViewType());
            compiledCollection = compiledCollectionRefSpec;
        }

        GuiObjectListViewType reportView = getReportViewByType(
                dashboardConfig, ObjectUtils.defaultIfNull(compiledCollection.getContainerType(), ObjectType.COMPLEX_TYPE));
        if (reportView != null) {
            getModelInteractionService().applyView(compiledCollection, reportView);
        }

        if (compiledCollection.getColumns().isEmpty()) {
           Class<Containerable> type = resolveTypeForReport(compiledCollection);
           getModelInteractionService().applyView(
                   compiledCollection, DefaultColumnUtils.getDefaultView(ObjectUtils.defaultIfNull(type, ObjectType.class)));
        }
        return compiledCollection;
    }

    private GuiObjectListViewType getReportViewByType(DashboardReportEngineConfigurationType dashboardConfig, QName type) {
        for (GuiObjectListViewType view : dashboardConfig.getView()) {
            if (QNameUtil.match(view.getType(), type)) {
                return view;
            }
        }
        return null;
    }

    public CompiledObjectCollectionView createCompiledView(ObjectCollectionReportEngineConfigurationType collectionConfig, boolean useDefaultView, Task task, OperationResult result)
            throws CommunicationException, ObjectNotFoundException, SchemaException, SecurityViolationException, ConfigurationException, ExpressionEvaluationException {
        Validate.notNull(collectionConfig, "Collection engine in report couldn't be null.");

        CompiledObjectCollectionView compiledCollection = new CompiledObjectCollectionView();
        GuiObjectListViewType reportView = collectionConfig.getView();
        if (reportView != null) {
            getModelInteractionService().applyView(compiledCollection, reportView);
        }

        CollectionRefSpecificationType collectionRefSpecification = collectionConfig.getCollection();
        if (collectionRefSpecification != null) {
            @NotNull CompiledObjectCollectionView compiledCollectionRefSpec = getModelInteractionService().compileObjectCollectionView(
                    collectionRefSpecification, compiledCollection.getTargetClass(prismContext), task, result);

            if (Boolean.TRUE.equals(collectionConfig.isUseOnlyReportView())) {
                compiledCollectionRefSpec.getColumns().clear();
            }
            getModelInteractionService().applyView(compiledCollectionRefSpec, compiledCollection.toGuiObjectListViewType());
            compiledCollection = compiledCollectionRefSpec;
        }

        if (compiledCollection.getColumns().isEmpty()) {
            if (useDefaultView) {
                Class<Containerable> type = resolveTypeForReport(compiledCollection);
                getModelInteractionService().applyView(
                        compiledCollection, DefaultColumnUtils.getDefaultView(ObjectUtils.defaultIfNull(type, ObjectType.class)));
            } else {
                return null;
            }
        }
        return compiledCollection;
    }

    public Class<Containerable> resolveTypeForReport(CompiledObjectCollectionView compiledCollection) {
        QName type = compiledCollection.getContainerType();
        ComplexTypeDefinition def = getPrismContext().getSchemaRegistry().findComplexTypeDefinitionByType(type);
        if (def != null) {
            Class<?> clazz = def.getCompileTimeClass();
            if (clazz != null && Containerable.class.isAssignableFrom(clazz)) {
                return (Class<Containerable>) clazz;
            }
        }
        throw new IllegalArgumentException("Couldn't define type for QName " + type);
    }

    public <O extends ObjectType> PrismObject<O> getObjectFromReference(Referencable ref, Task task, OperationResult result) {
        Class<O> type = getPrismContext().getSchemaRegistry().determineClassForType(ref.getType());

        if (ref.asReferenceValue().getObject() != null) {
            return ref.asReferenceValue().getObject();
        }

        PrismObject<O> object = null;
        try {
            object = getModelService().getObject(type, ref.getOid(), null, task, result.createSubresult("get ref object"));
        } catch (Exception e) {
            LOGGER.debug("Couldn't get object from objectRef " + ref, e);
        }
        return object;
    }

    public VariablesMap evaluateSubreportParameters(PrismObject<ReportType> report, VariablesMap variables, Task task, OperationResult result) {
        VariablesMap subreportVariable = new VariablesMap();
        if (report != null && report.asObjectable().getObjectCollection() != null
                && report.asObjectable().getObjectCollection().getSubreport() != null
                && !report.asObjectable().getObjectCollection().getSubreport().isEmpty()) {
            Collection<SubreportParameterType> subreports = report.asObjectable().getObjectCollection().getSubreport();
            List<SubreportParameterType> sortedSubreports = new ArrayList<>(subreports);
            sortedSubreports.sort(Comparator.comparingInt(s -> ObjectUtils.defaultIfNull(s.getOrder(), Integer.MAX_VALUE)));
            for (SubreportParameterType subreport : sortedSubreports) {
                if (subreport.getExpression() == null || subreport.getName() == null) {
                    continue;
                }
                ExpressionType expression = subreport.getExpression();
                try {
                    Collection<? extends PrismValue> subreportParameter = evaluateScript(report, expression, variables, "subreport parameter", task, result);
                    Class<?> subreportParameterClass;
                    if (subreport.getType() != null) {
                        subreportParameterClass = getPrismContext().getSchemaRegistry().determineClassForType(subreport.getType());
                    } else {
                        if (subreportParameter != null && !subreportParameter.isEmpty()) {
                            subreportParameterClass = subreportParameter.iterator().next().getRealClass();
                        } else {
                            subreportParameterClass = Object.class;
                        }
                    }
                    subreportVariable.put(subreport.getName(), subreportParameter, subreportParameterClass);
                } catch (Exception e) {
                    LOGGER.error("Couldn't execute expression " + expression, e);
                }
            }
            return subreportVariable;
        }
        return subreportVariable;
    }

    public Clock getClock() {
        return clock;
    }

    public TaskManager getTaskManager() {
        return taskManager;
    }

    public ModelService getModelService() {
        return modelService;
    }

    public ModelAuditService getModelAuditService() {
        return modelAuditService;
    }

    public AuditService getAuditService() {
        return auditService;
    }

    public ModelInteractionService getModelInteractionService() {
        return modelInteractionService;
    }

    public ObjectResolver getObjectResolver() {
        return objectResolver;
    }

    public RepositoryService getRepositoryService() {
        return repositoryService;
    }

    public DashboardService getDashboardService() {
        return dashboardService;
    }

    public LocalizationService getLocalizationService() {
        return localizationService;
    }

    public ExpressionFactory getExpressionFactory() {
        return expressionFactory;
    }

    public CommandLineScriptExecutor getCommandLineScriptExecutor() {
        return commandLineScriptExecutor;
    }

    public SchemaService getSchemaService() {
        return schemaService;
    }

    public ScriptingService getScriptingService() {
        return scriptingService;
    }

    public @NotNull List<ReportOutputCreatedListener> getReportCreatedListeners() {
        return emptyIfNull(reportOutputCreatedListeners);
    }
}
