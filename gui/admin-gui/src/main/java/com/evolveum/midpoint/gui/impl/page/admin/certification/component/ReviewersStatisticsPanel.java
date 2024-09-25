/*
 * Copyright (c) 2024 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.gui.impl.page.admin.certification.component;

import com.evolveum.midpoint.gui.api.component.BasePanel;
import com.evolveum.midpoint.gui.api.component.Toggle;
import com.evolveum.midpoint.gui.api.component.TogglePanel;
import com.evolveum.midpoint.gui.api.component.form.SwitchBoxPanel;
import com.evolveum.midpoint.gui.api.model.LoadableModel;
import com.evolveum.midpoint.gui.api.util.GuiDisplayTypeUtil;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.gui.api.util.WebModelServiceUtils;
import com.evolveum.midpoint.gui.impl.component.tile.TileTablePanel;
import com.evolveum.midpoint.gui.impl.component.tile.ViewToggle;
import com.evolveum.midpoint.gui.impl.page.admin.certification.CertificationDetailsModel;
import com.evolveum.midpoint.gui.impl.page.admin.certification.helpers.CertMiscUtil;
import com.evolveum.midpoint.prism.PrismConstants;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.schema.GetOperationOptions;
import com.evolveum.midpoint.schema.SelectorOptions;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.CertCampaignTypeUtil;
import com.evolveum.midpoint.security.api.MidPointPrincipal;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.web.component.util.VisibleBehaviour;
import com.evolveum.midpoint.web.component.util.VisibleEnableBehaviour;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import com.evolveum.wicket.chartjs.ChartConfiguration;
import com.evolveum.wicket.chartjs.ChartJsPanel;
import com.evolveum.wicket.chartjs.DoughnutChartConfiguration;

import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.markup.ComponentTag;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.request.resource.IResource;

import java.io.Serial;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.evolveum.midpoint.util.MiscUtil.or0;

public class ReviewersStatisticsPanel extends BasePanel {

    @Serial private static final long serialVersionUID = 1L;

    private static final String DOT_CLASS = ReviewersStatisticsPanel.class.getName() + ".";
    private static final String OPERATION_LOAD_CERT_ITEMS = DOT_CLASS + "loadCertItems";
    private static final String OPERATION_REVIEWER = DOT_CLASS + "loadReviewer";

    private static final String ID_REVIEWERS = "reviewers";

    //todo?
    private static final int MAX_REVIEWERS = 5;
    private int realReviewersCount;
    private CertificationDetailsModel model;

    //as a default, sorting of the reviewers is done by the percentage of not decided items
    //can be switched to the number of not decided items
    private IModel<Boolean> percentageSortingModel = Model.of(true);

    public ReviewersStatisticsPanel(String id, CertificationDetailsModel model) {
        super(id);
        this.model = model;
    }

    @Override
    protected void onInitialize() {
        super.onInitialize();

        setOutputMarkupId(true);
        add(initReviewersPanel(ID_REVIEWERS, true));
    }

    private StatisticListBoxPanel<ObjectReferenceType> initReviewersPanel(String id, boolean allowViewAll) {
        LoadableDetachableModel<List<StatisticBoxDto<ObjectReferenceType>>> reviewersModel = getReviewersModel(allowViewAll);
        return new StatisticListBoxPanel<>(id,
                getReviewersPanelDisplayModel(reviewersModel.getObject().size()), reviewersModel) {
            @Serial private static final long serialVersionUID = 1L;

            @Override
            protected boolean isViewAllAllowed() {
                return reviewersCountExceedsLimit() && allowViewAll;
            }

            @Override
            protected void viewAllActionPerformed(AjaxRequestTarget target) {
                showAllReviewersPerformed(target);
            }

            @Override
            protected Component createRightSideBoxComponent(String id, StatisticBoxDto<ObjectReferenceType> statisticObject) {
                ObjectReferenceType reviewerRef = statisticObject.getStatisticObject();
                DoughnutChartConfiguration chartConfig = getReviewerProgressChartConfig(reviewerRef);

                ChartJsPanel<ChartConfiguration> chartPanel = new ChartJsPanel<>(id, Model.of(chartConfig)) {

                    @Serial private static final long serialVersionUID = 1L;

                    @Override
                    protected void onComponentTag(ComponentTag tag) {
                        tag.setName("canvas");
                        super.onComponentTag(tag);
                    }
                };
                chartPanel.setOutputMarkupId(true);
                return chartPanel;
            }

            @Override
            protected boolean isLabelClickable() {
                return true;
            }

            @Override
            protected Component createRightSideHeaderComponent(String id) {
                IModel<List<Toggle<Boolean>>> items = new LoadableModel<>(false) {

                    @Override
                    protected List<Toggle<Boolean>> load() {

                        List<Toggle<Boolean>> list = new ArrayList<>();

                        Toggle<Boolean> percentage = new Toggle<>("fa fa-solid fa-percent", "",
                                "ReviewersStatisticsPanel.toggle.sortByPercentage");
                        percentage.setActive(Boolean.TRUE.equals(percentageSortingModel.getObject()));
                        percentage.setValue(true);
                        list.add(percentage);

                        Toggle<Boolean> countable = new Toggle<>("fa fa-solid fa-arrow-down-9-1", "",
                                "ReviewersStatisticsPanel.toggle.sortByCount");
                        countable.setActive(Boolean.FALSE.equals(percentageSortingModel.getObject()));
                        countable.setValue(false);
                        list.add(countable);

                        return list;
                    }
                };
                return new TogglePanel<>(id, items) {

                    @Serial private static final long serialVersionUID = 1L;

                    @Override
                    protected void itemSelected(AjaxRequestTarget target, IModel<Toggle<Boolean>> item) {
                        super.itemSelected(target, item);
                        percentageSortingModel.setObject(item.getObject().getValue());
//                        reviewersModel.detach();
                        target.add(ReviewersStatisticsPanel.this);
                    }
                };
            }
        };
    }

    private DoughnutChartConfiguration getReviewerProgressChartConfig(ObjectReferenceType reviewerRef) {
        PrismObject<FocusType> reviewer = WebModelServiceUtils.loadObject(reviewerRef, getPageBase());
        if (reviewer == null) {
            return null;
        }
        AccessCertificationCampaignType campaign = model.getObjectType();
        MidPointPrincipal principal = MidPointPrincipal.create(reviewer.asObjectable());
        return CertMiscUtil.createDoughnutChartConfigForCampaigns(
                Collections.singletonList(campaign.getOid()), principal, getPageBase());
    }

    private LoadableDetachableModel<List<StatisticBoxDto<ObjectReferenceType>>> getReviewersModel(boolean restricted) {
        return new LoadableDetachableModel<>() {

            @Serial private static final long serialVersionUID = 1L;

            @Override
            protected List<StatisticBoxDto<ObjectReferenceType>> load() {
                List<StatisticBoxDto<ObjectReferenceType>> list = new ArrayList<>();
                AccessCertificationCampaignType campaign = model.getObjectType();
                List<ObjectReferenceType> reviewers = CertMiscUtil.loadCampaignReviewers(campaign.getOid(),
                        ReviewersStatisticsPanel.this.getPageBase());
                reviewers = reviewers.stream().sorted((r1, r2) -> {
                    float r1ItemsPercent = getNotDecidedItems(r1);
                    float r2ItemsPercent = getNotDecidedItems(r2);
                    return Float.compare(r2ItemsPercent, r1ItemsPercent);
                }).toList();
                if (restricted) {
                    realReviewersCount = reviewers.size();
                    reviewers.stream().limit(MAX_REVIEWERS).forEach(r -> list.add(createReviewerStatisticBoxDto(r)));
                } else {
                    reviewers.forEach(r -> list.add(createReviewerStatisticBoxDto(r)));
                }
                return list;
            }
        };
    }

    private float getNotDecidedItems(ObjectReferenceType reviewerRef) {
        String campaignOid = model.getObjectType().getOid();
        PrismObject<FocusType> reviewer = WebModelServiceUtils.loadObject(reviewerRef, getPageBase());
        if (reviewer == null) {
            return 0;
        }
        MidPointPrincipal principal = MidPointPrincipal.create(reviewer.asObjectable());
        long notDecidedItemsCount = CertMiscUtil.countOpenCertItems(Collections.singletonList(campaignOid), principal,
                true, getPageBase());
        if (Boolean.FALSE.equals(percentageSortingModel.getObject())) {
            return notDecidedItemsCount;
        }
        long allOpenItemsCount = CertMiscUtil.countOpenCertItems(Collections.singletonList(campaignOid), principal,
                false, getPageBase());
        return allOpenItemsCount == 0 ? 0 : (float) notDecidedItemsCount / allOpenItemsCount * 100;
    }

    private boolean reviewersCountExceedsLimit() {
        return realReviewersCount > MAX_REVIEWERS;
    }

    private List<ObjectReferenceType> loadReviewers() {
        OperationResult result = new OperationResult(OPERATION_LOAD_CERT_ITEMS);
        AccessCertificationCampaignType campaign = model.getObjectType();
        Integer iteration = CertCampaignTypeUtil.norm(campaign.getIteration());
        Integer stage = CertCampaignTypeUtil.accountForClosingStates(or0(campaign.getStageNumber()), campaign.getState());
        ObjectQuery query = getPrismContext().queryFor(AccessCertificationWorkItemType.class)
                .exists(PrismConstants.T_PARENT)
                .ownerId(campaign.getOid())
                .and()
                .item(AccessCertificationWorkItemType.F_STAGE_NUMBER).eq(stage)
                .and()
                .item(AccessCertificationWorkItemType.F_ITERATION).eq(iteration)
                .build();
        List<AccessCertificationWorkItemType> certItems = WebModelServiceUtils.searchContainers(
                AccessCertificationWorkItemType.class, query, null, result, getPageBase());
        return collectReviewers(certItems);
    }

    private List<ObjectReferenceType> collectReviewers(List<AccessCertificationWorkItemType> certItems) {
        List<ObjectReferenceType> reviewersList = new ArrayList<>();
        certItems.forEach(certItem -> certItem.getAssigneeRef()
                .forEach(assignee -> {
                    if (!alreadyExistInList(reviewersList, assignee)) {
                        reviewersList.add(assignee);
                    }
                }));
        return reviewersList;
    }

    private boolean alreadyExistInList(List<ObjectReferenceType> reviewersList, ObjectReferenceType ref) {
        return reviewersList
                .stream()
                .anyMatch(r -> r.getOid().equals(ref.getOid()));
    }

    private StatisticBoxDto<ObjectReferenceType> createReviewerStatisticBoxDto(ObjectReferenceType ref) {
        OperationResult result = new OperationResult(OPERATION_REVIEWER);
        Task task = getPageBase().createSimpleTask(OPERATION_REVIEWER);
        Collection<SelectorOptions<GetOperationOptions>> options = getPageBase().getOperationOptionsBuilder()
                .item(FocusType.F_JPEG_PHOTO).retrieve()
                .build();
        PrismObject<FocusType> object = WebModelServiceUtils.loadObject(FocusType.class, ref.getOid(), options,
                getPageBase(), task, result);
        String name = WebComponentUtil.getName(object);
        String displayName = WebComponentUtil.getDisplayName(object);
        DisplayType displayType = new DisplayType()
                .label(name)
                .help(displayName)
                .icon(new IconType().cssClass("fa fa-user"));
        IResource userPhoto = WebComponentUtil.createJpegPhotoResource(object);
        return new StatisticBoxDto<>(Model.of(displayType), Model.of(userPhoto)) {
            @Serial private static final long serialVersionUID = 1L;

            @Override
            public ObjectReferenceType getStatisticObject() {
                return ref;
            }

        };
    }

    private IModel<DisplayType> getReviewersPanelDisplayModel(int reviewersCount) {
        String reviewersCountKey = reviewersCount == 1 ? "CampaignStatisticsPanel.reviewersPanel.singleReviewerCount" :
                "CampaignStatisticsPanel.reviewersPanel.reviewersCount";
        return () -> new DisplayType()
                .label("CampaignStatisticsPanel.reviewersPanel.title")
                .help(createStringResource(reviewersCountKey, reviewersCount).getString());
    }

    private void showAllReviewersPerformed(AjaxRequestTarget target) {
        getPageBase().showMainPopup(initReviewersPanel(getPageBase().getMainPopupBodyId(), false), target);
    }

}
