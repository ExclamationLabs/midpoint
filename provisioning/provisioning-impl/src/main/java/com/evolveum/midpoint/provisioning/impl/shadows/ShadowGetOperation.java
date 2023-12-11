/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.provisioning.impl.shadows;

import static com.evolveum.midpoint.provisioning.impl.ResourceObjectFuturizer.futurizeRepoShadow;
import static com.evolveum.midpoint.provisioning.impl.ResourceObjectFuturizer.futurizeResourceObject;
import static com.evolveum.midpoint.provisioning.util.ProvisioningUtil.validateShadow;
import static com.evolveum.midpoint.schema.GetOperationOptions.*;
import static com.evolveum.midpoint.util.MiscUtil.argCheck;
import static com.evolveum.midpoint.util.MiscUtil.formatExceptionMessage;
import static com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowLifecycleStateType.CONCEIVED;
import static com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowLifecycleStateType.GESTATING;

import java.util.Collection;
import javax.xml.datatype.XMLGregorianCalendar;

import com.evolveum.midpoint.provisioning.impl.*;
import com.evolveum.midpoint.provisioning.impl.resourceobjects.CompleteResourceObject;
import com.evolveum.midpoint.provisioning.impl.resourceobjects.ExistingResourceObject;
import com.evolveum.midpoint.provisioning.impl.resourceobjects.ResourceObject;

import com.evolveum.midpoint.schema.processor.ResourceObjectDefinition;
import com.evolveum.midpoint.schema.processor.ResourceObjectIdentification;

import com.evolveum.midpoint.schema.util.RawRepoShadow;
import com.evolveum.midpoint.xml.ns._public.resource.capabilities_3.ReadCapabilityType;

import com.google.common.base.Preconditions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.crypto.EncryptionException;
import com.evolveum.midpoint.prism.xml.XmlTypeConverter;
import com.evolveum.midpoint.provisioning.api.*;
import com.evolveum.midpoint.provisioning.ucf.api.GenericFrameworkException;
import com.evolveum.midpoint.provisioning.util.ProvisioningUtil;
import com.evolveum.midpoint.schema.GetOperationOptions;
import com.evolveum.midpoint.schema.PointInTimeType;
import com.evolveum.midpoint.schema.SelectorOptions;
import com.evolveum.midpoint.schema.internals.InternalCounters;
import com.evolveum.midpoint.schema.internals.InternalMonitor;
import com.evolveum.midpoint.schema.processor.ResourceAttribute;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.ResourceTypeUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.DebugUtil;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AvailabilityStatusType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.CachingMetadataType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowLifecycleStateType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowType;

/**
 * Implements the `get` operation, except for the `raw` mode.
 *
 * See {@link ProvisioningService#getObject(Class, String, Collection, Task, OperationResult)} for the full contract.
 * (The processing between {@link ProvisioningServiceImpl} and this class is minimal. So the contract there is quite relevant
 * for what is done here.)
 */
class ShadowGetOperation {

    private static final String OP_GET_RESOURCE_OBJECT = ShadowGetOperation.class + ".getResourceObject";

    private static final Trace LOGGER = TraceManager.getTrace(ShadowGetOperation.class);

    /** Provisioning context derived from the repository shadow. Reflecting also the eventual (re)classification. */
    @NotNull private ProvisioningContext ctx;

    /** OID of the shadow to be gotten. */
    @NotNull private final String oid;

    /**
     * The shadow obtained from the repository, gradually updated, fixed, and so on.
     * If the shadow disappears (currently, it can happen during error processing), this field
     * is not cleared, but an exception is thrown.
     *
     * Note that the content of the resource object that was really fetched is not stored here.
     */
    @NotNull private RepoShadow repoShadow;

    /** If present, overwrites the identifiers from the {@link #repoShadow}. */
    @Nullable private final Collection<ResourceAttribute<?>> identifiersOverride;

