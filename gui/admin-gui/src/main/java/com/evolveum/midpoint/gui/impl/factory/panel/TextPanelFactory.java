/*
 * Copyright (C) 2010-2020 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.gui.impl.factory.panel;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import javax.xml.namespace.QName;

import com.evolveum.midpoint.gui.api.component.autocomplete.AutoCompleteTextPanel;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.springframework.stereotype.Component;

import com.evolveum.midpoint.common.LocalizationService;
import com.evolveum.midpoint.gui.api.component.autocomplete.LookupAutocompletePanel;
import com.evolveum.midpoint.gui.api.prism.wrapper.ItemWrapper;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.util.DOMUtil;
import com.evolveum.midpoint.util.DisplayableValue;
import com.evolveum.midpoint.web.component.input.TextPanel;
import com.evolveum.midpoint.web.component.prism.InputPanel;
import com.evolveum.midpoint.xml.ns._public.common.common_3.LookupTableType;

//FIXME serializable
@Component
public class TextPanelFactory<T> extends AbstractInputGuiComponentFactory<T> implements Serializable {

    @PostConstruct
    public void register() {
        getRegistry().addToRegistry(this);
    }

    @Override
    public <IW extends ItemWrapper<?, ?>> boolean match(IW wrapper) {
        QName type = wrapper.getTypeName();
        return DOMUtil.XSD_STRING.equals(type) || DOMUtil.XSD_LONG.equals(type)
                || DOMUtil.XSD_ANYURI.equals(type) || DOMUtil.XSD_INT.equals(type) || DOMUtil.XSD_INTEGER.equals(type)
                || DOMUtil.XSD_DECIMAL.equals(type);
    }

    @Override
    protected InputPanel getPanel(PrismPropertyPanelContext<T> panelCtx) {
        String lookupTableOid = panelCtx.getPredefinedValuesOid();
        if (lookupTableOid != null) {
            return new LookupAutocompletePanel<>(panelCtx.getComponentId(), panelCtx.getRealValueModel(), panelCtx.getTypeClass(), panelCtx.hasValueEnumerationRef(), lookupTableOid);
        }

        Collection<? extends DisplayableValue<T>> allowedValues = panelCtx.getAllowedValues();
        if (CollectionUtils.isNotEmpty(allowedValues)) {
            IModel<List<DisplayableValue<T>>> choices = Model.ofList(allowedValues.stream().collect(Collectors.toCollection(ArrayList::new)));
            IModel convertModel = new IModel<DisplayableValue<T>>(){
                @Override
                public DisplayableValue<T> getObject() {
                    Object value = panelCtx.getRealValueModel().getObject();
                    for (DisplayableValue<T> dispValue : choices.getObject()) {
                        if (dispValue.getValue().equals(value)) {
                            return dispValue;
                        }
                    }
                    return null;
                }

                @Override
                public void setObject(DisplayableValue<T> object) {
                    panelCtx.getRealValueModel().setObject(object.getValue());
                }
            };
            return WebComponentUtil.createDropDownChoices(panelCtx.getComponentId(), convertModel, choices, true, panelCtx.getPageBase());
        }

        Collection<? extends DisplayableValue<T>> suggestedValues = panelCtx.getSuggestedValues();
        if (CollectionUtils.isNotEmpty(suggestedValues)) {
            Iterator<T> choices = suggestedValues.stream()
                    .map(disValue -> disValue.getValue())
                    .collect(Collectors.toCollection(ArrayList::new)).iterator();
            return new AutoCompleteTextPanel<>(panelCtx.getComponentId(), panelCtx.getRealValueModel(), panelCtx.getTypeClass(), false) {
                @Override
                public Iterator<T> getIterator(String input) {
                    return choices;
                }
            };
        }

        return new TextPanel<>(panelCtx.getComponentId(),
                panelCtx.getRealValueModel(), panelCtx.getTypeClass(), false);
    }

    protected List<String> prepareAutoCompleteList(String input, LookupTableType lookupTable, LocalizationService localizationService) {
        return WebComponentUtil.prepareAutoCompleteList(lookupTable, input, localizationService);
    }
}
