/*
 * Copyright (C) 2010-2023 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.gui.impl.page.admin.role.mining.page.panel.cluster;

import static com.evolveum.midpoint.common.mining.objects.analysis.AttributeAnalysisStructure.extractAttributeAnalysis;
import static com.evolveum.midpoint.common.mining.utils.ExtractPatternUtils.transformDefaultPattern;
import static com.evolveum.midpoint.common.mining.utils.ExtractPatternUtils.transformPattern;
import static com.evolveum.midpoint.common.mining.utils.RoleAnalysisAttributeDefUtils.getObjectNameDef;
import static com.evolveum.midpoint.common.mining.utils.RoleAnalysisUtils.getRolesOidAssignment;
import static com.evolveum.midpoint.common.mining.utils.RoleAnalysisUtils.getRolesOidInducements;
import static com.evolveum.midpoint.gui.impl.page.admin.role.mining.utils.table.RoleAnalysisTableCellFillResolver.initRoleBasedDetectionPattern;
import static com.evolveum.midpoint.gui.impl.page.admin.role.mining.utils.table.RoleAnalysisTableCellFillResolver.initUserBasedDetectionPattern;
import static com.evolveum.midpoint.gui.impl.page.admin.role.mining.utils.table.RoleAnalysisTableTools.applyTableScaleScript;

import java.util.*;

import com.google.common.collect.ListMultimap;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.util.string.StringValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.evolveum.midpoint.common.mining.objects.analysis.AttributeAnalysisStructure;
import com.evolveum.midpoint.common.mining.objects.chunk.DisplayValueOption;
import com.evolveum.midpoint.common.mining.objects.chunk.MiningOperationChunk;
import com.evolveum.midpoint.common.mining.objects.chunk.MiningRoleTypeChunk;
import com.evolveum.midpoint.common.mining.objects.chunk.MiningUserTypeChunk;
import com.evolveum.midpoint.common.mining.objects.detection.DetectedPattern;
import com.evolveum.midpoint.common.mining.utils.values.RoleAnalysisChunkMode;
import com.evolveum.midpoint.common.mining.utils.values.RoleAnalysisOperationMode;
import com.evolveum.midpoint.common.mining.utils.values.RoleAnalysisSortMode;
import com.evolveum.midpoint.gui.api.GuiStyleConstants;
import com.evolveum.midpoint.gui.api.page.PageBase;
import com.evolveum.midpoint.gui.impl.page.admin.AbstractObjectMainPanel;
import com.evolveum.midpoint.gui.impl.page.admin.ObjectDetailsModels;
import com.evolveum.midpoint.gui.impl.page.admin.role.mining.page.panel.chart.RoleAnalysisAttributeChartPanel;
import com.evolveum.midpoint.gui.impl.page.admin.role.mining.tables.RoleAnalysisRoleBasedTable;
import com.evolveum.midpoint.gui.impl.page.admin.role.mining.tables.RoleAnalysisUserBasedTable;
import com.evolveum.midpoint.model.api.mining.RoleAnalysisService;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.web.application.PanelDisplay;
import com.evolveum.midpoint.web.application.PanelInstance;
import com.evolveum.midpoint.web.application.PanelType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

@PanelType(name = "clusterDetails")
@PanelInstance(
        identifier = "clusterDetails",
        applicableForType = RoleAnalysisClusterType.class,
        display = @PanelDisplay(
                label = "RoleAnalysisClusterType.operationsPanel",
                icon = GuiStyleConstants.CLASS_ICON_TASK_RESULTS,
                order = 20
        )
)
public class RoleAnalysisClusterOperationPanel extends AbstractObjectMainPanel<RoleAnalysisClusterType, ObjectDetailsModels<RoleAnalysisClusterType>> {

    private static final String ID_MAIN_PANEL = "main";
    private static final String ID_DATATABLE = "datatable_extra";
    private static final String ID_CHART_PANEL = "chartPanel";

    private static final String DOT_CLASS = RoleAnalysisClusterOperationPanel.class.getName() + ".";
    private static final String OP_PREPARE_OBJECTS = DOT_CLASS + "prepareObjects";
    private final OperationResult result = new OperationResult(OP_PREPARE_OBJECTS);
    private List<DetectedPattern> analysePattern = null;
    private MiningOperationChunk miningOperationChunk;
    private LoadableDetachableModel<DisplayValueOption> displayValueOptionModel = new LoadableDetachableModel<>() {
        @Override
        protected DisplayValueOption load() {
            DisplayValueOption displayValueOption = new DisplayValueOption();
            displayValueOption.setChunkMode(RoleAnalysisChunkMode.COMPRESS);
            return displayValueOption;
        }
    };
    private boolean isRoleMode;

    public static final String PARAM_CANDIDATE_ROLE_ID = "candidateRoleId";
    public static final String PARAM_DETECTED_PATER_ID = "detectedPatternId";
    public static final String PARAM_TABLE_SETTING = "tableSetting";
    boolean isOutlierDetection = false;

    public List<String> getCandidateRoleContainerId() {
        StringValue stringValue = getPageBase().getPageParameters().get(PARAM_CANDIDATE_ROLE_ID);
        if (!stringValue.isNull()) {
            String[] split = stringValue.toString().split(",");
            return Arrays.asList(split);
        }
        return null;
    }

    public Integer getParameterTableSetting() {
        StringValue stringValue = getPageBase().getPageParameters().get(PARAM_TABLE_SETTING);
        if (!stringValue.isNull()) {
            return Integer.valueOf(stringValue.toString());
        }
        return null;
    }

    public Long getDetectedPatternContainerId() {
        StringValue stringValue = getPageBase().getPageParameters().get(PARAM_DETECTED_PATER_ID);
        if (!stringValue.isNull()) {
            return Long.valueOf(stringValue.toString());
        }
        return null;
    }

    public RoleAnalysisClusterOperationPanel(String id, ObjectDetailsModels<RoleAnalysisClusterType> model,
            ContainerPanelConfigurationType config) {
        super(id, model, config);
    }

    @Override
    protected void initLayout() {
        RoleAnalysisService roleAnalysisService = getPageBase().getRoleAnalysisService();
        Task task = ((PageBase) getPage()).createSimpleTask(OP_PREPARE_OBJECTS);
        RoleAnalysisClusterType cluster = getObjectDetailsModels().getObjectType();
        PrismObject<RoleAnalysisSessionType> getParent = roleAnalysisService.
                getSessionTypeObject(cluster.getRoleAnalysisSessionRef().getOid(), task, result);

        if (getCandidateRoleContainerId() != null) {
            loadCandidateRole(cluster, roleAnalysisService, task);
        } else if (getDetectedPatternContainerId() != null) {
            loadDetectedPattern(cluster);
        }

        RoleAnalysisProcessModeType mode = null;
        if (getParent != null) {
            RoleAnalysisSessionType session = getParent.asObjectable();
            RoleAnalysisOptionType analysisOption = session.getAnalysisOption();
            RoleAnalysisCategoryType analysisCategory = analysisOption.getAnalysisCategory();
            if (analysisCategory.equals(RoleAnalysisCategoryType.OUTLIERS)) {
                isOutlierDetection = true;
            }
            mode = analysisOption.getProcessMode();
            isRoleMode = mode.equals(RoleAnalysisProcessModeType.ROLE);
            displayValueOptionModel.getObject().setProcessMode(mode);
            Integer parameterTableSetting = getParameterTableSetting();
            if (parameterTableSetting != null && parameterTableSetting == 1) {
                displayValueOptionModel.getObject().setFullPage(true);
            }
        }

        AnalysisClusterStatisticType clusterStatistics = cluster.getClusterStatistics();

        resolveDisplayValue(clusterStatistics);

        loadMiningTableData(displayValueOptionModel.getObject());

        WebMarkupContainer webMarkupContainer = new WebMarkupContainer(ID_MAIN_PANEL);
        webMarkupContainer.setOutputMarkupId(true);

        loadMiningTable(webMarkupContainer, analysePattern);

        List<AttributeAnalysisStructure> attributeAnalysisStructures = extractAttributeAnalysis(cluster);
        RoleAnalysisAttributeChartPanel roleAnalysisChartPanel = new RoleAnalysisAttributeChartPanel(
                ID_CHART_PANEL, attributeAnalysisStructures, cluster) {
            @Override
            public boolean isExpanded() {
                return false;
            }
        };
        roleAnalysisChartPanel.setOutputMarkupId(true);
        webMarkupContainer.add(roleAnalysisChartPanel);

        add(webMarkupContainer);

    }

    private void resolveDisplayValue(@NotNull AnalysisClusterStatisticType clusterStatistics) {
        Integer rolesCount = clusterStatistics.getRolesCount();
        Integer usersCount = clusterStatistics.getUsersCount();

        if (rolesCount == null || usersCount == null) {
            displayValueOptionModel.getObject().setSortMode(RoleAnalysisSortMode.NONE);
        } else {

            int maxRoles;
            int maxUsers;

            if (isRoleMode) {
                maxRoles = 20;
                maxUsers = 13;
            } else {
                maxRoles = 13;
                maxUsers = 20;
            }
            int max = Math.max(rolesCount, usersCount);

            if (max <= 500) {
                displayValueOptionModel.getObject().setSortMode(RoleAnalysisSortMode.JACCARD);
            } else {
                displayValueOptionModel.getObject().setSortMode(RoleAnalysisSortMode.FREQUENCY);
            }

            if (rolesCount > maxRoles && usersCount > maxUsers) {
                displayValueOptionModel.getObject().setChunkMode(RoleAnalysisChunkMode.COMPRESS);
            } else if (rolesCount > maxRoles && usersCount <= maxUsers) {
                displayValueOptionModel.getObject().setChunkMode(RoleAnalysisChunkMode.EXPAND_USER);
            } else if (rolesCount <= maxRoles && usersCount > maxUsers) {
                displayValueOptionModel.getObject().setChunkMode(RoleAnalysisChunkMode.EXPAND_ROLE);
            } else {
                displayValueOptionModel.getObject().setChunkMode(RoleAnalysisChunkMode.EXPAND);
            }
        }
    }

    private void loadCandidateRole(RoleAnalysisClusterType cluster,
            RoleAnalysisService roleAnalysisService,
            Task task) {
        List<RoleAnalysisCandidateRoleType> candidateRoles = cluster.getCandidateRoles();

        analysePattern = new ArrayList<>();
        for (RoleAnalysisCandidateRoleType candidateRole : candidateRoles) {

            List<String> candidateRoleContainerId = getCandidateRoleContainerId();
            for (String candidateRoleId : candidateRoleContainerId) {

                if (candidateRoleId.equals(candidateRole.getId().toString())) {
                    String roleOid = candidateRole.getCandidateRoleRef().getOid();
                    PrismObject<RoleType> rolePrismObject = roleAnalysisService.getRoleTypeObject(
                            roleOid, task, result);
                    List<String> rolesOidInducements;
                    if (rolePrismObject == null) {
                        return;
                    }
                    rolesOidInducements = getRolesOidInducements(rolePrismObject);
                    List<String> rolesOidAssignment = getRolesOidAssignment(rolePrismObject.asObjectable());

                    Set<String> accessOidSet = new HashSet<>(rolesOidInducements);
                    accessOidSet.addAll(rolesOidAssignment);

                    ListMultimap<String, String> mappedMembers = roleAnalysisService.extractUserTypeMembers(new HashMap<>(),
                            null,
                            Collections.singleton(roleOid),
                            task,
                            result);

                    List<ObjectReferenceType> candidateMembers = candidateRole.getCandidateMembers();
                    Set<String> membersOidSet = new HashSet<>();
                    for (ObjectReferenceType candidateMember : candidateMembers) {
                        String oid = candidateMember.getOid();
                        if (oid != null) {
                            membersOidSet.add(oid);
                        }
                    }

                    membersOidSet.addAll(mappedMembers.get(roleOid));
                    double clusterMetric = accessOidSet.size() * membersOidSet.size();

                    DetectedPattern pattern = new DetectedPattern(
                            accessOidSet,
                            membersOidSet,
                            clusterMetric,
                            null);
                    pattern.setIdentifier(rolePrismObject.getName().getOrig());
                    pattern.setId(candidateRole.getId());

                    analysePattern.add(pattern);
                }
            }
        }
    }

    private void loadDetectedPattern(RoleAnalysisClusterType cluster) {
        List<RoleAnalysisDetectionPatternType> detectedPattern = cluster.getDetectedPattern();

        for (RoleAnalysisDetectionPatternType pattern : detectedPattern) {
            Long id = pattern.getId();
            if (getDetectedPatternContainerId().equals(id)) {
                analysePattern = Collections.singletonList(transformPattern(pattern));
            }
        }
    }

    private void loadMiningTableData(DisplayValueOption displayValueOption) {
        Task task = ((PageBase) getPage()).createSimpleTask(OP_PREPARE_OBJECTS);
        RoleAnalysisService roleAnalysisService = getPageBase().getRoleAnalysisService();
        RoleAnalysisClusterType cluster = getObjectDetailsModels().getObjectType();

        RoleAnalysisProcessModeType processMode;
        if (isRoleMode) {
            processMode = RoleAnalysisProcessModeType.ROLE;
        } else {
            processMode = RoleAnalysisProcessModeType.USER;
        }

        miningOperationChunk = roleAnalysisService.prepareMiningStructure(cluster, displayValueOption,
                processMode, result, task);

        RoleAnalysisDetectionOptionType detectionOption = cluster.getDetectionOption();

        RangeType frequencyRange = detectionOption.getFrequencyRange();

        double minFrequency = 0;
        double maxFrequency = 0;
        if (frequencyRange != null) {
            minFrequency = frequencyRange.getMin() / 100;
            maxFrequency = frequencyRange.getMax() / 100;
        }

        RoleAnalysisSortMode sortMode = displayValueOption.getSortMode();
        if (sortMode == null) {
            displayValueOption.setSortMode(RoleAnalysisSortMode.NONE);
            sortMode = RoleAnalysisSortMode.NONE;
        }

        List<MiningUserTypeChunk> users = miningOperationChunk.getMiningUserTypeChunks(sortMode);
        List<MiningRoleTypeChunk> roles = miningOperationChunk.getMiningRoleTypeChunks(sortMode);

        if (isOutlierDetection) {
            if (!isRoleMode) {
                roleAnalysisService.resolveOutliersZScore(roles, frequencyRange.getMin(), frequencyRange.getMax());
            } else {
                roleAnalysisService.resolveOutliersZScore(users, frequencyRange.getMin(), frequencyRange.getMax());
            }
        }
        if (analysePattern != null && !analysePattern.isEmpty()) {
            if (isRoleMode) {
                initRoleBasedDetectionPattern(getPageBase(), users, roles, analysePattern, minFrequency, maxFrequency, task, result);
            } else {
                initUserBasedDetectionPattern(getPageBase(), users, roles, analysePattern, minFrequency, maxFrequency, task, result);
            }
        }

    }

    private void loadMiningTable(WebMarkupContainer webMarkupContainer, List<DetectedPattern> analysePattern) {
        RoleAnalysisDetectionOptionType detectionOption = getObjectDetailsModels().getObjectType().getDetectionOption();
        if (detectionOption == null || detectionOption.getFrequencyRange() == null) {
            return;
        }

        if (isRoleMode) {
            RoleAnalysisRoleBasedTable boxedTablePanel = generateMiningRoleBasedTable(
                    analysePattern
            );
            boxedTablePanel.setOutputMarkupId(true);
            webMarkupContainer.add(boxedTablePanel);
        } else {
            RoleAnalysisUserBasedTable boxedTablePanel = generateMiningUserBasedTable(
                    analysePattern
            );
            boxedTablePanel.setOutputMarkupId(true);
            webMarkupContainer.add(boxedTablePanel);
        }

    }

    //TODO - check reset
    private void updateMiningTable(AjaxRequestTarget target, DisplayValueOption displayValueOption, boolean resetData) {
        RoleAnalysisDetectionOptionType detectionOption = getObjectDetailsModels().getObjectType().getDetectionOption();
        if (detectionOption == null || detectionOption.getFrequencyRange() == null) {
            return;
        }

        if (false) {

            List<MiningRoleTypeChunk> simpleMiningRoleTypeChunks = miningOperationChunk.getSimpleMiningRoleTypeChunks();

            List<MiningUserTypeChunk> simpleMiningUserTypeChunks = miningOperationChunk.getSimpleMiningUserTypeChunks();

            for (MiningRoleTypeChunk miningRoleTypeChunk : simpleMiningRoleTypeChunks) {
                miningRoleTypeChunk.setStatus(RoleAnalysisOperationMode.EXCLUDE);
            }
            for (MiningUserTypeChunk miningUserTypeChunk : simpleMiningUserTypeChunks) {
                miningUserTypeChunk.setStatus(RoleAnalysisOperationMode.EXCLUDE);
            }
        }

        if (resetData) {
            loadMiningTableData(displayValueOption);
        }

        if (isRoleMode) {
            RoleAnalysisRoleBasedTable boxedTablePanel = generateMiningRoleBasedTable(
                    analysePattern
            );
            boxedTablePanel.setOutputMarkupId(true);
            getMiningRoleBasedTable().replaceWith(boxedTablePanel);
            target.appendJavaScript(applyTableScaleScript());
            target.add(getMiningRoleBasedTable().setOutputMarkupId(true));

        } else {

            RoleAnalysisUserBasedTable boxedTablePanel = generateMiningUserBasedTable(
                    analysePattern
            );
            boxedTablePanel.setOutputMarkupId(true);
            getMiningUserBasedTable().replaceWith(boxedTablePanel);
            target.appendJavaScript(applyTableScaleScript());
            target.add(getMiningUserBasedTable().setOutputMarkupId(true));
        }

    }

    public RoleAnalysisUserBasedTable generateMiningUserBasedTable(List<DetectedPattern> selectedPattern) {

        return new RoleAnalysisUserBasedTable(ID_DATATABLE, miningOperationChunk,
                selectedPattern,
                displayValueOptionModel,
                getObjectWrapperObject()) {

            @Override
            public boolean isOutlierDetection() {
                return isOutlierDetection;
            }

            @Override
            protected @Nullable Set<RoleAnalysisCandidateRoleType> getCandidateRole() {
                return getRoleAnalysisCandidateRoleType();
            }

            @Override
            public void resetTable(AjaxRequestTarget target, @Nullable DisplayValueOption displayValueOption) {
                displayValueOptionModel.setObject(displayValueOption);
                updateMiningTable(target, displayValueOption, true);
            }

            @Override
            protected String getCompressStatus() {
                return displayValueOptionModel.getObject().getChunkMode().getValue();
            }

            @Override
            protected void onPerform(AjaxRequestTarget ajaxRequestTarget) {
                RoleAnalysisSortMode roleAnalysisSortMode = getMiningUserBasedTable().getRoleAnalysisSortMode();
                displayValueOptionModel.getObject().setSortMode(roleAnalysisSortMode);

                RoleAnalysisChunkMode chunkMode = displayValueOptionModel.getObject().getChunkMode();
                if (chunkMode.equals(RoleAnalysisChunkMode.COMPRESS)) {
                    displayValueOptionModel.getObject().setChunkMode(RoleAnalysisChunkMode.EXPAND);
                } else {
                    displayValueOptionModel.getObject().setChunkMode(RoleAnalysisChunkMode.COMPRESS);
                    displayValueOptionModel.getObject().setUserAnalysisUserDef(getObjectNameDef());
                    displayValueOptionModel.getObject().setRoleAnalysisRoleDef(getObjectNameDef());
                }

                updateMiningTable(ajaxRequestTarget, displayValueOptionModel.getObject(), true);
            }

            @Override
            protected void showDetectedPatternPanel(AjaxRequestTarget target) {
                RoleAnalysisClusterType cluster = getObjectDetailsModels().getObjectType();
                List<DetectedPattern> detectedPatternList = transformDefaultPattern(cluster);
                DetectedPatternPopupPanel detailsPanel = new DetectedPatternPopupPanel(((PageBase) getPage()).getMainPopupBodyId(),
                        Model.of("Patterns panel"), detectedPatternList);

                getPageBase().showMainPopup(detailsPanel, target);
            }

            @Override
            protected void showCandidateRolesPanel(AjaxRequestTarget target) {
                RoleAnalysisClusterType cluster = getObjectDetailsModels().getObjectType();

                Task task = getPageBase().createSimpleTask(OP_PREPARE_OBJECTS);
                List<RoleAnalysisCandidateRoleType> candidateRoles = cluster.getCandidateRoles();

                HashMap<String, RoleAnalysisCandidateRoleType> cacheCandidate = new HashMap<>();
                List<RoleType> roles = new ArrayList<>();
                for (RoleAnalysisCandidateRoleType candidateRoleType : candidateRoles) {
                    ObjectReferenceType candidateRoleRef = candidateRoleType.getCandidateRoleRef();
                    PrismObject<RoleType> role = getPageBase().getRoleAnalysisService()
                            .getRoleTypeObject(candidateRoleRef.getOid(), task, result);
                    if (Objects.nonNull(role)) {
                        cacheCandidate.put(candidateRoleRef.getOid(), candidateRoleType);
                        roles.add(role.asObjectable());
                    }
                }

                List<String> selectedCandidates = getCandidateRoleContainerId();
                CandidateRolesPopupPanel detailsPanel = new CandidateRolesPopupPanel(((PageBase) getPage()).getMainPopupBodyId(),
                        Model.of("Analyzed members details panel"), cluster, cacheCandidate, roles, selectedCandidates);

                getPageBase().showMainPopup(detailsPanel, target);

            }
        };
    }

    public RoleAnalysisRoleBasedTable generateMiningRoleBasedTable(List<DetectedPattern> intersection) {

        return new RoleAnalysisRoleBasedTable(ID_DATATABLE,
                miningOperationChunk,
                intersection,
                displayValueOptionModel, getObjectWrapperObject()) {

            @Override
            public boolean isOutlierDetection() {
                return isOutlierDetection;
            }

            @Override
            protected @Nullable Set<RoleAnalysisCandidateRoleType> getCandidateRole() {
                return getRoleAnalysisCandidateRoleType();
            }

            @Override
            public void resetTable(AjaxRequestTarget target, DisplayValueOption displayValueOption) {
                displayValueOptionModel.setObject(displayValueOption);
                updateMiningTable(target, displayValueOption, true);
            }

            @Override
            protected String getCompressStatus() {
                return displayValueOptionModel.getObject().getChunkMode().getValue();
            }

            @Override
            protected void onPerform(AjaxRequestTarget ajaxRequestTarget) {
                RoleAnalysisSortMode roleAnalysisSortMode = getMiningRoleBasedTable().getRoleAnalysisSortMode();
                displayValueOptionModel.getObject().setSortMode(roleAnalysisSortMode);

                RoleAnalysisChunkMode chunkMode = displayValueOptionModel.getObject().getChunkMode();
                if (chunkMode.equals(RoleAnalysisChunkMode.COMPRESS)) {
                    displayValueOptionModel.getObject().setChunkMode(RoleAnalysisChunkMode.EXPAND);
                } else {
                    displayValueOptionModel.getObject().setChunkMode(RoleAnalysisChunkMode.COMPRESS);
                    displayValueOptionModel.getObject().setUserAnalysisUserDef(getObjectNameDef());
                    displayValueOptionModel.getObject().setRoleAnalysisRoleDef(getObjectNameDef());
                }

                updateMiningTable(ajaxRequestTarget, displayValueOptionModel.getObject(), true);
            }

            @Override
            protected void showDetectedPatternPanel(AjaxRequestTarget target) {
                RoleAnalysisClusterType cluster = getObjectDetailsModels().getObjectType();
                List<DetectedPattern> detectedPatternList = transformDefaultPattern(cluster);
                DetectedPatternPopupPanel detailsPanel = new DetectedPatternPopupPanel(((PageBase) getPage()).getMainPopupBodyId(),
                        Model.of("Patterns panel"), detectedPatternList);

                getPageBase().showMainPopup(detailsPanel, target);
            }

            @Override
            protected void showCandidateRolesPanel(AjaxRequestTarget target) {
                RoleAnalysisClusterType cluster = getObjectDetailsModels().getObjectType();

                Task task = getPageBase().createSimpleTask(OP_PREPARE_OBJECTS);
                List<RoleAnalysisCandidateRoleType> candidateRoles = cluster.getCandidateRoles();

                HashMap<String, RoleAnalysisCandidateRoleType> cacheCandidate = new HashMap<>();
                List<RoleType> roles = new ArrayList<>();
                for (RoleAnalysisCandidateRoleType candidateRoleType : candidateRoles) {
                    ObjectReferenceType candidateRoleRef = candidateRoleType.getCandidateRoleRef();
                    PrismObject<RoleType> role = getPageBase().getRoleAnalysisService()
                            .getRoleTypeObject(candidateRoleRef.getOid(), task, result);
                    if (Objects.nonNull(role)) {
                        cacheCandidate.put(candidateRoleRef.getOid(), candidateRoleType);
                        roles.add(role.asObjectable());
                    }
                }
                List<String> selectedCandidates = getCandidateRoleContainerId();
                CandidateRolesPopupPanel detailsPanel = new CandidateRolesPopupPanel(((PageBase) getPage()).getMainPopupBodyId(),
                        Model.of("Analyzed members details panel"), cluster, cacheCandidate, roles, selectedCandidates);

                getPageBase().showMainPopup(detailsPanel, target);

            }
        };
    }

    private @Nullable Set<RoleAnalysisCandidateRoleType> getRoleAnalysisCandidateRoleType() {
        List<String> candidateRoleContainerId = getCandidateRoleContainerId();

        Set<RoleAnalysisCandidateRoleType> candidateRoleTypes = new HashSet<>();
        if (candidateRoleContainerId != null && !candidateRoleContainerId.isEmpty()) {
            RoleAnalysisClusterType cluster = getObjectWrapperObject().asObjectable();
            List<RoleAnalysisCandidateRoleType> candidateRoles = cluster.getCandidateRoles();

            for (RoleAnalysisCandidateRoleType candidateRole : candidateRoles) {
                if (candidateRoleContainerId.contains(candidateRole.getId().toString())) {
                    candidateRoleTypes.add(candidateRole);
                }
            }
            if (!candidateRoleTypes.isEmpty()) {
                return candidateRoleTypes;
            }
            return null;
        }
        return null;
    }

    protected RoleAnalysisRoleBasedTable getMiningRoleBasedTable() {
        return (RoleAnalysisRoleBasedTable) get(((PageBase) getPage()).createComponentPath(ID_MAIN_PANEL, ID_DATATABLE));
    }

    protected RoleAnalysisUserBasedTable getMiningUserBasedTable() {
        return (RoleAnalysisUserBasedTable) get(((PageBase) getPage()).createComponentPath(ID_MAIN_PANEL, ID_DATATABLE));
    }

    private DisplayValueOption getDisplayValueRoleTableConfigurations() {
        LoadableDetachableModel<DisplayValueOption> displayModel = getMiningRoleBasedTable().getDisplayValueOptionModel();
        if (displayModel != null) {
            return displayModel.getObject();
        }
        return null;
    }

    private DisplayValueOption getDisplayValueUserTableConfigurations() {
        LoadableDetachableModel<DisplayValueOption> displayModel = getMiningUserBasedTable().getDisplayValueOptionModel();
        if (displayModel != null) {
            return displayModel.getObject();
        }
        return null;
    }

    public PageBase getPageBase() {
        return ((PageBase) getPage());
    }
}
