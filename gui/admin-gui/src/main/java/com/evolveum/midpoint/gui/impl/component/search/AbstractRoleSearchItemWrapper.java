/*
 * Copyright (c) 2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.gui.impl.component.search;

import com.evolveum.midpoint.gui.api.page.PageBase;
import com.evolveum.midpoint.gui.impl.page.admin.abstractrole.component.MemberOperationsHelper;
import com.evolveum.midpoint.prism.PrismConstants;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismReferenceDefinition;
import com.evolveum.midpoint.prism.path.ItemName;
import com.evolveum.midpoint.prism.query.ObjectFilter;
import com.evolveum.midpoint.prism.query.builder.S_FilterExit;
import com.evolveum.midpoint.schema.constants.ExpressionConstants;
import com.evolveum.midpoint.schema.expression.VariablesMap;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.util.QNameUtil;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;

import javax.xml.namespace.QName;
import java.util.Collections;
import java.util.List;

public abstract class AbstractRoleSearchItemWrapper extends AbstractSearchItemWrapper {

    private static final Trace LOGGER = TraceManager.getTrace(AbstractRoleSearchItemWrapper.class);
    private SearchConfigurationWrapper searchConfig;
    protected String name;
    protected String help;

    public static final String F_SEARCH_CONFIG = "searchConfig";

    public AbstractRoleSearchItemWrapper(SearchConfigurationWrapper searchConfig) {
        this.searchConfig = searchConfig;
    }

    @Override
    public ObjectFilter createFilter(Class type, PageBase pageBase, VariablesMap variables) {
        ObjectReferenceType ref = getParentVariables(variables);
        if (ref == null) {
            return null;
        }

        ScopeSearchItemWrapper scope = getScopeSearchItemWrapper();
        if (scope != null && SearchBoxScopeType.SUBTREE.equals(scope.getValue().getValue())) {
            return pageBase.getPrismContext().queryFor(type).isChildOf(ref.asReferenceValue()).buildFilter();
        }

        PrismContext prismContext = pageBase.getPrismContext();
        List relations;
        QName relation = getSearchConfig().getDefaultRelation();
        if (QNameUtil.match(relation, PrismConstants.Q_ANY)){
            relations = getSearchConfig().getSupportedRelations();
        } else {
            relations = Collections.singletonList(relation);
        }

        ObjectFilter filter;
        Boolean indirect = getSearchConfig().isIndirect();

        if(BooleanUtils.isTrue(indirect)) {
            filter = prismContext.queryFor(type)
                    .item(FocusType.F_ROLE_MEMBERSHIP_REF).ref(MemberOperationsHelper.createReferenceValuesList(ref, relations))
                    .buildFilter();
        } else {
            S_FilterExit q = prismContext.queryFor(type).exists(AssignmentHolderType.F_ASSIGNMENT)
                    .block()
                    .item(AssignmentType.F_TARGET_REF)
                    .ref(MemberOperationsHelper.createReferenceValuesList(ref, relations));

            if (!getSearchConfig().isTenantEmpty()) {
                q = q.and().item(AssignmentType.F_TENANT_REF).ref(getSearchConfig().getTenantRef().getOid());
            }

            if (!getSearchConfig().isProjectEmpty()) {
                q = q.and().item(AssignmentType.F_ORG_REF).ref(getSearchConfig().getProjectRef().getOid());
            }
            filter = q.endBlock().buildFilter();
        }
        return filter;
    }

    @Override
    public boolean canRemoveSearchItem() {
        return false;
    }

    private ObjectReferenceType getParentVariables(VariablesMap variables) {
        if (variables == null) {
            return null;
        }
        try {
            return variables.getValue(ExpressionConstants.VAR_PARENT_OBJECT, ObjectReferenceType.class);
        } catch (SchemaException e) {
            LOGGER.error("Couldn't load parent object.");
        }
        return null;
    }

    public SearchConfigurationWrapper getSearchConfig() {
        return searchConfig;
    }

    private ScopeSearchItemWrapper getScopeSearchItemWrapper() {
        List<AbstractSearchItemWrapper> items = searchConfig.getItemsList();
        for (AbstractSearchItemWrapper item : items) {
            if (item instanceof ScopeSearchItemWrapper) {
                return (ScopeSearchItemWrapper) item;
            }
        }
        return null;
    }

    private RelationSearchItemWrapper getRelationSearchItemWrapper() {
        List<AbstractSearchItemWrapper> items = searchConfig.getItemsList();
        for (AbstractSearchItemWrapper item : items) {
            if (item instanceof RelationSearchItemWrapper) {
                return (RelationSearchItemWrapper) item;
            }
        }
        return null;
    }

    private IndirectSearchItemWrapper getIndirectSearchItemWrapper() {
        List<AbstractSearchItemWrapper> items = searchConfig.getItemsList();
        for (AbstractSearchItemWrapper item : items) {
            if (item instanceof IndirectSearchItemWrapper) {
                return (IndirectSearchItemWrapper) item;
            }
        }
        return null;
    }

    private ProjectSearchItemWrapper getProjectSearchItemWrapper() {
        List<AbstractSearchItemWrapper> items = searchConfig.getItemsList();
        for (AbstractSearchItemWrapper item : items) {
            if (item instanceof ProjectSearchItemWrapper) {
                return (ProjectSearchItemWrapper) item;
            }
        }
        return null;
    }

    private TenantSearchItemWrapper getTenantSearchItemWrapper() {
        List<AbstractSearchItemWrapper> items = searchConfig.getItemsList();
        for (AbstractSearchItemWrapper item : items) {
            if (item instanceof TenantSearchItemWrapper) {
                return (TenantSearchItemWrapper) item;
            }
        }
        return null;
    }

    @Override
    public String getName() {
        return StringUtils.isNotEmpty(name) ? name : getNameResourceKey();
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getHelp() {
        return StringUtils.isNotEmpty(help) ? help : getHelpResourceKey();
    }
    public void setHelp(String help) {
        this.help = help;
    }

    protected abstract String getNameResourceKey();
    protected abstract String getHelpResourceKey();

    protected PrismReferenceDefinition getReferenceDefinition(ItemName refName) {
        return PrismContext.get().getSchemaRegistry()
                .findContainerDefinitionByCompileTimeClass(AssignmentType.class)
                .findReferenceDefinition(refName);
    }

}
