/*
 * Copyright (C) 2016-2020 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.web.component.objectdetails;

import com.evolveum.midpoint.gui.api.GuiStyleConstants;
import com.evolveum.midpoint.web.application.PanelDescription;
import com.evolveum.midpoint.web.page.admin.server.CasesTablePanel;

import com.evolveum.midpoint.web.session.UserProfileStorage;

import com.evolveum.midpoint.gui.api.model.LoadableModel;
import com.evolveum.midpoint.gui.api.prism.wrapper.PrismObjectWrapper;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.query.ObjectFilter;
import com.evolveum.midpoint.web.component.form.MidpointForm;
import com.evolveum.midpoint.wf.util.QueryUtils;
import com.evolveum.midpoint.xml.ns._public.common.common_3.CaseType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.FocusType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.MetadataType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.UserType;

/**
 * @author mederly
 * @author semancik
 */
@PanelDescription(identifier = "tasks", applicableFor = UserType.class, label = "Cases", icon = GuiStyleConstants.EVO_CASE_OBJECT_ICON)
public class FocusTasksTabPanel<F extends FocusType>
        extends AbstractObjectTabPanel<F> {
    private static final long serialVersionUID = 1L;

    protected static final String ID_TASK_TABLE = "taskTable";
    protected static final String ID_LABEL = "label";

    public FocusTasksTabPanel(String id, LoadableModel<PrismObjectWrapper<F>> focusModel) {
        super(id, focusModel);
    }

    @Override
    protected void onInitialize() {
        super.onInitialize();
        initLayout();
    }

    private void initLayout() {
        CasesTablePanel casesPanel = new CasesTablePanel(ID_TASK_TABLE) {
            private static final long serialVersionUID = 1L;

            @Override
            protected ObjectFilter getCasesFilter() {
                String oid = getObjectWrapper().getOid();
                return QueryUtils.filterForCasesOverUser(getPageBase().getPrismContext().queryFor(CaseType.class), oid)
                        .desc(ItemPath.create(CaseType.F_METADATA, MetadataType.F_CREATE_TIMESTAMP))
                        .buildFilter();
            }

            @Override
            protected boolean isDashboard() {
                return true;
            }

            @Override
            protected UserProfileStorage.TableId getTableId() {
                return UserProfileStorage.TableId.PAGE_CASE_CHILD_CASES_TAB;
            }
        };
        casesPanel.setOutputMarkupId(true);
        add(casesPanel);
    }
}
