/*
 * Copyright (c) 2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.gui.impl.component.search;


import com.evolveum.midpoint.gui.api.page.PageBase;
import com.evolveum.midpoint.prism.Containerable;
import com.evolveum.midpoint.prism.query.ObjectFilter;
import com.evolveum.midpoint.schema.expression.TypedValue;
import com.evolveum.midpoint.schema.expression.VariablesMap;
import com.evolveum.midpoint.util.DisplayableValue;
import com.evolveum.midpoint.web.component.util.SelectableBean;
import com.evolveum.midpoint.web.component.util.SelectableRow;
import com.evolveum.midpoint.xml.ns._public.common.common_3.SearchBoxModeType;

import java.io.Serializable;
import java.util.Objects;

public abstract class AbstractSearchItemWrapper<T extends Serializable> implements Serializable, SelectableRow {

    public static final String F_SELECTED = "selected";
    public static final String F_VALUE = "value.value";
    public static final String F_DISPLAYABLE_VALUE = "value";
    public static final String F_NAME = "name";
    public static final String F_HELP = "help";
    public static final String F_TITLE = "title";

    private DisplayableValue<T> value;
    private boolean applyFilter;
    private boolean selected;
    private boolean visible;
    private boolean canConfigure = true;

    String functionParameterName;
    TypedValue functionParameterValue;

    public abstract Class<? extends AbstractSearchItemPanel> getSearchItemPanelClass();

    public abstract String getName();

    public abstract String getHelp();

    public abstract String getTitle();

    public abstract DisplayableValue<T> getDefaultValue();

    public abstract <C extends Containerable> ObjectFilter createFilter(Class<C> type, PageBase pageBase, VariablesMap variables);

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public boolean isEnabled() {
        return true;
    }

    public boolean canRemoveSearchItem() {
        return canConfigure;
    }

    public void setCanConfigure(boolean canConfigure) {
        this.canConfigure = canConfigure;
    }

    public DisplayableValue<T> getValue() {
        if (value == null) {
            setValue(getDefaultValue());
        }
        return value;
    }

    public String getFunctionParameterName() {
        return functionParameterName;
    }

    public void setFunctionParameterName(String functionParameterName) {
        this.functionParameterName = functionParameterName;
    }

    public TypedValue getFunctionParameterValue() {
        return functionParameterValue;
    }

    public void setFunctionParameterValue(TypedValue functionParameterValue) {
        this.functionParameterValue = functionParameterValue;
    }

    public void setValue(DisplayableValue<T> value) {
        this.value = value;
    }

    public boolean isApplyFilter(SearchBoxModeType searchBoxMode) {
        return isVisible();
    }

    public void setApplyFilter(boolean applyFilter) {
        this.applyFilter = applyFilter;
    }

    @Override
    public boolean isSelected() {
        return selected;
    }

    @Override
    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    @Override
    public boolean equals(Object o) {
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash();
    }

}
