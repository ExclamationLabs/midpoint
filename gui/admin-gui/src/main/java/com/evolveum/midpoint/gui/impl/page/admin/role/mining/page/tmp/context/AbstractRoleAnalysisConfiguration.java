/*
 * Copyright (C) 2010-2024 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.gui.impl.page.admin.role.mining.page.tmp.context;

import com.evolveum.midpoint.gui.api.prism.wrapper.ItemVisibilityHandler;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import com.evolveum.prism.xml.ns._public.query_3.SearchFilterType;

public abstract class AbstractRoleAnalysisConfiguration implements RoleAnalysisConfigurator {

    private RoleAnalysisProcessModeType processMode;
    private AbstractAnalysisSessionOptionType analysisSessionOption;
    private RoleAnalysisDetectionOptionType detectionOption;
    private ItemVisibilityHandler visibilityHandler;
    private RoleAnalysisSessionType object;

    public AbstractRoleAnalysisConfiguration(RoleAnalysisSessionType objectWrapper) {
        this.object = objectWrapper;
        RoleAnalysisOptionType analysisOption = object.getAnalysisOption();
        processMode = analysisOption.getProcessMode();

        if (processMode != null) {
            if (processMode.equals(RoleAnalysisProcessModeType.ROLE)) {
                this.analysisSessionOption = object.getRoleModeOptions();
            } else {
                this.analysisSessionOption = object.getUserModeOptions();
            }
        }

        this.detectionOption = object.getDefaultDetectionOption();
    }

    public RoleAnalysisProcessModeType getProcessMode() {
        return processMode;
    }

    public void setProcessMode(RoleAnalysisProcessModeType processMode) {
        this.processMode = processMode;
    }

    public AbstractAnalysisSessionOptionType getAnalysisSessionOption() {
        return analysisSessionOption;
    }

    public RoleAnalysisDetectionOptionType getDetectionOption() {
        return detectionOption;
    }

    public ItemVisibilityHandler getVisibilityHandler() {
        return visibilityHandler;
    }

    public void setVisibilityHandler(ItemVisibilityHandler visibilityHandler) {
        this.visibilityHandler = visibilityHandler;
    }

    public AnalysisAttributeSettingType getDefaultAnalysisAttributes() {
        AnalysisAttributeSettingType value = new AnalysisAttributeSettingType();
        value.getPath().add(UserType.F_TITLE.toBean());

        AnalysisAttributeRuleType rule = new AnalysisAttributeRuleType();
        rule.setTargetType(ArchetypeType.COMPLEX_TYPE);
        value.getAssignmentRule().add(rule);
        //TODO load from system config
//        List<AnalysisAttributeRuleType> analysisAttributeRule = new ArrayList<>();
//        RoleAnalysisAttributeDef title = RoleAnalysisAttributeDefUtils.getTitle();
//        RoleAnalysisAttributeDef archetypeRef = RoleAnalysisAttributeDefUtils.getArchetypeRef();
//        RoleAnalysisAttributeDef locality = RoleAnalysisAttributeDefUtils.getLocality();
//        RoleAnalysisAttributeDef orgAssignment = RoleAnalysisAttributeDefUtils.getOrgAssignment();

//        analysisAttributeRule
//                .add(new AnalysisAttributeRuleType()
//                        .attributeIdentifier(title.getDisplayValue())
//                        .propertyType(UserType.COMPLEX_TYPE));
//        analysisAttributeRule
//                .add(new AnalysisAttributeRuleType()
//                        .attributeIdentifier(archetypeRef.getDisplayValue())
//                        .propertyType(UserType.COMPLEX_TYPE));
//        analysisAttributeRule
//                .add(new AnalysisAttributeRuleType()
//                        .attributeIdentifier(locality.getDisplayValue())
//                        .propertyType(UserType.COMPLEX_TYPE));
//        analysisAttributeRule
//                .add(new AnalysisAttributeRuleType()
//                        .attributeIdentifier(orgAssignment.getDisplayValue())
//                        .propertyType(UserType.COMPLEX_TYPE));
//        analysisAttributeRule
//                .add(new AnalysisAttributeRuleType()
//                        .attributeIdentifier(archetypeRef.getDisplayValue())
//                        .propertyType(RoleType.COMPLEX_TYPE));
//
//        value.getAnalysisAttributeRule().addAll(analysisAttributeRule);

        return value;
    }

    protected AbstractAnalysisSessionOptionType getPrimaryOptionContainerFormModel() {
        if (RoleAnalysisProcessModeType.ROLE.equals(getProcessMode())) {
            RoleAnalysisSessionOptionType roleModeOption = object.getRoleModeOptions();
            if (roleModeOption == null) {
                roleModeOption = new RoleAnalysisSessionOptionType();
                object.setRoleModeOptions(roleModeOption);
            }
            return roleModeOption;
        }

        UserAnalysisSessionOptionType userModeOption = object.getUserModeOptions();
        if (userModeOption == null) {
            userModeOption = new UserAnalysisSessionOptionType();
            object.setUserModeOptions(userModeOption);
        }
        return userModeOption;

//            return PrismContainerWrapperModel.fromContainerWrapper(objectWrapperModel,
//                    ItemPath.create(RoleAnalysisSessionType.F_ROLE_MODE_OPTIONS));
//        }
//        return PrismContainerWrapperModel.fromContainerWrapper(objectWrapperModel,
//                ItemPath.create(RoleAnalysisSessionType.F_USER_MODE_OPTIONS));
    }

//    protected IModel<? extends PrismContainerWrapper<RoleAnalysisDetectionOptionType>> getDetectionOptionFormModel(
//            @NotNull LoadableModel<PrismObjectWrapper<RoleAnalysisSessionType>> objectWrapperModel
//    ) {
//        return PrismContainerWrapperModel.fromContainerWrapper(objectWrapperModel,
//                ItemPath.create(RoleAnalysisSessionType.F_DEFAULT_DETECTION_OPTION));
//    }
//
//    private void setNewPrimaryOptionValue(@NotNull PrismContainerValueWrapper<AbstractAnalysisSessionOptionType> sessionType,
//            ItemName itemName, Object realValue) throws SchemaException {
//        sessionType.findProperty(itemName).getValue().setRealValue(realValue);
//    }
//
//    private void setNewDetectionOptionValue(@NotNull PrismContainerValueWrapper<RoleAnalysisDetectionOptionType> sessionType,
//            ItemName itemName, Object realValue) throws SchemaException {
//
//        if (sessionType.findProperty(itemName) != null) {
//            sessionType.findProperty(itemName).getValue().setRealValue(realValue);
//        } else {
//            LOGGER.warn("Property not found: " + itemName);
//        }
//        return object.getUserModeOptions();
//    }

    public void updatePrimaryOptions(
            SearchFilterType filter,
            boolean isIndirect,
            RangeType propertiesRange,
            AnalysisAttributeSettingType analysisAttributeSetting,
            ClusteringAttributeSettingType clusteringAttributeSetting,
            Double similarityThreshold,
            Integer minMembersCount,
            Integer minPropertiesOverlap,
            boolean detailedAnalysis) {

//        try {
            AbstractAnalysisSessionOptionType sessionOptions = getPrimaryOptionContainerFormModel();
            sessionOptions.isIndirect(isIndirect)
                    .propertiesRange(propertiesRange)
                    .userAnalysisAttributeSetting(analysisAttributeSetting)
                    .clusteringAttributeSetting(clusteringAttributeSetting)
                    .similarityThreshold(similarityThreshold)
                    .minMembersCount(minMembersCount)
                    .minPropertiesOverlap(minPropertiesOverlap)
                    .detailedAnalysis(detailedAnalysis);
            if (filter != null) {
                sessionOptions.query(filter);
            }
//            setNewPrimaryOptionValue(primaryOptions, AbstractAnalysisSessionOptionType.F_IS_INDIRECT, isIndirect);
//            setNewPrimaryOptionValue(primaryOptions, AbstractAnalysisSessionOptionType.F_PROPERTIES_RANGE, propertiesRange);
//            setNewPrimaryOptionValue(primaryOptions, AbstractAnalysisSessionOptionType.F_USER_ANALYSIS_ATTRIBUTE_SETTING, analysisAttributeSetting);
//            setNewPrimaryOptionValue(primaryOptions, AbstractAnalysisSessionOptionType.F_CLUSTERING_ATTRIBUTE_SETTING, clusteringAttributeSetting);
//            setNewPrimaryOptionValue(primaryOptions, AbstractAnalysisSessionOptionType.F_SIMILARITY_THRESHOLD, similarityThreshold);
//            setNewPrimaryOptionValue(primaryOptions, AbstractAnalysisSessionOptionType.F_MIN_MEMBERS_COUNT, minMembersCount);
//            setNewPrimaryOptionValue(primaryOptions, AbstractAnalysisSessionOptionType.F_MIN_PROPERTIES_OVERLAP, minPropertiesOverlap);
//            setNewPrimaryOptionValue(primaryOptions, AbstractAnalysisSessionOptionType.F_QUERY, query);
//            setNewPrimaryOptionValue(primaryOptions, AbstractAnalysisSessionOptionType.F_DETAILED_ANALYSIS, detailedAnalysis);
//        } catch (SchemaException e) {
//            throw new RuntimeException(e);
//        }
    }

    public void updateDetectionOptions(
            Integer minRolesOccupancy,
            Integer minUserOccupancy,
            Double sensitivity,
            RangeType frequencyRange,
            RoleAnalysisDetectionProcessType detectionProcessMode) {

            RoleAnalysisDetectionOptionType primaryOptions = object.getDefaultDetectionOption();
            if (primaryOptions == null) {
                primaryOptions = new RoleAnalysisDetectionOptionType();
                object.setDefaultDetectionOption(primaryOptions);
            }
            primaryOptions.minRolesOccupancy(minRolesOccupancy)
                    .minUserOccupancy(minUserOccupancy)
                    .sensitivity(sensitivity)
                    .frequencyRange(frequencyRange)
                    .detectionProcessMode(detectionProcessMode);
    }
}
