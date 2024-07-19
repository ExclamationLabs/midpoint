/*
 * Copyright (C) 2010-2024 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.gui.impl.page.admin.resource.component.wizard.schemaHandling.associationType.subject.mappingContainer.inbound;

import com.evolveum.midpoint.gui.api.component.wizard.WizardModel;
import com.evolveum.midpoint.gui.api.component.wizard.WizardPanel;
import com.evolveum.midpoint.gui.api.component.wizard.WizardStep;
import com.evolveum.midpoint.gui.impl.component.wizard.WizardPanelHelper;
import com.evolveum.midpoint.gui.impl.page.admin.resource.ResourceDetailsModel;
import com.evolveum.midpoint.gui.impl.component.wizard.AbstractWizardChoicePanelWithSeparatedCreatePanel;
import com.evolveum.midpoint.gui.impl.page.admin.resource.component.wizard.schemaHandling.associationType.ResourceAssociationTypeWizardChoicePanel;
import com.evolveum.midpoint.gui.impl.page.admin.resource.component.wizard.schemaHandling.associationType.basic.ObjectAssociationStepPanel;
import com.evolveum.midpoint.gui.impl.page.admin.resource.component.wizard.schemaHandling.associationType.basic.ResourceAssociationTypeBasicWizardPanel;
import com.evolveum.midpoint.gui.impl.page.admin.resource.component.wizard.schemaHandling.associationType.subject.ResourceAssociationTypeSubjectWizardPanel;
import com.evolveum.midpoint.gui.impl.page.admin.resource.component.wizard.schemaHandling.objectType.activation.ActivationsWizardPanel;
import com.evolveum.midpoint.gui.impl.page.admin.resource.component.wizard.schemaHandling.objectType.correlation.CorrelationWizardPanel;
import com.evolveum.midpoint.gui.impl.page.admin.resource.component.wizard.schemaHandling.objectType.synchronization.SynchronizationWizardPanel;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.schema.SchemaConstantsGenerated;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.web.model.PrismContainerValueWrapperModel;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AssociationSynchronizationExpressionEvaluatorType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.MappingType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowAssociationTypeDefinitionType;

import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowAssociationTypeSubjectDefinitionType;

import org.apache.wicket.ajax.AjaxRequestTarget;

import java.util.ArrayList;
import java.util.List;

/**
 * @author lskublik
 */
public class AssociationInboundEvaluatorWizardPanel extends AbstractWizardChoicePanelWithSeparatedCreatePanel<MappingType> {

    public AssociationInboundEvaluatorWizardPanel(
            String id,
            WizardPanelHelper<MappingType, ResourceDetailsModel> helper) {
        super(id, helper);
    }

    @Override
    protected AssociationInboundBasicWizardPanel createNewTypeWizard(String id, WizardPanelHelper<MappingType, ResourceDetailsModel> helper) {
        return new AssociationInboundBasicWizardPanel(id, helper);
    }

    @Override
    protected AssociationInboundWizardChoicePanel createTypePreview() {
        return new AssociationInboundWizardChoicePanel(getIdOfChoicePanel(), createHelper(false)) {
            @Override
            protected void onTileClickPerformed(AssociationInboundEvaluatorTileType value, AjaxRequestTarget target) {
                switch (value) {
                    case BASIC:
                        showChoiceFragment(target, createNewTypeWizard(getIdOfChoicePanel(), createHelper(true)));
                        break;
//                    case MAPPING:
//                        showTableForAttributesMappings(target);
//                        break;
                    case SYNCHRONIZATION:
                        showSynchronizationConfigWizard(target);
                        break;
                    case CORRELATION:
                        showCorrelationItemsTable(target);
                        break;
                    case ACTIVATION:
                        showActivationsWizard(target);
                        break;
                }

            }

            @Override
            protected void onExitPerformed(AjaxRequestTarget target) {
                super.onExitPerformed(target);
                AssociationInboundEvaluatorWizardPanel.this.onExitPerformed(target);
            }
        };
    }

    private void showCorrelationItemsTable(AjaxRequestTarget target) {
        showChoiceFragment(
                target,
                new CorrelationWizardPanel(
                        getIdOfChoicePanel(),
                        createHelper(
                                ItemPath.create(
                                        SchemaConstantsGenerated.C_ASSOCIATION_SYNCHRONIZATION,
                                        AssociationSynchronizationExpressionEvaluatorType.F_CORRELATION),
                                false))
        );
    }

    private void showSynchronizationConfigWizard(AjaxRequestTarget target) {
        showWizardFragment(
                target,
                new SynchronizationWizardPanel<>(
                        getIdOfWizardPanel(),
                        createHelper(
                                ItemPath.create(
                                        SchemaConstantsGenerated.C_ASSOCIATION_SYNCHRONIZATION,
                                        AssociationSynchronizationExpressionEvaluatorType.F_SYNCHRONIZATION),
                                false))
        );
    }

    private void showActivationsWizard(AjaxRequestTarget target) {
        showWizardFragment(
                target,
                new ActivationsWizardPanel(
                        getIdOfWizardPanel(),
                        createHelper(
                                ItemPath.create(
                                        SchemaConstantsGenerated.C_ASSOCIATION_SYNCHRONIZATION,
                                        AssociationSynchronizationExpressionEvaluatorType.F_ACTIVATION),
                                false))
        );
    }

//    private void showTableForAttributesMappings(AjaxRequestTarget target) {
//        showWizardFragment(
//                target,
//                new AttributeMappingWizardPanel<>(
//                        getIdOfWizardPanel(),
//                        createHelper(
//                                ItemPath.create(
//                                        ShadowAssociationTypeDefinitionType.F_SUBJECT,
//                                        ShadowAssociationTypeSubjectDefinitionType.F_ASSOCIATION),
//                                false))
//        );
//    }
//
//    private void showTableForReferenceMappings(AjaxRequestTarget target) {
//        showWizardFragment(
//                target,
//                new ReferenceMappingWizardPanel(
//                        getIdOfWizardPanel(),
//                        createHelper(
//                                ItemPath.create(
//                                        ShadowAssociationTypeDefinitionType.F_SUBJECT,
//                                        ShadowAssociationTypeSubjectDefinitionType.F_ASSOCIATION),
//                                false))
//        );
//    }
}
