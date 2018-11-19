/**
 * Copyright (c) 2015-2016 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.evolveum.midpoint.web.component.objectdetails;

import java.util.List;
import java.util.Map;

import javax.xml.namespace.QName;

import org.apache.wicket.ajax.AjaxChannel;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.attributes.AjaxRequestAttributes;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.extensions.markup.html.tabs.ITab;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.model.Model;

import com.evolveum.midpoint.gui.api.ComponentConstants;
import com.evolveum.midpoint.gui.api.component.tabs.CountablePanelTab;
import com.evolveum.midpoint.gui.api.component.tabs.PanelTab;
import com.evolveum.midpoint.gui.api.model.LoadableModel;
import com.evolveum.midpoint.gui.api.page.PageBase;
import com.evolveum.midpoint.gui.api.util.FocusTabVisibleBehavior;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.model.api.ModelAuthorizationAction;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.prism.query.builder.QueryBuilder;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.component.AjaxButton;
import com.evolveum.midpoint.web.component.assignment.AssignmentEditorDto;
import com.evolveum.midpoint.web.component.assignment.AssignmentsUtil;
import com.evolveum.midpoint.web.component.prism.ContainerStatus;
import com.evolveum.midpoint.web.component.prism.ObjectWrapper;
import com.evolveum.midpoint.web.component.util.VisibleEnableBehaviour;
import com.evolveum.midpoint.web.page.admin.PageAdminFocus;
import com.evolveum.midpoint.web.page.admin.PageAdminObjectDetails;
import com.evolveum.midpoint.web.page.admin.roles.AbstractRoleMemberPanel;
import com.evolveum.midpoint.web.page.admin.users.dto.FocusSubwrapperDto;
import com.evolveum.midpoint.web.page.admin.users.dto.UserDtoStatus;
import com.evolveum.midpoint.web.page.self.PageAssignmentShoppingCart;
import com.evolveum.midpoint.web.security.GuiAuthorizationConstants;
import com.evolveum.midpoint.web.session.RoleCatalogStorage;
import com.evolveum.midpoint.web.session.UserProfileStorage.TableId;
import com.evolveum.midpoint.web.util.ExpressionUtil;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AbstractRoleType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AreaCategoryType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AssignmentType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.FocusType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.OrgType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ResourceObjectAssociationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.RoleType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ServiceType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.UserType;

/**
 * @author semancik
 *
 */
public abstract class AbstractRoleMainPanel<R extends AbstractRoleType> extends FocusMainPanel<R> {
	private static final long serialVersionUID = 1L;

	private static final Trace LOGGER = TraceManager.getTrace(AbstractRoleMainPanel.class);
	
    private static final String DOT_CLASS = AbstractRoleMainPanel.class.getName();
    private static final String OPERATION_CAN_SEARCH_ROLE_MEMBERSHIP_ITEM = DOT_CLASS + "canSearchRoleMembershipItem";
	private static final String OPERATION_LOAD_ASSIGNMENTS_LIMIT = DOT_CLASS + "loadAssignmentsLimit";
    private static final String ID_SHOPPING_CART_BUTTONS_PANEL = "shoppingCartButtonsPanel";
    private static final String ID_ADD_TO_CART_BUTTON = "addToCartButton";

	public AbstractRoleMainPanel(String id, LoadableModel<ObjectWrapper<R>> objectModel,
			LoadableModel<List<FocusSubwrapperDto<ShadowType>>> projectionModel,
			PageAdminFocus<R> parentPage) {
		super(id, objectModel, projectionModel, parentPage);
	}

	@Override
	protected void initLayoutButtons(PageAdminObjectDetails<R> parentPage) {
		super.initLayoutButtons(parentPage);
		initShoppingCartPanel(parentPage);
	}

