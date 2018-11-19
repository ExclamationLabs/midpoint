/*

 * Copyright (c) 2010-2018 Evolveum
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

package com.evolveum.midpoint.model.impl.sync;

import static com.evolveum.midpoint.schema.internals.InternalsConfig.consistencyChecks;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.evolveum.midpoint.common.Clock;
import com.evolveum.midpoint.common.SynchronizationUtils;
import com.evolveum.midpoint.model.api.ModelExecuteOptions;
import com.evolveum.midpoint.model.common.SystemObjectCache;
import com.evolveum.midpoint.model.impl.expr.ExpressionEnvironment;
import com.evolveum.midpoint.model.impl.expr.ModelExpressionThreadLocalHolder;
import com.evolveum.midpoint.model.impl.lens.Clockwork;
import com.evolveum.midpoint.model.impl.lens.ContextFactory;
import com.evolveum.midpoint.model.impl.lens.LensContext;
import com.evolveum.midpoint.model.impl.lens.LensFocusContext;
import com.evolveum.midpoint.model.impl.lens.LensProjectionContext;
import com.evolveum.midpoint.model.impl.util.ModelImplUtils;
import com.evolveum.midpoint.prism.PrismContainerDefinition;
import com.evolveum.midpoint.prism.PrismContainerValue;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismProperty;
import com.evolveum.midpoint.prism.PrismPropertyDefinition;
import com.evolveum.midpoint.prism.PrismPropertyDefinitionImpl;
import com.evolveum.midpoint.prism.PrismPropertyValue;
import com.evolveum.midpoint.prism.delta.ChangeType;
import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.delta.PropertyDelta;
import com.evolveum.midpoint.prism.polystring.PolyString;
import com.evolveum.midpoint.provisioning.api.ResourceObjectShadowChangeDescription;
import com.evolveum.midpoint.repo.api.PreconditionViolationException;
import com.evolveum.midpoint.repo.api.RepositoryService;
import com.evolveum.midpoint.repo.common.expression.ExpressionFactory;
import com.evolveum.midpoint.repo.common.expression.ExpressionUtil;
import com.evolveum.midpoint.repo.common.expression.ExpressionVariables;
import com.evolveum.midpoint.schema.GetOperationOptions;
import com.evolveum.midpoint.schema.ResourceShadowDiscriminator;
import com.evolveum.midpoint.schema.SelectorOptions;
import com.evolveum.midpoint.schema.constants.ExpressionConstants;
import com.evolveum.midpoint.schema.constants.ObjectTypes;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.internals.InternalsConfig;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.result.OperationResultStatus;
import com.evolveum.midpoint.schema.statistics.StatisticsUtil;
import com.evolveum.midpoint.schema.statistics.SynchronizationInformation;
import com.evolveum.midpoint.schema.util.MiscSchemaUtil;
import com.evolveum.midpoint.schema.util.ShadowUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.QNameUtil;
import com.evolveum.midpoint.util.exception.CommunicationException;
import com.evolveum.midpoint.util.exception.ConfigurationException;
import com.evolveum.midpoint.util.exception.ExpressionEvaluationException;
import com.evolveum.midpoint.util.exception.ObjectAlreadyExistsException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.PolicyViolationException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SecurityViolationException;
import com.evolveum.midpoint.util.exception.SystemException;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.BeforeAfterType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ExpressionType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.FocusType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectReferenceType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectSynchronizationDiscriminatorType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectSynchronizationSorterType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectSynchronizationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectTemplateType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ResourceType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowKindType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.SynchronizationActionType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.SynchronizationReactionType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.SynchronizationSituationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.SynchronizationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.SystemConfigurationType;

/**
 * Synchronization service receives change notifications from provisioning. It
 * decides which synchronization policy to use and evaluates it (correlation,
 * confirmation, situations, reaction, ...)
 *
 * @author lazyman
 * @author Radovan Semancik
 *
 *         Note: don't autowire this bean by implementing class, as it is
 *         proxied by Spring AOP. Use the interface instead.
 */
@Service(value = "synchronizationService")
public class SynchronizationServiceImpl implements SynchronizationService {

	private static final Trace LOGGER = TraceManager.getTrace(SynchronizationServiceImpl.class);

	@Autowired private ActionManager<Action> actionManager;
	@Autowired private CorrelationConfirmationEvaluator correlationConfirmationEvaluator;
	@Autowired private ContextFactory contextFactory;
	@Autowired private Clockwork clockwork;
	@Autowired private ExpressionFactory expressionFactory;
	@Autowired private SystemObjectCache systemObjectCache;
	@Autowired private PrismContext prismContext;
	@Autowired private Clock clock;
	
	@Autowired
	@Qualifier("cacheRepositoryService")
	private RepositoryService repositoryService;

