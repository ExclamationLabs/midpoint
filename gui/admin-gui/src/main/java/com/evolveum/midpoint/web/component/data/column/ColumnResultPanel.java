/*
 * Copyright (c) 2010-2019 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.web.component.data.column;

import com.evolveum.midpoint.gui.api.GuiStyleConstants;
import com.evolveum.midpoint.gui.api.component.BasePanel;
import com.evolveum.midpoint.gui.api.model.LoadableModel;
import com.evolveum.midpoint.gui.impl.component.data.column.CompositedIconPanel;
import com.evolveum.midpoint.gui.impl.component.icon.CompositedIcon;
import com.evolveum.midpoint.gui.impl.component.icon.CompositedIconBuilder;
import com.evolveum.midpoint.gui.impl.component.icon.IconCssStyle;
import com.evolveum.midpoint.gui.impl.component.icon.LayeredIconCssStyle;
import com.evolveum.midpoint.repo.common.util.RepoCommonUtils;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.result.OperationResultStatus;
import com.evolveum.midpoint.util.exception.CommunicationException;
import com.evolveum.midpoint.web.component.AjaxButton;
import com.evolveum.midpoint.web.component.util.VisibleEnableBehaviour;
import com.evolveum.midpoint.web.page.admin.server.dto.OperationResultStatusPresentationProperties;
import com.evolveum.midpoint.xml.ns._public.common.common_3.IconType;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.model.IModel;

/**
 * @author skublik
 */

public class ColumnResultPanel extends BasePanel<OperationResult> {

    private static final String ID_RESULT_ICON = "resultIcon";
    private static final String ID_DETAILS_BUTTON = "detailsButton";

    public ColumnResultPanel(String id, IModel<OperationResult> model) {
        super(id, model);
    }

    @Override
    protected void onInitialize() {
        super.onInitialize();
        initLayout();
    }

    @Override
    protected void onAfterRender() {
        super.onAfterRender();
        ((LoadableModel)((CompositedIconPanel)get(ID_RESULT_ICON)).getModel()).reset();
    }

    private void initLayout() {
        LoadableModel<CompositedIcon> compositedIcon = new LoadableModel<CompositedIcon>() {
            @Override
            protected CompositedIcon load() {
                OperationResult status = getModelObject();
                if (status == null || status.getStatus() == null) {
                    return null;
                }
                OperationResultStatusPresentationProperties statusProperties = OperationResultStatusPresentationProperties.parseOperationalResultStatus(
                        status.getStatus());
                String basicIconCssClass = statusProperties.getIcon() + " fa-lg";
                String title = getPageBase().createStringResource(statusProperties.getStatusLabelKey()).getString();

                CompositedIconBuilder builder = new CompositedIconBuilder();
                String additionalCssClass = "";

                Throwable cause = RepoCommonUtils.getResultExceptionIfExists(getModelObject());
                if (status.getStatus().equals(OperationResultStatus.IN_PROGRESS) &&
                        (cause instanceof CommunicationException) && isProjectionResult()){
                    IconType icon = new IconType();
                    icon.setCssClass("fa fa-info-circle " + GuiStyleConstants.BLUE_COLOR);
                    builder.appendLayerIcon(icon, LayeredIconCssStyle.BOTTOM_RIGHT_STYLE);
                    builder.setTitle(getPageBase().createStringResource("ColumnResultPanel.message.communicationFail").getString());
                    additionalCssClass = "change-password-icon-result-center";
                } else {
                    builder.setTitle(title);
                }
                builder.setBasicIcon(basicIconCssClass, IconCssStyle.IN_ROW_STYLE, additionalCssClass);

                return builder.build();
            }
        };

        CompositedIconPanel iconPanel = new CompositedIconPanel(ID_RESULT_ICON, compositedIcon);
        iconPanel.setOutputMarkupId(true);
        add(iconPanel);

        AjaxButton showErrorDetailsButton = new AjaxButton(ID_DETAILS_BUTTON) {
            @Override
            public void onClick(AjaxRequestTarget target) {
                getPageBase().showResult(ColumnResultPanel.this.getModelObject());
                target.add(getPageBase().getFeedbackPanel());
            }
        };
        showErrorDetailsButton.setOutputMarkupId(true);
        showErrorDetailsButton.add(AttributeAppender.append("title", getPageBase().createStringResource("ColumnResultPanel.showDetails")));
        showErrorDetailsButton.add(new VisibleEnableBehaviour(){
            @Override
            public boolean isVisible() {
                return getModelObject() != null && RepoCommonUtils.getResultExceptionIfExists(getModelObject()) != null;
            }
        });
        add(showErrorDetailsButton);
    }

    protected boolean isProjectionResult() {
        return false;
    }
}
