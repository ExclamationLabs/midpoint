/*
 * Copyright (c) 2010-2017 Evolveum
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

package com.evolveum.midpoint.wf.util;

import com.evolveum.midpoint.model.api.util.DeputyUtils;
import com.evolveum.midpoint.prism.PrismReferenceValue;
import com.evolveum.midpoint.prism.query.builder.S_AtomicFilterExit;
import com.evolveum.midpoint.prism.query.builder.S_FilterEntryOrEmpty;
import com.evolveum.midpoint.prism.query.builder.S_FilterExit;
import com.evolveum.midpoint.repo.api.RepositoryService;
import com.evolveum.midpoint.schema.RelationRegistry;
import com.evolveum.midpoint.schema.constants.ObjectTypes;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.security.api.DelegatorWithOtherPrivilegesLimitations;
import com.evolveum.midpoint.security.api.MidPointPrincipal;
import com.evolveum.midpoint.util.QNameUtil;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.UserType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.WorkItemType;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.List;

/**
 * TODO move to more appropriate place (common for both wf and certifications)
 *
 * @author mederly
 */
public class QueryUtils {

	/**
	 * Augments work item query by including filter to see only work items assigned to the current user or any of his delegators,
	 * providing that the limitation(s) allow it.
	 *
	 * Note that work item limitations are supported only in the current (crude) form: all or none.
	 */
	public static S_AtomicFilterExit filterForAssignees(S_FilterEntryOrEmpty q, MidPointPrincipal principal,
			QName limitationItemName, RelationRegistry relationRegistry) {
		if (principal == null) {
			return q.none();
		} else {
			return q.item(WorkItemType.F_ASSIGNEE_REF).ref(getPotentialAssigneesForUser(principal, limitationItemName, relationRegistry));
		}
	}

	public static S_FilterExit filterForGroups(S_FilterEntryOrEmpty q, String userOid, RepositoryService repositoryService,
			RelationRegistry relationRegistry, OperationResult result)
			throws SchemaException {
		return q.item(WorkItemType.F_CANDIDATE_REF).ref(getGroupsForUser(userOid, repositoryService, relationRegistry, result));
	}

	private static List<PrismReferenceValue> getPotentialAssigneesForUser(MidPointPrincipal principal,
			QName limitationItemName, RelationRegistry relationRegistry) {
		// As for relations, WorkItem.assigneeRef should contain only the default ones.
		QName defaultRelation = relationRegistry.getDefaultRelation();
		List<PrismReferenceValue> rv = new ArrayList<>();
		rv.add(ObjectTypeUtil.createObjectRef(principal.getOid(), ObjectTypes.USER).relation(defaultRelation).asReferenceValue());
		for (DelegatorWithOtherPrivilegesLimitations delegator : principal.getDelegatorWithOtherPrivilegesLimitationsCollection()) {
			if (DeputyUtils.limitationsAllow(delegator.getLimitations(), limitationItemName)) {
				rv.add(ObjectTypeUtil.createObjectRef(delegator.getDelegator(), defaultRelation).asReferenceValue());
			}
		}
		return rv;
	}

	private static List<PrismReferenceValue> getGroupsForUser(String userOid, RepositoryService repositoryService,
			RelationRegistry relationRegistry, OperationResult result) throws SchemaException {
		List<PrismReferenceValue> rv = new ArrayList<>();
		UserType userType;
		try {
			userType = repositoryService.getObject(UserType.class, userOid, null, result).asObjectable();
		} catch (ObjectNotFoundException e) {
			return rv;
		}
		userType.getRoleMembershipRef().stream()
				.filter(ref -> relationRegistry.isMember(ref.getRelation()))
				.forEach(ref -> rv.add(ref.clone().asReferenceValue()));
		userType.getDelegatedRef().stream()
				.filter(ref -> relationRegistry.isMember(ref.getRelation()))
				.filter(ref -> !QNameUtil.match(ref.getType(), UserType.COMPLEX_TYPE))   // we are not interested in deputies (but this should be treated above)
				.forEach(ref -> rv.add(ref.clone().asReferenceValue()));
		return rv;
	}

}
