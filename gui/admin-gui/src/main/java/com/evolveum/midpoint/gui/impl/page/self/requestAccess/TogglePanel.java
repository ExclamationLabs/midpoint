/*
 * Copyright (c) 2022 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.gui.impl.page.self.requestAccess;

import com.evolveum.midpoint.gui.api.component.BasePanel;

import org.apache.wicket.model.IModel;

import java.util.List;

/**
 * Created by Viliam Repan (lazyman).
 */
public class TogglePanel extends BasePanel<List<Toggle>> {

    public TogglePanel(String id, IModel<List<Toggle>> model) {
        super(id, model);

        initLayout();
    }

    private void initLayout() {
        // todo implement generic toggle, get rid of ViewTogglePanel. or at least make it reuse this generic component
    }
}
