/*
 * Copyright (C) 2010-2024 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.gui.impl.component.tile;

import java.util.Collection;
import java.util.List;

import org.apache.wicket.AttributeModifier;
import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxEventBehavior;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.extensions.markup.html.repeater.data.table.ISortableDataProvider;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;

import com.evolveum.midpoint.gui.api.model.LoadableModel;
import com.evolveum.midpoint.gui.impl.component.data.provider.SelectableBeanDataProvider;
import com.evolveum.midpoint.gui.impl.component.data.provider.SelectableBeanObjectDataProvider;
import com.evolveum.midpoint.gui.impl.component.search.Search;
import com.evolveum.midpoint.gui.impl.component.search.SearchBuilder;
import com.evolveum.midpoint.gui.impl.component.search.SearchContext;
import com.evolveum.midpoint.gui.impl.page.admin.resource.component.TemplateTile;
import com.evolveum.midpoint.gui.impl.page.admin.resource.component.wizard.schemaHandling.objectType.synchronization.ActionStepPanel;
import com.evolveum.midpoint.model.api.authentication.CompiledObjectCollectionView;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.schema.GetOperationOptions;
import com.evolveum.midpoint.schema.SelectorOptions;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.exception.CommonException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.component.data.BoxedTablePanel;
import com.evolveum.midpoint.web.component.util.SelectableBean;
import com.evolveum.midpoint.web.session.PageStorage;
import com.evolveum.midpoint.web.session.UserProfileStorage;
import com.evolveum.midpoint.xml.ns._public.common.common_3.CollectionRefSpecificationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ContainerPanelConfigurationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;

public class SingleSelectObjectTileTablePanel<O extends ObjectType>
        extends SingleSelectTileTablePanel<SelectableBean<O>, TemplateTile<SelectableBean<O>>>
        implements SelectTileTablePanel<TemplateTile<SelectableBean<O>>, O>{

    private static final Trace LOGGER = TraceManager.getTrace(ActionStepPanel.class);

    public SingleSelectObjectTileTablePanel(
            String id,
            UserProfileStorage.TableId tableId) {
        this(id, Model.of(ViewToggle.TILE), tableId);
    }

    public SingleSelectObjectTileTablePanel(
            String id,
            IModel<ViewToggle> viewToggle,
            UserProfileStorage.TableId tableId) {
        super(id, viewToggle, tableId);
    }

    @Override
    public SelectableBeanObjectDataProvider<O> createProvider() {
        return SelectTileTablePanel.super.createProvider();
    }

    @Override
    protected TemplateTile<SelectableBean<O>> createTileObject(SelectableBean<O> object) {
        TemplateTile<SelectableBean<O>> t = TemplateTile.createTileFromObject(object, getPageBase());
        return t;
    }

    @Override
    public Component createTile(String id, IModel<TemplateTile<SelectableBean<O>>> model) {

        return new SelectableObjectTilePanel<>(id, model) {
            @Override
            protected void onClick(AjaxRequestTarget target) {
                boolean oldState = getModelObject().getValue().isSelected();
                ((SelectableBeanDataProvider) getProvider()).clearSelectedObjects();
                getTilesModel().getObject().forEach(tile -> {
                    tile.setSelected(false);
                    tile.getValue().setSelected(false);
                });

                getModelObject().setSelected(!oldState);
                getModelObject().getValue().setSelected(!oldState);

                refresh(target);
            }
        };
    }

    @Override
    public Class<O> getType() {
        return (Class<O>) ObjectType.class;
    }

    void onSelectTableRow(IModel<SelectableBean<O>> model, AjaxRequestTarget target) {
        super.onSelectTableRow(model, target);
        if (model.getObject().isSelected()) {
            ((SelectableBeanDataProvider) getProvider()).getSelected().add(model.getObject().getValue());
        }
    }
}
