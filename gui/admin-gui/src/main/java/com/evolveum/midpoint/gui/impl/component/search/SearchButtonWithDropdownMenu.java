/*
 * Copyright (C) 2022 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.gui.impl.component.search;

import com.evolveum.midpoint.gui.api.component.BasePanel;
import com.evolveum.midpoint.web.component.menu.cog.InlineMenuItem;

import com.evolveum.midpoint.web.component.menu.cog.InlineMenuItemAction;
import com.evolveum.midpoint.web.component.menu.cog.MenuLinkPanel;
import com.evolveum.midpoint.web.component.util.VisibleEnableBehaviour;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.form.AjaxSubmitLink;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.Model;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public abstract class SearchButtonWithDropdownMenu<E extends Enum> extends BasePanel<List<E>> {
    private static final long serialVersionUID = 1L;

    private static final String ID_SEARCH_BUTTON = "searchButton";
    private static final String ID_SEARCH_BUTTON_LABEL = "searchButtonLabel";
    private static final String ID_MENU_ITEMS = "menuItems";
    private static final String ID_MENU_ITEM = "menuItem";

    E selectedValue = null;

    public SearchButtonWithDropdownMenu(String id, @NotNull IModel<List<E>> menuItemsModel) {
        this(id, menuItemsModel, null);
    }

    public SearchButtonWithDropdownMenu(String id, @NotNull IModel<List<E>> menuItemsModel, E defaultValue) {
        super(id, menuItemsModel);
        selectedValue = defaultValue == null ? menuItemsModel.getObject().get(0) : defaultValue;
    }

    protected void onInitialize() {
        super.onInitialize();
        initLayout();
    }

    private void initLayout() {
        final AjaxSubmitLink searchButton = new AjaxSubmitLink(ID_SEARCH_BUTTON) {

            private static final long serialVersionUID = 1L;

            @Override
            protected void onError(AjaxRequestTarget target) {
                Form form = SearchButtonWithDropdownMenu.this.findParent(Form.class);
                if (form != null) {
                    target.add(form);
                } else {
                    target.add(SearchButtonWithDropdownMenu.this.getPageBase());
                }
            }

            @Override
            protected void onSubmit(AjaxRequestTarget target) {
                searchPerformed(target);
            }
        };
        searchButton.setOutputMarkupId(true);
        searchButton.add(getSearchButtonVisibleEnableBehavior());

        Label buttonLabel = new Label(ID_SEARCH_BUTTON_LABEL, new LoadableDetachableModel<String>() {
            @Override
            protected String load() {
                return createStringResource(selectedValue).getString();
            }
        });
        searchButton.add(buttonLabel);
        add(searchButton);

        ListView<InlineMenuItem> menuItems = new ListView<InlineMenuItem>(ID_MENU_ITEMS, createMenuItemsModel()) {

            private static final long serialVersionUID = 1L;

            @Override
            protected void populateItem(ListItem<InlineMenuItem> item) {
                WebMarkupContainer menuItemBody = new MenuLinkPanel(ID_MENU_ITEM, item.getModel());
                menuItemBody.setRenderBodyOnly(true);
                item.add(menuItemBody);
                menuItemBody.add(new VisibleEnableBehaviour() {
                    @Override
                    public boolean isVisible() {
                        return Boolean.TRUE.equals(item.getModelObject().getVisible().getObject());
                    }
                });
            }
        };
        menuItems.setOutputMarkupId(true);
        add(menuItems);

    }

    protected VisibleEnableBehaviour getSearchButtonVisibleEnableBehavior() {
        return new VisibleEnableBehaviour();
    }

    private IModel<List<InlineMenuItem>> createMenuItemsModel() {
        List<InlineMenuItem> menuItems = new ArrayList<>();
        getModelObject().forEach(item -> {
            InlineMenuItem searchItem = new InlineMenuItem(createStringResource(item)) {
                private static final long serialVersionUID = 1L;

                @Override
                public InlineMenuItemAction initAction() {
                    return new InlineMenuItemAction() {

                        private static final long serialVersionUID = 1L;

                        @Override
                        public void onClick(AjaxRequestTarget target) {
                            target.add(getSearchButton());
                            selectedValue = item;
                            menuItemSelected(target, item);
                        }
                    };
                }

                @Override
                public IModel<Boolean> getVisible() {
                    return isMenuItemVisible(item);
                }
            };
            menuItems.add(searchItem);
        });
        return Model.ofList(menuItems);
    }

    public AjaxSubmitLink getSearchButton() {
        return (AjaxSubmitLink) get(ID_SEARCH_BUTTON);
    }

    public IModel<Boolean> isMenuItemVisible(E item) {
        return Model.of(true);
    }

    protected abstract void searchPerformed(AjaxRequestTarget target);

    protected abstract void menuItemSelected(AjaxRequestTarget target, E item);
}