    /** The "readOnly" is never set. This is because we need to modify the shadow during post-processing. */
    @Nullable private final Collection<SelectorOptions<GetOperationOptions>> options;

    @Nullable private final GetOperationOptions rootOptions;

    @NotNull private final ShadowsLocalBeans b = ShadowsLocalBeans.get();

    @NotNull private final XMLGregorianCalendar now;

    private ShadowGetOperation(
            @NotNull ProvisioningContext ctx,
            @NotNull RepoShadow repoShadow,
            @Nullable Collection<ResourceAttribute<?>> identifiersOverride,
            @Nullable Collection<SelectorOptions<GetOperationOptions>> options) {
        this.ctx = ctx;
        this.oid = repoShadow.getOid();
        this.repoShadow = repoShadow;
        this.identifiersOverride = identifiersOverride;
        this.options = GetOperationOptions.updateToReadWrite(options);
        this.rootOptions = SelectorOptions.findRootOptions(this.options);
        assert !GetOperationOptions.isReadOnly(rootOptions);
        this.now = b.clock.currentTimeXMLGregorianCalendar();
    }

    static Shadow execute(
            @NotNull String oid,
            @Nullable RawRepoShadow providedRepositoryShadow,
            @Nullable Collection<ResourceAttribute<?>> identifiersOverride,
            @Nullable Collection<SelectorOptions<GetOperationOptions>> options,
            @NotNull ProvisioningOperationContext context,
            @NotNull Task task,
            @NotNull OperationResult result)
            throws SchemaException, ExpressionEvaluationException, ConfigurationException, ObjectNotFoundException,
            CommunicationException, SecurityViolationException, EncryptionException {
        RawRepoShadow rawRepoShadow = obtainRepositoryShadow(oid, providedRepositoryShadow, options, result);
        ProvisioningContext ctx = createProvisioningContext(rawRepoShadow, options, context, task, result);
        var repoShadow = ctx.adoptRawRepoShadow(rawRepoShadow);
        return new ShadowGetOperation(ctx, repoShadow, identifiersOverride, options)
                .executeInternal(result);
    }

    private Shadow executeInternal(OperationResult parentResult)
            throws ObjectNotFoundException, CommunicationException, SchemaException,
            ConfigurationException, SecurityViolationException, ExpressionEvaluationException, EncryptionException {

        Preconditions.checkArgument(!isRaw(), "Raw mode is not supported here");

        if (isNoFetch()) {
            // Even here we want to delete expired pending operations; and delete the shadow if needed.
            doQuickShadowRefresh(parentResult);
            return returnCached("noFetch option");
        }

        ctx.checkForCapability(ReadCapabilityType.class);

        if (ctx.isInMaintenance()) {
            parentResult.setPartialError("Resource is in maintenance mode");
            return returnCached("maintenance mode");
        }

        refreshBeforeReading(parentResult);

        String returnCachedReason = getReasonForReturningCachedShadow();
        if (returnCachedReason != null) {
            return returnCached(returnCachedReason);
        }

        var identification = getPrimaryIdentification();
        if (identification == null) {
            return returnCached("no primary identifier but can return repository shadow");
        }

        ExistingResourceObject resourceObject;
        OperationResult result = parentResult.createSubresult(OP_GET_RESOURCE_OBJECT);
        result.addArbitraryObjectAsParam("identification", identification);
        result.addArbitraryObjectAsParam("context", ctx);
        try {
            resourceObject = getResourceObject(identification, result).resourceObject();
        } catch (ReturnCachedException e) {
            result.muteAllSubresultErrors();
            result.recordSuccess();
            return returnCached(e.reason);
        } catch (Exception ex) {
            result.recordException(ex);
            result.close(); // This is necessary before invoking the error handler
            try {
                invokeErrorHandler(ex, result, parentResult);
                if (!repoShadow.isDeleted()) {
                    return returnCached(
                            "(handled) exception during resource object retrieval: " + formatExceptionMessage(ex));
                } else {
                    throw ex;
                }
            } catch (GenericFrameworkException | ObjectAlreadyExistsException | PolicyViolationException e) {
                throw new SystemException(e.getMessage(), e);
            }
        } finally {
            result.close();
        }

        var shadowPostProcessor = new ShadowPostProcessor(ctx, repoShadow, resourceObject, null);

        // FIXME maybe special object type for combined object could be created
        var combinedObject = shadowPostProcessor.execute(parentResult);
        ctx = shadowPostProcessor.getCurrentProvisioningContext();

        return returnRetrieved(combinedObject);
    }

