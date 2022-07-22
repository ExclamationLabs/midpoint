/*
 * Copyright (c) 2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.gui.impl.page.admin;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.evolveum.midpoint.gui.api.factory.wrapper.PrismObjectWrapperFactory;
import com.evolveum.midpoint.gui.api.factory.wrapper.WrapperContext;
import com.evolveum.midpoint.gui.api.model.LoadableModel;
import com.evolveum.midpoint.gui.api.page.PageBase;
import com.evolveum.midpoint.gui.api.prism.ItemStatus;
import com.evolveum.midpoint.gui.api.prism.wrapper.PrismObjectWrapper;
import com.evolveum.midpoint.gui.api.util.ModelServiceLocator;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.model.api.AdminGuiConfigurationMergeManager;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.MiscUtil;
import com.evolveum.midpoint.util.exception.AuthorizationException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.util.validation.MidpointFormValidator;
import com.evolveum.midpoint.web.util.validation.SimpleValidationError;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

import org.apache.wicket.model.IDetachable;
import org.apache.wicket.model.LoadableDetachableModel;
import org.jetbrains.annotations.NotNull;

public class ObjectDetailsModels<O extends ObjectType> implements Serializable, IDetachable {

    private static final Trace LOGGER = TraceManager.getTrace(ObjectDetailsModels.class);

    private static final String DOT_CLASS = ObjectDetailsModels.class.getName() + ".";
    protected static final String OPERATION_LOAD_PARENT_ORG = DOT_CLASS + "loadParentOrgs";

    private ModelServiceLocator modelServiceLocator;
    private LoadableDetachableModel<PrismObject<O>> prismObjectModel;

    private LoadableModel<PrismObjectWrapper<O>> objectWrapperModel;
    private LoadableModel<GuiObjectDetailsPageType> detailsPageConfigurationModel;

    private LoadableDetachableModel<O> summaryModel;

    public ObjectDetailsModels(LoadableDetachableModel<PrismObject<O>> prismObjectModel, ModelServiceLocator serviceLocator) {
        this.prismObjectModel = prismObjectModel;
        this.modelServiceLocator = serviceLocator;

        objectWrapperModel = new LoadableModel<>(false) {

            @Override
            protected PrismObjectWrapper<O> load() {
                PrismObject<O> prismObject = getPrismObject();//prismObjectModel.getObject();

                PrismObjectWrapperFactory<O> factory = modelServiceLocator.findObjectWrapperFactory(prismObject.getDefinition());
                Task task = modelServiceLocator.createSimpleTask("createWrapper");
                OperationResult result = task.getResult();
                WrapperContext ctx = new WrapperContext(task, result);
                ctx.setCreateIfEmpty(true);
                ctx.setDetailsPageTypeConfiguration(detailsPageConfigurationModel.getObject());
                if (isReadonly()) {
                    ctx.setReadOnly(isReadonly());
                }
                try {
                    return factory.createObjectWrapper(prismObject, isEditObject(prismObject) ? ItemStatus.NOT_CHANGED : ItemStatus.ADDED, ctx);
                } catch (SchemaException e) {
                    LoggingUtils.logUnexpectedException(LOGGER, "Cannot create wrapper for {} \nReason: {]", e, prismObject, e.getMessage());
                    result.recordFatalError("Cannot create wrapper for " + prismObject + ", because: " + e.getMessage(), e);
                    getPageBase().showResult(result);
                    throw getPageBase().redirectBackViaRestartResponseException();
//                    return null;
                }

            }
        };

        detailsPageConfigurationModel = new LoadableModel<>(false) {
            @Override
            protected GuiObjectDetailsPageType load() {
                return loadDetailsPageConfiguration().clone();
            }
        };

        summaryModel = new LoadableDetachableModel<O>() {

            @Override
            protected O load() {
                PrismObjectWrapper<O> wrapper = objectWrapperModel.getObject();
                if (wrapper == null) {
                    return null;
                }

                PrismObject<O> object = wrapper.getObject();
                loadParentOrgs(object);
                return object.asObjectable();
            }
        };
    }

    private void loadParentOrgs(PrismObject<O> object) {
        Task task = getModelServiceLocator().createSimpleTask(OPERATION_LOAD_PARENT_ORG);
        OperationResult subResult = task.getResult();
        // Load parent organizations (full objects). There are used in the
        // summary panel and also in the main form.
        // Do it here explicitly instead of using resolve option to have ability
        // to better handle (ignore) errors.
        for (ObjectReferenceType parentOrgRef : object.asObjectable().getParentOrgRef()) {

            PrismObject<OrgType> parentOrg = null;
            try {

                parentOrg = getModelServiceLocator().getModelService().getObject(
                        OrgType.class, parentOrgRef.getOid(), null, task, subResult);
                LOGGER.trace("Loaded parent org with result {}", subResult.getLastSubresult());
            } catch (AuthorizationException e) {
                // This can happen if the user has permission to read parentOrgRef but it does not have
                // the permission to read target org
                // It is OK to just ignore it.
                subResult.muteLastSubresultError();
                PrismObject<? extends FocusType> taskOwner = task.getOwner(subResult);
                LOGGER.debug("User {} does not have permission to read parent org unit {} (ignoring error)", taskOwner.getName(), parentOrgRef.getOid());
            } catch (Exception ex) {
                subResult.recordWarning(getPageBase().createStringResource("PageAdminObjectDetails.message.loadParentOrgs.warning", parentOrgRef.getOid()).getString(), ex);
                LOGGER.warn("Cannot load parent org {}: {}", parentOrgRef.getOid(), ex.getMessage(), ex);
            }

            if (parentOrg != null) {
                ObjectReferenceType ref = ObjectTypeUtil.createObjectRef(parentOrg, getPrismContext());
                ref.asReferenceValue().setObject(parentOrg);
                object.asObjectable().getParentOrgRef().add(ref);
            }
        }
        subResult.computeStatus();
    }


    protected PageBase getPageBase() {
        return (PageBase) getModelServiceLocator();
    }

    protected GuiObjectDetailsPageType loadDetailsPageConfiguration() {
        return modelServiceLocator.getCompiledGuiProfile().findObjectDetailsConfiguration(getPrismObject().getDefinition().getTypeName());
    }

    //TODO change summary panels to wrappers?
    public LoadableDetachableModel<O> getSummaryModel() {
        return summaryModel;
    }

    public boolean isEditObject(PrismObject<O> prismObject) {
        return prismObject.getOid() != null;
    }

    protected PrismContext getPrismContext() {
        return modelServiceLocator.getPrismContext();
    }

    private Collection<SimpleValidationError> validationErrors;
    private ObjectDelta<O> delta;

    public ObjectDelta<O> getDelta() {
        return delta;
    }

    public Collection<ObjectDelta<? extends ObjectType>> collectDeltas(OperationResult result) throws SchemaException {
        validationErrors = null;
//        delta = null;
        PrismObjectWrapper<O> objectWrapper = getObjectWrapperModel().getObject();
        delta = objectWrapper.getObjectDelta();
        WebComponentUtil.encryptCredentials(delta, true, modelServiceLocator);
        switch (objectWrapper.getStatus()) {
            case ADDED:
                    PrismObject<O> objectToAdd = delta.getObjectToAdd();
//                    WebComponentUtil.encryptCredentials(objectToAdd, true, modelServiceLocator);
                    prepareObjectForAdd(objectToAdd);
                    getPrismContext().adopt(objectToAdd, objectWrapper.getCompileTimeClass());
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("Delta before add user:\n{}", delta.debugDump(3));
                    }

                    if (!delta.isEmpty()) {
                        delta.revive(getPrismContext());

                        final Collection<ObjectDelta<? extends ObjectType>> deltas = MiscUtil.createCollection(delta);
                        validationErrors = performCustomValidation(objectToAdd, deltas);
                        return deltas;

//                        if (checkValidationErrors(target, validationErrors)) {
//                            return null;
//                        }
                    }
                break;

            case NOT_CHANGED:
//                    WebComponentUtil.encryptCredentials(delta, true, modelServiceLocator);
                    prepareObjectDeltaForModify(delta); //preparing of deltas for projections (ADD, DELETE, UNLINK)

                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("Delta before modify user:\n{}", delta.debugDump(3));
                    }

                    Collection<ObjectDelta<? extends ObjectType>> deltas = new ArrayList<>();
                    if (!delta.isEmpty()) {
                        delta.revive(getPrismContext());
                        deltas.add(delta);
                    }

                    List<ObjectDelta<? extends ObjectType>> additionalDeltas = getAdditionalModifyDeltas(result);
                    if (additionalDeltas != null) {
                        for (ObjectDelta additionalDelta : additionalDeltas) {
                            if (!additionalDelta.isEmpty()) {
                                additionalDelta.revive(getPrismContext());
                                deltas.add(additionalDelta);
                            }
                        }
                    }
                    return deltas;
            // support for add/delete containers (e.g. delete credentials)
            default:
                throw new UnsupportedOperationException("Unsupported state");
        }
        LOGGER.trace("returning from saveOrPreviewPerformed");
        return new ArrayList<>();
    }

    public Collection<SimpleValidationError> getValidationErrors() {
        return validationErrors;
    }

    protected Collection<SimpleValidationError> performCustomValidation(PrismObject<O> object,
            Collection<ObjectDelta<? extends ObjectType>> deltas) throws SchemaException {
        Collection<SimpleValidationError> errors = null;

        if (object == null) {
            if (getObjectWrapper() != null && getObjectWrapper().getObjectOld() != null) {
                object = getObjectWrapper().getObjectOld().clone();        // otherwise original object could get corrupted e.g. by applying the delta below

                for (ObjectDelta delta : deltas) {
                    // because among deltas there can be also ShadowType deltas
                    if (UserType.class.isAssignableFrom(delta.getObjectTypeClass())) {
                        delta.applyTo(object);
                    }
                }
            }
        } else {
            object = object.clone();
        }

//        performAdditionalValidation(object, deltas, errors);

        for (MidpointFormValidator validator : getValidators()) {
            if (errors == null) {
                errors = validator.validateObject(object, deltas);
            } else {
                errors.addAll(validator.validateObject(object, deltas));
            }
        }

        return errors;
    }

    private Collection<MidpointFormValidator> getValidators() {
        return modelServiceLocator.getFormValidatorRegistry().getValidators();
    }

    protected void prepareObjectForAdd(PrismObject<O> objectToAdd) throws SchemaException {

    }

    protected void prepareObjectDeltaForModify(ObjectDelta<O> modifyDelta) throws SchemaException {

    }

    protected List<ObjectDelta<? extends ObjectType>> getAdditionalModifyDeltas(OperationResult result) {
        return new ArrayList<>();
    }


    public void reset() {
        prismObjectModel.detach();
        objectWrapperModel.reset();
        detailsPageConfigurationModel.reset();
        summaryModel.detach();
    }

    protected ModelServiceLocator getModelServiceLocator() {
        return modelServiceLocator;
    }

    protected AdminGuiConfigurationMergeManager getAdminGuiConfigurationMergeManager() {
        return modelServiceLocator.getAdminGuiConfigurationMergeManager();
    }

    public LoadableModel<PrismObjectWrapper<O>> getObjectWrapperModel() {
        return objectWrapperModel;
    }

    public PrismObjectWrapper<O> getObjectWrapper() {
        return getObjectWrapperModel().getObject();
    }

    protected PrismObject<O> getPrismObject() {
        if (!objectWrapperModel.isLoaded()) {
            return prismObjectModel.getObject();
        }
        return getObjectWrapper().getObject();
    }

    public void reloadPrismObjectModel(@NotNull PrismObject<O> newObject) {
        prismObjectModel.detach();
        prismObjectModel = new LoadableDetachableModel<>(){

            @Override
            protected PrismObject<O> load() {
                return newObject;
            }
        };
    }

    public LoadableModel<GuiObjectDetailsPageType> getObjectDetailsPageConfiguration() {
        return detailsPageConfigurationModel;
    }

    public O getObjectType() {
        return getPrismObject().asObjectable();
    }

    protected boolean isReadonly() {
        return false;
    }

    public ItemStatus getObjectStatus() {
        return objectWrapperModel.getObject().getStatus();
    }

    public SummaryPanelSpecificationType getSummaryPanelSpecification() {
        GuiObjectDetailsPageType detailsPageConfig = detailsPageConfigurationModel.getObject();
        if (detailsPageConfig == null) {
            return null;
        }

        return detailsPageConfig.getSummaryPanel();
    }

    @Override
    public void detach() {
        prismObjectModel.detach();
        objectWrapperModel.detach();
        detailsPageConfigurationModel.detach();
        summaryModel.detach();
    }
}