	@Override
	public <F extends FocusType> void notifyChange(ResourceObjectShadowChangeDescription change, Task task, OperationResult parentResult) {
		validate(change);
		Validate.notNull(parentResult, "Parent operation result must not be null.");

		boolean logDebug = isLogDebug(change);
		if (LOGGER.isTraceEnabled()) {
			LOGGER.trace("SYNCHRONIZATION: received change notification\n:{}", change.debugDump(1));
		} else {
			if (logDebug) {
				LOGGER.debug("SYNCHRONIZATION: received change notification {}", change);
			}
		}

		OperationResult subResult = parentResult.createSubresult(NOTIFY_CHANGE);

		PrismObject<ShadowType> currentShadow = change.getCurrentShadow();
		PrismObject<ShadowType> applicableShadow = currentShadow;
		if (applicableShadow == null) {
			// We need this e.g. in case of delete
			applicableShadow = change.getOldShadow();
		}
		
		XMLGregorianCalendar now = clock.currentTimeXMLGregorianCalendar();
		SynchronizationEventInformation eventInfo = new SynchronizationEventInformation(applicableShadow,
				change.getSourceChannel(), task);

		try {

			PrismObject<SystemConfigurationType> configuration = systemObjectCache.getSystemConfiguration(subResult);
			SynchronizationContext<F> syncCtx = loadSynchronizationContext(applicableShadow, currentShadow, change.getResource(), change.getSourceChannel(), configuration, task, subResult);
			syncCtx.setUnrelatedChange(change.isUnrelatedChange());
			
			traceObjectSynchronization(syncCtx);

			if (!checkSynchronizationPolicy(syncCtx, eventInfo)) {
				return;
			}

			if (!checkProtected(syncCtx, eventInfo)) {
				return;
			}

			if (LOGGER.isTraceEnabled()) {
				LOGGER.trace("Synchronization is enabled, focus class: {}, found applicable policy: {}",
						syncCtx.getFocusClass(), syncCtx.getPolicyName());
			}

			setupSituation(syncCtx, eventInfo, change);
			
			if (!checkDryRunAndUnrelatedChange(syncCtx, eventInfo, now)) {
				return;
			}

			// must be here, because when the reaction has no action, the
			// situation won't be set.
			PrismObject<ShadowType> newCurrentShadow = saveSyncMetadata(syncCtx, change, now);
			if (newCurrentShadow != null) {
				change.setCurrentShadow(newCurrentShadow);
				syncCtx.setCurrentShadow(newCurrentShadow);
			}

			SynchronizationSituationType newSituation = reactToChange(syncCtx, change,
					logDebug);
			eventInfo.setNewSituation(newSituation);
			eventInfo.record(task);
			subResult.computeStatus();

		} catch (SystemException ex) {
			// avoid unnecessary re-wrap
			eventInfo.setException(ex);
			eventInfo.record(task);
			subResult.recordFatalError(ex);
			throw ex;

		} catch (Exception ex) {
			eventInfo.setException(ex);
			eventInfo.record(task);
			subResult.recordFatalError(ex);
			throw new SystemException(ex);

		} finally {
			task.markObjectActionExecutedBoundary();
		}
		LOGGER.debug("SYNCHRONIZATION: DONE for {}", currentShadow);
	}
	
	@Override
	public <F extends FocusType> SynchronizationContext<F> loadSynchronizationContext(PrismObject<ShadowType> applicableShadow, PrismObject<ShadowType> currentShadow, PrismObject<ResourceType> resource,
			String sourceChanel, PrismObject<SystemConfigurationType> configuration,
			Task task, OperationResult result)
					throws SchemaException, ObjectNotFoundException, ExpressionEvaluationException, CommunicationException, ConfigurationException, SecurityViolationException {

		SynchronizationContext<F> syncCtx = new SynchronizationContext<F>(applicableShadow, currentShadow, resource, sourceChanel, task, result);
		syncCtx.setSystemConfiguration(configuration);

		
		SynchronizationType synchronization = resource.asObjectable().getSynchronization();
		if (synchronization == null) {
			return syncCtx;
		}
		
		ObjectSynchronizationDiscriminatorType synchronizationDiscriminator = determineObjectSynchronizationDiscriminatorType(syncCtx, task, result);
		if (synchronizationDiscriminator != null) {
			syncCtx.setForceIntentChange(true);
			LOGGER.trace("Setting synchronization situation to synchronization context: {}", synchronizationDiscriminator.getSynchronizationSituation());
			syncCtx.setSituation(synchronizationDiscriminator.getSynchronizationSituation());
			F owner = (F) syncCtx.getCurrentOwner();
			if (owner != null && alreadyLinked(owner, syncCtx.getApplicableShadow())) {
				LOGGER.trace("Setting owner to synchronization context: {}", synchronizationDiscriminator.getOwner());
				syncCtx.setCurrentOwner((F) synchronizationDiscriminator.getOwner());
			}
			LOGGER.trace("Setting correlated owner to synchronization context: {}", synchronizationDiscriminator.getOwner());
			syncCtx.setCorrelatedOwner((F) synchronizationDiscriminator.getOwner());
		}
		
		for (ObjectSynchronizationType objectSynchronization : synchronization.getObjectSynchronization()) {
			if (isPolicyApplicable(objectSynchronization, synchronizationDiscriminator, syncCtx)) {
				syncCtx.setObjectSynchronization(objectSynchronization);
				return syncCtx;
			}
		}
		
		return syncCtx;
	}
	
	private <F extends FocusType> ObjectSynchronizationDiscriminatorType determineObjectSynchronizationDiscriminatorType(SynchronizationContext<F> syncCtx, Task task, OperationResult subResult) 
			throws SchemaException, ExpressionEvaluationException, ObjectNotFoundException, CommunicationException, 
			ConfigurationException, SecurityViolationException {

		SynchronizationType synchronizationType = syncCtx.getResource().asObjectable().getSynchronization();
		if (synchronizationType == null) {
			return null;
		}

		ObjectSynchronizationSorterType divider = synchronizationType.getObjectSynchronizationSorter();
		if (divider == null) {
			return null;
		}

		return evaluateSynchronizationDivision(divider, syncCtx, task, subResult);

	}
	
	private <F extends FocusType> boolean isPolicyApplicable(ObjectSynchronizationType synchronizationPolicy, ObjectSynchronizationDiscriminatorType synchronizationDiscriminator, SynchronizationContext<F> syncCtx)
					throws SchemaException, ObjectNotFoundException, ExpressionEvaluationException, CommunicationException, ConfigurationException, SecurityViolationException {
		return SynchronizationServiceUtils.isPolicyApplicable(synchronizationPolicy, synchronizationDiscriminator, expressionFactory, syncCtx);
	}
	
	private <F extends FocusType> ObjectSynchronizationDiscriminatorType evaluateSynchronizationDivision(ObjectSynchronizationSorterType synchronizationSorterType,
			SynchronizationContext<F> syncCtx, Task task, OperationResult result)
					throws SchemaException, ExpressionEvaluationException, ObjectNotFoundException, CommunicationException, ConfigurationException, SecurityViolationException {
		if (synchronizationSorterType.getExpression() == null) {
			return null;
		}
		ExpressionType classificationExpression = synchronizationSorterType.getExpression();
		String desc = "syncrhonization divider type ";
		ExpressionVariables variables = ModelImplUtils.getDefaultExpressionVariables(null, syncCtx.getApplicableShadow(), null,
				syncCtx.getResource(), syncCtx.getSystemConfiguration(), null);
		variables.addVariableDefinition(ExpressionConstants.VAR_CHANNEL, syncCtx.getChanel());
		try {
			ModelExpressionThreadLocalHolder.pushExpressionEnvironment(new ExpressionEnvironment<>(task, result));
			PrismPropertyDefinition<ObjectSynchronizationDiscriminatorType> discriminatorDef = prismContext.getSchemaRegistry()
					.findPropertyDefinitionByElementName(new QName(SchemaConstants.NS_C, "objectSynchronizationDiscriminator"));
			PrismPropertyValue<ObjectSynchronizationDiscriminatorType> evaluateDiscriminator = ExpressionUtil.evaluateExpression(variables, discriminatorDef, 
					classificationExpression, expressionFactory, desc, task, result);
			if (evaluateDiscriminator == null) {
				return null;
			}
			return evaluateDiscriminator.getValue();
		} finally {
			ModelExpressionThreadLocalHolder.popExpressionEnvironment();
		}
	}
	