	private void initShoppingCartPanel(PageAdminObjectDetails<R> parentPage){
		RoleCatalogStorage storage = parentPage.getSessionStorage().getRoleCatalog();

		WebMarkupContainer shoppingCartButtonsPanel = new WebMarkupContainer(ID_SHOPPING_CART_BUTTONS_PANEL);
		shoppingCartButtonsPanel.setOutputMarkupId(true);
		shoppingCartButtonsPanel.add(new VisibleEnableBehaviour(){
			private static final long serialVersionUID = 1L;

			@Override
			public boolean isVisible(){
				//show panel only in case if user came to object details from
				// Role Catalog page
				return PageAssignmentShoppingCart.class.equals(WebComponentUtil.getPreviousPageClass(parentPage));
			}
		});
		getMainForm().add(shoppingCartButtonsPanel);

		AjaxButton addToCartButton = new AjaxButton(ID_ADD_TO_CART_BUTTON, parentPage
				.createStringResource("PageAssignmentDetails.addToCartButton")) {
			private static final long serialVersionUID = 1L;

			@Override
			protected void updateAjaxAttributes(AjaxRequestAttributes attributes) {
				attributes.setChannel(new AjaxChannel("blocking", AjaxChannel.Type.ACTIVE));
			}

			@Override
			public void onClick(AjaxRequestTarget target) {
				AssignmentEditorDto dto = AssignmentEditorDto.createDtoFromObject(getObject().asObjectable(), UserDtoStatus.ADD, parentPage);
				storage.getAssignmentShoppingCart().add(dto);
				parentPage.redirectBack();
			}
		};
		addToCartButton.add(AttributeAppender.append("class", new LoadableModel<String>() {
			@Override
			protected String load() {
				return addToCartButton.isEnabled() ? "btn btn-success" : "btn btn-success disabled";
			}
		}));
		addToCartButton.setOutputMarkupId(true);
		addToCartButton.add(new VisibleEnableBehaviour(){
			private static final long serialVersionUID = 1L;

			@Override
			public boolean isEnabled(){
				int assignmentsLimit = AssignmentsUtil.loadAssignmentsLimit(new OperationResult(OPERATION_LOAD_ASSIGNMENTS_LIMIT),
												parentPage);
								AssignmentEditorDto dto = AssignmentEditorDto.createDtoFromObject(AbstractRoleMainPanel.this.getObject().asObjectable(),
												UserDtoStatus.ADD, parentPage);
								return !AssignmentsUtil.isShoppingCartAssignmentsLimitReached(assignmentsLimit, parentPage)
												&& (storage.isMultiUserRequest() || dto.isAssignable());			}
		});
		addToCartButton.add(AttributeAppender.append("title",
				AssignmentsUtil.getShoppingCartAssignmentsLimitReachedTitleModel(parentPage)));
		shoppingCartButtonsPanel.add(addToCartButton);
	}

