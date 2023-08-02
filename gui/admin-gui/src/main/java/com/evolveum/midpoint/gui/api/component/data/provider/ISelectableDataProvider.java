/*
 * Copyright (C) 2010-2022 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.gui.api.component.data.provider;

import com.evolveum.midpoint.schema.GetOperationOptions;
import com.evolveum.midpoint.schema.SelectorOptions;

import org.apache.wicket.extensions.markup.html.repeater.data.table.ISortableDataProvider;

import com.evolveum.midpoint.prism.query.ObjectQuery;

import java.util.Collection;

public interface ISelectableDataProvider<S> extends ISortableDataProvider<S, String> {

    void setQuery(ObjectQuery query);

    default boolean canCountBeforeExport() {
        return true;
    }

}
