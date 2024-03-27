/*
 * Copyright (C) 2010-2023 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.gui.impl.page.admin.role.mining.page.wizard;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.evolveum.midpoint.gui.api.prism.wrapper.ItemVisibilityHandler;
import com.evolveum.midpoint.gui.api.prism.wrapper.PrismContainerWrapper;
import com.evolveum.midpoint.web.component.prism.ItemVisibility;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.markup.html.panel.Fragment;

import com.evolveum.midpoint.gui.api.component.wizard.WizardModel;
import com.evolveum.midpoint.gui.api.component.wizard.WizardPanel;
import com.evolveum.midpoint.gui.api.component.wizard.WizardStep;
import com.evolveum.midpoint.gui.api.page.PageBase;
import com.evolveum.midpoint.gui.impl.component.wizard.AbstractWizardPanel;
import com.evolveum.midpoint.gui.impl.component.wizard.WizardPanelHelper;
import com.evolveum.midpoint.gui.impl.page.admin.ObjectChangesExecutorImpl;
import com.evolveum.midpoint.gui.impl.page.admin.assignmentholder.AssignmentHolderDetailsModel;
import com.evolveum.midpoint.gui.impl.page.admin.role.mining.page.page.PageRoleAnalysis;
import com.evolveum.midpoint.model.api.mining.RoleAnalysisService;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.schema.ObjectDeltaOperation;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.component.util.VisibleEnableBehaviour;

import org.apache.wicket.model.IModel;

public class RoleAnalysisSessionWizardPanel extends AbstractWizardPanel<RoleAnalysisSessionType, AssignmentHolderDetailsModel<RoleAnalysisSessionType>> {

    private static final String DOT_CLASS = RoleAnalysisSessionWizardPanel.class.getName() + ".";
    private static final String OP_PROCESS_CLUSTERING = DOT_CLASS + "processClustering";

    public static final Trace LOGGER = TraceManager.getTrace(RoleAnalysisSessionWizardPanel.class);

    public RoleAnalysisSessionWizardPanel(String id, WizardPanelHelper<RoleAnalysisSessionType, AssignmentHolderDetailsModel<RoleAnalysisSessionType>> helper) {
        super(id, helper);
    }

    protected void initLayout() {
        getPageBase().getFeedbackPanel().add(VisibleEnableBehaviour.ALWAYS_INVISIBLE);

        String idOfChoicePanel = getIdOfChoicePanel();

        AnalysisCategoryChoiceStepPanel components = new AnalysisCategoryChoiceStepPanel(idOfChoicePanel, getHelper().getDetailsModel()) {
            @Override
            protected void onExitPerformed(AjaxRequestTarget target) {
                RoleAnalysisSessionWizardPanel.this.onExitPerformed();
            }

            @Override
            protected void onSubmitPerformed(AjaxRequestTarget target) {
                showWizardFragment(target, new WizardPanel(getIdOfWizardPanel(), new WizardModel(createBasicSteps())));
                super.onSubmitPerformed(target);
            }
        };

        Fragment choiceFragment = createChoiceFragment(new ProcessModeChoiceStepPanel(idOfChoicePanel, getHelper().getDetailsModel()) {
            @Override
            protected void onExitPerformed(AjaxRequestTarget target) {
                RoleAnalysisSessionWizardPanel.this.onExitPerformed();
            }

            @Override
            protected void onSubmitPerformed(AjaxRequestTarget target) {
                showChoiceFragment(target, components);
                super.onSubmitPerformed(target);
            }
        });

        add(choiceFragment);
    }

    private List<WizardStep> createBasicSteps() {
        List<WizardStep> steps = new ArrayList<>();

        steps.add(new BasicSessionInformationStepPanel(getHelper().getDetailsModel()) {
            @Override
            public VisibleEnableBehaviour getBackBehaviour() {
                return VisibleEnableBehaviour.ALWAYS_INVISIBLE;
            }

            @Override
            protected void onExitPerformed(AjaxRequestTarget target) {
                RoleAnalysisSessionWizardPanel.this.onExitPerformed();
            }

        });

        steps.add(new RoleAnalysisSessionSimpleObjectsWizardPanel(getHelper().getDetailsModel()) {
            @Override
            public VisibleEnableBehaviour getBackBehaviour() {
                return VisibleEnableBehaviour.ALWAYS_VISIBLE_ENABLED;
            }

            @Override
            public boolean onNextPerformed(AjaxRequestTarget target) {
                return super.onNextPerformed(target);
            }

            @Override
            protected void onExitPerformed(AjaxRequestTarget target) {
                RoleAnalysisSessionWizardPanel.this.onExitPerformed();
            }
        });

        RoleAnalysisSessionType session = getAssignmentHolderModel().getObjectType();
        RoleAnalysisOptionType analysisOption = session.getAnalysisOption();
        RoleAnalysisCategoryType analysisCategory = analysisOption.getAnalysisCategory();

        if (analysisCategory.equals(RoleAnalysisCategoryType.ADVANCED) || analysisCategory.equals(RoleAnalysisCategoryType.OUTLIERS)) {
            steps.add(new RoleAnalysisMatchingRulesWizardPanel(getHelper().getDetailsModel()) {
                @Override
                public VisibleEnableBehaviour getBackBehaviour() {
                    return VisibleEnableBehaviour.ALWAYS_VISIBLE_ENABLED;
                }

                @Override
                public boolean onNextPerformed(AjaxRequestTarget target) {
                    return super.onNextPerformed(target);
                }

                @Override
                protected void onExitPerformed(AjaxRequestTarget target) {
                    RoleAnalysisSessionWizardPanel.this.onExitPerformed();
                }
            });
        }

        boolean outlier = analysisCategory.equals(RoleAnalysisCategoryType.OUTLIERS);

        steps.add(new RoleAnalysisSessionDetectionOptionsWizardPanel(getHelper().getDetailsModel()) {
            @Override
            public VisibleEnableBehaviour getBackBehaviour() {
                return VisibleEnableBehaviour.ALWAYS_VISIBLE_ENABLED;
            }

            @Override
            public IModel<String> getTitle() {
                return super.getTitle();
            }

            @Override
            protected IModel<String> getTextModel() {
                if (outlier) {
                    return createStringResource("PageRoleAnalysisSession.wizard.step.work.filter.options.outlier.text");
                }
                return super.getTextModel();
            }

            @Override
            protected IModel<String> getSubTextModel() {
                if (outlier) {
                    return createStringResource("PageRoleAnalysisSession.wizard.step.work.filter.options.outlier.subText");
                }
                return super.getSubTextModel();
            }

            @Override
            protected ItemVisibilityHandler getVisibilityHandler() {
                return wrapper -> {
                    if (analysisCategory.equals(RoleAnalysisCategoryType.OUTLIERS)
                            && (wrapper.getItemName().equals(RoleAnalysisDetectionOptionType.F_MIN_ROLES_OCCUPANCY)
                            || wrapper.getItemName().equals(RoleAnalysisDetectionOptionType.F_MIN_USER_OCCUPANCY))) {
                        return ItemVisibility.HIDDEN;
                    }
                    return ItemVisibility.AUTO;
                };
            }

            @Override
            protected boolean isVisibleSubContainer(PrismContainerWrapper c) {
                return super.isVisibleSubContainer(c);
            }

            @Override
            public boolean onBackPerformed(AjaxRequestTarget target) {
                return super.onBackPerformed(target);
            }

            @Override
            protected void onExitPerformed(AjaxRequestTarget target) {
                RoleAnalysisSessionWizardPanel.this.onExitPerformed();
            }

            @Override
            protected void onSubmitPerformed(AjaxRequestTarget target) {
                Task task = getPageBase().createSimpleTask(OP_PROCESS_CLUSTERING);
                OperationResult result = task.getResult();

                Collection<ObjectDelta<? extends ObjectType>> deltas;
                try {
                    deltas = getHelper().getDetailsModel().collectDeltas(result);

                    Collection<ObjectDeltaOperation<? extends ObjectType>> objectDeltaOperations = new ObjectChangesExecutorImpl()
                            .executeChanges(deltas, false, task, result, target);

                    String sessionOid = ObjectDeltaOperation.findAddDeltaOidRequired(objectDeltaOperations,
                            RoleAnalysisSessionType.class);

                    RoleAnalysisService roleAnalysisService = getPageBase().getRoleAnalysisService();

                    PrismObject<RoleAnalysisSessionType> sessionTypeObject = roleAnalysisService.getSessionTypeObject(sessionOid, task, result);

                    if (sessionTypeObject != null) {
                        roleAnalysisService.executeClusteringTask(getPageBase().getModelInteractionService(), sessionTypeObject, null, null, task, result
                        );
                    }
                } catch (Throwable e) {
                    LoggingUtils.logException(LOGGER, "Couldn't process clustering", e);
                    result.recordFatalError(
                            createStringResource("RoleAnalysisSessionWizardPanel.message.clustering.error").getString()
                            , e);
                }

                setResponsePage(PageRoleAnalysis.class);
                ((PageBase) getPage()).showResult(result);
                target.add(getFeedbackPanel());
            }

        });

        return steps;
    }

    private void onExitPerformed() {
        setResponsePage(PageRoleAnalysis.class);
    }

    private void exitToPreview(AjaxRequestTarget target) {
    }

}