    private static @NotNull RawRepoShadow obtainRepositoryShadow(
            @NotNull String oid,
            @Nullable RawRepoShadow providedRepositoryShadow,
            @Nullable Collection<SelectorOptions<GetOperationOptions>> options,
            @NotNull OperationResult result) throws SchemaException, ObjectNotFoundException {
        if (providedRepositoryShadow != null) {
            LOGGER.trace("Start getting '{}' (opts {})", providedRepositoryShadow, options);
            argCheck(
                    oid.equals(providedRepositoryShadow.getOid()),
                    "Provided OID is not equal to OID of repository shadow");
            argCheck(!providedRepositoryShadow.getBean().isImmutable(),
                    "Provided shadow is immutable, it cannot be used for GET operation: %s", providedRepositoryShadow);
            return providedRepositoryShadow;
        } else {
            LOGGER.trace("Start getting shadow '{}' (opts {})", oid, options);
            // Get the shadow from repository. There are identifiers that we need for accessing the object by UCF.
            RawRepoShadow repoShadow = b().shadowFinder.getRepoShadow(oid, disableReadOnly(options), result);
            LOGGER.trace("Got repository shadow:\n{}", repoShadow.debugDumpLazily());
            return repoShadow;
        }
    }

    private static ShadowsLocalBeans b() {
        return ShadowsLocalBeans.get();
    }

    private static @NotNull ProvisioningContext createProvisioningContext(
            @NotNull RawRepoShadow repositoryShadow,
            @Nullable Collection<SelectorOptions<GetOperationOptions>> options,
            @NotNull ProvisioningOperationContext operationContext,
            @NotNull Task task,
            @NotNull OperationResult result)
            throws SchemaException, ExpressionEvaluationException, CommunicationException, ConfigurationException,
            ObjectNotFoundException {
        ProvisioningContext ctx = b().ctxFactory.createForShadow(repositoryShadow.getBean(), task, result);
        ctx.setGetOperationOptions(options);
        ctx.setOperationContext(operationContext);
        ctx.assertDefinition();
        return ctx;
    }

    private boolean isRaw() {
        return GetOperationOptions.isRaw(rootOptions);
    }

    private boolean isNoFetch() {
        return GetOperationOptions.isNoFetch(rootOptions);
    }

    private void refreshBeforeReading(@NotNull OperationResult result)
            throws ObjectNotFoundException, SchemaException, ConfigurationException, ExpressionEvaluationException {
        if (isForceRefresh(rootOptions)
                || isForceRetry(rootOptions)
                || ResourceTypeUtil.isRefreshOnRead(ctx.getResource())) {
            LOGGER.trace("Doing full shadow refresh before read operation for {}", repoShadow);
            doFullShadowRefresh(result);
        } else {
            LOGGER.trace("Full refresh is not requested, doing quick one only for {}", repoShadow);
            doQuickShadowRefresh(result);
        }
    }

    private void doQuickShadowRefresh(OperationResult result) throws ObjectNotFoundException, SchemaException {
        var refreshedShadow = ShadowRefreshOperation.executeQuick(ctx, repoShadow, result);
        if (refreshedShadow.isDeleted()) {
            throw new ObjectNotFoundException(
                    "Shadow was deleted (during quick refresh)",
                    ShadowType.class,
                    oid,
                    ctx.isAllowNotFound());
        }
        ctx.updateShadowState(refreshedShadow);
        repoShadow = refreshedShadow;
    }