	private <F extends FocusType> void traceObjectSynchronization(SynchronizationContext<F> syncCtx) {
		if (LOGGER.isTraceEnabled()) {
			LOGGER.trace("SYNCHRONIZATION determined policy: {}", syncCtx);
		}
	}
	
	private <F extends FocusType> boolean checkSynchronizationPolicy(SynchronizationContext<F> syncCtx, SynchronizationEventInformation eventInfo) throws SchemaException {
		OperationResult subResult = syncCtx.getResult();
		Task task = syncCtx.getTask();
		
		if (syncCtx.isUnrelatedChange()) {
			PrismObject<ShadowType> applicableShadow = syncCtx.getApplicableShadow();
			Validate.notNull(applicableShadow, "No current nor old shadow present: ");
			List<PropertyDelta<?>> modifications = SynchronizationUtils.createSynchronizationTimestampsDelta(applicableShadow);
			ShadowType applicableShadowType = applicableShadow.asObjectable();
			if (applicableShadowType.getIntent() == null) {
				PropertyDelta<String> intentDelta = PropertyDelta.createModificationReplaceProperty(ShadowType.F_INTENT,
						syncCtx.getApplicableShadow().getDefinition(), syncCtx.getIntent());
				modifications.add(intentDelta);
			}
			if (applicableShadowType.getKind() == null) {
				PropertyDelta<ShadowKindType> intentDelta = PropertyDelta.createModificationReplaceProperty(ShadowType.F_KIND,
						syncCtx.getApplicableShadow().getDefinition(), syncCtx.getKind());
				modifications.add(intentDelta);
			}
			
			executeShadowModifications(syncCtx.getApplicableShadow(), modifications, task, subResult);
			subResult.recordSuccess();
			eventInfo.record(task);
			LOGGER.debug("SYNCHRONIZATION: UNRELATED CHNAGE for {}", syncCtx.getApplicableShadow());
			return false;
		}
		
		if (!syncCtx.hasApplicablePolicy()) {
			String message = "SYNCHRONIZATION no matching policy for " + syncCtx.getApplicableShadow() + " ("
					+ syncCtx.getApplicableShadow().asObjectable().getObjectClass() + ") " + " on " + syncCtx.getResource()
					+ ", ignoring change from channel " + syncCtx.getChanel();
			LOGGER.debug(message);
			List<PropertyDelta<?>> modifications = createShadowIntentAndSynchronizationTimestampDelta(syncCtx, false);
			executeShadowModifications(syncCtx.getApplicableShadow(), modifications, task, subResult);
			subResult.recordStatus(OperationResultStatus.NOT_APPLICABLE, message);
			eventInfo.setNoSynchronizationPolicy();
			eventInfo.record(task);
			return false;
		}
		
		if (!syncCtx.isSynchronizationEnabled()) {
			String message = "SYNCHRONIZATION is not enabled for " + syncCtx.getResource()
					+ " ignoring change from channel " + syncCtx.getChanel();
			LOGGER.debug(message);
			List<PropertyDelta<?>> modifications = createShadowIntentAndSynchronizationTimestampDelta(syncCtx, true);
			executeShadowModifications(syncCtx.getApplicableShadow(), modifications, task, subResult);
			subResult.recordStatus(OperationResultStatus.NOT_APPLICABLE, message);
			eventInfo.setSynchronizationNotEnabled();
			eventInfo.record(task);
			return false;
		}
		
		return true;
	}
	
	private <F extends FocusType> boolean checkProtected(SynchronizationContext<F> syncCtx, SynchronizationEventInformation eventInfo) throws SchemaException {
		if (syncCtx.isProtected()) {
			OperationResult subResult = syncCtx.getResult();
			Task task = syncCtx.getTask();
			List<PropertyDelta<?>> modifications = createShadowIntentAndSynchronizationTimestampDelta(syncCtx, true);
			executeShadowModifications(syncCtx.getApplicableShadow(), modifications, task, subResult);
			subResult.recordSuccess();
			eventInfo.setProtected();
			eventInfo.record(task);
			LOGGER.debug("SYNCHRONIZATION: DONE (dry run) for protected shadow {}", syncCtx.getApplicableShadow());
			return false;
		}
		return true;
	}
	
	private <F extends FocusType> boolean checkDryRunAndUnrelatedChange(SynchronizationContext<F> syncCtx, SynchronizationEventInformation eventInfo, XMLGregorianCalendar now) throws SchemaException {
		OperationResult subResult = syncCtx.getResult();
		Task task = syncCtx.getTask();
		if (ModelImplUtils.isDryRun(task)) {
			List<PropertyDelta<?>> modifications = createShadowIntentAndSynchronizationTimestampDelta(syncCtx, true);
			executeShadowModifications(syncCtx.getApplicableShadow(), modifications, task, subResult);
			subResult.recordSuccess();
			eventInfo.record(task);
			LOGGER.debug("SYNCHRONIZATION: DONE (dry run) for {}", syncCtx.getApplicableShadow());
			return false;
		}
		return true;
	}
	
	private <F extends FocusType> List<PropertyDelta<?>> createShadowIntentAndSynchronizationTimestampDelta(SynchronizationContext<F> syncCtx, boolean saveIntent) throws SchemaException {
		Validate.notNull(syncCtx.getApplicableShadow(), "No current nor old shadow present: ");
		List<PropertyDelta<?>> modifications = SynchronizationUtils.createSynchronizationTimestampsDelta(syncCtx.getApplicableShadow());
		if (saveIntent && StringUtils.isNotBlank(syncCtx.getIntent())) {
			PropertyDelta<String> intentDelta = PropertyDelta.createModificationReplaceProperty(ShadowType.F_INTENT,
					syncCtx.getApplicableShadow().getDefinition(), syncCtx.getIntent());
			modifications.add(intentDelta);
		}
		return modifications;
	}

