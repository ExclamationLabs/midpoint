/*
 * Copyright (C) 2022 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.gui.impl.page.admin.resource.component.wizard;

import com.evolveum.midpoint.gui.api.page.PageBase;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.gui.api.util.WebModelServiceUtils;
import com.evolveum.midpoint.gui.impl.page.admin.resource.ResourceDetailsModel;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.application.PanelDisplay;
import com.evolveum.midpoint.web.application.PanelInstance;
import com.evolveum.midpoint.web.application.PanelType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.OperationTypeType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ResourceType;

import com.evolveum.midpoint.xml.ns._public.resource.capabilities_3.CapabilityCollectionType;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.model.IModel;
import org.jetbrains.annotations.Nullable;

/**
 * @author lskublik
 */
public abstract class AbstractConfigurationStepPanel extends AbstractResourceWizardStepPanel {

    private static final Trace LOGGER = TraceManager.getTrace(AbstractConfigurationStepPanel.class);

    private static final String OPERATION_RESOURCE_TEST = AbstractConfigurationStepPanel.class.getName() + ".resourceTest";
    public AbstractConfigurationStepPanel(ResourceDetailsModel model) {
        super(model);
    }

    @Override
    protected String getIcon() {
        return "fa fa-cog";
    }

    @Override
    public IModel<String> getTitle() {
        return createStringResource("PageResource.wizard.step.configuration");
    }

    @Override
    protected IModel<?> getTextModel() {
        return createStringResource("PageResource.wizard.configuration.text");
    }

    @Override
    protected IModel<?> getSubTextModel() {
        return createStringResource("PageResource.wizard.configuration.subText");
    }

    @Override
    public boolean onNextPerformed(AjaxRequestTarget target) {

        CapabilityCollectionType capabilities
                = WebComponentUtil.getNativeCapabilities(getResourceModel().getObjectType(), getPageBase());

        if (capabilities.getSchema() != null || capabilities.getTestConnection() != null) {
            PageBase pageBase = getPageBase();
            Task task = pageBase.createSimpleTask(OPERATION_RESOURCE_TEST);
            OperationResult result = task.getResult();

            try {
                pageBase.getModelService().testResource(getResourceModel().getObjectWrapper().getObjectApplyDelta(), task, result);
            } catch (Exception e) {
                LoggingUtils.logUnexpectedException(LOGGER, "Failed to test resource connection", e);
                result.recordFatalError(getString("TestConnectionMessagesPanel.message.testConnection.fatalError"), e);
            }
            result.computeStatus();

            if (result.isSuccess()) {
                return super.onNextPerformed(target);
            }
            pageBase.showResult(result);
            target.add(getFeedback());
        } else {
            super.onNextPerformed(target);
        }

        return false;
    }
}
