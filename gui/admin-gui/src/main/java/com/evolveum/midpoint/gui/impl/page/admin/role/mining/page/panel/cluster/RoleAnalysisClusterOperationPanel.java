/*
 * Copyright (C) 2010-2023 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.gui.impl.page.admin.role.mining.page.panel.cluster;

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
import org.apache.wicket.util.string.StringValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.evolveum.midpoint.common.mining.objects.chunk.*;
import com.evolveum.midpoint.common.mining.objects.detection.DetectedPattern;
import com.evolveum.midpoint.common.mining.utils.values.RoleAnalysisChunkMode;
import com.evolveum.midpoint.common.mining.utils.values.RoleAnalysisSortMode;
import com.evolveum.midpoint.gui.api.GuiStyleConstants;
import com.evolveum.midpoint.gui.api.page.PageBase;
import com.evolveum.midpoint.gui.impl.page.admin.AbstractObjectMainPanel;
import com.evolveum.midpoint.gui.impl.page.admin.ObjectDetailsModels;
import com.evolveum.midpoint.gui.impl.page.admin.role.mining.tables.operation.RoleAnalysisMatrixTable;
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
    private static final String DOT_CLASS = RoleAnalysisClusterOperationPanel.class.getName() + ".";
    private static final String OP_PREPARE_OBJECTS = DOT_CLASS + "prepareObjects";


    private List<DetectedPattern> analysePattern = null;
    private MiningOperationChunk miningOperationChunk;
    private boolean isRoleMode;

    private LoadableDetachableModel<DisplayValueOption> displayValueOptionModel;



    public static final String PARAM_CANDIDATE_ROLE_ID = "candidateRoleId";
    public static final String PARAM_DETECTED_PATER_ID = "detectedPatternId";
    public static final String PARAM_TABLE_SETTING = "tableSetting";
    boolean isOutlierDetection = false;
    Set<String> markedUsers = new HashSet<>();

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
        PageBase pageBase = getPageBase();
        RoleAnalysisService roleAnalysisService = pageBase.getRoleAnalysisService();
        Task task = pageBase.createSimpleTask(OP_PREPARE_OBJECTS);
        OperationResult result = task.getResult();

        if (getCandidateRoleContainerId() != null) {
            analysePattersForCandidateRole(task, result);
        } else if (getDetectedPatternContainerId() != null) {
            loadDetectedPattern();
        }

        RoleAnalysisProcessModeType mode;
        RoleAnalysisClusterType cluster = getObjectDetailsModels().getObjectType();
        PrismObject<RoleAnalysisSessionType> getParent = roleAnalysisService.
                getSessionTypeObject(cluster.getRoleAnalysisSessionRef().getOid(), task, result);
        if (getParent != null) {
            RoleAnalysisSessionType session = getParent.asObjectable();
            RoleAnalysisOptionType analysisOption = session.getAnalysisOption();
            RoleAnalysisCategoryType analysisCategory = analysisOption.getAnalysisCategory();
            if (RoleAnalysisCategoryType.OUTLIERS.equals(analysisCategory)) {
                isOutlierDetection = true;
            }
            mode = analysisOption.getProcessMode();
            isRoleMode = mode.equals(RoleAnalysisProcessModeType.ROLE);
        }

        if (isOutlierDetection) {
            markedUsers = new HashSet<>();
            List<RoleAnalysisOutlierType> searchResultList = roleAnalysisService.findClusterOutliers(
                    cluster, task, result);
            if (searchResultList != null && !searchResultList.isEmpty()) {
                for (RoleAnalysisOutlierType outlier : searchResultList) {
                    ObjectReferenceType targetObjectRef = outlier.getTargetObjectRef();
                    if (targetObjectRef != null && targetObjectRef.getOid() != null) {
                        markedUsers.add(targetObjectRef.getOid());
                    }
                }
            }
        }

        loadDisplayValueOptionModel();
        loadMiningTableData(displayValueOptionModel.getObject());

        WebMarkupContainer webMarkupContainer = new WebMarkupContainer(ID_MAIN_PANEL);
        webMarkupContainer.setOutputMarkupId(true);

        loadMiningTable(webMarkupContainer);

        add(webMarkupContainer);

    }

    private void loadDisplayValueOptionModel() {
        displayValueOptionModel = new LoadableDetachableModel<>() {
            @Override
            protected @NotNull DisplayValueOption load() {

                RoleAnalysisClusterType cluster = getObjectDetailsModels().getObjectType();
                AnalysisClusterStatisticType clusterStatistics = cluster.getClusterStatistics();

                DisplayValueOption displayValueOption = new DisplayValueOption();
                displayValueOption.setChunkMode(RoleAnalysisChunkMode.COMPRESS);

                displayValueOption.setProcessMode(isRoleMode ? RoleAnalysisProcessModeType.ROLE : RoleAnalysisProcessModeType.USER);

                Integer parameterTableSetting = getParameterTableSetting();
                if (parameterTableSetting != null && parameterTableSetting == 1) {
                    displayValueOption.setFullPage(true);
                }

                Integer rolesCount = clusterStatistics.getRolesCount();
                Integer usersCount = clusterStatistics.getUsersCount();

                if (rolesCount == null || usersCount == null) {
                    displayValueOption.setSortMode(RoleAnalysisSortMode.NONE);
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
                        displayValueOption.setSortMode(RoleAnalysisSortMode.JACCARD);
                    } else {
                        displayValueOption.setSortMode(RoleAnalysisSortMode.FREQUENCY);
                    }

                    if (rolesCount > maxRoles && usersCount > maxUsers) {
                        displayValueOption.setChunkMode(RoleAnalysisChunkMode.COMPRESS);
                    } else if (rolesCount > maxRoles) {
                        displayValueOption.setChunkMode(RoleAnalysisChunkMode.EXPAND_USER);
                    } else if (usersCount > maxUsers) {
                        displayValueOption.setChunkMode(RoleAnalysisChunkMode.EXPAND_ROLE);
                    } else {
                        displayValueOption.setChunkMode(RoleAnalysisChunkMode.EXPAND);
                    }
                }

                return displayValueOption;
            }
        };
    }

    private void analysePattersForCandidateRole(Task task, OperationResult result) {
        analysePattern = new ArrayList<>();
        RoleAnalysisClusterType cluster = getObjectDetailsModels().getObjectType();
        RoleAnalysisService roleAnalysisService = getPageBase().getRoleAnalysisService();

        List<RoleAnalysisCandidateRoleType> candidateRoles = cluster.getCandidateRoles();

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
                    double clusterMetric = (accessOidSet.size() * membersOidSet.size()) - membersOidSet.size();

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

    private @NotNull List<DetectedPattern> loadAllCandidateRoles(@NotNull RoleAnalysisClusterType cluster) {
        List<RoleAnalysisCandidateRoleType> clusterCandidateRoles = cluster.getCandidateRoles();
        List<DetectedPattern> candidateRoles = new ArrayList<>();
        Task task = getPageBase().createSimpleTask(OP_PREPARE_OBJECTS); //TODO task name?
        OperationResult result = task.getResult();
        for (RoleAnalysisCandidateRoleType candidateRole : clusterCandidateRoles) {

            RoleAnalysisOperationStatus operationStatus = candidateRole.getOperationStatus();
            boolean isMigrated = operationStatus != null
                    && operationStatus.getOperationChannel() != null
                    && operationStatus.getOperationChannel().equals(RoleAnalysisOperation.MIGRATION);

            if (isMigrated) {
                continue;
            }


            String roleOid = candidateRole.getCandidateRoleRef().getOid();
            //TODO does it make sense to create subresult for each iteration?
            PrismObject<RoleType> rolePrismObject = getPageBase().getRoleAnalysisService().getRoleTypeObject(
                    roleOid, task, result);
            List<String> rolesOidInducements;
            if (rolePrismObject == null) {
                return new ArrayList<>();
            }

            rolesOidInducements = getRolesOidInducements(rolePrismObject);
            List<String> rolesOidAssignment = getRolesOidAssignment(rolePrismObject.asObjectable());

            Set<String> accessOidSet = new HashSet<>(rolesOidInducements);
            accessOidSet.addAll(rolesOidAssignment);

            ListMultimap<String, String> mappedMembers = getPageBase().getRoleAnalysisService().extractUserTypeMembers(new HashMap<>(),
                    null,
                    Collections.singleton(roleOid),
                    getPageBase().createSimpleTask(OP_PREPARE_OBJECTS),
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
            double clusterMetric = (accessOidSet.size() * membersOidSet.size()) - membersOidSet.size();

            DetectedPattern pattern = new DetectedPattern(
                    accessOidSet,
                    membersOidSet,
                    clusterMetric,
                    null,
                    roleOid);
            pattern.setIdentifier(rolePrismObject.getName().getOrig());
            pattern.setId(candidateRole.getId());
            pattern.setClusterRef(new ObjectReferenceType().oid(cluster.getOid()).type(RoleAnalysisClusterType.COMPLEX_TYPE));

            candidateRoles.add(pattern);
        }
        return candidateRoles;
    }

    private void loadDetectedPattern() {
        RoleAnalysisClusterType cluster = getObjectDetailsModels().getObjectType();
        List<RoleAnalysisDetectionPatternType> detectedPattern = cluster.getDetectedPattern();

        for (RoleAnalysisDetectionPatternType pattern : detectedPattern) {
            Long id = pattern.getId();
            if (getDetectedPatternContainerId().equals(id)) {
                analysePattern = Collections.singletonList(transformPattern(pattern));
            }
        }
    }

    private void loadMiningTableData(DisplayValueOption displayValueOption) {
        Task task = ((PageBase) getPage()).createSimpleTask(OP_PREPARE_OBJECTS); //TODO task name?
        OperationResult result = task.getResult();
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
        Double sensitivity = detectionOption.getSensitivity();

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

        if (isOutlierDetection && frequencyRange != null) {
            if (!isRoleMode) {
                roleAnalysisService.resolveOutliersZScore(roles, frequencyRange, sensitivity);
            } else {
                roleAnalysisService.resolveOutliersZScore(users, frequencyRange, sensitivity);
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

    private <B extends MiningBaseTypeChunk, A extends  MiningBaseTypeChunk> void loadMiningTable(WebMarkupContainer webMarkupContainer) {
        RoleAnalysisDetectionOptionType detectionOption = getObjectDetailsModels().getObjectType().getDetectionOption();
        if (detectionOption == null || detectionOption.getFrequencyRange() == null) {
            return;
        }

        RoleAnalysisMatrixTable<B, A> boxedTablePanel = generateMiningTable();
        boxedTablePanel.setOutputMarkupId(true);
        webMarkupContainer.add(boxedTablePanel);

    }

    //TODO - check reset
    private <B extends MiningBaseTypeChunk, A extends MiningBaseTypeChunk> void updateMiningTable(AjaxRequestTarget target, DisplayValueOption displayValueOption) {
        RoleAnalysisDetectionOptionType detectionOption = getObjectDetailsModels().getObjectType().getDetectionOption();
        if (detectionOption == null || detectionOption.getFrequencyRange() == null) {
            return;
        }

        loadMiningTableData(displayValueOption);

        RoleAnalysisMatrixTable<B, A> boxedTablePanel = generateMiningTable();
        boxedTablePanel.setOutputMarkupId(true);
        target.appendJavaScript(applyTableScaleScript());
        target.add(boxedTablePanel);

    }

    public <B extends MiningBaseTypeChunk, A extends MiningBaseTypeChunk> RoleAnalysisMatrixTable<B, A> generateMiningTable() {

        return new RoleAnalysisMatrixTable<>(
                ID_DATATABLE,
                miningOperationChunk,
                analysePattern,
                displayValueOptionModel,
                getObjectWrapperObject(),
                isRoleMode) {

            @Override
            protected Set<String> getMarkMemberObjects() {
                if (isOutlierDetection()) {
                    return markedUsers;
                }

                return null;
            }

            @Override
            public List<DetectedPattern> getClusterPatterns() {
                RoleAnalysisClusterType cluster = RoleAnalysisClusterOperationPanel.this.getObjectDetailsModels().getObjectType();
                return transformDefaultPattern(cluster);
            }

            @Override
            public List<DetectedPattern> getClusterCandidateRoles() {
                RoleAnalysisClusterType cluster = RoleAnalysisClusterOperationPanel.this.getObjectDetailsModels().getObjectType();
                return loadAllCandidateRoles(cluster);
            }

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
                updateMiningTable(target, displayValueOption);
            }

            @Override
            protected String getCompressStatus(@NotNull LoadableDetachableModel<DisplayValueOption> displayValueOptionModel) {
                return RoleAnalysisClusterOperationPanel.this.displayValueOptionModel.getObject().getChunkMode().getValue();
            }

            @Override
            protected void onPerform(AjaxRequestTarget ajaxRequestTarget) {
                RoleAnalysisSortMode roleAnalysisSortMode = getRoleAnalysisSortMode(displayValueOptionModel);

                DisplayValueOption options = displayValueOptionModel.getObject();

                options.setSortMode(roleAnalysisSortMode);

                RoleAnalysisChunkMode chunkMode = displayValueOptionModel.getObject().getChunkMode();
                if (chunkMode.equals(RoleAnalysisChunkMode.COMPRESS)) {
                    options.setChunkMode(RoleAnalysisChunkMode.EXPAND);
                } else {
                    options.setChunkMode(RoleAnalysisChunkMode.COMPRESS);
                    options.setUserAnalysisUserDef(getObjectNameDef());
                    options.setRoleAnalysisRoleDef(getObjectNameDef());
                }

                updateMiningTable(ajaxRequestTarget, options);
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
}