	private void executeShadowModifications(PrismObject<? extends ShadowType> object, List<PropertyDelta<?>> modifications,
			Task task, OperationResult subResult) {
		try {
			repositoryService.modifyObject(ShadowType.class, object.getOid(), modifications, subResult);
			task.recordObjectActionExecuted(object, ChangeType.MODIFY, null);
		} catch (Throwable t) {
			task.recordObjectActionExecuted(object, ChangeType.MODIFY, t);
		} finally {
			task.markObjectActionExecutedBoundary();
		}
	}

	
	private <F extends FocusType> boolean alreadyLinked(F focus, PrismObject<ShadowType> shadow) {
		return focus.getLinkRef().stream().anyMatch(link -> link.getOid().equals(shadow.getOid()));
	}

	private boolean isLogDebug(ResourceObjectShadowChangeDescription change) {
		// Reconciliation changes are routine. Do not let it polute the
		// logfiles.
		return !SchemaConstants.CHANGE_CHANNEL_RECON_URI.equals(change.getSourceChannel());
	}

	private void validate(ResourceObjectShadowChangeDescription change) {
		Validate.notNull(change, "Resource object shadow change description must not be null.");
		Validate.isTrue(change.getCurrentShadow() != null || change.getObjectDelta() != null,
				"Object delta and current shadow are null. At least one must be provided.");
		Validate.notNull(change.getResource(), "Resource in change must not be null.");

		if (consistencyChecks) {
			if (change.getCurrentShadow() != null) {
				change.getCurrentShadow().checkConsistence();
				ShadowUtil.checkConsistence(change.getCurrentShadow(),
						"current shadow in change description");
			}
			if (change.getObjectDelta() != null) {
				change.getObjectDelta().checkConsistence();
			}
		}
	}

	// @Override
	// public void notifyFailure(ResourceOperationFailureDescription
	// failureDescription,
	// Task task, OperationResult parentResult) {
	// Validate.notNull(failureDescription, "Resource object shadow failure
	// description must not be null.");
	// Validate.notNull(failureDescription.getCurrentShadow(), "Current shadow
	// in resource object shadow failure description must not be null.");
	// Validate.notNull(failureDescription.getObjectDelta(), "Delta in resource
	// object shadow failure description must not be null.");
	// Validate.notNull(failureDescription.getResource(), "Resource in failure
	// must not be null.");
	// Validate.notNull(failureDescription.getResult(), "Result in failure
	// description must not be null.");
	// Validate.notNull(parentResult, "Parent operation result must not be
	// null.");
	//
	// LOGGER.debug("SYNCHRONIZATION: received failure notifiation {}",
	// failureDescription);
	//
	// LOGGER.error("Provisioning error: {}",
	// failureDescription.getResult().getMessage());
	//
	// // TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO
	// TODO TODO TODO TODO
	// }

		/**
	 * XXX: in situation when one account belongs to two different idm users
	 * (repository returns only first user, method
	 * {@link com.evolveum.midpoint.model.api.ModelService#findShadowOwner(String, Task, OperationResult)}
	 * (String, com.evolveum.midpoint.schema.result.OperationResult)} ). It
	 * should be changed because otherwise we can't find
	 * {@link SynchronizationSituationType#DISPUTED} situation
	 */
	private <F extends FocusType> void setupSituation(SynchronizationContext<F> syncCtx,
			SynchronizationEventInformation eventInfo, ResourceObjectShadowChangeDescription change) {

		OperationResult result = syncCtx.getResult();
		Task task = syncCtx.getTask();
		OperationResult subResult = result.createSubresult(CHECK_SITUATION);
		LOGGER.trace("Determining situation for resource object shadow.");

		try {
			String shadowOid = getOidFromChange(change);
			Validate.notEmpty(shadowOid, "Couldn't get resource object shadow oid from change.");
			
			F currentOwnerType = syncCtx.getCurrentOwner();
			if (currentOwnerType == null) {

				PrismObject<F> currrentOwner = repositoryService.searchShadowOwner(shadowOid,
						SelectorOptions.createCollection(GetOperationOptions.createAllowNotFound()), subResult);
				if (currrentOwner != null) {
					currentOwnerType = currrentOwner.asObjectable();					
				}
			}
			
			F correlatedOwner = syncCtx.getCorrelatedOwner();
			if (!isCorrelatedOwnerSameAsCurrentOwner(correlatedOwner, currentOwnerType)) {
				LOGGER.error("Cannot synchronize {}, current owner and expected owner are not the same. Current owner: {}, expected owner: {}", syncCtx.getApplicableShadow(), currentOwnerType, correlatedOwner);
				String msg= "Cannot synchronize " + syncCtx.getApplicableShadow()
				+ ", current owner and expected owner are not the same. Current owner: " + currentOwnerType
				+ ", expected owner: " + correlatedOwner;
				result.recordFatalError(msg);
				throw new ConfigurationException(msg);
			}
			
			
			if (currentOwnerType != null) {
				
				LOGGER.trace("Shadow OID {} does have owner: {}", shadowOid, currentOwnerType.getName());
				
				syncCtx.setCurrentOwner(currentOwnerType);
				
				if (syncCtx.getSituation() != null) {
					return;
				}
				
				SynchronizationSituationType state = null;
				switch (getModificationType(change)) {
					case ADD:
					case MODIFY:
						// if user is found it means account/group is linked to
						// resource
						state = SynchronizationSituationType.LINKED;
						break;
					case DELETE:
						state = SynchronizationSituationType.DELETED;
				}
				syncCtx.setSituation(state);
			} else {
				LOGGER.trace("Resource object shadow doesn't have owner.");
				determineSituationWithCorrelation(syncCtx, change, task, result);
			}
		} catch (Exception ex) {
			LOGGER.error("Error occurred during resource object shadow owner lookup.");
			throw new SystemException(
					"Error occurred during resource object shadow owner lookup, reason: " + ex.getMessage(),
					ex);
		} finally {
			subResult.computeStatus();
			String syncSituationValue = syncCtx.getSituation() != null ? syncCtx.getSituation().value() : null;
			if (isLogDebug(change)) {
				LOGGER.debug("SYNCHRONIZATION: SITUATION: '{}', currentOwner={}, correlatedOwner={}",
						syncSituationValue, syncCtx.getCurrentOwner(),
						syncCtx.getCorrelatedOwner());
			} else {
				LOGGER.trace("SYNCHRONIZATION: SITUATION: '{}', currentOwner={}, correlatedOwner={}",
						syncSituationValue, syncCtx.getCurrentOwner(),
						syncCtx.getCorrelatedOwner());
			}
			eventInfo.setOriginalSituation(syncCtx.getSituation());
			eventInfo.setNewSituation(syncCtx.getSituation()); // overwritten later (TODO fix this!)

		}
	}

