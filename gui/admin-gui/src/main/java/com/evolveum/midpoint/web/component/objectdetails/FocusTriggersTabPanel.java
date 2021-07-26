/*
 * Copyright (c) 2019 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.web.component.objectdetails;

import com.evolveum.midpoint.gui.api.model.LoadableModel;
import com.evolveum.midpoint.gui.api.prism.wrapper.PrismContainerWrapper;
import com.evolveum.midpoint.gui.api.prism.wrapper.PrismObjectWrapper;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.gui.impl.component.MultivalueContainerListPanel;
import com.evolveum.midpoint.gui.api.prism.wrapper.PrismContainerValueWrapper;
import com.evolveum.midpoint.web.component.DateLabelComponent;
import com.evolveum.midpoint.web.component.form.MidpointForm;
import com.evolveum.midpoint.web.component.util.VisibleBehaviour;
import com.evolveum.midpoint.web.model.PrismContainerWrapperModel;
import com.evolveum.midpoint.web.session.SessionStorage;
import com.evolveum.midpoint.web.session.UserProfileStorage;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.extensions.markup.html.repeater.data.grid.ICellPopulator;
import org.apache.wicket.extensions.markup.html.repeater.data.table.AbstractColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.model.IModel;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Kate Honchar
 */
public class FocusTriggersTabPanel<F extends FocusType> extends AbstractObjectTabPanel<F> {
    private static final long serialVersionUID = 1L;

    private static final String ID_TRIGGERS_PANEL = "triggersPanel";

    public FocusTriggersTabPanel(String id, LoadableModel<PrismObjectWrapper<F>> focusModel){
        super(id, focusModel);
    }

    @Override
    protected void onInitialize() {
        super.onInitialize();
        initLayout();
    }

    private void initLayout() {
        PrismContainerWrapperModel<F, TriggerType> triggersModel = PrismContainerWrapperModel.fromContainerWrapper(
                getObjectWrapperModel(), FocusType.F_TRIGGER);

        MultivalueContainerListPanel<TriggerType> multivalueContainerListPanel =
                new MultivalueContainerListPanel<TriggerType>(ID_TRIGGERS_PANEL, TriggerType.class) {

                    private static final long serialVersionUID = 1L;

                    @Override
                    protected boolean isCreateNewObjectVisible() {
                        return false;
                    }

                    @Override
                    protected IModel<PrismContainerWrapper<TriggerType>> getContainerModel() {
                        return triggersModel;
                    }

                    @Override
                    protected boolean isHeaderVisible(){
                        return false;
                    }

                    @Override
                    protected String getStorageKey() {
                        return SessionStorage.KEY_TRIGGERS_TAB;
                    }

                    @Override
                    protected UserProfileStorage.TableId getTableId() {
                        return UserProfileStorage.TableId.TRIGGERS_TAB_TABLE;
                    }

                    @Override
                    protected List<IColumn<PrismContainerValueWrapper<TriggerType>, String>> createDefaultColumns() {
                        return createTriggersColumns();
                    }

                    @Override
                    protected void editItemPerformed(AjaxRequestTarget target, IModel<PrismContainerValueWrapper<TriggerType>> rowModel,
                                                                 List<PrismContainerValueWrapper<TriggerType>> listItems) {

                    }
                };
        multivalueContainerListPanel.add(new VisibleBehaviour(() -> triggersModel != null && triggersModel.getObject() != null));
        add(multivalueContainerListPanel);

        setOutputMarkupId(true);
    }

    private List<IColumn<PrismContainerValueWrapper<TriggerType>, String>> createTriggersColumns(){
        List<IColumn<PrismContainerValueWrapper<TriggerType>, String>> columns = new ArrayList<>();
        columns.add(new AbstractColumn<PrismContainerValueWrapper<TriggerType>, String>(createStringResource("FocusTriggersTabPanel.timestampColumn")){
            private static final long serialVersionUID = 1L;

            @Override
            public void populateItem(Item<ICellPopulator<PrismContainerValueWrapper<TriggerType>>> cellItem,
                                        String componentId, IModel<PrismContainerValueWrapper<TriggerType>> rowModel) {
                TriggerType triggerType = unwrapModel(rowModel);
                String timestamp = WebComponentUtil.getLocalizedDate(triggerType != null ? triggerType.getTimestamp() : null,
                        DateLabelComponent.SHORT_SHORT_STYLE);
                cellItem.add(new Label(componentId, timestamp));
            }
        });
         columns.add(new AbstractColumn<PrismContainerValueWrapper<TriggerType>, String>(createStringResource("FocusTriggersTabPanel.handlerUriColumn")){
            private static final long serialVersionUID = 1L;

            @Override
            public void populateItem(Item<ICellPopulator<PrismContainerValueWrapper<TriggerType>>> cellItem,
                                        String componentId, IModel<PrismContainerValueWrapper<TriggerType>> rowModel) {
                TriggerType triggerType = unwrapModel(rowModel);
                String handlerUri = triggerType != null ? triggerType.getHandlerUri() : "";
                cellItem.add(new Label(componentId, handlerUri));
            }
        });
         columns.add(new AbstractColumn<PrismContainerValueWrapper<TriggerType>, String>(createStringResource("FocusTriggersTabPanel.originDescriptionColumn")){
            private static final long serialVersionUID = 1L;

            @Override
            public void populateItem(Item<ICellPopulator<PrismContainerValueWrapper<TriggerType>>> cellItem,
                                        String componentId, IModel<PrismContainerValueWrapper<TriggerType>> rowModel) {
                TriggerType triggerType = unwrapModel(rowModel);
                cellItem.add(new Label(componentId, triggerType != null ? triggerType.getOriginDescription() : ""));
            }
        });
        return columns;
    }

    private TriggerType unwrapModel(IModel<PrismContainerValueWrapper<TriggerType>> rowModel){
        if (rowModel == null || rowModel.getObject() == null){
            return null;
        }
        return rowModel.getObject().getRealValue();
    }

}
