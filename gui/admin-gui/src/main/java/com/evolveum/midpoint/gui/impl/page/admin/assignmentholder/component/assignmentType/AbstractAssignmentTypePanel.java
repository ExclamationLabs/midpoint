/*
 * Copyright (C) 2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.gui.impl.page.admin.assignmentholder.component.assignmentType;

import java.util.*;
import java.util.stream.Collectors;

import javax.xml.namespace.QName;

import com.evolveum.midpoint.gui.impl.component.search.AbstractSearchItemWrapper;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;

import com.evolveum.midpoint.gui.api.GuiStyleConstants;
import com.evolveum.midpoint.gui.api.component.AssignmentPopup;
import com.evolveum.midpoint.gui.api.component.AssignmentPopupDto;
import com.evolveum.midpoint.gui.api.model.LoadableModel;
import com.evolveum.midpoint.gui.api.prism.wrapper.PrismContainerValueWrapper;
import com.evolveum.midpoint.gui.api.prism.wrapper.PrismContainerWrapper;
import com.evolveum.midpoint.gui.api.prism.wrapper.PrismObjectValueWrapper;
import com.evolveum.midpoint.gui.api.prism.wrapper.PrismReferenceWrapper;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.gui.api.util.WebModelServiceUtils;
import com.evolveum.midpoint.gui.impl.component.AssignmentsDetailsPanel;
import com.evolveum.midpoint.gui.impl.component.MultivalueContainerDetailsPanel;
import com.evolveum.midpoint.gui.impl.component.MultivalueContainerListPanelWithDetailsPanel;
import com.evolveum.midpoint.gui.impl.component.data.column.CompositedIconColumn;
import com.evolveum.midpoint.gui.impl.component.data.column.PrismContainerWrapperColumn;
import com.evolveum.midpoint.gui.impl.component.icon.CompositedIcon;
import com.evolveum.midpoint.gui.impl.component.icon.CompositedIconBuilder;
import com.evolveum.midpoint.gui.impl.component.icon.IconCssStyle;
import com.evolveum.midpoint.gui.impl.prism.wrapper.PrismReferenceValueWrapperImpl;
import com.evolveum.midpoint.prism.*;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.query.ObjectFilter;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.prism.query.RefFilter;
import com.evolveum.midpoint.schema.constants.ObjectTypes;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.security.api.AuthorizationConstants;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.TunnelException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.component.AjaxIconButton;
import com.evolveum.midpoint.web.component.assignment.AssignmentsUtil;
import com.evolveum.midpoint.web.component.data.ContainerValueDataProviderFactory;
import com.evolveum.midpoint.web.component.data.ISelectableDataProvider;
import com.evolveum.midpoint.web.component.data.column.AjaxLinkColumn;
import com.evolveum.midpoint.web.component.data.column.CheckBoxHeaderColumn;
import com.evolveum.midpoint.web.component.data.column.ColumnMenuAction;
import com.evolveum.midpoint.web.component.menu.cog.ButtonInlineMenuItem;
import com.evolveum.midpoint.web.component.menu.cog.InlineMenuItem;
import com.evolveum.midpoint.web.component.menu.cog.InlineMenuItemAction;
import com.evolveum.midpoint.web.component.prism.ValueStatus;
import com.evolveum.midpoint.gui.impl.component.search.Search;
import com.evolveum.midpoint.gui.impl.component.search.SearchFactory;
import com.evolveum.midpoint.web.component.search.SearchItemDefinition;
import com.evolveum.midpoint.web.component.util.AssignmentListProvider;
import com.evolveum.midpoint.web.component.util.RepoAssignmentListProvider;
import com.evolveum.midpoint.web.component.util.VisibleEnableBehaviour;
import com.evolveum.midpoint.web.session.GenericPageStorage;
import com.evolveum.midpoint.web.session.PageStorage;
import com.evolveum.midpoint.web.session.UserProfileStorage;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

public abstract class AbstractAssignmentTypePanel extends MultivalueContainerListPanelWithDetailsPanel<AssignmentType> {

    private static final Trace LOGGER = TraceManager.getTrace(AbstractAssignmentTypePanel.class);

    private static final String DOT_CLASS = AbstractAssignmentTypePanel.class.getName() + ".";
    protected static final String OPERATION_LOAD_ASSIGNMENTS_LIMIT = DOT_CLASS + "loadAssignmentsLimit";
    protected static final String OPERATION_LOAD_ASSIGNMENTS_TARGET_OBJ = DOT_CLASS + "loadAssignmentsTargetRefObject";
    protected static final String OPERATION_LOAD_ASSIGNMENT_TARGET_RELATIONS = DOT_CLASS + "loadAssignmentTargetRelations";

    private IModel<PrismContainerWrapper<AssignmentType>> model;
    protected int assignmentsRequestsLimit = -1;

    private Class<? extends Objectable> objectType;
    private String objectOid;

    public AbstractAssignmentTypePanel(String id, IModel<PrismContainerWrapper<AssignmentType>> model, ContainerPanelConfigurationType config, Class<? extends Objectable> type, String oid) {
        super(id, AssignmentType.class, config);
        this.model = model;
        this.objectType = type;
        this.objectOid = oid;
    }


    protected void setModel(IModel<PrismContainerWrapper<AssignmentType>> model) {
        this.model = model;
    }

    @Override
    protected void onInitialize() {
        super.onInitialize();
        assignmentsRequestsLimit = AssignmentsUtil.loadAssignmentsLimit(new OperationResult(OPERATION_LOAD_ASSIGNMENTS_LIMIT), getPageBase());
    }

    @Override
    protected boolean isCreateNewObjectVisible() {
        return isNewObjectButtonVisible(getFocusObject());
    }

    protected boolean isRepositorySearchEnabled() {
        return providerFactory().isRepositorySearchEnabled();
    }

    @Override
    protected IModel<PrismContainerWrapper<AssignmentType>> getContainerModel() {
        return model;
    }

    @Override
    protected void cancelItemDetailsPerformed(AjaxRequestTarget target) {
        target.add(AbstractAssignmentTypePanel.this);
    }

    @Override
    protected List<IColumn<PrismContainerValueWrapper<AssignmentType>, String>> createDefaultColumns() {
        List<IColumn<PrismContainerValueWrapper<AssignmentType>, String>> columns = new ArrayList<>();
        columns.add(new PrismContainerWrapperColumn<>(getContainerModel(), AssignmentType.F_ACTIVATION, getPageBase()));

        List<IColumn<PrismContainerValueWrapper<AssignmentType>, String>> additionalColumns = initColumns();
        if (additionalColumns != null) {
            columns.addAll(additionalColumns);
        }
        return columns;
    }

    @Override
    protected IColumn<PrismContainerValueWrapper<AssignmentType>, String> createCheckboxColumn() {
        return new CheckBoxHeaderColumn<>();
    }

    @Override
    protected IColumn<PrismContainerValueWrapper<AssignmentType>, String> createIconColumn() {
        return createAssignmentIconColumn();
    }

    @Override
    protected IColumn<PrismContainerValueWrapper<AssignmentType>, String> createNameColumn(IModel<String> displayModel, GuiObjectColumnType customColumn, ItemPath itemPath, ExpressionType expression) {
        displayModel = displayModel == null ? createStringResource("PolicyRulesPanel.nameColumn") : displayModel;

        return new AjaxLinkColumn<>(displayModel, RepoAssignmentListProvider.TARGET_NAME_STRING, null) {
            private static final long serialVersionUID = 1L;

            @Override
            public IModel<String> createLinkModel(IModel<PrismContainerValueWrapper<AssignmentType>> rowModel) {
                return new LoadableModel<>() {
                    @Override
                    protected String load() {
                        return loadValuesForNameColumn(rowModel, customColumn, itemPath, expression);
                    }
                };
            }

            @Override
            public boolean isEnabled(IModel<PrismContainerValueWrapper<AssignmentType>> rowModel) {
                return rowModel.getObject().getRealValue().getFocusMappings() == null;
            }

            @Override
            public void onClick(AjaxRequestTarget target, IModel<PrismContainerValueWrapper<AssignmentType>> rowModel) {
                AbstractAssignmentTypePanel.this.itemDetailsPerformed(target, rowModel);
            }
        };
    }

    @Override
    protected PageStorage getPageStorage(String storageKey) {
        Map<String, PageStorage> storage = getSession().getSessionStorage().getPageStorageMap();
        PageStorage pageStorage = storage.get(storageKey);
        if (pageStorage != null) {
            return pageStorage;
        }

        pageStorage = new GenericPageStorage();
        storage.put(storageKey, pageStorage);

        return pageStorage;
    }

    private String loadValuesForNameColumn(IModel<PrismContainerValueWrapper<AssignmentType>> rowModel, GuiObjectColumnType customColumn, ItemPath itemPath, ExpressionType expression) {
        if (expression != null || itemPath != null) {
            Collection<String> evaluatedValues = loadExportableColumnDataModel(rowModel, customColumn, itemPath, expression);
            if (CollectionUtils.isEmpty(evaluatedValues)) {
                return "";
            }
            if (evaluatedValues.size() == 1) {
                return evaluatedValues.iterator().next();
            }
            return String.join(", ", evaluatedValues);
        }
        String name = AssignmentsUtil.getName(rowModel.getObject(), getPageBase());
        LOGGER.trace("Name for AssignmentType: " + name);
        if (StringUtils.isBlank(name)) {
            return createStringResource("AssignmentPanel.noName").getString();
        }

        return name;
    }

    @Override
    protected List<InlineMenuItem> createInlineMenu() {
        return getAssignmentMenuActions();
    }

    @Override
    protected boolean isMenuItemVisible(IModel<PrismContainerValueWrapper<AssignmentType>> rowModel) {
        if (rowModel != null
                && ValueStatus.ADDED.equals(rowModel.getObject().getStatus())) {
            return true;
        }
        return !isAssignmentsLimitReached();
    }

    protected abstract List<IColumn<PrismContainerValueWrapper<AssignmentType>, String>> initColumns();

    private List<InlineMenuItem> getAssignmentMenuActions() {
        List<InlineMenuItem> menuItems = new ArrayList<>();

        ButtonInlineMenuItem unassignMenuItem = createUnassignAction();
        if (unassignMenuItem != null) {
            menuItems.add(unassignMenuItem);
        }
        menuItems.add(new ButtonInlineMenuItem(createStringResource("PageBase.button.edit")) {
            private static final long serialVersionUID = 1L;

            @Override
            public CompositedIconBuilder getIconCompositedBuilder() {
                return getDefaultCompositedIconBuilder(GuiStyleConstants.CLASS_EDIT_MENU_ITEM);
            }

            @Override
            public InlineMenuItemAction initAction() {
                return AbstractAssignmentTypePanel.this.createEditColumnAction();
            }
        });
        ButtonInlineMenuItem viewTargetObjectMenuItem = createViewTargetObjectAction();
        menuItems.add(viewTargetObjectMenuItem);
        return menuItems;
    }

    private <AH extends AssignmentHolderType> ButtonInlineMenuItem createUnassignAction() {
        PrismObject<AH> obj = getFocusObject();
        try {
            boolean isUnassignAuthorized = getPageBase().isAuthorized(AuthorizationConstants.AUTZ_UI_ADMIN_UNASSIGN_ACTION_URI,
                    AuthorizationPhaseType.REQUEST, obj,
                    null, null, null);
            if (isUnassignAuthorized) {
                return createUnassignButtonInlineMenuItem(getAssignmentsLimitReachedUnassignTitleModel());
            }
        } catch (Exception ex) {
            LOGGER.error("Couldn't check unassign authorization for the object: {}, {}", obj.getName(), ex.getLocalizedMessage());
            if (WebComponentUtil.isAuthorized(AuthorizationConstants.AUTZ_UI_ADMIN_ASSIGN_ACTION_URI)) {
                return createUnassignButtonInlineMenuItem(createStringResource("PageBase.button.unassign"));
            }
        }
        return null;
    }

    private ButtonInlineMenuItem createUnassignButtonInlineMenuItem(IModel<String> unassignLabelModel) {
        return new ButtonInlineMenuItem(unassignLabelModel) {
            private static final long serialVersionUID = 1L;

            @Override
            public CompositedIconBuilder getIconCompositedBuilder() {
                return getDefaultCompositedIconBuilder(GuiStyleConstants.CLASS_DELETE_MENU_ITEM);
            }

            @Override
            public InlineMenuItemAction initAction() {
                return AbstractAssignmentTypePanel.this.createDeleteColumnAction();
            }
        };
    }

    private ButtonInlineMenuItem createViewTargetObjectAction() {
        ButtonInlineMenuItem menu = new ButtonInlineMenuItem(createStringResource("AssignmentPanel.viewTargetObject")) {
            private static final long serialVersionUID = 1L;

            @Override
            public CompositedIconBuilder getIconCompositedBuilder() {
                return getDefaultCompositedIconBuilder(GuiStyleConstants.CLASS_NAVIGATE_ARROW);
            }

            @Override
            public InlineMenuItemAction initAction() {
                return new ColumnMenuAction<PrismContainerValueWrapper<AssignmentType>>() {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public void onClick(AjaxRequestTarget target) {
                        targetObjectDetailsPerformed(target, getRowModel().getObject());
                    }
                };
            }

            @Override
            public boolean isHeaderMenuItem() {
                return false;
            }

        };
        menu.setVisibilityChecker(isViewTargetObjectMenuVisible());
        return menu;
    }

    private void targetObjectDetailsPerformed(AjaxRequestTarget target, PrismContainerValueWrapper<AssignmentType> assignmentContainer) {
        PrismReferenceWrapper<ObjectReferenceType> targetRef;
        try {
            targetRef = assignmentContainer.findReference(ItemPath.create(AssignmentType.F_TARGET_REF));
        } catch (SchemaException e) {
            getSession().error("Couldn't show details page. More information provided in log.");
            LOGGER.error("Couldn't show details page, no targetRef reference wrapper found: {}", e.getMessage(), e);
            target.add(getPageBase().getFeedbackPanel());
            return;
        }

        if (targetRef != null && targetRef.getValues() != null && targetRef.getValues().size() > 0) {
            PrismReferenceValueWrapperImpl<ObjectReferenceType> refWrapper = targetRef.getValues().get(0);
            if (!StringUtils.isEmpty(refWrapper.getNewValue().getOid())) {
                Class<? extends ObjectType> targetClass = ObjectTypes.getObjectTypeFromTypeQName(refWrapper.getRealValue().getType()).getClassDefinition();
                WebComponentUtil.dispatchToObjectDetailsPage(targetClass, refWrapper.getNewValue().getOid(), AbstractAssignmentTypePanel.this, false);
            }
        }
    }

    private InlineMenuItem.VisibilityChecker isViewTargetObjectMenuVisible() {
        return (rowModel, isHeader) -> {
            PrismContainerValueWrapper<AssignmentType> assignment =
                    (PrismContainerValueWrapper<AssignmentType>) rowModel.getObject();
            if (assignment == null) {
                return false;
            }
            PrismReferenceWrapper<Referencable> target = null;
            try {
                target = assignment.findReference(AssignmentType.F_TARGET_REF);
            } catch (Exception e) {
                LOGGER.error("Couldn't find targetRef in assignment");
            }
            return target != null && !target.isEmpty();
        };
    }

    private IColumn<PrismContainerValueWrapper<AssignmentType>, String> createAssignmentIconColumn() {
        return new CompositedIconColumn<>(Model.of("")) {

            @Override
            protected CompositedIcon getCompositedIcon(IModel<PrismContainerValueWrapper<AssignmentType>> rowModel) {
                AssignmentType assignment = rowModel.getObject().getRealValue();
                LOGGER.trace("Create icon for AssignmentType: " + assignment);
                PrismObject<? extends FocusType> object = loadTargetObject(assignment);
                if (object != null) {
                    return WebComponentUtil.createCompositeIconForObject(object.asObjectable(),
                            new OperationResult("create_assignment_composited_icon"), getPageBase());
                }
                String displayType = WebComponentUtil.createDefaultBlackIcon(AssignmentsUtil.getTargetType(assignment));
                CompositedIconBuilder iconBuilder = new CompositedIconBuilder();
                iconBuilder.setBasicIcon(displayType, IconCssStyle.IN_ROW_STYLE);
                return iconBuilder.build();
            }
        };
    }

    protected <F extends FocusType> PrismObject<F> loadTargetObject(AssignmentType assignmentType) {
        if (assignmentType == null) {
            return null;
        }

        ObjectReferenceType targetRef = assignmentType.getTargetRef();
        if (targetRef == null || targetRef.getOid() == null) {
            return null;
        }

        PrismObject<F> targetObject = targetRef.getObject();
        if (targetObject == null) {
            Task task = getPageBase().createSimpleTask(OPERATION_LOAD_ASSIGNMENTS_TARGET_OBJ);
            OperationResult result = task.getResult();
            targetObject = WebModelServiceUtils.loadObject(targetRef, getPageBase(), task, result);
            result.recomputeStatus();
        }
        return targetObject;
    }
    @Override
    protected List<Component> createToolbarButtonsList(String idButton) {
        List<Component> bar = new ArrayList<>();

        AjaxIconButton newObjectButton = new AjaxIconButton(idButton, new Model<>(GuiStyleConstants.EVO_ASSIGNMENT_ICON),
                createStringResource("MainObjectListPanel.newObject")) {

            private static final long serialVersionUID = 1L;

            @Override
            public void onClick(AjaxRequestTarget target) {
                newAssignmentClickPerformed(target);
            }
        };
        newObjectButton.add(AttributeAppender.append("class", "btn btn-default btn-sm"));
        bar.add(newObjectButton);

        newObjectButton.add(new VisibleEnableBehaviour() {

            @Override
            public boolean isVisible() {
                return isNewObjectButtonVisible(getFocusObject());
            }

            @Override
            public boolean isEnabled() {
                return !isAssignmentsLimitReached();
            }
        });
        return bar;
    }

    protected void newAssignmentClickPerformed(AjaxRequestTarget target) {
        AssignmentPopup popupPanel = new AssignmentPopup(getPageBase().getMainPopupBodyId(), createAssignmentPopupModel()) {

            private static final long serialVersionUID = 1L;

            @Override
            protected void addPerformed(AjaxRequestTarget target, List<AssignmentType> newAssignmentsList) {
                super.addPerformed(target, newAssignmentsList);
                addSelectedAssignmentsPerformed(target, newAssignmentsList);
            }

            @Override
            protected List<ObjectTypes> getObjectTypesList() {
                return AbstractAssignmentTypePanel.this.getObjectTypesList();
            }

            @Override
            protected ObjectFilter getSubtypeFilter() {
                return AbstractAssignmentTypePanel.this.getSubtypeFilter();
            }

            @Override
            protected boolean isEntitlementAssignment() {
                return AbstractAssignmentTypePanel.this.isEntitlementAssignment();
            }

            @Override
            protected PrismContainerWrapper<AssignmentType> getAssignmentWrapperModel() {
                return AbstractAssignmentTypePanel.this.getContainerModel().getObject();
            }

            @Override
            protected <F extends AssignmentHolderType> PrismObject<F> getFocusObject() {
                return AbstractAssignmentTypePanel.this.getFocusObject();
            }
        };
        popupPanel.setOutputMarkupId(true);
        popupPanel.setOutputMarkupPlaceholderTag(true);
        getPageBase().showMainPopup(popupPanel, target);
    }

    protected List<ObjectTypes> getObjectTypesList() {
        QName assignmentType = getAssignmentType();
        if (assignmentType == null) {
            return Collections.EMPTY_LIST;
        }
        return Collections.singletonList(ObjectTypes.getObjectTypeFromTypeQName(assignmentType));
    }

    protected RefFilter getTargetTypeFilter() {
        QName targetType = getAssignmentType();
        RefFilter targetRefFilter = null;
        if (targetType != null) {
            ObjectReferenceType ort = new ObjectReferenceType();
            ort.setType(targetType);
            ort.setRelation(new QName(PrismConstants.NS_QUERY, "any"));
            targetRefFilter = (RefFilter) getPageBase().getPrismContext().queryFor(AssignmentType.class)
                    .item(AssignmentType.F_TARGET_REF)
                    .ref(ort.asReferenceValue())
                    .buildFilter();
            targetRefFilter.setOidNullAsAny(true);
        }
        return targetRefFilter;
    }

    protected ObjectFilter getSubtypeFilter() {
        return null;
    }

    protected abstract IModel<AssignmentPopupDto> createAssignmentPopupModel();

    protected abstract QName getAssignmentType();

    protected void addSelectedAssignmentsPerformed(AjaxRequestTarget target, List<AssignmentType> newAssignmentsList) {
        if (CollectionUtils.isEmpty(newAssignmentsList)) {
            warn(getPageBase().getString("AssignmentTablePanel.message.noAssignmentSelected"));
            target.add(getPageBase().getFeedbackPanel());
            return;
        }
        boolean isAssignmentsLimitReached = isAssignmentsLimitReached(newAssignmentsList.size(), true);
        if (isAssignmentsLimitReached) {
            warn(getPageBase().getString("AssignmentPanel.assignmentsLimitReachedWarning", assignmentsRequestsLimit));
            target.add(getPageBase().getFeedbackPanel());
            return;
        }

        newAssignmentsList.forEach(assignment -> {

            PrismContainerValue<AssignmentType> newAssignment = getContainerModel().getObject().getItem().createNewValue();
            AssignmentType assignmentType = newAssignment.asContainerable();

            if (assignment.getConstruction() != null && assignment.getConstruction().getResourceRef() != null) {
                assignmentType.setConstruction(assignment.getConstruction());
            } else {
                assignmentType.setTargetRef(assignment.getTargetRef());
            }
            AbstractAssignmentTypePanel.this.createNewItemContainerValueWrapper(newAssignment, getContainerModel().getObject(),
                    target);
        });
        AbstractAssignmentTypePanel.this.refreshTable(target);
        getPageBase().hideMainPopup(target);
    }

    @Override
    public void deleteItemPerformed(AjaxRequestTarget target, List<PrismContainerValueWrapper<AssignmentType>> toDeleteList) {
        int countAddedAssignments = 0;
        for (PrismContainerValueWrapper<AssignmentType> assignment : toDeleteList) {
            if (ValueStatus.ADDED.equals(assignment.getStatus())) {
                countAddedAssignments++;
            }
        }
        boolean isLimitReached = isAssignmentsLimitReached(toDeleteList.size() - countAddedAssignments, true);
        if (isLimitReached) {
            warn(getPageBase().getString("AssignmentPanel.assignmentsLimitReachedWarning", assignmentsRequestsLimit));
            target.add(getPageBase().getFeedbackPanel());
            return;
        }
        super.deleteItemPerformed(target, toDeleteList);
    }

    protected ContainerValueDataProviderFactory<AssignmentType, ?> providerFactory() {
        return getPageBase().getDataProviderRegistry().forContainerValue(AssignmentType.class, this.getPanelConfiguration().getListView(), InMemoryAssignmentDataProviderType.class);
    }

    @Override
    protected ISelectableDataProvider<AssignmentType, PrismContainerValueWrapper<AssignmentType>> createProvider() {
        var searchModel = getSearchModel();
        var assignments = loadValuesModel();
        var itemPath = model.getObject().getPath();
        return providerFactory().create(AbstractAssignmentTypePanel.this, searchModel, assignments, objectType, objectOid, itemPath, getObjectCollectionView(), new ContainerValueDataProviderFactory.Customization<AssignmentType>() {

            private static final long serialVersionUID = 1L;

            @Override
            public PageStorage getPageStorage() {
                return AbstractAssignmentTypePanel.this.getPageStorage();
            }

            @Override
            public List<PrismContainerValueWrapper<AssignmentType>> postFilter(List<PrismContainerValueWrapper<AssignmentType>> assignmentList) {
                return customPostSearch(assignmentList);
            }

            @Override
            public ObjectQuery getCustomizeContentQuery() {
                return AbstractAssignmentTypePanel.this.getCustomizeQuery();
            }
        });
    }

    protected IModel<List<PrismContainerValueWrapper<AssignmentType>>> loadValuesModel() {
        return new PropertyModel<>(getContainerModel(), "values");
    }

    protected List<PrismContainerValueWrapper<AssignmentType>> customPostSearch(List<PrismContainerValueWrapper<AssignmentType>> assignments) {
        return assignments;
    }

    protected abstract ObjectQuery getCustomizeQuery();

    protected List<PrismContainerValueWrapper<AssignmentType>> prefilterUsingQuery(
            List<PrismContainerValueWrapper<AssignmentType>> list, ObjectQuery query) {
        return list.stream().filter(valueWrapper -> {
            try {
                return ObjectQuery.match(valueWrapper.getRealValue(), query.getFilter(), getPageBase().getMatchingRuleRegistry());
            } catch (SchemaException e) {
                throw new TunnelException(e.getMessage());
            }
        }).collect(Collectors.toList());
    }

    @Override
    protected MultivalueContainerDetailsPanel<AssignmentType> getMultivalueContainerDetailsPanel(
            ListItem<PrismContainerValueWrapper<AssignmentType>> item) {
        return createMultivalueContainerDetailsPanel(item);
    }

    private MultivalueContainerDetailsPanel<AssignmentType> createMultivalueContainerDetailsPanel(ListItem<PrismContainerValueWrapper<AssignmentType>> item) {
        if (isAssignmentsLimitReached()) {
            item.getModelObject().setReadOnly(true, true);
        } else if (item.getModelObject().isReadOnly()) {
            item.getModelObject().setReadOnly(false, true);
        }
        return new AssignmentsDetailsPanel(MultivalueContainerListPanelWithDetailsPanel.ID_ITEM_DETAILS, item.getModel(), isEntitlementAssignment(), getPanelConfiguration());
    }

    protected boolean isEntitlementAssignment() {
        return false;
    }

    @Override
    protected abstract UserProfileStorage.TableId getTableId();

    @Override
    protected List<SearchItemDefinition> initSearchableItems(PrismContainerDefinition<AssignmentType> containerDef) {
        return createSearchableItems(containerDef);
    }

    @Override
    protected List<? super AbstractSearchItemWrapper> initSearchableItemWrappers(PrismContainerDefinition<AssignmentType> containerDef){
        return createSearchableItemWrappers(containerDef);
    }

    @Deprecated
    protected List<SearchItemDefinition> createSearchableItems(PrismContainerDefinition<AssignmentType> containerDef) {
        List<SearchItemDefinition> defs = new ArrayList<>();

        addSpecificSearchableItems(containerDef, defs);
        SearchFactory.addSearchPropertyDef(containerDef, ItemPath.create(AssignmentType.F_ACTIVATION, ActivationType.F_ADMINISTRATIVE_STATUS), defs);
        SearchFactory.addSearchPropertyDef(containerDef, ItemPath.create(AssignmentType.F_ACTIVATION, ActivationType.F_EFFECTIVE_STATUS), defs);

        defs.addAll(SearchFactory.createExtensionDefinitionList(containerDef));

        return defs;

    }

    protected List<? super AbstractSearchItemWrapper> createSearchableItemWrappers(PrismContainerDefinition<AssignmentType> containerDef) {
        List<? super AbstractSearchItemWrapper> defs = new ArrayList<>();

        addSpecificSearchableItemWrappers(containerDef, defs);
        SearchFactory.addSearchPropertyWrapper(containerDef, ItemPath.create(AssignmentType.F_ACTIVATION, ActivationType.F_ADMINISTRATIVE_STATUS), defs);
        SearchFactory.addSearchPropertyWrapper(containerDef, ItemPath.create(AssignmentType.F_ACTIVATION, ActivationType.F_EFFECTIVE_STATUS), defs);

        defs.addAll(SearchFactory.createSearchableExtensionWrapperList(containerDef));

        return defs;

    }

    @Deprecated
    protected abstract void addSpecificSearchableItems(PrismContainerDefinition<AssignmentType> containerDef, List<SearchItemDefinition> defs);

    protected abstract void addSpecificSearchableItemWrappers(PrismContainerDefinition<AssignmentType> containerDef, List<? super AbstractSearchItemWrapper> defs);

    protected <AH extends AssignmentHolderType> boolean isNewObjectButtonVisible(PrismObject<AH> focusObject) {
        try {
            return getPageBase().isAuthorized(AuthorizationConstants.AUTZ_UI_ADMIN_ASSIGN_ACTION_URI,
                    AuthorizationPhaseType.REQUEST, focusObject,
                    null, null, null);
        } catch (Exception ex) {
            return WebComponentUtil.isAuthorized(AuthorizationConstants.AUTZ_UI_ADMIN_ASSIGN_ACTION_URI);
        }
    }

    private boolean isAssignmentsLimitReached() {
        return isAssignmentsLimitReached(0, false);
    }

    @SuppressWarnings("deprecation")
    private boolean isAssignmentsLimitReached(int selectedAssignmentsCount, boolean actionPerformed) {
        if (assignmentsRequestsLimit < 0) {
            return false;
        }
        int changedItems = 0;
        List<PrismContainerValueWrapper<AssignmentType>> assignmentsList = getContainerModel().getObject().getValues();
        for (PrismContainerValueWrapper<AssignmentType> assignment : assignmentsList) {
            if (assignment.hasChanged()) {
                changedItems++;
            }
        }
        return actionPerformed ? (changedItems + selectedAssignmentsCount) > assignmentsRequestsLimit :
                (changedItems + selectedAssignmentsCount) >= assignmentsRequestsLimit;
    }

    private IModel<String> getAssignmentsLimitReachedUnassignTitleModel() {
        return new LoadableModel<>(true) {
            @Override
            protected String load() {
                return isAssignmentsLimitReached() ?
                        AbstractAssignmentTypePanel.this.getPageBase().createStringResource("RoleCatalogItemButton.assignmentsLimitReachedTitle",
                                assignmentsRequestsLimit).getString() : createStringResource("pageAdminFocus.menu.unassign").getString();
            }
        };
    }

}