	private <F extends FocusType> boolean isCorrelatedOwnerSameAsCurrentOwner(F expectedOwner, F currentOwnerType) {
		if (expectedOwner == null) {
			return true;
		}
		
		if (currentOwnerType == null) {
			return true;
		}
		
		return (expectedOwner.getOid().equals(currentOwnerType.getOid()));
	}

	private String getOidFromChange(ResourceObjectShadowChangeDescription change) {
		if (change.getCurrentShadow() != null && StringUtils.isNotEmpty(change.getCurrentShadow().getOid())) {
			return change.getCurrentShadow().getOid();
		}
		if (change.getOldShadow() != null && StringUtils.isNotEmpty(change.getOldShadow().getOid())) {
			return change.getOldShadow().getOid();
		}

		if (change.getObjectDelta() == null || StringUtils.isEmpty(change.getObjectDelta().getOid())) {
			throw new IllegalArgumentException(
					"Oid was not defined in change (not in current, old shadow, delta).");
		}

		return change.getObjectDelta().getOid();
	}
	
	/**
	 * Tries to match specified focus and shadow. Return true if it matches,
	 * false otherwise.
	 */
	@Override
	public <F extends FocusType> boolean matchUserCorrelationRule(PrismObject<ShadowType> shadow,
			PrismObject<F> focus, ResourceType resourceType,
			PrismObject<SystemConfigurationType> configuration, Task task, OperationResult result)
					throws ConfigurationException, SchemaException, ObjectNotFoundException,
					ExpressionEvaluationException, CommunicationException, SecurityViolationException {
		
		SynchronizationContext<F> synchronizationContext = loadSynchronizationContext(shadow, shadow, resourceType.asPrismObject(), task.getChannel(), configuration, task, result);
		Class<F> focusClass;
		// TODO is this correct? The problem is that synchronizationPolicy can
		// be null...
		if (synchronizationContext.hasApplicablePolicy()) {
			focusClass = synchronizationContext.getFocusClass();
		} else {
			//noinspection unchecked
			focusClass = (Class<F>) focus.asObjectable().getClass();
		}
		return correlationConfirmationEvaluator.matchFocusByCorrelationRule(synchronizationContext, focus);
	}

	/**
	 * account is not linked to user. you have to use correlation and
	 * confirmation rule to be sure user for this account doesn't exists
	 * resourceShadow only contains the data that were in the repository before
	 * the change. But the correlation/confirmation should work on the updated
	 * data. Therefore let's apply the changes before running
	 * correlation/confirmation
	 */
	private <F extends FocusType> void determineSituationWithCorrelation(SynchronizationContext<F> syncCtx, ResourceObjectShadowChangeDescription change,
			Task task, OperationResult result)
					throws SchemaException, ObjectNotFoundException, ExpressionEvaluationException, CommunicationException, ConfigurationException, SecurityViolationException {

		if (ChangeType.DELETE.equals(getModificationType(change))) {
			// account was deleted and we know it didn't have owner
			if (syncCtx.getSituation() == null) {
				syncCtx.setSituation(SynchronizationSituationType.DELETED);
			}
			return;
		}

		F user = syncCtx.getCorrelatedOwner();
		LOGGER.trace("Correlated owner present in synchronization context: {}", user);
		if (user != null) {
			if (syncCtx.getSituation() != null) {
				return;
			}
			syncCtx.setSituation(getSynchornizationSituationFromChange(change));
			return;
		}
		
		
		PrismObject<? extends ShadowType> resourceShadow = change.getCurrentShadow();

		ObjectDelta<ShadowType> syncDelta = change.getObjectDelta();
		if (resourceShadow == null && syncDelta != null && ChangeType.ADD.equals(syncDelta.getChangeType())) {
			LOGGER.trace("Trying to compute current shadow from change delta add.");
			PrismObject<ShadowType> shadow = syncDelta.computeChangedObject(syncDelta.getObjectToAdd());
			resourceShadow = shadow;
			change.setCurrentShadow(shadow);
		}
		Validate.notNull(resourceShadow, "Current shadow must not be null.");

		ResourceType resource = change.getResource().asObjectable();
		validateResourceInShadow(resourceShadow.asObjectable(), resource);

		SynchronizationSituationType state = null;
		LOGGER.trace("SYNCHRONIZATION: CORRELATION: Looking for list of {} objects based on correlation rule.",
				syncCtx.getFocusClass().getSimpleName());
		List<PrismObject<F>> users = correlationConfirmationEvaluator.findFocusesByCorrelationRule(syncCtx.getFocusClass(),
				resourceShadow.asObjectable(), syncCtx.getCorrelation(), resource,
				syncCtx.getSystemConfiguration().asObjectable(), task, result);
		if (users == null) {
			users = new ArrayList<>();
		}

		if (users.size() > 1) {
			if (syncCtx.getConfirmation() == null) {
				LOGGER.trace("SYNCHRONIZATION: CONFIRMATION: no confirmation defined.");
			} else {
				LOGGER.debug("SYNCHRONIZATION: CONFIRMATION: Checking objects from correlation with confirmation rule.");
				users = correlationConfirmationEvaluator.findUserByConfirmationRule(syncCtx.getFocusClass(), users,
						resourceShadow.asObjectable(), resource, syncCtx.getSystemConfiguration().asObjectable(),
						syncCtx.getConfirmation(), task, result);
			}
		}

		switch (users.size()) {
			case 0:
				state = SynchronizationSituationType.UNMATCHED;
				break;
			case 1:
				state = getSynchornizationSituationFromChange(change);

				user = users.get(0).asObjectable();
				break;
			default:
				state = SynchronizationSituationType.DISPUTED;
		}

		syncCtx.setCorrelatedOwner(user);
		syncCtx.setSituation(state);
	}
	
