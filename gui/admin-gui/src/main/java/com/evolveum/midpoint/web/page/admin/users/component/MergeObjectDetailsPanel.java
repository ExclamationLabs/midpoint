/*
 * Copyright (c) 2010-2017 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.web.page.admin.users.component;

import com.evolveum.midpoint.gui.api.component.BasePanel;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import org.apache.wicket.markup.html.basic.Label;

import java.util.List;

/**
 * Created by honchar.
 */
public class MergeObjectDetailsPanel<F extends FocusType> extends BasePanel<F> {
    private static final String ID_OBJECT_NAME = "objectName";
    private static final String ID_OBJECT_FULLNAME = "objectFullName";
    private static final String ID_OBJECT_ASSIGNMENTS_COUNT = "objectAssignmentsCount";
    private static final String ID_OBJECT_PROJECTIONS_COUNT = "objectProjectionsCount";

    private F mergeObject;

    public MergeObjectDetailsPanel(String id, F mergeObject, Class<F> type){
        super(id);
        this.mergeObject = mergeObject;
        initLayout(type);
    }

    private void initLayout(Class<F> type){
        setOutputMarkupId(true);
        Label nameLabel = new Label(ID_OBJECT_NAME, mergeObject.getName());
        add(nameLabel);

        Label fullNameLabel;
        if (UserType.class.equals(type)){
            fullNameLabel = new Label(ID_OBJECT_FULLNAME, ((UserType) mergeObject).getFullName());
        } else {
            fullNameLabel = new Label(ID_OBJECT_FULLNAME, ((AbstractRoleType) mergeObject).getDisplayName());
        }
        add(fullNameLabel);

        Label assignmentsCount = new Label(ID_OBJECT_ASSIGNMENTS_COUNT, getAssignmentsCount());
        add(assignmentsCount);

        Label projectionsCount = new Label(ID_OBJECT_PROJECTIONS_COUNT, getProjectionsCount());
        add(projectionsCount);

    }

    private int getProjectionsCount(){
        if (mergeObject == null){
            return 0;
        }
        List<ObjectReferenceType> referenceTypes = mergeObject.getLinkRef();
        return referenceTypes == null ? 0 : referenceTypes.size();
    }

    private int getAssignmentsCount(){
        return mergeObject != null ?
                (mergeObject.getAssignment() != null ? mergeObject.getAssignment().size() : 0)
                : 0;
    }
}