    private void doFullShadowRefresh(OperationResult result)
            throws ObjectNotFoundException, SchemaException, ConfigurationException, ExpressionEvaluationException {
        ProvisioningOperationOptions refreshOpts = toProvisioningOperationOptions(rootOptions);
        var refreshedShadow =
                ShadowRefreshOperation
                        .executeFull(repoShadow, refreshOpts, ctx.getOperationContext(), ctx.getTask(), result)
                        .getShadow();
        LOGGER.trace("Refreshed repository shadow:\n{}", DebugUtil.debugDumpLazily(refreshedShadow, 1));

        if (refreshedShadow.isDeleted()) {
            // Most probably a dead shadow was just removed
            // TODO: is this OK? What about re-appeared objects
            LOGGER.debug("Shadow (no longer) exists: {}", refreshedShadow);
            throw new ObjectNotFoundException("Resource object does not exist", ShadowType.class, oid);
        }

        // Refresh might change the shadow state.
        ctx.updateShadowState(refreshedShadow);
        repoShadow = refreshedShadow;
    }

    private ProvisioningOperationOptions toProvisioningOperationOptions(GetOperationOptions getOpts) {
        if (getOpts == null) {
            return null;
        }

        ProvisioningOperationOptions provisioningOpts = new ProvisioningOperationOptions();
        // for now, we are interested in forceRetry option. In the future, there can be more.
        provisioningOpts.setForceRetry(getOpts.getForceRetry());
        return provisioningOpts;
    }

    /**
     * Returns `null` in case there are no suitable identifiers but we can return the cached shadow.
     */
    private ResourceObjectIdentification.WithPrimary getPrimaryIdentification() throws SchemaException {
        ResourceObjectDefinition objDef = ctx.getObjectDefinitionRequired();
        if (identifiersOverride != null) {
            LOGGER.trace("Using overridden identifiers: {}", identifiersOverride);
            var identification = ResourceObjectIdentification.fromIdentifiers(objDef, identifiersOverride);
            if (identification instanceof ResourceObjectIdentification.WithPrimary primary) {
                return primary;
            }
            throw new SchemaException("Overridden identifiers are not primary: " + identification);
        }

        var identification = repoShadow.getIdentificationRequired();
        if (identification instanceof ResourceObjectIdentification.WithPrimary primary) {
            return primary;
        }

        if (repoShadow.hasPendingAddOperation()
                || repoShadow.hasPendingDeleteOperation()
                || repoShadow.isDead()) {
            if (ProvisioningUtil.isFuturePointInTime(options)) {
                // Trying to get the future state of uncreated or dead shadow.
                // But we cannot even try fetch operation here, as we do not have the identifiers.
                // But we have quite a good idea how the shadow is going to look like.
                // Therefore we can return it.
                //
                // NOTE: do NOT re-try add operation here (for "pending add" shadows). It will be retried in separate task.
                // Re-trying the operation here would not provide big benefits and it will complicate the code.
                return null;
            } else {
                // Get of uncreated shadow, but we want current state. Therefore we have to throw an error because
                // the object does not exist yet - to our best knowledge. But we cannot really throw ObjectNotFound here.
                // ObjectNotFound is a positive indication that the object does not exist.
                // We do not know that for sure because resource is unavailable.
                // The object might have been created in the meantime.
                throw new GenericConnectorException(
                        "Unable to get object from the resource. Probably it has not been created yet because "
                                + "of previous unavailability of the resource.");
            }
        }

        throw new SchemaException(
                String.format("No primary identifiers found in the repository shadow %s (%s) with respect to %s",
                        repoShadow, identification, ctx.getResource()));
    }