	private SynchronizationSituationType getSynchornizationSituationFromChange(ResourceObjectShadowChangeDescription change) {
		switch (getModificationType(change)) {
			case ADD:
			case MODIFY:
				return SynchronizationSituationType.UNLINKED;
			case DELETE:
				return SynchronizationSituationType.DELETED;
		}
		
		return null;
	}

	private void validateResourceInShadow(ShadowType shadow, ResourceType resource) {
		if (shadow.getResource() != null || shadow.getResourceRef() != null) {
			return;
		}

		ObjectReferenceType reference = new ObjectReferenceType();
		reference.setOid(resource.getOid());
		reference.setType(ObjectTypes.RESOURCE.getTypeQName());

		shadow.setResourceRef(reference);
	}

	/**
	 * @param change
	 * @return method checks change type in object delta if available, otherwise
	 *         returns {@link ChangeType#ADD}
	 */
	private ChangeType getModificationType(ResourceObjectShadowChangeDescription change) {
		if (change.getObjectDelta() != null) {
			return change.getObjectDelta().getChangeType();
		}

		return ChangeType.ADD;
	}

	private <F extends FocusType> SynchronizationSituationType reactToChange(SynchronizationContext<F> syncCtx,
			ResourceObjectShadowChangeDescription change, boolean logDebug)
					throws ConfigurationException, ObjectNotFoundException, SchemaException,
					PolicyViolationException, ExpressionEvaluationException, ObjectAlreadyExistsException,
					CommunicationException, SecurityViolationException {

		SynchronizationSituationType newSituation = syncCtx.getSituation();

		findReactionDefinition(syncCtx);
		if (syncCtx.getReaction() == null) {
			LOGGER.trace("No reaction is defined for situation {} in {}", syncCtx.getSituation(), syncCtx.getResource());
			return newSituation;
		}

		// seems to be unused so commented it out [med]
		// PrismObject<? extends ObjectType> shadow = null;
		// if (change.getCurrentShadow() != null) {
		// shadow = change.getCurrentShadow();
		// } else if (change.getOldShadow() != null) {
		// shadow = change.getOldShadow();
		// }

		Boolean doReconciliation = syncCtx.isDoReconciliation();
		if (doReconciliation == null) {
			// We have to do reconciliation if we have got a full shadow and no
			// delta.
			// There is no other good way how to reflect the changes from the
			// shadow.
			if (change.getObjectDelta() == null) {
				doReconciliation = true;
			}
		}

		ModelExecuteOptions options = new ModelExecuteOptions();
		options.setReconcile(doReconciliation);
		options.setLimitPropagation(syncCtx.isLimitPropagation());

		final boolean willSynchronize = isSynchronize(syncCtx.getReaction());
		LensContext<F> lensContext = null;
		
		OperationResult parentResult = syncCtx.getResult();
		Task task = syncCtx.getTask();
		if (willSynchronize) {
			lensContext = createLensContext(syncCtx, change, syncCtx.getReaction(), options, parentResult);
		}

		if (LOGGER.isTraceEnabled() && lensContext != null) {
			LOGGER.trace("---[ SYNCHRONIZATION context before action execution ]-------------------------\n"
					+ "{}\n------------------------------------------", lensContext.debugDump());
		}

		if (willSynchronize) {

			// there's no point in calling executeAction without context - so
			// the actions are executed only if synchronize == true
			executeActions(syncCtx, lensContext, BeforeAfterType.BEFORE, logDebug, task, parentResult);

			Iterator<LensProjectionContext> iterator = lensContext.getProjectionContextsIterator();
			LensProjectionContext originalProjectionContext = iterator.hasNext() ? iterator.next() : null;

			try {

				clockwork.run(lensContext, task, parentResult);

			} catch (ConfigurationException | ObjectNotFoundException | SchemaException |
					PolicyViolationException | ExpressionEvaluationException | ObjectAlreadyExistsException |
					CommunicationException | SecurityViolationException | PreconditionViolationException | RuntimeException e) {
				LOGGER.error("SYNCHRONIZATION: Error in synchronization on {} for situation {}: {}: {}. Change was {}",
						syncCtx.getResource(), syncCtx.getSituation(), e.getClass().getSimpleName(), e.getMessage(), change, e);
				// what to do here? We cannot throw the error back. All that the notifyChange method
				// could do is to convert it to SystemException. But that indicates an internal error and it will
				// break whatever code called the notifyChange in the first place. We do not want that.
				// If the clockwork could not do anything with the exception then perhaps nothing can be done at all.
				// So just log the error (the error should be remembered in the result and task already)
				// and then just go on.
			}

			// note: actions "AFTER" seem to be useless here (basically they
			// modify lens context - which is relevant only if followed by
			// clockwork run)
			executeActions(syncCtx, lensContext, BeforeAfterType.AFTER,
					logDebug, task, parentResult);

			if (originalProjectionContext != null) {
				newSituation = originalProjectionContext.getSynchronizationSituationResolved();
			}

		} else {
			LOGGER.trace("Skipping clockwork run on {} for situation {}, synchronize is set to false.",
					new Object[] { syncCtx.getResource(), syncCtx.getSituation() });
		}

		return newSituation;

	}