	@Override
	protected List<ITab> createTabs(final PageAdminObjectDetails<R> parentPage) {
		List<ITab> tabs = super.createTabs(parentPage);

		FocusTabVisibleBehavior<R> authorization = new FocusTabVisibleBehavior<>(unwrapModel(), ComponentConstants.UI_FOCUS_TAB_POLICY_RULES_URL, false, isFocusHistoryPage(), parentPage);
		tabs.add(
				new CountablePanelTab(parentPage.createStringResource("pageAdminFocus.policyRules"), authorization) {

					private static final long serialVersionUID = 1L;

					@Override
					public WebMarkupContainer createPanel(String panelId) {
						return createFocusPolicyRulesTabPanel(panelId, parentPage);
					}

					@Override
					public String getCount() {
						return Integer.toString(countPolicyRules());
					}
				});

		authorization = new FocusTabVisibleBehavior<>(unwrapModel(), ComponentConstants.UI_FOCUS_TAB_APPLICABLE_POLICIES_URL, false, isFocusHistoryPage(), parentPage);
		tabs.add(
				new PanelTab(parentPage.createStringResource("pageAdminFocus.applicablePolicies"), authorization) {

					private static final long serialVersionUID = 1L;

					@Override
					public WebMarkupContainer createPanel(String panelId) {
						return new FocusApplicablePoliciesTabPanel<>(panelId, getMainForm(), getObjectModel(), parentPage);
					}
				});

		authorization = new FocusTabVisibleBehavior<>(unwrapModel(),
				ComponentConstants.UI_FOCUS_TAB_INDUCEMENTS_URL, false, isFocusHistoryPage(), parentPage);
		tabs.add(new CountablePanelTab(parentPage.createStringResource("FocusType.inducement"), authorization) {

			private static final long serialVersionUID = 1L;

			@Override
			public WebMarkupContainer createPanel(String panelId) {
				return new AbstractRoleInducementPanel<>(panelId, getMainForm(), getObjectModel(), parentPage);
			}

			@Override
			public String getCount(){
				return getInducementsCount();
			}

		});
		authorization = new FocusTabVisibleBehavior<>(unwrapModel(),
				ComponentConstants.UI_ROLE_TAB_INDUCED_ENTITLEMENTS_URL, false, isFocusHistoryPage(), parentPage);
		tabs.add(new CountablePanelTab(parentPage.createStringResource("AbstractRoleMainPanel.inducedEntitlements"), authorization) {

			private static final long serialVersionUID = 1L;

			@Override
			public WebMarkupContainer createPanel(String panelId) {
				return new InducedEntitlementsTabPanel<>(panelId, getMainForm(), getObjectModel(), parentPage);
			}

			@Override
			public String getCount(){
				return getInducedEntitlementsCount();
			}

		});

		if (WebComponentUtil.isAuthorized(ModelAuthorizationAction.AUDIT_READ.getUrl()) && getObjectWrapper().getStatus() != ContainerStatus.ADDING){
			authorization = new FocusTabVisibleBehavior<>(unwrapModel(), ComponentConstants.UI_FOCUS_TAB_OBJECT_HISTORY_URL, false, isFocusHistoryPage(), parentPage);
			tabs.add(
					new PanelTab<R>(parentPage.createStringResource("pageAdminFocus.objectHistory"), authorization) {

						private static final long serialVersionUID = 1L;

						@Override
						public WebMarkupContainer createPanel(String panelId) {
							return createObjectHistoryTabPanel(panelId, parentPage);
						}
					});
		}

		authorization = new FocusTabVisibleBehavior<>(unwrapModel(),
				ComponentConstants.UI_FOCUS_TAB_MEMBERS_URL, false, isFocusHistoryPage(), parentPage);
		tabs.add(new PanelTab<R>(parentPage.createStringResource("pageRole.members"), authorization) {

			private static final long serialVersionUID = 1L;

			@Override
			public WebMarkupContainer createPanel(String panelId) {
				return createMemberPanel(panelId);
			}

			@Override
			public boolean isVisible() {
				return super.isVisible() &&
						getObjectWrapper().getStatus() != ContainerStatus.ADDING &&
						isAllowedToReadRoleMembership(getObjectWrapper().getOid(), parentPage);
			}
		});
		
		authorization = new FocusTabVisibleBehavior<>(unwrapModel(),
				ComponentConstants.UI_FOCUS_TAB_GOVERNANCE_URL, false, isFocusHistoryPage(), parentPage);

		tabs.add(new PanelTab<R>(parentPage.createStringResource("pageRole.governance"), authorization) {

			private static final long serialVersionUID = 1L;

			@Override
			public WebMarkupContainer createPanel(String panelId) {
				return createGovernancePanel(panelId);
			}

			@Override
			public boolean isVisible() {
				return super.isVisible() && getObjectWrapper().getStatus() != ContainerStatus.ADDING;
			}
		});
		
		return tabs;
	}

	
	public AbstractRoleMemberPanel<R> createMemberPanel(String panelId) {
		
		return new AbstractRoleMemberPanel<R>(panelId, new Model<>(getObject().asObjectable())) {
			
			private static final long serialVersionUID = 1L;

			@Override
			protected List<QName> getSupportedRelations() {
				List<QName> relations =  WebComponentUtil.getCategoryRelationChoices(AreaCategoryType.ADMINISTRATION, getDetailsPage());
				List<QName> governance = WebComponentUtil.getCategoryRelationChoices(AreaCategoryType.GOVERNANCE, getDetailsPage());
				governance.forEach(r -> relations.remove(r));
				return relations;
			}
			
		};
	}

	
	public AbstractRoleMemberPanel<R> createGovernancePanel(String panelId) {
		
		return new AbstractRoleMemberPanel<R>(panelId, new Model<>(getObject().asObjectable())) {
			
			private static final long serialVersionUID = 1L;

			@Override
			protected List<QName> getSupportedRelations() {
				return WebComponentUtil.getCategoryRelationChoices(AreaCategoryType.GOVERNANCE, getDetailsPage());
			}
			
			@Override
			protected Map<String, String> getAuthorizations(QName complexType) {
				return GuiAuthorizationConstants.GOVERNANCE_MEMBERS_AUTHORIZATIONS;
			}
		
		};
	}
	