    private @NotNull CompleteResourceObject getResourceObject(
            ResourceObjectIdentification.WithPrimary identification, OperationResult result)
            throws CommunicationException, SchemaException, ConfigurationException, SecurityViolationException,
            ExpressionEvaluationException, ReturnCachedException, ObjectNotFoundException {

        InternalMonitor.recordCount(InternalCounters.SHADOW_FETCH_OPERATION_COUNT);
        try {
            assert repoShadow != null;
            CompleteResourceObject completeObject;
            if (ctx.isReadingCachingOnly()) {
                // TODO most probably we should NOT call the converter here. The shadow should be already converted.
                //  (not sure about the association)
                completeObject = b.resourceObjectConverter.completeResourceObject(
                        ctx,
                        ExistingResourceObject.fromRepoShadow(repoShadow),
                        true,
                        result);
            } else {
                completeObject = b.resourceObjectConverter.fetchResourceObject(
                        ctx,
                        identification,
                        ctx.createAttributesToReturn(),
                        true,
                        result);
                markResourceUp(result);
            }
            LOGGER.trace("Object returned by ResourceObjectConverter:\n{}", completeObject.debugDumpLazily(1));
            return completeObject;
        } catch (ObjectNotFoundException e) {
            ShadowLifecycleStateType shadowState = repoShadow.getShadowLifecycleState();
            // This may be OK, e.g. for connectors that have running async add operation.
            if (shadowState == CONCEIVED || shadowState == GESTATING) {
                LOGGER.trace("{} was not found, but we can return cached shadow because it is in {} state",
                        repoShadow, shadowState);
                throw new ReturnCachedException("'conceived' or 'gestating' shadow was not found on resource");
            } else {
                LOGGER.trace("{} was not found, following normal error processing because shadow is in {} state",
                        repoShadow, shadowState);
                // This is live shadow that was not found on resource. Just re-throw the exception. It will
                // be caught later and the usual error handlers will bury the shadow.
                //
                // We re-wrap the exception (in a custom way) instead of re-throwing it or calling e.wrap() method
                // in order to provide shadow OID and "allow not found" information.
                throw new ObjectNotFoundException(
                        "Resource object for shadow " + oid + " could not be retrieved: " + e.getMessage(),
                        e,
                        ShadowType.class,
                        oid,
                        isAllowNotFound(rootOptions));
            }
        }
    }

    private void markResourceUp(OperationResult result) throws ObjectNotFoundException {
        // The resourceManager.modifyResourceAvailabilityStatus method retrieves the resource from cache. It is a bit
        // costly now (e.g., it includes ResourceType cloning). Even if this cost could be reduced, we may skip the operation
        // altogether, if the (currently known) resource is marked as UP. The data may be slightly outdated, but not much.
        // Even if so, the resource would certainly be sooner or later marked as UP by a different "get" operation.
        if (!ResourceTypeUtil.isUp(ctx.getResource())) {
            b.resourceManager.modifyResourceAvailabilityStatus(
                    ctx.getResourceOid(),
                    AvailabilityStatusType.UP,
                    "getting " + repoShadow + " was successful.",
                    ctx.getTask(),
                    result,
                    false);
        }
    }

    private void invokeErrorHandler(Exception cause, OperationResult failedOpResult, OperationResult result)
            throws SchemaException, GenericFrameworkException, CommunicationException, ObjectNotFoundException,
            ObjectAlreadyExistsException, ConfigurationException, SecurityViolationException, PolicyViolationException,
            ExpressionEvaluationException {
        LOGGER.debug("Handling provisioning GET exception {}: {}", cause.getClass(), cause.getMessage());
        assert !repoShadow.isDeleted();
        repoShadow = b.errorHandlerLocator
                .locateErrorHandlerRequired(cause)
                .handleGetError(ctx, repoShadow, cause, failedOpResult, result);
    }

