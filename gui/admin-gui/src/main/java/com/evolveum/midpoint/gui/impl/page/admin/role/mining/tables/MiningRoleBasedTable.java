/*
 * Copyright (C) 2010-2023 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.gui.impl.page.admin.role.mining.tables;

import static com.evolveum.midpoint.gui.impl.page.admin.role.mining.utils.RoleAnalysisObjectUtils.*;
import static com.evolveum.midpoint.gui.impl.page.admin.role.mining.utils.table.TableCellFillOperation.updateFrequencyRoleBased;
import static com.evolveum.midpoint.gui.impl.page.admin.role.mining.utils.table.TableCellFillOperation.updateRoleBasedTableData;
import static com.evolveum.midpoint.gui.impl.page.admin.role.mining.utils.table.Tools.applySquareTableCell;
import static com.evolveum.midpoint.gui.impl.page.admin.role.mining.utils.table.Tools.getScaleScript;
import static com.evolveum.midpoint.web.component.data.column.ColumnUtils.createStringResource;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.extensions.markup.html.repeater.data.grid.ICellPopulator;
import org.apache.wicket.extensions.markup.html.repeater.data.table.AbstractColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.DataTable;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.util.ListModel;

import com.evolveum.midpoint.common.mining.objects.chunk.MiningOperationChunk;
import com.evolveum.midpoint.common.mining.objects.chunk.MiningRoleTypeChunk;
import com.evolveum.midpoint.common.mining.objects.chunk.MiningUserTypeChunk;
import com.evolveum.midpoint.common.mining.objects.detection.DetectedPattern;
import com.evolveum.midpoint.common.mining.utils.values.RoleAnalysisOperationMode;
import com.evolveum.midpoint.common.mining.utils.values.RoleAnalysisSortMode;
import com.evolveum.midpoint.gui.api.model.LoadableModel;
import com.evolveum.midpoint.gui.api.page.PageBase;
import com.evolveum.midpoint.gui.api.util.GuiDisplayTypeUtil;
import com.evolveum.midpoint.gui.impl.page.admin.role.mining.model.BusinessRoleApplicationDto;
import com.evolveum.midpoint.gui.impl.page.admin.role.mining.model.BusinessRoleDto;
import com.evolveum.midpoint.gui.impl.page.admin.role.mining.page.panel.cluster.MembersDetailsPanel;
import com.evolveum.midpoint.gui.impl.util.IconAndStylesUtil;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.schema.constants.ObjectTypes;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.web.component.data.SpecialBoxedTablePanel;
import com.evolveum.midpoint.web.component.data.column.AjaxLinkPanel;
import com.evolveum.midpoint.web.component.data.column.AjaxLinkTruncatePanelAction;
import com.evolveum.midpoint.web.component.data.column.IconColumn;
import com.evolveum.midpoint.web.component.data.column.LinkIconPanelStatus;
import com.evolveum.midpoint.web.component.util.RoleMiningProvider;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

public class MiningRoleBasedTable extends Panel {

    private static final String ID_DATATABLE = "datatable_extra";
    private final OperationResult result = new OperationResult("loadMiningTableObject");

    private String valueTitle = null;
    private int columnPageCount = 100;
    private int fromCol;
    private int toCol;
    private final int specialColumnCount;

    private final MiningOperationChunk miningOperationChunk;
    private final PrismObject<RoleAnalysisClusterType> cluster;

    public MiningRoleBasedTable(String id,
            MiningOperationChunk miningOperationChunk,
            double minFrequency, double maxFrequency,
            DetectedPattern intersection, List<ObjectReferenceType> reductionObjects,
            RoleAnalysisSortMode roleAnalysisSortMode,
            PrismObject<RoleAnalysisClusterType> cluster) {
        super(id);

        this.cluster = cluster;
        this.miningOperationChunk = miningOperationChunk;

        List<MiningUserTypeChunk> users = miningOperationChunk.getMiningUserTypeChunks(roleAnalysisSortMode);
        List<MiningRoleTypeChunk> roles = miningOperationChunk.getMiningRoleTypeChunks(roleAnalysisSortMode);

        fromCol = 1;
        toCol = 100;
        specialColumnCount = roles.size();

        if (specialColumnCount < toCol) {
            toCol = specialColumnCount;
        }

        initLayout(minFrequency, maxFrequency, intersection, reductionObjects, roleAnalysisSortMode, users, roles);
    }

    private void initLayout(double minFrequency, double maxFrequency, DetectedPattern intersection,
            List<ObjectReferenceType> reductionObjects, RoleAnalysisSortMode roleAnalysisSortMode,
            List<MiningUserTypeChunk> users, List<MiningRoleTypeChunk> roles) {

        RoleMiningProvider<MiningUserTypeChunk> provider = new RoleMiningProvider<>(
                this, new ListModel<>(users) {

            @Serial private static final long serialVersionUID = 1L;

            @Override
            public void setObject(List<MiningUserTypeChunk> object) {
                super.setObject(object);
            }
        }, false);

        SpecialBoxedTablePanel<MiningUserTypeChunk> table = generateTable(provider, roles, minFrequency,
                intersection, maxFrequency, reductionObjects, roleAnalysisSortMode);
        add(table);
    }

    public SpecialBoxedTablePanel<MiningUserTypeChunk> generateTable(RoleMiningProvider<MiningUserTypeChunk> provider,
            List<MiningRoleTypeChunk> roles,
            double frequency, DetectedPattern intersection,
            double maxFrequency, List<ObjectReferenceType> reductionObjects,
            RoleAnalysisSortMode roleAnalysisSortMode) {

        SpecialBoxedTablePanel<MiningUserTypeChunk> table = new SpecialBoxedTablePanel<>(
                ID_DATATABLE, provider, initColumns(roles, frequency, intersection, maxFrequency, reductionObjects),
                null, true, true, specialColumnCount, roleAnalysisSortMode) {
            @Override
            public void onChange(String value, AjaxRequestTarget target) {
                valueTitle = value;
                String[] rangeParts = value.split(" - ");
                fromCol = Integer.parseInt(rangeParts[0]);
                toCol = Integer.parseInt(rangeParts[1]);
                getTable().replaceWith(generateTable(provider, roles, frequency, intersection, maxFrequency, reductionObjects,
                        roleAnalysisSortMode));
                target.add(getTable().setOutputMarkupId(true));
            }

            @Override
            public BusinessRoleApplicationDto getOperationData() {

                OperationResult operationResult = new OperationResult("PerformPatternCreation");
                if (miningOperationChunk == null) {
                    return null;
                }

                List<AssignmentType> roleAssignments = new ArrayList<>();

                List<MiningRoleTypeChunk> simpleMiningRoleTypeChunks = miningOperationChunk.getSimpleMiningRoleTypeChunks();
                for (MiningRoleTypeChunk roleChunk : simpleMiningRoleTypeChunks) {
                    if (roleChunk.getStatus().equals(RoleAnalysisOperationMode.ADD)) {
                        for (String roleOid : roleChunk.getRoles()) {
                            PrismObject<RoleType> roleObject = getRoleTypeObject(getPageBase(), roleOid, operationResult);
                            if (roleObject != null) {
                                roleAssignments.add(ObjectTypeUtil.createAssignmentTo(roleOid, ObjectTypes.ROLE));
                            }
                        }
                    }
                }

                PrismObject<RoleType> businessRole = generateBusinessRole((PageBase) getPage(), roleAssignments, "");

                List<BusinessRoleDto> roleApplicationDtos = new ArrayList<>();

                List<MiningUserTypeChunk> simpleMiningUserTypeChunks = miningOperationChunk.getSimpleMiningUserTypeChunks();

                for (MiningUserTypeChunk userChunk : simpleMiningUserTypeChunks) {
                    if (userChunk.getStatus().equals(RoleAnalysisOperationMode.ADD)) {
                        for (String userOid : userChunk.getUsers()) {
                            PrismObject<UserType> userObject = getUserTypeObject(getPageBase(), userOid, operationResult);
                            if (userObject != null) {
                                roleApplicationDtos.add(new BusinessRoleDto(userObject,
                                        businessRole, getPageBase()));
                            }
                        }
                    }
                }

                return new BusinessRoleApplicationDto(cluster, businessRole, roleApplicationDtos);
            }

            @Override
            public void onChangeSortMode(RoleAnalysisSortMode roleAnalysisSortMode, AjaxRequestTarget target) {

                List<MiningRoleTypeChunk> roles = miningOperationChunk.getMiningRoleTypeChunks(roleAnalysisSortMode);
                List<MiningUserTypeChunk> users = miningOperationChunk.getMiningUserTypeChunks(roleAnalysisSortMode);
                RoleMiningProvider<MiningUserTypeChunk> provider = new RoleMiningProvider<>(
                        this, new ListModel<>(users) {

                    @Serial private static final long serialVersionUID = 1L;

                    @Override
                    public void setObject(List<MiningUserTypeChunk> object) {
                        super.setObject(object);
                    }
                }, false);

                getTable().replaceWith(generateTable(provider, roles, frequency, intersection, maxFrequency, reductionObjects,
                        roleAnalysisSortMode));
                target.add(getTable().setOutputMarkupId(true));
                target.appendJavaScript(getScaleScript());
            }

            @Override
            public int onChangeSize(int value, AjaxRequestTarget target) {
                columnPageCount = value;
                fromCol = 1;
                toCol = Math.min(value, specialColumnCount);
                valueTitle = "0 - " + toCol;

                getTable().replaceWith(generateTable(provider, roles, frequency, intersection, maxFrequency,
                        reductionObjects, roleAnalysisSortMode));
                target.add(getTable().setOutputMarkupId(true));
                target.appendJavaScript(getScaleScript());
                return value;
            }

            @Override
            public String getColumnPagingTitle() {
                if (valueTitle == null) {
                    return super.getColumnPagingTitle();
                } else {
                    return valueTitle;
                }
            }

            @Override
            public int getColumnPageCount() {
                return columnPageCount;
            }

        };
        table.setItemsPerPage(50);
        table.setOutputMarkupId(true);

        return table;
    }

    public List<IColumn<MiningUserTypeChunk, String>> initColumns(List<MiningRoleTypeChunk> roles, double minFrequency,
            DetectedPattern intersection, double maxFrequency, List<ObjectReferenceType> reductionObjects) {

        List<IColumn<MiningUserTypeChunk, String>> columns = new ArrayList<>();

        columns.add(new IconColumn<>(null) {
            @Serial private static final long serialVersionUID = 1L;

            @Override
            public String getCssClass() {
                return " role-mining-static-header role-mining-no-border";
            }

            @Override
            protected DisplayType getIconDisplayType(IModel<MiningUserTypeChunk> rowModel) {
                return GuiDisplayTypeUtil
                        .createDisplayType(IconAndStylesUtil.createDefaultBlackIcon(UserType.COMPLEX_TYPE));
            }
        });

        columns.add(new AbstractColumn<>(createStringResource("")) {

            @Override
            public String getSortProperty() {
                return UserType.F_NAME.getLocalPart();
            }

            @Override
            public boolean isSortable() {
                return false;
            }

            @Override
            public void populateItem(Item<ICellPopulator<MiningUserTypeChunk>> item, String componentId,
                    IModel<MiningUserTypeChunk> rowModel) {

                item.add(AttributeAppender.replace("class", " "));
                item.add(new AttributeAppender("style", " width:150px"));

                List<String> elements = rowModel.getObject().getUsers();

                updateFrequencyRoleBased(rowModel, minFrequency, maxFrequency);

                String title = rowModel.getObject().getChunkName();
                AjaxLinkPanel analyzedMembersDetailsPanel = new AjaxLinkPanel(componentId,
                        createStringResource(title)) {
                    @Override
                    public void onClick(AjaxRequestTarget target) {

                        List<PrismObject<FocusType>> objects = new ArrayList<>();
                        for (String s : elements) {
                            objects.add(getFocusTypeObject(getPageBase(), s, result));
                        }
                        MembersDetailsPanel detailsPanel = new MembersDetailsPanel(((PageBase) getPage()).getMainPopupBodyId(),
                                Model.of("Analyzed members details panel"), objects, RoleAnalysisProcessModeType.USER) {
                            @Override
                            public void onClose(AjaxRequestTarget ajaxRequestTarget) {
                                super.onClose(ajaxRequestTarget);
                            }
                        };
                        ((PageBase) getPage()).showMainPopup(detailsPanel, target);
                    }

                };

                analyzedMembersDetailsPanel.setOutputMarkupId(true);
                item.add(analyzedMembersDetailsPanel);
            }

            @Override
            public Component getHeader(String componentId) {
                AjaxLinkPanel ajaxLinkPanel = new AjaxLinkPanel(componentId, new LoadableModel<>() {
                    @Override
                    protected Object load() {
                        return Model.of(getCompressStatus());
                    }
                }) {
                    @Override
                    public void onClick(AjaxRequestTarget target) {
                        onPerform(target);
                        target.add(this);
                    }
                };
                ajaxLinkPanel.setOutputMarkupId(true);
                ajaxLinkPanel.setOutputMarkupPlaceholderTag(true);
                add(ajaxLinkPanel);

                return ajaxLinkPanel.add(
                        new AttributeAppender("style",
                                "  writing-mode: vertical-lr;  -webkit-transform: rotate(45deg);"));
            }

            @Override
            public String getCssClass() {
                return "overflow-auto role-mining-static-row-header role-mining-static-header-name role-mining-no-border";
            }
        });

        columns.add(new AbstractColumn<>(createStringResource("")) {

            @Override
            public String getSortProperty() {
                return UserType.F_NAME.getLocalPart();
            }

            @Override
            public boolean isSortable() {
                return false;
            }

            @Override
            public void populateItem(Item<ICellPopulator<MiningUserTypeChunk>> item, String componentId,
                    IModel<MiningUserTypeChunk> rowModel) {

                item.add(AttributeAppender.replace("style", " overflow-wrap: break-word !important; word-break: inherit;"));

                LinkIconPanelStatus linkIconPanel = new LinkIconPanelStatus(componentId, new LoadableDetachableModel<>() {
                    @Override
                    protected RoleAnalysisOperationMode load() {
                        return rowModel.getObject().getStatus();
                    }
                }) {
                    @Override
                    protected RoleAnalysisOperationMode onClickPerformed(AjaxRequestTarget target, RoleAnalysisOperationMode status) {

                        RoleAnalysisOperationMode roleAnalysisOperationMode1 = rowModel.getObject().getStatus();
                        if (roleAnalysisOperationMode1.equals(RoleAnalysisOperationMode.NEUTRAL)) {
                            rowModel.getObject().setStatus(RoleAnalysisOperationMode.ADD);
                        } else if (roleAnalysisOperationMode1.equals(RoleAnalysisOperationMode.ADD)) {
                            rowModel.getObject().setStatus(RoleAnalysisOperationMode.REMOVE);
                        } else if (roleAnalysisOperationMode1.equals(RoleAnalysisOperationMode.REMOVE)) {
                            rowModel.getObject().setStatus(RoleAnalysisOperationMode.NEUTRAL);
                        }
                        resetTable(target);
                        return rowModel.getObject().getStatus();
                    }
                };

                linkIconPanel.setOutputMarkupId(true);
                item.add(linkIconPanel);
            }

            @Override
            public String getCssClass() {
                return " role-mining-static-header role-mining-no-border";
            }
        });

        IColumn<MiningUserTypeChunk, String> column;
        for (int i = fromCol - 1; i < toCol; i++) {
            MiningRoleTypeChunk roleChunk = roles.get(i);
            List<String> colRoles = roleChunk.getRoles();

            column = new AbstractColumn<>(createStringResource("")) {

                @Override
                public void populateItem(Item<ICellPopulator<MiningUserTypeChunk>> cellItem,
                        String componentId, IModel<MiningUserTypeChunk> model) {
                    applySquareTableCell(cellItem);
                    List<String> rowRoles = model.getObject().getRoles();
                    RoleAnalysisOperationMode colRoleAnalysisOperationMode = roleChunk.getStatus();
                    updateRoleBasedTableData(cellItem, componentId, model, rowRoles,
                            colRoleAnalysisOperationMode, colRoles, intersection, roleChunk);

                }

                @Override
                public Component getHeader(String componentId) {

                    List<String> elements = roleChunk.getRoles();

                    String color = null;
                    for (ObjectReferenceType ref : reductionObjects) {
                        if (elements.contains(ref.getOid())) {
                            color = " table-info";
                            break;
                        }
                    }

                    DisplayType displayType = GuiDisplayTypeUtil.createDisplayType(
                            IconAndStylesUtil.createDefaultBlackIcon(RoleType.COMPLEX_TYPE));

                    String title = roleChunk.getChunkName();

                    String finalColor = color;
                    return new AjaxLinkTruncatePanelAction(componentId,
                            createStringResource(title), createStringResource(title), displayType,
                            new LoadableDetachableModel<>() {
                                @Override
                                protected RoleAnalysisOperationMode load() {
                                    return roleChunk.getStatus();
                                }
                            }) {

                        @Override
                        protected RoleAnalysisOperationMode onClickPerformedAction(AjaxRequestTarget target, RoleAnalysisOperationMode roleAnalysisOperationMode) {
                            RoleAnalysisOperationMode roleAnalysisOperationMode1 = roleChunk.getStatus();
                            if (roleAnalysisOperationMode1.equals(RoleAnalysisOperationMode.NEUTRAL)) {
                                roleChunk.setStatus(RoleAnalysisOperationMode.ADD);
                            } else if (roleAnalysisOperationMode1.equals(RoleAnalysisOperationMode.ADD)) {
                                roleChunk.setStatus(RoleAnalysisOperationMode.REMOVE);
                            } else if (roleAnalysisOperationMode1.equals(RoleAnalysisOperationMode.REMOVE)) {
                                roleChunk.setStatus(RoleAnalysisOperationMode.NEUTRAL);
                            }
                            resetTable(target);
                            return roleChunk.getStatus();
                        }

                        @Override
                        protected String getColor() {
                            return finalColor;
                        }

                        @Override
                        public void onClick(AjaxRequestTarget target) {

                            List<PrismObject<FocusType>> objects = new ArrayList<>();
                            for (String s : elements) {
                                objects.add(getFocusTypeObject(getPageBase(), s, result));
                            }
                            MembersDetailsPanel detailsPanel = new MembersDetailsPanel(((PageBase) getPage()).getMainPopupBodyId(),
                                    Model.of("Analyzed members details panel"), objects, RoleAnalysisProcessModeType.ROLE) {
                                @Override
                                public void onClose(AjaxRequestTarget ajaxRequestTarget) {
                                    super.onClose(ajaxRequestTarget);
                                }
                            };
                            ((PageBase) getPage()).showMainPopup(detailsPanel, target);
                        }

                    };
                }

            };
            columns.add(column);

        }

        return columns;
    }

    public PageBase getPageBase() {
        return ((PageBase) getPage());
    }

    public DataTable<?, ?> getDataTable() {
        return ((SpecialBoxedTablePanel<?>) get(((PageBase) getPage()).createComponentPath(ID_DATATABLE))).getDataTable();
    }

    protected SpecialBoxedTablePanel<?> getTable() {
        return ((SpecialBoxedTablePanel<?>) get(((PageBase) getPage()).createComponentPath(ID_DATATABLE)));
    }

    protected void resetTable(AjaxRequestTarget target) {

    }

    protected String getCompressStatus() {
        return "COMPRESS MODE";
    }

    protected void onPerform(AjaxRequestTarget ajaxRequestTarget) {
    }
}