	@NotNull
	private <F extends FocusType> LensContext<F> createLensContext(SynchronizationContext<F> syncCtx, 
			ResourceObjectShadowChangeDescription change, SynchronizationReactionType reactionDefinition,
			ModelExecuteOptions options, 
			OperationResult parentResult) throws ObjectNotFoundException, SchemaException, ConfigurationException {

		LensContext<F> context = contextFactory.createSyncContext(syncCtx.getFocusClass(), change);
		context.setLazyAuditRequest(true);
		context.setSystemConfiguration(syncCtx.getSystemConfiguration());
		context.setOptions(options);

		ResourceType resource = change.getResource().asObjectable();
		if (ModelExecuteOptions.isLimitPropagation(options)) {
			context.setTriggeredResource(resource);
		}

		context.rememberResource(resource);
		PrismObject<ShadowType> shadow = getShadowFromChange(change);
		if (InternalsConfig.consistencyChecks)
			shadow.checkConsistence();

		// Projection context

		ShadowKindType kind = getKind(shadow, syncCtx.getKind());
		String intent = getIntent(shadow, syncCtx.getIntent());
		boolean thombstone = isThombstone(change);
		ResourceShadowDiscriminator descr = new ResourceShadowDiscriminator(resource.getOid(), kind, intent, thombstone);
		LensProjectionContext projectionContext = context.createProjectionContext(descr);
		projectionContext.setResource(resource);
		projectionContext.setOid(getOidFromChange(change));
		projectionContext.setSynchronizationSituationDetected(syncCtx.getSituation());
		projectionContext.setShadowExistsInRepo(syncCtx.isShadowExistsInRepo());

		// insert object delta if available in change
		ObjectDelta<? extends ShadowType> delta = change.getObjectDelta();
		if (delta != null) {
			projectionContext.setSyncDelta((ObjectDelta<ShadowType>) delta);
		} else {
			projectionContext.setSyncAbsoluteTrigger(true);
		}

		// we insert account if available in change
		PrismObject<ShadowType> currentAccount = shadow;
		if (currentAccount != null) {
			projectionContext.setLoadedObject(currentAccount);
			if (!thombstone) {
				projectionContext.setFullShadow(true);
			}
			projectionContext.setFresh(true);
		}

		if (delta != null && delta.isDelete()) {
			projectionContext.setExists(false);
		} else {
			projectionContext.setExists(true);
		}

		projectionContext.setDoReconciliation(ModelExecuteOptions.isReconcile(options));

		// Focus context
		if (syncCtx.getCurrentOwner() != null) {
			F focusType = syncCtx.getCurrentOwner();
			LensFocusContext<F> focusContext = context.createFocusContext();
			PrismObject<F> focusOld = (PrismObject<F>) focusType.asPrismObject();
			focusContext.setLoadedObject(focusOld);
		}

		// Global stuff
		
		if (syncCtx.getObjectTemplateRef() != null) {
			ObjectTemplateType objectTemplate = repositoryService
					.getObject(ObjectTemplateType.class, syncCtx.getObjectTemplateRef().getOid(), null, parentResult)
					.asObjectable();
			context.setFocusTemplate(objectTemplate);
		}

		return context;
	}

	private PrismObject<ShadowType> getShadowFromChange(ResourceObjectShadowChangeDescription change) {
		if (change.getCurrentShadow() != null) {
			return change.getCurrentShadow();
		}
		if (change.getOldShadow() != null) {
			return change.getOldShadow();
		}
		return null;
	}

	private ShadowKindType getKind(PrismObject<ShadowType> shadow,
			ShadowKindType objectSynchronizationKind) {
		ShadowKindType shadowKind = shadow.asObjectable().getKind();
		if (shadowKind != null) {
			return shadowKind;
		}
		return objectSynchronizationKind;
	}

	private String getIntent(PrismObject<ShadowType> shadow,
			String objectSynchronizationIntent) {
		String shadowIntent = shadow.asObjectable().getIntent();
		if (shadowIntent != null) {
			return shadowIntent;
		}
		return objectSynchronizationIntent;
	}

	private boolean isThombstone(ResourceObjectShadowChangeDescription change) {
		PrismObject<? extends ShadowType> shadow = null;
		if (change.getOldShadow() != null) {
			shadow = change.getOldShadow();
		} else if (change.getCurrentShadow() != null) {
			shadow = change.getCurrentShadow();
		}
		if (shadow != null) {
			if (shadow.asObjectable().isDead() != null) {
				return shadow.asObjectable().isDead();
			}
		}
		ObjectDelta<? extends ShadowType> objectDelta = change.getObjectDelta();
		return objectDelta != null && objectDelta.isDelete();
	}

	private boolean isSynchronize(SynchronizationReactionType reactionDefinition) {
		if (reactionDefinition.isSynchronize() != null) {
			return reactionDefinition.isSynchronize();
		}
		return !reactionDefinition.getAction().isEmpty();
	}

	private <F extends FocusType> void findReactionDefinition(SynchronizationContext<F> syncCtx) throws ConfigurationException {
		
	}

	/**
	 * Saves situation, timestamps, kind and intent (if needed)
	 */
	private <F extends FocusType> PrismObject<ShadowType> saveSyncMetadata(SynchronizationContext<F> syncCtx, ResourceObjectShadowChangeDescription change,
			XMLGregorianCalendar now) {
		PrismObject<ShadowType> shadow = syncCtx.getCurrentShadow();
		if (shadow == null) {
			return null;
		}
		
		OperationResult parentResult = syncCtx.getResult();
		Task task = syncCtx.getTask();
		
		try {
			ShadowType shadowType = shadow.asObjectable();
			// new situation description
			List<PropertyDelta<?>> deltas = SynchronizationUtils
					.createSynchronizationSituationAndDescriptionDelta(shadow, syncCtx.getSituation(),
							change.getSourceChannel(), true, now);

			if (shadowType.getKind() == null) {
				PropertyDelta<ShadowKindType> kindDelta = PropertyDelta.createReplaceDelta(shadow.getDefinition(),
						ShadowType.F_KIND, syncCtx.getKind());
				deltas.add(kindDelta);
			}

			if (isNullIntentOrIsForceIntent(syncCtx)) {
				PropertyDelta<String> intentDelta = PropertyDelta.createReplaceDelta(shadow.getDefinition(),
						ShadowType.F_INTENT, syncCtx.getIntent());
				deltas.add(intentDelta);
			}

			repositoryService.modifyObject(shadowType.getClass(), shadow.getOid(), deltas, parentResult);
			ItemDelta.applyTo(deltas, shadow);
			task.recordObjectActionExecuted(shadow, ChangeType.MODIFY, null);
			return shadow;
		} catch (ObjectNotFoundException ex) {
			task.recordObjectActionExecuted(shadow, ChangeType.MODIFY, ex);
			// This may happen e.g. during some recon-livesync interactions.
			// If the shadow is gone then it is gone. No point in recording the
			// situation any more.
			LOGGER.debug(
					"Could not update situation in account, because shadow {} does not exist any more (this may be harmless)",
					shadow.getOid());
			syncCtx.setShadowExistsInRepo(false);
			parentResult.getLastSubresult().setStatus(OperationResultStatus.HANDLED_ERROR);
		} catch (ObjectAlreadyExistsException | SchemaException ex) {
			task.recordObjectActionExecuted(shadow, ChangeType.MODIFY, ex);
			LoggingUtils.logException(LOGGER,
					"### SYNCHRONIZATION # notifyChange(..): Save of synchronization situation failed: could not modify shadow "
							+ shadow.getOid() + ": " + ex.getMessage(),
					ex);
			parentResult.recordFatalError("Save of synchronization situation failed: could not modify shadow "
					+ shadow.getOid() + ": " + ex.getMessage(), ex);
			throw new SystemException("Save of synchronization situation failed: could not modify shadow "
					+ shadow.getOid() + ": " + ex.getMessage(), ex);
		} catch (Throwable t) {
			task.recordObjectActionExecuted(shadow, ChangeType.MODIFY, t);
			throw t;
		}

		return null;
	}
	
