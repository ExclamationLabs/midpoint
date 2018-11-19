/*
 * Copyright (c) 2010-2018 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.evolveum.midpoint.gui.api.util;

import com.evolveum.midpoint.gui.api.page.PageBase;
import com.evolveum.midpoint.model.api.ModelInteractionService;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.task.api.TaskManager;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SystemException;
import com.evolveum.midpoint.web.component.util.VisibleEnableBehaviour;
import com.evolveum.midpoint.web.security.MidPointApplication;
import com.evolveum.midpoint.web.security.SecurityUtils;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.wicket.model.IModel;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Viliam Repan (lazyman).
 */
public class FocusTabVisibleBehavior<O extends ObjectType> extends VisibleEnableBehaviour {
	private static final long serialVersionUID = 1L;

	private static final String OPERATION_LOAD_GUI_CONFIGURATION = FocusTabVisibleBehavior.class.getName() + ".loadGuiConfiguration";

    private IModel<PrismObject<O>> objectModel;
    private String uiAuthorizationUrl;
    private boolean visibleOnHistoryPage = false;
    private boolean isHistoryPage = false;
    private PageBase pageBase;

    public FocusTabVisibleBehavior(IModel<PrismObject<O>> objectModel, String uiAuthorizationUrl, boolean visibleOnHistoryPage, boolean isHistoryPage, PageBase pageBase) {
        this.objectModel = objectModel;
        this.uiAuthorizationUrl = uiAuthorizationUrl;
        this.visibleOnHistoryPage = visibleOnHistoryPage;
        this.isHistoryPage = isHistoryPage;
        this.pageBase = pageBase;
    }

    private ModelInteractionService getModelInteractionService() {
        return ((MidPointApplication) MidPointApplication.get()).getModelInteractionService();
    }

    private TaskManager getTaskManager() {
        return ((MidPointApplication) MidPointApplication.get()).getTaskManager();
    }

    @Override
    public boolean isVisible() {
        PrismObject<O> object = objectModel.getObject();
        if (object == null) {
            return true;
        }

        Task task = WebModelServiceUtils.createSimpleTask(OPERATION_LOAD_GUI_CONFIGURATION,
                SecurityUtils.getPrincipalUser().getUser().asPrismObject(), getTaskManager());
        OperationResult result = task.getResult();

        AdminGuiConfigurationType config;
        try {
            config = getModelInteractionService().getAdminGuiConfiguration(task, result);
        } catch (ObjectNotFoundException | SchemaException e) {
            throw new SystemException("Cannot load GUI configuration: " + e.getMessage(), e);
        }

        // find all object form definitions for specified type, if there is none we'll show all default tabs
        List<ObjectFormType> forms = findObjectForm(config, object);
        if (forms.isEmpty()) {
            return !isHistoryPage || visibleOnHistoryPage;
        }

        // we'll try to find includeDefault, if there is includeDefault=true, we can return true (all tabs visible)
        for (ObjectFormType form : forms) {
            if (BooleanUtils.isTrue(form.isIncludeDefaultForms())) {
                return !isHistoryPage || visibleOnHistoryPage;
            }
        }

        for (ObjectFormType form : forms) {
            FormSpecificationType spec = form.getFormSpecification();
            if (spec == null || StringUtils.isEmpty(spec.getPanelUri())) {
                continue;
            }

            if (ObjectUtils.equals(uiAuthorizationUrl, spec.getPanelUri())) {
                return !isHistoryPage || visibleOnHistoryPage;
            }
        }

        return false;
    }

    private List<ObjectFormType> findObjectForm(AdminGuiConfigurationType config, PrismObject<O> object) {
        List<ObjectFormType> result = new ArrayList<>();

        if (config == null || config.getObjectForms() == null) {
            return result;
        }

        ObjectFormsType forms = config.getObjectForms();
        List<ObjectFormType> list = forms.getObjectForm();
        if (list.isEmpty()) {
            return result;
        }

        for (ObjectFormType form : list) {
            if (isApplicable(form, object)) {
                result.add(form);
            }
        }

        return result;
    }
    
    private boolean isApplicable(ObjectFormType form, PrismObject<O> object) {
    	QName objectType = object.getDefinition().getTypeName();
    	if (!objectType.equals(form.getType())) {
    		return false;
    	}
    	RoleRelationObjectSpecificationType roleRelation = form.getRoleRelation();
    	if (roleRelation != null) {
    		List<QName> subjectRelations = roleRelation.getSubjectRelation();
    		if (!pageBase.hasSubjectRoleRelation(object.getOid(), subjectRelations)) {
    			return false;
    		}
    	}
    	// TODO: roleRelation
    	return true;
    }
}
