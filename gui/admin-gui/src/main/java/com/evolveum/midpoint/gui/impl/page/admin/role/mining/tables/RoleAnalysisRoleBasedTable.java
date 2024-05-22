/*
 * Copyright (C) 2010-2023 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.gui.impl.page.admin.role.mining.tables;

import static com.evolveum.midpoint.gui.impl.page.admin.role.mining.utils.object.RoleAnalysisObjectUtils.executeChangesOnCandidateRole;
import static com.evolveum.midpoint.gui.impl.page.admin.role.mining.utils.table.RoleAnalysisTableCellFillResolver.*;
import static com.evolveum.midpoint.gui.impl.page.admin.role.mining.utils.table.RoleAnalysisTableTools.applySquareTableCell;
import static com.evolveum.midpoint.gui.impl.page.admin.role.mining.utils.table.RoleAnalysisTableTools.applyTableScaleScript;

import java.io.Serial;
import java.util.*;

import com.evolveum.midpoint.gui.impl.page.admin.role.mining.page.tmp.panel.RoleAnalysisDetectedPatternDetailsPopup;
import com.evolveum.midpoint.gui.impl.page.admin.role.mining.page.tmp.panel.RoleAnalysisInfoItem;

import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.extensions.markup.html.repeater.data.grid.ICellPopulator;
import org.apache.wicket.extensions.markup.html.repeater.data.table.AbstractColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.DataTable;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.RepeatingView;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.util.ListModel;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.evolveum.midpoint.common.mining.objects.chunk.DisplayValueOption;
import com.evolveum.midpoint.common.mining.objects.chunk.MiningOperationChunk;
import com.evolveum.midpoint.common.mining.objects.chunk.MiningRoleTypeChunk;
import com.evolveum.midpoint.common.mining.objects.chunk.MiningUserTypeChunk;
import com.evolveum.midpoint.common.mining.objects.detection.DetectedPattern;
import com.evolveum.midpoint.common.mining.utils.values.RoleAnalysisChunkMode;
import com.evolveum.midpoint.common.mining.utils.values.RoleAnalysisObjectStatus;
import com.evolveum.midpoint.common.mining.utils.values.RoleAnalysisOperationMode;
import com.evolveum.midpoint.common.mining.utils.values.RoleAnalysisSortMode;
import com.evolveum.midpoint.gui.api.GuiStyleConstants;
import com.evolveum.midpoint.gui.api.component.BasePanel;
import com.evolveum.midpoint.gui.api.page.PageBase;
import com.evolveum.midpoint.gui.impl.component.AjaxCompositedIconButton;
import com.evolveum.midpoint.gui.impl.component.data.column.CompositedIconColumn;
import com.evolveum.midpoint.gui.impl.component.icon.CompositedIcon;
import com.evolveum.midpoint.gui.impl.component.icon.CompositedIconBuilder;
import com.evolveum.midpoint.gui.impl.component.icon.IconCssStyle;
import com.evolveum.midpoint.gui.impl.component.icon.LayeredIconCssStyle;
import com.evolveum.midpoint.gui.impl.page.admin.role.PageRole;
import com.evolveum.midpoint.gui.impl.page.admin.role.mining.model.BusinessRoleApplicationDto;
import com.evolveum.midpoint.gui.impl.page.admin.role.mining.model.BusinessRoleDto;
import com.evolveum.midpoint.gui.impl.page.admin.role.mining.page.panel.cluster.MembersDetailsPopupPanel;
import com.evolveum.midpoint.gui.impl.page.admin.role.mining.page.panel.experimental.RoleAnalysisTableSettingPanel;
import com.evolveum.midpoint.gui.impl.page.admin.role.mining.utils.table.RoleAnalysisTableTools;
import com.evolveum.midpoint.gui.impl.util.DetailsPageUtil;
import com.evolveum.midpoint.gui.impl.util.IconAndStylesUtil;
import com.evolveum.midpoint.model.api.mining.RoleAnalysisService;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.schema.constants.ObjectTypes;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.web.component.AjaxCompositedIconSubmitButton;
import com.evolveum.midpoint.web.component.AjaxIconButton;
import com.evolveum.midpoint.web.component.data.RoleAnalysisTable;
import com.evolveum.midpoint.web.component.data.column.AjaxLinkPanel;
import com.evolveum.midpoint.web.component.data.column.AjaxLinkTruncatePanelAction;
import com.evolveum.midpoint.web.component.data.column.LinkIconPanelStatus;
import com.evolveum.midpoint.web.component.util.RoleMiningProvider;
import com.evolveum.midpoint.web.util.OnePageParameterEncoder;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import com.evolveum.prism.xml.ns._public.types_3.PolyStringType;

public class RoleAnalysisRoleBasedTable extends BasePanel<String> {

    private static final String ID_DATATABLE = "datatable";

    private static final String DOT_CLASS = RoleAnalysisRoleBasedTable.class.getName() + ".";
    private static final String OP_PREPARE_OBJECTS = DOT_CLASS + "prepareObjects";
    private static final String OP_PROCESS_CANDIDATE_ROLE = DOT_CLASS + "processCandidate";
    private final OperationResult result = new OperationResult(OP_PREPARE_OBJECTS);
    private String valueTitle = null;
    private int currentPageView = 0;
    private int columnPageCount = 100;
    private int fromCol = 1;
    private int toCol = 100;
    private int specialColumnCount;
    private final MiningOperationChunk miningOperationChunk;
    double minFrequency;
    double maxFrequency;
    List<DetectedPattern> displayedPatterns;
    boolean isRelationSelected = false;
    boolean isCandidateRoleSelector = false;

    LoadableDetachableModel<Map<String, String>> patternColorPalette = new LoadableDetachableModel<>() {
        @Override
        protected @NotNull Map<String, String> load() {
            return generateObjectColors(getPatternIdentifiers());
        }
    };

    LoadableDetachableModel<DisplayValueOption> displayValueOptionModel;

    public RoleAnalysisRoleBasedTable(
            @NotNull String id,
            @NotNull MiningOperationChunk miningOperationChunk,
            @Nullable List<DetectedPattern> displayedPatterns,
            @NotNull LoadableDetachableModel<DisplayValueOption> displayValueOptionModel,
            @NotNull PrismObject<RoleAnalysisClusterType> cluster) {
        super(id);

        this.displayValueOptionModel = displayValueOptionModel;
        this.miningOperationChunk = miningOperationChunk;

        if (displayedPatterns != null) {
            this.displayedPatterns = new ArrayList<>(displayedPatterns);
        } else {
            this.displayedPatterns = new ArrayList<>();
        }

        RoleAnalysisClusterType clusterObject = cluster.asObjectable();
        RoleAnalysisDetectionOptionType detectionOption = clusterObject.getDetectionOption();
        RangeType frequencyRange = detectionOption.getFrequencyRange();

        if (frequencyRange != null) {
            this.minFrequency = frequencyRange.getMin() / 100;
            this.maxFrequency = frequencyRange.getMax() / 100;
        }

        initLayout(cluster);
    }

    private void initLayout(@NotNull PrismObject<RoleAnalysisClusterType> cluster) {
        List<ObjectReferenceType> resolvedPattern = cluster.asObjectable().getResolvedPattern();

        DisplayValueOption object = displayValueOptionModel.getObject();
        RoleAnalysisSortMode roleAnalysisSortMode = RoleAnalysisSortMode.NONE;
        if (object != null) {
            roleAnalysisSortMode = object.getSortMode();
        }

        List<MiningUserTypeChunk> users = miningOperationChunk.getMiningUserTypeChunks(roleAnalysisSortMode);
        List<MiningRoleTypeChunk> roles = miningOperationChunk.getMiningRoleTypeChunks(roleAnalysisSortMode);

        specialColumnCount = roles.size();
        toCol = Math.min(toCol, specialColumnCount);

        RoleMiningProvider<MiningUserTypeChunk> provider = createRoleMiningProvider(users);
        RoleAnalysisTable<MiningUserTypeChunk> table = generateTable(provider, roles, resolvedPattern, cluster);
        add(table);
    }

    private @NotNull RoleMiningProvider<MiningUserTypeChunk> createRoleMiningProvider(List<MiningUserTypeChunk> users) {

        ListModel<MiningUserTypeChunk> model = new ListModel<>(users) {
            @Serial
            private static final long serialVersionUID = 1L;

            @Override
            public void setObject(List<MiningUserTypeChunk> object) {
                super.setObject(object);
            }
        };

        return new RoleMiningProvider<>(this, model, false);
    }

    public RoleAnalysisTable<MiningUserTypeChunk> generateTable(
            RoleMiningProvider<MiningUserTypeChunk> provider,
            List<MiningRoleTypeChunk> roles,
            List<ObjectReferenceType> reductionObjects,
            PrismObject<RoleAnalysisClusterType> cluster) {

        RoleAnalysisTable<MiningUserTypeChunk> table = new RoleAnalysisTable<>(
                ID_DATATABLE, provider, initColumns(roles, reductionObjects),
                null, true, specialColumnCount, displayValueOptionModel) {

            @Override
            public String getAdditionalBoxCssClasses() {
                return " m-0";
            }

            @Override
            protected @Nullable Set<RoleAnalysisCandidateRoleType> getCandidateRoleContainer() {
                if (displayedPatterns != null && displayedPatterns.size() > 1) {
                    return null;
                } else if (displayedPatterns != null && displayedPatterns.size() == 1) {
                    DetectedPattern detectedPattern = displayedPatterns.get(0);
                    Long id = detectedPattern.getId();
                    List<RoleAnalysisCandidateRoleType> candidateRoles = cluster.asObjectable().getCandidateRoles();
                    for (RoleAnalysisCandidateRoleType candidateRole : candidateRoles) {
                        if (candidateRole.getId().equals(id)) {
                            return Collections.singleton(candidateRole);
                        }
                    }
                }

                return getCandidateRole();
            }

            @Override
            protected boolean getMigrationButtonVisibility() {
                Set<RoleAnalysisCandidateRoleType> candidateRole = getCandidateRole();
                if (candidateRole != null) {
                    if (candidateRole.size() > 1) {
                        return false;
                    }
                }
                if (displayedPatterns != null && displayedPatterns.size() > 1) {
                    return false;
                }

                return isRelationSelected;
            }


            @Override
            public void initHeaderToolsPanelItems(RepeatingView toolsPanelItems, boolean isExpanded, boolean isPatternMode) {

//                RoleAnalysisInfoItem collapseHeader = new RoleAnalysisInfoItem(toolsPanelItems.newChildId(), Model.of()) {
//
//                    @Override
//                    protected String getIconClass() {
//                        return "fas fa-bars text-primary";
//                    }
//
//                    @Override
//                    protected String getIconBoxIconStyle() {
//                        return "font-size:25px;";
//                    }
//
//                    protected String getIconBoxTextStyle() {
//                        return null;
//                    }
//
//                    @Override
//                    protected boolean isBoxVisible() {
//                        return isExpanded;
//                    }
//
//                    @Override
//                    protected void addDescriptionComponents() {
//                        boolean toolsPanelExpanded = displayValueOptionModel.getObject().isToolsPanelExpanded();
//                        if(toolsPanelExpanded) {
//                            appendText("Expanded view");
//                        } else {
//                            appendText("Collapsed view");
//                        }
//                    }
//
//                    @Override
//                    protected String getDescriptionStyle() {
//                        return "font-size:20px; line-height: 1.1;";
//                    }
//
//                    @Override
//                    protected IModel<String> getLinkModel() {
//                        return Model.of("");
//                    }
//
//                    @Override
//                    protected void onClickIconPerform(AjaxRequestTarget target) {
//                        //TODO
//                        boolean toolsPanelExpanded = displayValueOptionModel.getObject().isToolsPanelExpanded();
//                        displayValueOptionModel.getObject().setToolsPanelExpanded(!toolsPanelExpanded);
//                        refreshItemPanel(target);
//                        target.add(getTable());
//                    }
//
//                    @Override
//                    protected void onClickLinkPerform(AjaxRequestTarget target) {
//
//                    }
//                };
//                collapseHeader.setOutputMarkupId(true);
//                toolsPanelItems.add(collapseHeader);
//
//                if (isPatternMode) {
//                    RoleAnalysisInfoItem patternPanelHeader = new RoleAnalysisInfoItem(toolsPanelItems.newChildId(), Model.of()) {
//
//                        @Override
//                        protected String getIconClass() {
//                            return super.getIconClass() + " text-primary";
//                        }
//
//                        @Override
//                        protected String getIconBoxIconStyle() {
//                            return "font-size:25px;";
//                        }
//
//                        protected String getIconBoxTextStyle() {
//                            return null;
//                        }
//
//                        @Override
//                        protected boolean isBoxVisible() {
//                            return isExpanded;
//                        }
//
//                        @Override
//                        protected void addDescriptionComponents() {
//                            appendText("Detected patterns");
//                        }
//
//                        @Override
//                        protected String getDescriptionStyle() {
//                            return "font-size:20px; line-height: 1.1;";
//                        }
//
//                        @Override
//                        protected IModel<String> getLinkModel() {
//                            return Model.of("");
//                        }
//
//                        @Override
//                        protected void onClickIconPerform(AjaxRequestTarget target) {
//                            //TODO
//                            displayValueOptionModel.getObject().setPatternToolsPanelMode(false);
//                            displayedPatterns.clear();
//                            refreshItemPanel(target);
//                            target.add(getTable());
//                        }
//
//                        @Override
//                        protected void onClickLinkPerform(AjaxRequestTarget target) {
//
//                        }
//                    };
//                    patternPanelHeader.setOutputMarkupId(true);
//                    toolsPanelItems.add(patternPanelHeader);
//                } else {
//                    RoleAnalysisInfoItem candidatePanelHeader = new RoleAnalysisInfoItem(toolsPanelItems.newChildId(), Model.of()) {
//
//                        @Override
//                        protected String getIconClass() {
//                            return GuiStyleConstants.CLASS_OBJECT_ROLE_ICON + " text-primary";
//                        }
//
//                        @Override
//                        protected String getIconBoxIconStyle() {
//                            return "font-size:25px;color: #008099;";
//                        }
//
//                        protected String getIconBoxTextStyle() {
//                            return null;
//                        }
//
//                        @Override
//                        protected boolean isBoxVisible() {
//                            return isExpanded;
//                        }
//
//                        @Override
//                        protected void addDescriptionComponents() {
//                                appendText("Candidate roles");
//                        }
//
//                        @Override
//                        protected String getDescriptionStyle() {
//                            return "font-size:20px; line-height: 1.1;";
//                        }
//
//                        @Override
//                        protected IModel<String> getLinkModel() {
//                            return Model.of("");
//                        }
//
//                        @Override
//                        protected void onClickIconPerform(AjaxRequestTarget target) {
//                            displayValueOptionModel.getObject().setPatternToolsPanelMode(true);
//                            displayedPatterns.clear();
//                            refreshItemPanel(target);
//                            target.add(getTable());
//                        }
//
//                        @Override
//                        protected void onClickLinkPerform(AjaxRequestTarget target) {
//                        }
//                    };
//                    candidatePanelHeader.setOutputMarkupId(true);
//                    toolsPanelItems.add(candidatePanelHeader);
//                }
            }

            @Override
            public void initToolsPanelItems(RepeatingView toolsPanelItems, boolean isExpanded, boolean isPatternMode) {
                if (isPatternMode) {
                    List<DetectedPattern> patterns = getDisplayedPatterns();
                    for (int i = 0; i < patterns.size(); i++) {
                        DetectedPattern pattern = patterns.get(i);
                        double reductionFactorConfidence = pattern.getMetric();
                        String formattedReductionFactorConfidence = String.format("%.0f", reductionFactorConfidence);
                        double itemsConfidence = pattern.getItemsConfidence();
                        String formattedItemConfidence = String.format("%.1f", itemsConfidence);
                        String label = "Detected a potential reduction of " +
                                formattedReductionFactorConfidence +
                                "x relationships with a confidence of  " +
                                formattedItemConfidence + "%";
                        int finalI = i;
                        RoleAnalysisInfoItem patternPanel = new RoleAnalysisInfoItem(toolsPanelItems.newChildId(), Model.of(label)) {

                            @Override
                            protected String getIconBoxText() {
                                return "#" + (finalI + 1);
                            }

                            protected String getIconBoxTextStyle() {
                                return null;
                            }

                            @Override
                            protected String getIconClass() {
                                return null;
                            }

                            @Override
                            protected String getIconContainerCssClass() {
                                Map<String, String> pallet = patternColorPalette.getObject();

                                switchToDefaultStyleView();

                                if (pallet != null) {
                                    String color = pallet.get(pattern.getIdentifier());

                                    if (color != null) {
                                        return "info-box-icon elevation-1 btn btn-outline-dark gap-1";
                                    }
                                }
                                return null;
                            }

                            @Override
                            protected String getIconContainerStyle() {
                                Map<String, String> pallet = patternColorPalette.getObject();

                                switchToDefaultStyleView();

                                if (pallet != null) {
                                    String color = pallet.get(pattern.getIdentifier());

                                    if (color != null) {
                                        return "background-color:" + color + ";";
                                    }
                                }
                                return null;
                            }

                            @Override
                            protected boolean isBoxVisible() {
                                return isExpanded;
                            }

                            @Override
                            protected void addDescriptionComponents() {
                                appendText("Reduction for");
                                appendIcon("fe fe-assignment", null);
                                appendText(" " + formattedReductionFactorConfidence + " assignments");
                                appendText("with confidence of");
                                appendIcon("fa fa-leaf", null);
                                appendText(" " + formattedItemConfidence + "%.");
                            }

                            @Override
                            protected String getDescriptionStyle() {
                                return "font-size:14px; line-height: 1.1;";
                            }

                            @Override
                            protected IModel<String> getLinkModel() {
                                String identifier = pattern.getIdentifier();
                                return Model.of("Pattern: " + Objects.requireNonNullElse(identifier, finalI));
                            }

                            @Override
                            protected void onClickIconPerform(AjaxRequestTarget target) {
                                if (displayedPatterns == null) {
                                    displayedPatterns = new ArrayList<>();
                                }

                                boolean alreadySelected = false;
                                for (DetectedPattern displayedPattern : displayedPatterns) {
                                    if (displayedPattern.getId().equals(pattern.getId())) {
                                        displayedPatterns.remove(displayedPattern);
                                        alreadySelected = true;
                                        break;
                                    }
                                }

                                if (isCandidateRoleSelector) {
                                    isCandidateRoleSelector = false;
                                    displayedPatterns.clear();
                                }

                                if (!alreadySelected) {
                                    displayedPatterns.add(pattern);
                                }

                                if (displayedPatterns.isEmpty()) {
                                    isRelationSelected = false;
                                }

                                patternColorPalette = new LoadableDetachableModel<>() {
                                    @Override
                                    protected @NotNull Map<String, String> load() {
                                        return generateObjectColors(getPatternIdentifiers());
                                    }
                                };

                                loadDetectedPattern(target, displayedPatterns);

                                Map<String, String> pallet = patternColorPalette.getObject();

                                switchToDefaultStyleView();

                                if (pallet != null) {
                                    String color = pallet.get(pattern.getIdentifier());

                                    if (color != null) {
                                        switchToSelectedStyleView(color);
                                        target.add(this);
                                    }
                                }

                                refreshItemPanel(target);
                                target.add(getTable().getDataTable());
                                target.add(getTable());
                            }

                            @Override
                            protected void onClickLinkPerform(AjaxRequestTarget target) {
                                RoleAnalysisDetectedPatternDetailsPopup component = new RoleAnalysisDetectedPatternDetailsPopup(
                                        ((PageBase) getPage()).getMainPopupBodyId(),
                                        Model.of(pattern));
                                ((PageBase) getPage()).showMainPopup(component, target);
                            }
                        };
                        patternPanel.setOutputMarkupId(true);
                        toolsPanelItems.add(patternPanel);
                    }
                } else {
                    List<DetectedPattern> candidateRoles = getCandidateRoles();
                    for (int i = 0; i < candidateRoles.size(); i++) {
                        DetectedPattern pattern = candidateRoles.get(i);
                        double reductionFactorConfidence = pattern.getMetric();
                        String formattedReductionFactorConfidence = String.format("%.0f", reductionFactorConfidence);
                        double itemsConfidence = pattern.getItemsConfidence();
                        String formattedItemConfidence = String.format("%.1f", itemsConfidence);
                        String label = "Potential reduction of " +
                                formattedReductionFactorConfidence +
                                "x relationships with a confidence of  " +
                                formattedItemConfidence + "%";
                        int finalI = i;

                        RoleAnalysisInfoItem candidatePanel = new RoleAnalysisInfoItem(toolsPanelItems.newChildId(), Model.of(label)) {

                            @Override
                            protected String getIconBoxText() {
                                return "#" + (finalI + 1);
                            }

                            protected String getIconBoxTextStyle() {
                                return null;
                            }

                            @Override
                            protected String getIconClass() {
                                return null;
                            }

                            @Override
                            protected String getIconContainerCssClass() {
                                Map<String, String> pallet = patternColorPalette.getObject();

                                switchToDefaultStyleView();

                                if (pallet != null) {
                                    String color = pallet.get(pattern.getIdentifier());

                                    if (color != null) {
                                        return "info-box-icon elevation-1 btn btn-outline-dark gap-1";
                                    }
                                }
                                return null;
                            }

                            @Override
                            protected String getIconContainerStyle() {
                                Map<String, String> pallet = patternColorPalette.getObject();

                                switchToDefaultStyleView();

                                if (pallet != null) {
                                    String color = pallet.get(pattern.getIdentifier());

                                    if (color != null) {
                                        return "background-color:" + color + ";";
                                    }
                                }
                                return null;
                            }

                            @Override
                            protected IModel<String> getLinkModel() {
                                String identifier = pattern.getIdentifier();
                                return Model.of("Role: " + Objects.requireNonNullElse(identifier, finalI));
                            }

                            @Override
                            protected boolean isBoxVisible() {
                                return isExpanded;
                            }

                            @Override
                            protected void addDescriptionComponents() {
                                appendText("Reduction for");
                                appendIcon("fe fe-assignment", null);
                                appendText(" " + formattedReductionFactorConfidence + " assignments");
                                appendText("with confidence of");
                                appendIcon("fa fa-leaf", null);
                                appendText(" " + formattedItemConfidence + "%.");
                            }

                            @Override
                            protected String getDescriptionStyle() {
                                return "font-size:14px; line-height: 1.1;";
                            }

                            @Override
                            protected void onClickIconPerform(AjaxRequestTarget target) {
                                if (displayedPatterns == null) {
                                    displayedPatterns = new ArrayList<>();
                                }

                                boolean alreadySelected = false;
                                for (DetectedPattern displayedPattern : displayedPatterns) {
                                    if (displayedPattern.getId().equals(pattern.getId())) {
                                        displayedPatterns.remove(displayedPattern);
                                        alreadySelected = true;
                                        break;
                                    }
                                }

                                if (!isCandidateRoleSelector) {
                                    isCandidateRoleSelector = true;
                                    displayedPatterns.clear();
                                }

                                if (!alreadySelected) {
                                    displayedPatterns.add(pattern);
                                }

                                if (displayedPatterns.isEmpty()) {
                                    isRelationSelected = false;
                                }
                                patternColorPalette = new LoadableDetachableModel<>() {
                                    @Override
                                    protected @NotNull Map<String, String> load() {
                                        return generateObjectColors(getPatternIdentifiers());
                                    }
                                };

                                loadDetectedPattern(target, displayedPatterns);

                                Map<String, String> pallet = patternColorPalette.getObject();

                                switchToDefaultStyleView();

                                if (pallet != null) {
                                    String color = pallet.get(pattern.getIdentifier());

                                    if (color != null) {
                                        switchToSelectedStyleView(color);
                                        target.add(this);
                                    }
                                }

                                refreshItemPanel(target);
                                target.add(getTable().getDataTable());
                                target.add(getTable());
                            }

                            @Override
                            protected void onClickLinkPerform(AjaxRequestTarget target) {
                                RoleAnalysisCandidateRoleType candidateRole = findCandidateRole(
                                        cluster.asObjectable(),
                                        pattern.getId().toString());
                                if (candidateRole != null) {
                                    String roleOid = candidateRole.getCandidateRoleRef().getOid();
                                    PageParameters parameters = new PageParameters();
                                    parameters.add(OnePageParameterEncoder.PARAMETER, roleOid);
                                    Class<? extends PageBase> detailsPageClass = DetailsPageUtil
                                            .getObjectDetailsPage(RoleType.class);
                                    getPageBase().navigateToNext(detailsPageClass, parameters);
                                }
                            }
                        };
                        candidatePanel.setOutputMarkupId(true);
                        toolsPanelItems.add(candidatePanel);
                    }

                }

            }

            @Override
            protected void onSubmitEditButton(AjaxRequestTarget target) {
                onSubmitCandidateRolePerform(target, cluster);
            }

            @Override
            protected @NotNull WebMarkupContainer createButtonToolbar(String id) {

                RepeatingView repeatingView = new RepeatingView(id);
                repeatingView.setOutputMarkupId(true);

                CompositedIconBuilder iconBuilder = new CompositedIconBuilder().setBasicIcon(
                        "fa fa-search", LayeredIconCssStyle.IN_ROW_STYLE);

                AjaxCompositedIconButton detectionPanel = new AjaxCompositedIconButton(
                        repeatingView.newChildId(), iconBuilder.build(),
                        createStringResource("RoleAnalysis.explore.patterns")) {

                    @Serial private static final long serialVersionUID = 1L;

                    @Override
                    public void onClick(AjaxRequestTarget target) {
                        showDetectedPatternPanel(target);
                    }

                };
                detectionPanel.add(
                        AttributeAppender.replace("class", "btn btn-default btn-sm mr-1"));
                detectionPanel.titleAsLabel(true);

                repeatingView.add(detectionPanel);

                iconBuilder = new CompositedIconBuilder().setBasicIcon(
                        "fa fa-users", LayeredIconCssStyle.IN_ROW_STYLE);
                AjaxCompositedIconButton createCandidateRolePanelButton = new AjaxCompositedIconButton(
                        repeatingView.newChildId(), iconBuilder.build(),
                        createStringResource("RoleAnalysis.explore.candidate.roles")) {

                    @Serial private static final long serialVersionUID = 1L;

                    @Override
                    public void onClick(AjaxRequestTarget target) {
                        showCandidateRolesPanel(target);
                    }

                };
                createCandidateRolePanelButton.add(
                        AttributeAppender.replace("class", "btn btn-default btn-sm mr-1"));
                createCandidateRolePanelButton.titleAsLabel(true);

                repeatingView.add(createCandidateRolePanelButton);

                AjaxIconButton refreshIcon = new AjaxIconButton(
                        repeatingView.newChildId(),
                        new Model<>(GuiStyleConstants.CLASS_RECONCILE),
                        createStringResource("MainObjectListPanel.refresh")) {

                    @Serial
                    private static final long serialVersionUID = 1L;

                    @Override
                    public void onClick(AjaxRequestTarget target) {
                        onRefresh(cluster);
                    }
                };
                refreshIcon.add(AttributeAppender.replace("class", "btn btn-default btn-sm"));

                repeatingView.add(refreshIcon);

                iconBuilder = new CompositedIconBuilder().setBasicIcon(
                        "fa fa-cog", LayeredIconCssStyle.IN_ROW_STYLE);
                AjaxCompositedIconButton tableSetting = new AjaxCompositedIconButton(
                        repeatingView.newChildId(), iconBuilder.build(), createStringResource("")) {

                    @Serial private static final long serialVersionUID = 1L;

                    @Override
                    public void onClick(AjaxRequestTarget target) {
                        RoleAnalysisTableSettingPanel selector = new RoleAnalysisTableSettingPanel(
                                ((PageBase) getPage()).getMainPopupBodyId(),
                                createStringResource("RoleAnalysisPathTableSelector.title"), displayValueOptionModel) {

                            @Override
                            public void performAfterFinish(AjaxRequestTarget target) {
                                resetTable(target, displayValueOptionModel.getObject());
                            }
                        };
                        ((PageBase) getPage()).showMainPopup(selector, target);
                    }

                };

                tableSetting.add(AttributeAppender.replace("class", "btn btn-default btn-sm"));

                repeatingView.add(tableSetting);
                return repeatingView;
            }

            @Override
            public void onChange(String value, AjaxRequestTarget target, int currentPage) {
                currentPageView = currentPage;
                valueTitle = value;
                String[] rangeParts = value.split(" - ");
                fromCol = Integer.parseInt(rangeParts[0]);
                toCol = Integer.parseInt(rangeParts[1]);
                getTable().replaceWith(generateTable(provider, roles, reductionObjects, cluster));
                target.add(getTable().setOutputMarkupId(true));
            }

            @Override
            public void onChangeSize(int value, AjaxRequestTarget target) {
                currentPageView = 0;
                columnPageCount = value;
                fromCol = 1;
                toCol = Math.min(value, specialColumnCount);
                valueTitle = "0 - " + toCol;

                getTable().replaceWith(generateTable(provider, roles, reductionObjects, cluster));
                target.add(getTable().setOutputMarkupId(true));
                target.appendJavaScript(applyTableScaleScript());
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
            protected int getCurrentPage() {
                return currentPageView;
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

    private boolean isObjectIconHeaderActive = true;
    private boolean isObjectLinkHeaderActive = true;
    private boolean isActionIconHeaderActive = true;

    public List<IColumn<MiningUserTypeChunk, String>> initColumns(List<MiningRoleTypeChunk> roles,
            List<ObjectReferenceType> reductionObjects) {

        int detectedPatternCount;
        if (displayedPatterns != null) {
            detectedPatternCount = displayedPatterns.size();
        } else {
            detectedPatternCount = 0;
        }

        List<IColumn<MiningUserTypeChunk, String>> columns = new ArrayList<>();

        columns.add(new CompositedIconColumn<>(null) {

            @Serial private static final long serialVersionUID = 1L;

            @Override
            public String getCssClass() {
                if (isObjectIconHeaderActive) {
                    isObjectIconHeaderActive = false;
                    return " role-mining-static-header role-mining-no-border ";
                }
                return " role-mining-static-header ";
            }

            @Override
            protected CompositedIcon getCompositedIcon(IModel<MiningUserTypeChunk> rowModel) {
                MiningUserTypeChunk object = rowModel.getObject();

                String defaultBlackIcon = IconAndStylesUtil.createDefaultBlackIcon(UserType.COMPLEX_TYPE);
                CompositedIconBuilder compositedIconBuilder = new CompositedIconBuilder().setBasicIcon(defaultBlackIcon,
                        LayeredIconCssStyle.IN_ROW_STYLE);

                String iconColor = object.getIconColor();
                if (iconColor != null) {
                    compositedIconBuilder.appendColorHtmlValue(iconColor);
                }

                return compositedIconBuilder.build();
            }

            @Override
            public void populateItem(Item<ICellPopulator<MiningUserTypeChunk>> cellItem, String componentId, IModel<MiningUserTypeChunk> rowModel) {
                int propertiesCount = rowModel.getObject().getUsers().size();
                RoleAnalysisTableTools.StyleResolution styleResolution = RoleAnalysisTableTools
                        .StyleResolution
                        .resolveSize(propertiesCount);

                String sizeInPixels = styleResolution.getSizeInPixels();
                cellItem.add(AttributeAppender.append("style", " width:40px; height:" + sizeInPixels + ";"));
                super.populateItem(cellItem, componentId, rowModel);
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
                int propertiesCount = rowModel.getObject().getUsers().size();
                RoleAnalysisTableTools.StyleResolution styleResolution = RoleAnalysisTableTools
                        .StyleResolution
                        .resolveSize(propertiesCount);

                String sizeInPixels = styleResolution.getSizeInPixels();
                item.add(AttributeAppender.append("style", " width:150px; height:" + sizeInPixels + ";"));

                List<String> elements = rowModel.getObject().getUsers();

                updateFrequencyBased(rowModel, minFrequency, maxFrequency, isOutlierDetection());

                String title = rowModel.getObject().getChunkName();
                AjaxLinkPanel analyzedMembersDetailsPanel = new AjaxLinkPanel(componentId,
                        createStringResource(title)) {
                    @Override
                    public void onClick(AjaxRequestTarget target) {
                        Task task = getPageBase().createSimpleTask(OP_PREPARE_OBJECTS);

                        List<PrismObject<FocusType>> objects = new ArrayList<>();
                        for (String objectOid : elements) {
                            objects.add(getPageBase().getRoleAnalysisService()
                                    .getFocusTypeObject(objectOid, task, result));
                        }
                        MembersDetailsPopupPanel detailsPanel = new MembersDetailsPopupPanel(((PageBase) getPage()).getMainPopupBodyId(),
                                Model.of("Analyzed members details panel"), objects, RoleAnalysisProcessModeType.USER) {
                            @Override
                            public void onClose(AjaxRequestTarget ajaxRequestTarget) {
                                super.onClose(ajaxRequestTarget);
                            }
                        };
                        ((PageBase) getPage()).showMainPopup(detailsPanel, target);
                    }

                };
                analyzedMembersDetailsPanel.add(
                        AttributeAppender.replace("class", "d-inline-block text-truncate"));
                analyzedMembersDetailsPanel.add(AttributeAppender.replace("style", "width:145px"));
                analyzedMembersDetailsPanel.setOutputMarkupId(true);
                item.add(analyzedMembersDetailsPanel);
            }

            @Override
            public Component getHeader(String componentId) {

                CompositedIconBuilder iconBuilder = new CompositedIconBuilder().setBasicIcon("fa fa-expand",
                        LayeredIconCssStyle.IN_ROW_STYLE);
                AjaxCompositedIconSubmitButton compressButton = new AjaxCompositedIconSubmitButton(componentId,
                        iconBuilder.build(),
                        new LoadableDetachableModel<>() {
                            @Override
                            protected String load() {
                                if (RoleAnalysisChunkMode.valueOf(getCompressStatus()).equals(RoleAnalysisChunkMode.COMPRESS)) {
                                    return getString("RoleMining.operation.panel.expand.button.title");
                                } else {
                                    return getString("RoleMining.operation.panel.compress.button.title");
                                }
                            }
                        }) {
                    @Serial private static final long serialVersionUID = 1L;

                    @Override
                    public CompositedIcon getIcon() {
                        CompositedIconBuilder iconBuilder;
                        if (RoleAnalysisChunkMode.valueOf(getCompressStatus()).equals(RoleAnalysisChunkMode.COMPRESS)) {
                            iconBuilder = new CompositedIconBuilder().setBasicIcon("fa fa-expand",
                                    LayeredIconCssStyle.IN_ROW_STYLE);
                        } else {
                            iconBuilder = new CompositedIconBuilder().setBasicIcon("fa fa-compress",
                                    LayeredIconCssStyle.IN_ROW_STYLE);
                        }

                        return iconBuilder.build();
                    }

                    @Override
                    protected void onSubmit(AjaxRequestTarget target) {
                        onPerform(target);
                    }

                    @Override
                    protected void onError(AjaxRequestTarget target) {
                        target.add(((PageBase) getPage()).getFeedbackPanel());
                    }
                };
                compressButton.titleAsLabel(true);
                compressButton.setOutputMarkupId(true);
                compressButton.add(AttributeAppender.append("class", "btn btn-default btn-sm"));
                compressButton.add(AttributeAppender.append("style",
                        "  writing-mode: vertical-lr;  -webkit-transform: rotate(90deg);"));

                return compressButton;
            }

            @Override
            public String getCssClass() {
                if (isObjectLinkHeaderActive) {
                    isObjectLinkHeaderActive = false;
                    return "overflow-auto role-mining-static-row-header role-mining-static-header-name "
                            + "role-mining-no-border align-self-center ";
                }
                return "overflow-auto role-mining-static-row-header role-mining-static-header-name align-self-center";
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

                int propertiesCount = rowModel.getObject().getUsers().size();
                RoleAnalysisTableTools.StyleResolution styleResolution = RoleAnalysisTableTools
                        .StyleResolution
                        .resolveSize(propertiesCount);

                String sizeInPixels = styleResolution.getSizeInPixels();
                item.add(AttributeAppender.append("style", " overflow-wrap: break-word !important; "
                        + "word-break: inherit;"
                        + " width:40px; height:" + sizeInPixels + ";"));

                LinkIconPanelStatus linkIconPanel = new LinkIconPanelStatus(componentId, new LoadableDetachableModel<>() {
                    @Override
                    protected RoleAnalysisOperationMode load() {
                        if (displayedPatterns != null && displayedPatterns.size() > 1) {
                            return RoleAnalysisOperationMode.DISABLE;
                        }
                        return rowModel.getObject().getStatus();
                    }
                }) {
                    @Override
                    protected RoleAnalysisOperationMode onClickPerformed(
                            AjaxRequestTarget target,
                            RoleAnalysisOperationMode status) {
                        isRelationSelected = false;
                        MiningUserTypeChunk object = rowModel.getObject();
                        RoleAnalysisObjectStatus objectStatus = new RoleAnalysisObjectStatus(status.toggleStatus());
                        objectStatus.setContainerId(new HashSet<>(getPatternIdentifiers()));
                        object.setObjectStatus(objectStatus);
                        target.add(getTable().setOutputMarkupId(true));
                        return rowModel.getObject().getStatus();
                    }
                };

                linkIconPanel.setOutputMarkupId(true);
                item.add(linkIconPanel);
            }

            @Override
            public String getCssClass() {
                if (isActionIconHeaderActive) {
                    isActionIconHeaderActive = false;
                    return "role-mining-static-header role-mining-no-border ";
                }

                return " role-mining-static-header";
            }
        });


        IColumn<MiningUserTypeChunk, String> column;
        for (int i = fromCol - 1; i < toCol; i++) {
            MiningRoleTypeChunk roleChunk = roles.get(i);
            int membersSize = roleChunk.getRoles().size();
            RoleAnalysisTableTools.StyleResolution styleWidth = RoleAnalysisTableTools.StyleResolution.resolveSize(membersSize);

            column = new AbstractColumn<>(createStringResource("")) {

                @Override
                public void populateItem(Item<ICellPopulator<MiningUserTypeChunk>> cellItem,
                        String componentId, IModel<MiningUserTypeChunk> model) {
                    MiningUserTypeChunk object = model.getObject();
                    List<String> users = object.getUsers();
                    int propertiesCount = users.size();
                    RoleAnalysisTableTools.StyleResolution styleHeight = RoleAnalysisTableTools
                            .StyleResolution
                            .resolveSize(propertiesCount);

                    applySquareTableCell(cellItem, styleWidth, styleHeight);

                    boolean isInclude = resolveCellTypeRoleTable(componentId, cellItem, object, roleChunk, patternColorPalette);
                    if (isInclude) {
                        isRelationSelected = true;
                    }
                }

                @Override
                public String getCssClass() {
                    String cssLevel = RoleAnalysisTableTools.StyleResolution.resolveSizeLevel(styleWidth);
                    return cssLevel + " p-2";
                }

                @Override
                public Component getHeader(String componentId) {

                    List<String> roles = roleChunk.getRoles();

                    String defaultBlackIcon = IconAndStylesUtil.createDefaultBlackIcon(RoleType.COMPLEX_TYPE);
                    CompositedIconBuilder compositedIconBuilder = new CompositedIconBuilder().setBasicIcon(defaultBlackIcon,
                            LayeredIconCssStyle.IN_ROW_STYLE);

                    String iconColor = roleChunk.getIconColor();
                    if (iconColor != null) {
                        compositedIconBuilder.appendColorHtmlValue(iconColor);
                    }

                    for (ObjectReferenceType ref : reductionObjects) {
                        if (roles.contains(ref.getOid())) {
                            compositedIconBuilder.setBasicIcon(defaultBlackIcon + " " + GuiStyleConstants.GREEN_COLOR,
                                    LayeredIconCssStyle.IN_ROW_STYLE);
                            IconType icon = new IconType();
                            icon.setCssClass(GuiStyleConstants.CLASS_OP_RESULT_STATUS_ICON_SUCCESS_COLORED
                                    + " " + GuiStyleConstants.GREEN_COLOR);
                            compositedIconBuilder.appendLayerIcon(icon, IconCssStyle.BOTTOM_RIGHT_FOR_COLUMN_STYLE);
                            break;
                        }
                    }

                    CompositedIcon compositedIcon = compositedIconBuilder.build();

                    String title = roleChunk.getChunkName();

                    return new AjaxLinkTruncatePanelAction(componentId,
                            createStringResource(title), createStringResource(title), compositedIcon,
                            new LoadableDetachableModel<>() {
                                @Override
                                protected RoleAnalysisOperationMode load() {
                                    if (displayedPatterns != null && displayedPatterns.size() > 1) {
                                        return RoleAnalysisOperationMode.DISABLE;
                                    }
                                    return roleChunk.getStatus();
                                }
                            }) {

                        @Override
                        protected RoleAnalysisOperationMode onClickPerformedAction(
                                AjaxRequestTarget target,
                                RoleAnalysisOperationMode status) {
                            isRelationSelected = false;
                            RoleAnalysisObjectStatus objectStatus = new RoleAnalysisObjectStatus(status.toggleStatus());
                            objectStatus.setContainerId(new HashSet<>(getPatternIdentifiers()));
                            roleChunk.setObjectStatus(objectStatus);

                            target.add(getTable().setOutputMarkupId(true));
                            return roleChunk.getStatus();
                        }

                        @Override
                        public void onClick(AjaxRequestTarget target) {

                            Task task = getPageBase().createSimpleTask(OP_PREPARE_OBJECTS);

                            List<PrismObject<FocusType>> objects = new ArrayList<>();
                            for (String objectOid : roles) {
                                objects.add(getPageBase().getRoleAnalysisService()
                                        .getFocusTypeObject(objectOid, task, result));
                            }
                            MembersDetailsPopupPanel detailsPanel = new MembersDetailsPopupPanel(((PageBase) getPage()).getMainPopupBodyId(),
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
        return ((RoleAnalysisTable<?>) get(((PageBase) getPage()).createComponentPath(ID_DATATABLE))).getDataTable();
    }

    protected RoleAnalysisTable<MiningOperationChunk> getTable() {
        return ((RoleAnalysisTable<MiningOperationChunk>) get(((PageBase) getPage()).createComponentPath(ID_DATATABLE)));
    }

    protected void resetTable(AjaxRequestTarget target, DisplayValueOption displayValueOption) {

    }

    protected String getCompressStatus() {
        return displayValueOptionModel.getObject().getChunkMode().getValue();
    }

    protected void onPerform(AjaxRequestTarget ajaxRequestTarget) {
    }

    protected void showDetectedPatternPanel(AjaxRequestTarget target) {

    }

    protected void showCandidateRolesPanel(AjaxRequestTarget target) {

    }

    public void loadDetectedPattern(AjaxRequestTarget target, List<DetectedPattern> detectedPattern) {
        this.displayedPatterns = detectedPattern;
        List<MiningUserTypeChunk> users = miningOperationChunk.getMiningUserTypeChunks(RoleAnalysisSortMode.NONE);
        List<MiningRoleTypeChunk> roles = miningOperationChunk.getMiningRoleTypeChunks(RoleAnalysisSortMode.NONE);

        refreshCells(RoleAnalysisProcessModeType.ROLE, users, roles, minFrequency, maxFrequency);

        if (isPatternDetected()) {
            Task task = getPageBase().createSimpleTask("InitPattern");
            OperationResult result = task.getResult();
            initRoleBasedDetectionPattern(getPageBase(),
                    users,
                    roles,
                    this.displayedPatterns,
                    minFrequency,
                    maxFrequency,
                    task,
                    result);
        }

        target.add(getTable().setOutputMarkupId(true));
    }

    private void onRefresh(@NotNull PrismObject<RoleAnalysisClusterType> cluster) {
        PageParameters parameters = new PageParameters();
        parameters.add(OnePageParameterEncoder.PARAMETER, cluster.getOid());
        parameters.add("panelId", "clusterDetails");
        Class<? extends PageBase> detailsPageClass = DetailsPageUtil
                .getObjectDetailsPage(RoleAnalysisClusterType.class);
        getPageBase().navigateToNext(detailsPageClass, parameters);
    }

    private void navigateToClusterCandidateRolePanel(@NotNull PrismObject<RoleAnalysisClusterType> cluster) {
        PageParameters parameters = new PageParameters();
        String clusterOid = cluster.getOid();
        parameters.add(OnePageParameterEncoder.PARAMETER, clusterOid);
        parameters.add("panelId", "candidateRoles");
        Class<? extends PageBase> detailsPageClass = DetailsPageUtil
                .getObjectDetailsPage(RoleAnalysisClusterType.class);
        getPageBase().navigateToNext(detailsPageClass, parameters);
    }

    private boolean isPatternDetected() {
        return displayedPatterns != null && !displayedPatterns.isEmpty();
    }

    public RoleAnalysisSortMode getRoleAnalysisSortMode() {
        if (displayValueOptionModel.getObject() != null) {
            return displayValueOptionModel.getObject().getSortMode();
        }

        return RoleAnalysisSortMode.NONE;
    }

    protected @Nullable Set<RoleAnalysisCandidateRoleType> getCandidateRole() {
        return null;
    }

    private void onSubmitCandidateRolePerform(@NotNull AjaxRequestTarget target,
            @NotNull PrismObject<RoleAnalysisClusterType> cluster) {

        if (miningOperationChunk == null) {
            warn(createStringResource("RoleAnalysis.candidate.not.selected").getString());
            target.add(getPageBase().getFeedbackPanel());
            return;
        }

        Task task = getPageBase().createSimpleTask(OP_PROCESS_CANDIDATE_ROLE);
        OperationResult result = task.getResult();

        Set<RoleType> candidateInducements = new HashSet<>();

        List<MiningRoleTypeChunk> simpleMiningRoleTypeChunks = miningOperationChunk.getSimpleMiningRoleTypeChunks();
        for (MiningRoleTypeChunk roleChunk : simpleMiningRoleTypeChunks) {
            if (roleChunk.getStatus().equals(RoleAnalysisOperationMode.INCLUDE)) {
                for (String roleOid : roleChunk.getRoles()) {
                    PrismObject<RoleType> roleObject = getPageBase().getRoleAnalysisService()
                            .getRoleTypeObject(roleOid, task, result);
                    if (roleObject != null) {
                        candidateInducements.add(roleObject.asObjectable());
                    }
                }
            }
        }
        List<MiningUserTypeChunk> simpleMiningUserTypeChunks = miningOperationChunk.getSimpleMiningUserTypeChunks();
        Set<PrismObject<UserType>> candidateMembers = new HashSet<>();
        for (MiningUserTypeChunk userChunk : simpleMiningUserTypeChunks) {
            if (userChunk.getStatus().equals(RoleAnalysisOperationMode.INCLUDE)) {
                for (String userOid : userChunk.getUsers()) {
                    PrismObject<UserType> userObject = getPageBase().getRoleAnalysisService()
                            .getUserTypeObject(userOid, task, result);
                    if (userObject != null) {
                        candidateMembers.add(userObject);
                    }
                }
            }
        }

        if (getCandidateRole() != null) {
            @Nullable List<RoleAnalysisCandidateRoleType> candidateRole = new ArrayList<>(getCandidateRole());
            if (candidateRole.size() == 1) {
                PageBase pageBase = getPageBase();
                RoleAnalysisService roleAnalysisService = pageBase.getRoleAnalysisService();

                Set<AssignmentType> assignmentTypeSet = new HashSet<>();
                for (RoleType candidateInducement : candidateInducements) {
                    assignmentTypeSet.add(ObjectTypeUtil.createAssignmentTo(candidateInducement.getOid(), ObjectTypes.ROLE));
                }

                executeChangesOnCandidateRole(roleAnalysisService, pageBase, target,
                        cluster,
                        candidateRole,
                        candidateMembers,
                        assignmentTypeSet,
                        task,
                        result
                );

                result.computeStatus();
                getPageBase().showResult(result);
                navigateToClusterCandidateRolePanel(cluster);
                return;
            }
        }

        PrismObject<RoleType> businessRole = getPageBase().getRoleAnalysisService()
                .generateBusinessRole(new HashSet<>(), PolyStringType.fromOrig(""));

        List<BusinessRoleDto> roleApplicationDtos = new ArrayList<>();

        for (PrismObject<UserType> member : candidateMembers) {
            BusinessRoleDto businessRoleDto = new BusinessRoleDto(member,
                    businessRole, candidateInducements, getPageBase());
            roleApplicationDtos.add(businessRoleDto);
        }

        BusinessRoleApplicationDto operationData = new BusinessRoleApplicationDto(
                cluster, businessRole, roleApplicationDtos, candidateInducements);

        if (displayedPatterns != null && displayedPatterns.get(0).getId() != null) {
            operationData.setPatternId(displayedPatterns.get(0).getId());
        }

        List<BusinessRoleDto> businessRoleDtos = operationData.getBusinessRoleDtos();
        Set<RoleType> inducement = operationData.getCandidateRoles();
        if (!inducement.isEmpty() && !businessRoleDtos.isEmpty()) {
            PageRole pageRole = new PageRole(operationData.getBusinessRole(), operationData);
            setResponsePage(pageRole);
        } else {
            warn(createStringResource("RoleAnalysis.candidate.not.selected").getString());
            target.add(getPageBase().getFeedbackPanel());
        }
    }

    public List<String> getPatternIdentifiers() {
        List<String> patternIds = new ArrayList<>();
        if (displayedPatterns != null) {
            for (DetectedPattern pattern : displayedPatterns) {
                String identifier = pattern.getIdentifier();
                patternIds.add(identifier);
            }
        }
        return patternIds;
    }

    private @Nullable RoleAnalysisCandidateRoleType findCandidateRole(
            @NotNull RoleAnalysisClusterType cluster,
            @NotNull String identifier) {
        List<RoleAnalysisCandidateRoleType> candidateRoles = cluster.getCandidateRoles();
        for (RoleAnalysisCandidateRoleType candidateRole : candidateRoles) {
            if (String.valueOf(candidateRole.getId()).equals(identifier)) {
                return candidateRole;
            }
        }
        return null;
    }

    public LoadableDetachableModel<DisplayValueOption> getDisplayValueOptionModel() {
        return displayValueOptionModel;
    }

    public boolean isOutlierDetection() {
        return false;
    }

    public List<DetectedPattern> getDisplayedPatterns() {
        return new ArrayList<>();
    }

    public List<DetectedPattern> getCandidateRoles() {
        return new ArrayList<>();
    }

}