	private <F extends FocusType> boolean isNullIntentOrIsForceIntent(SynchronizationContext<F> syncCtx) throws SchemaException {
		ShadowType shadow = syncCtx.getCurrentShadow().asObjectable();
		if (shadow.getIntent() == null) {
			return true;
		}
		
		if (syncCtx.isForceIntentChange()) {
			String objectSyncIntent = syncCtx.getIntent();
			if (!MiscSchemaUtil.equalsIntent(shadow.getIntent(), objectSyncIntent)) {
				return true;
			}
		}
		
		return false;
	}

	private <F extends FocusType> void executeActions(SynchronizationContext<F> syncCtx,
			LensContext<F> context, BeforeAfterType order,
			boolean logDebug, Task task, OperationResult parentResult)
					throws ConfigurationException, SchemaException {

		for (SynchronizationActionType actionDef : syncCtx.getReaction().getAction()) {
			if ((actionDef.getOrder() == null && order == BeforeAfterType.BEFORE)
					|| (actionDef.getOrder() != null && actionDef.getOrder() == order)) {

				String handlerUri = actionDef.getHandlerUri();
				if (handlerUri == null) {
					handlerUri = actionDef.getRef();
				}
				if (handlerUri == null) {
					LOGGER.error("Action definition in resource {} doesn't contain handler URI", syncCtx.getResource());
					throw new ConfigurationException(
							"Action definition in resource " + syncCtx.getResource() + " doesn't contain handler URI");
				}

				Action action = actionManager.getActionInstance(handlerUri);
				if (action == null) {
					LOGGER.warn("Couldn't create action with uri '{}' in resource {}, skipping action.",
							new Object[] { handlerUri, syncCtx.getResource() });
					continue;
				}

				// TODO: legacy userTemplate

				Map<QName, Object> parameters = null;
				if (actionDef.getParameters() != null) {
					// TODO: process parameters
					// parameters = actionDef.getParameters().getAny();
				}

				if (logDebug) {
					LOGGER.debug("SYNCHRONIZATION: ACTION: Executing: {}.", action.getClass());
				} else {
					LOGGER.trace("SYNCHRONIZATION: ACTION: Executing: {}.", action.getClass());
				}
				SynchronizationSituation<F> situation = new SynchronizationSituation<F>(syncCtx.getCurrentOwner(), syncCtx.getCorrelatedOwner(), syncCtx.getSituation());
				action.handle(context, situation, parameters, task, parentResult);
			}
		}
	}

	@Override
	public String getName() {
		return "model synchronization service";
	}

	private static class SynchronizationEventInformation {

		private String objectName;
		private String objectDisplayName;
		private String objectOid;
		private Throwable exception;
		private long started;
		private String channel;

		private SynchronizationInformation.Record originalStateIncrement = new SynchronizationInformation.Record();
		private SynchronizationInformation.Record newStateIncrement = new SynchronizationInformation.Record();

		public SynchronizationEventInformation(PrismObject<? extends ShadowType> currentShadow, String channel, Task task) {
			this.channel = channel;
			started = System.currentTimeMillis();
			if (currentShadow != null) {
				final ShadowType shadow = currentShadow.asObjectable();
				objectName = PolyString.getOrig(shadow.getName());
				objectDisplayName = StatisticsUtil.getDisplayName(shadow);
				objectOid = currentShadow.getOid();
			}
			task.recordSynchronizationOperationStart(objectName, objectDisplayName, ShadowType.COMPLEX_TYPE, objectOid);
			if (SchemaConstants.CHANGE_CHANNEL_LIVE_SYNC_URI.equals(channel)) {
				// livesync processing is not controlled via model -> so we cannot do this in upper layers
				task.recordIterativeOperationStart(objectName, objectDisplayName, ShadowType.COMPLEX_TYPE, objectOid);
			}
		}

		public void setProtected() {
			originalStateIncrement.setCountProtected(1);
			newStateIncrement.setCountProtected(1);
		}

		public void setNoSynchronizationPolicy() {
			originalStateIncrement.setCountNoSynchronizationPolicy(1);
			newStateIncrement.setCountNoSynchronizationPolicy(1);
		}

		public void setSynchronizationNotEnabled() {
			originalStateIncrement.setCountSynchronizationDisabled(1);
			newStateIncrement.setCountSynchronizationDisabled(1);
		}

		private void setSituation(SynchronizationInformation.Record increment,
				SynchronizationSituationType situation) {
			if (situation != null) {
				switch (situation) {
					case LINKED:
						increment.setCountLinked(1);
						break;
					case UNLINKED:
						increment.setCountUnlinked(1);
						break;
					case DELETED:
						increment.setCountDeleted(1);
						break;
					case DISPUTED:
						increment.setCountDisputed(1);
						break;
					case UNMATCHED:
						increment.setCountUnmatched(1);
						break;
					default:
						// noop (or throw exception?)
				}
			}
		}

		public void setOriginalSituation(SynchronizationSituationType situation) {
			setSituation(originalStateIncrement, situation);
		}

		public void setNewSituation(SynchronizationSituationType situation) {
			newStateIncrement = new SynchronizationInformation.Record(); // brutal hack, TODO fix this!
			setSituation(newStateIncrement, situation);
		}

		public void setException(Exception ex) {
			exception = ex;
		}

		public void record(Task task) {
			task.recordSynchronizationOperationEnd(objectName, objectDisplayName, ShadowType.COMPLEX_TYPE,
					objectOid, started, exception, originalStateIncrement, newStateIncrement);
			if (SchemaConstants.CHANGE_CHANNEL_LIVE_SYNC_URI.equals(channel)) {
				// livesync processing is not controlled via model -> so we cannot do this in upper layers
				task.recordIterativeOperationEnd(objectName, objectDisplayName, ShadowType.COMPLEX_TYPE,
						objectOid, started, exception);
			}
		}
	}
	
}