    /**
     * Analogous to {@link ShadowSearchLikeOperation#processRepoShadow(PrismObject, OperationResult)}.
     *
     * TODO shouldn't we try to set the "protected" flag here (like we do in the search-like operation)?
     */
    private @NotNull Shadow returnCached(String reason) throws SchemaException, ConfigurationException {
        LOGGER.trace("Returning cached (repository) version of shadow {} because of: {}", repoShadow, reason);
        var futurized = futurizeRepoShadow(ctx, repoShadow, false, options, now);
        ctx.applyAttributesDefinition(futurized.getBean());
        LOGGER.trace("Futurized shadow:\n{}", DebugUtil.debugDumpLazily(futurized));
        return createShadow(ctx, futurized);
    }

    private @NotNull Shadow returnRetrieved(@NotNull ExistingResourceObject shadowedObject)
            throws SchemaException, ConfigurationException {
        assert repoShadow != null;
        ResourceObject futurized = futurizeResourceObject(
                ctx, repoShadow, shadowedObject, false, options, now);
        LOGGER.trace("Futurized shadowed resource object:\n{}", futurized.debugDumpLazily(1));
        return createShadow(ctx, futurized);
    }

    private Shadow createShadow(ProvisioningContext ctx, ResourceObject resourceObject) {
        validateShadow(resourceObject.getBean(), true);
        return resourceObject.asShadow(ctx.getResource());
    }

    private String getReasonForReturningCachedShadow() throws ConfigurationException {
        LOGGER.trace("Determining if we have a reason for returning cached shadow"); // the non-null result will be logged later
        if (ctx.isReadingCachingOnly()) {
            return "resource is caching only";
        }
        ShadowLifecycleStateType shadowState = repoShadow.getShadowLifecycleState();
        if (shadowState == ShadowLifecycleStateType.TOMBSTONE) {
            // Once shadow is buried it stays nine feet under. Therefore there is no point in trying to access the resource.
            // NOTE: this is just for tombstone! Schrodinger's shadows (corpse) will still work as if they were alive.
            return "shadow is tombstone";
        }
        long stalenessOption = getStaleness(rootOptions);
        PointInTimeType pit = getPointInTimeType(rootOptions);
        if (pit == null) {
            if (stalenessOption > 0) {
                pit = PointInTimeType.CACHED;
            } else {
                pit = PointInTimeType.CURRENT;
            }
        }
        switch (pit) {
            case CURRENT:
                LOGGER.trace("We need current reliable state -> we will NOT return cached data.");
                return null;
            case CACHED:
                if (isCachedShadowFreshEnough()) {
                    return "requested cached data";
                } else {
                    LOGGER.trace("Requested cached data, but the shadow is not fresh enough");
                    return null;
                }
            case FUTURE:
                LOGGER.trace("We were asked for future point in time. We could return cached, e.g. if there was a pending "
                        + "create operation. But let's try real get operation first and then we'll see.");
                return null;
            default:
                throw new IllegalArgumentException("Unknown point in time: " + pit);
        }
    }

    private boolean isCachedShadowFreshEnough() throws ConfigurationException {
        long stalenessOption = getStaleness(rootOptions);
        if (stalenessOption == 0L) {
            return false;
        }
        CachingMetadataType cachingMetadata = repoShadow.getBean().getCachingMetadata();
        if (cachingMetadata == null) {
            if (stalenessOption == Long.MAX_VALUE) {
                // We must return cached version but there is no cached version.
                throw new ConfigurationException("Cached version of " + repoShadow + " requested, but there is no cached value");
            }
            return false;
        }
        if (stalenessOption == Long.MAX_VALUE) {
            return true;
        }

        XMLGregorianCalendar retrievalTimestamp = cachingMetadata.getRetrievalTimestamp();
        if (retrievalTimestamp == null) {
            return false;
        }
        long retrievalTimestampMillis = XmlTypeConverter.toMillis(retrievalTimestamp);
        return b.clock.currentTimeMillis() - retrievalTimestampMillis < stalenessOption;
    }

    private static class ReturnCachedException extends Exception {
        private final String reason;

        private ReturnCachedException(String reason) {
            this.reason = reason;
        }
    }
}