	private boolean isAllowedToReadRoleMembership(String abstractRoleOid, PageBase parentPage){
		return isAllowedToReadRoleMembershipItemForType(abstractRoleOid, UserType.class, parentPage)
				|| isAllowedToReadRoleMembershipItemForType(abstractRoleOid, RoleType.class, parentPage)
				|| isAllowedToReadRoleMembershipItemForType(abstractRoleOid, OrgType.class, parentPage)
				|| isAllowedToReadRoleMembershipItemForType(abstractRoleOid, ServiceType.class, parentPage);
	}

	private <F extends FocusType> boolean isAllowedToReadRoleMembershipItemForType(String abstractRoleOid, Class<F> type, PageBase parentPage){
		ObjectQuery query = QueryBuilder.queryFor(type, parentPage.getPrismContext())
				.item(FocusType.F_ROLE_MEMBERSHIP_REF).ref(abstractRoleOid).build();
		Task task = parentPage.createSimpleTask(OPERATION_CAN_SEARCH_ROLE_MEMBERSHIP_ITEM);
		OperationResult result = task.getResult();
		boolean isAllowed = false;
		try {
			isAllowed = parentPage.getModelInteractionService()
                    .canSearch(type, null, null, false, query, task, result);
        } catch (Exception ex){
            LoggingUtils.logUnexpectedException(LOGGER, "Couldn't check if user is allowed to search for roleMembershipRef item", ex);
        }
        return isAllowed;
    }

	
	private WebMarkupContainer createFocusPolicyRulesTabPanel(String panelId, PageAdminObjectDetails<R> parentPage) {
		return new FocusPolicyRulesTabPanel<>(panelId, getMainForm(), getObjectModel(), parentPage);
	}

	private String getInducementsCount(){
			PrismObject<R> focus = getObjectModel().getObject().getObject();
			List<AssignmentType> inducements = focus.asObjectable().getInducement();
			if (inducements == null){
				return "";
			}
			return Integer.toString(inducements.size());
	}

	private String getInducedEntitlementsCount(){
			PrismObject<R> focus = getObjectModel().getObject().getObject();
			List<AssignmentType> inducements = focus.asObjectable().getInducement();
			if (inducements == null){
				return "";
			}
			int count = 0;
			for (AssignmentType inducement : inducements){
				if (inducement.getConstruction() == null){
					continue;
				}
				if (inducement.getConstruction().getAssociation() == null || inducement.getConstruction().getAssociation().size() == 0){
					continue;
				}
				for (ResourceObjectAssociationType association : inducement.getConstruction().getAssociation()){
					if (association.getOutbound() != null && association.getOutbound().getExpression() != null
							&& ExpressionUtil.getShadowRefValue(association.getOutbound().getExpression()) != null){
						count++;
						break;
					}
				}
			}
			return Integer.toString(count);
	}
}
