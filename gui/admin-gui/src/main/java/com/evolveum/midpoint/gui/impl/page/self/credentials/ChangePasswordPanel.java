/*
 * Copyright (c) 2022 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.gui.impl.page.self.credentials;

import com.evolveum.midpoint.authentication.api.util.AuthUtil;
import com.evolveum.midpoint.gui.api.component.BasePanel;
import com.evolveum.midpoint.gui.api.component.password.PasswordLimitationsPanel;
import com.evolveum.midpoint.gui.api.component.password.PasswordPanel;
import com.evolveum.midpoint.gui.api.component.result.Toast;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.gui.api.util.WebModelServiceUtils;
import com.evolveum.midpoint.model.api.validator.StringLimitationResult;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismObjectDefinition;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.delta.PropertyDelta;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.schema.SchemaRegistry;
import com.evolveum.midpoint.schema.SchemaConstantsGenerated;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.security.api.MidPointPrincipal;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.Producer;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.component.AjaxSubmitButton;
import com.evolveum.midpoint.web.component.progress.ProgressDto;
import com.evolveum.midpoint.web.component.progress.ProgressReporter;
import com.evolveum.midpoint.web.component.util.EnableBehaviour;
import com.evolveum.midpoint.web.component.util.VisibleBehaviour;
import com.evolveum.midpoint.web.page.admin.configuration.component.EmptyOnBlurAjaxFormUpdatingBehaviour;
import com.evolveum.midpoint.web.security.MidPointApplication;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

import com.evolveum.prism.xml.ns._public.types_3.ProtectedStringType;

import org.apache.commons.lang3.StringUtils;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.feedback.FeedbackMessages;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.PasswordTextField;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.Model;

import java.util.*;

public class ChangePasswordPanel<F extends FocusType> extends BasePanel<F> {

    private static final long serialVersionUID = 1L;

    private static final Trace LOGGER = TraceManager.getTrace(ChangePasswordPanel.class);

    private static final String ID_PASSWORD_PANEL = "passwordPanel";
    private static final String ID_CURRENT_PASSWORD_FIELD = "currentPassword";
    private static final String ID_PASSWORD_LABEL = "passwordLabel";
    private static final String ID_CHANGE_PASSWORD = "changePassword";
    private static final String ID_PASSWORD_VALIDATION_PANEL = "passwordValidationPanel";

    private static final String DOT_CLASS = ChangePasswordPanel.class.getName() + ".";
    private static final String OPERATION_VALIDATE_PASSWORD = DOT_CLASS + "validatePassword";
    private static final String OPERATION_LOAD_CREDENTIALS_POLICY = DOT_CLASS + "loadCredentialsPolicy";
    protected static final String OPERATION_CHECK_PASSWORD = DOT_CLASS + "checkPassword";
    private static final String OPERATION_SAVE_PASSWORD = DOT_CLASS + "savePassword";

   protected String currentPasswordValue = null;
   protected ProtectedStringType newPasswordValue = new ProtectedStringType();
   protected LoadableDetachableModel<CredentialsPolicyType> credentialsPolicyModel;
    protected boolean savedPassword = false;
    protected ProgressDto progress = null;

    public ChangePasswordPanel(String id, IModel<F> objectModel) {
        super(id, objectModel);
    }

    protected void onInitialize() {
        super.onInitialize();
        initCredentialsPolicyModel();
        initLayout();
    }

    private void initCredentialsPolicyModel() {
        credentialsPolicyModel = new LoadableDetachableModel<>() {
            private static final long serialVersionUID = 1L;
            @Override
            protected CredentialsPolicyType load() {
                Task task = getPageBase().createSimpleTask(OPERATION_LOAD_CREDENTIALS_POLICY);
                return WebComponentUtil.getPasswordCredentialsPolicy(getModelObject().asPrismObject(), getPageBase(), task);
            }
        };
    }

    private void initLayout() {
        IModel<String> currentPasswordModel = new IModel<String>() {
            @Override
            public String getObject() {
                return currentPasswordValue;
            }

            @Override
            public void setObject(String value) {
                currentPasswordValue = value;
            }
        };
        PasswordTextField currentPasswordField =
                new PasswordTextField(ID_CURRENT_PASSWORD_FIELD, currentPasswordModel);
        currentPasswordField.add(new EmptyOnBlurAjaxFormUpdatingBehaviour());
        currentPasswordField.add(new EnableBehaviour(() -> !savedPassword));
        currentPasswordField.setRequired(false);
        currentPasswordField.setResetPassword(false);
        currentPasswordField.setOutputMarkupId(true);
        add(currentPasswordField);

        Label passwordLabel = new Label(ID_PASSWORD_LABEL, createStringResource("PageSelfCredentials.passwordLabel1"));
        add(passwordLabel);

        PasswordPanel passwordPanel = new PasswordPanel(ID_PASSWORD_PANEL, Model.of(newPasswordValue), false, true, getModelObject().asPrismObject()) {
            private static final long serialVersionUID = 1L;

            @Override
            protected <F extends FocusType> ValuePolicyType getValuePolicy(PrismObject<F> object) {
                return null;//getModelObject().getFocusPolicy();
            }

            @Override
            protected void updatePasswordValidation(AjaxRequestTarget target) {
                super.updatePasswordValidation(target);
                updateNewPasswordValuePerformed(target);
            }

            @Override
            protected boolean canEditPassword() {
                return !savedPassword;
            }

            @Override
            protected boolean isRemovePasswordVisible() {
                return false;
            }
        };
        passwordPanel.getBaseFormComponent().add(new AttributeModifier("autofocus", ""));
        add(passwordPanel);

        LoadableDetachableModel<List<StringLimitationResult>> limitationsModel = new LoadableDetachableModel<>() {
            private static final long serialVersionUID = 1L;
            @Override
            protected List<StringLimitationResult> load() {
                return getLimitationsForActualPassword(newPasswordValue);
            }
        };

        PasswordLimitationsPanel passwordLimitationsPanel = new PasswordLimitationsPanel(ID_PASSWORD_VALIDATION_PANEL, limitationsModel);
        passwordLimitationsPanel.setOutputMarkupId(true);
        add(passwordLimitationsPanel);

        AjaxSubmitButton changePasswordButton = new AjaxSubmitButton(ID_CHANGE_PASSWORD,
                createStringResource("ChangePasswordPanel.changePasswordButton")) {

            private static final long serialVersionUID = 1L;

            @Override
            public void onError(AjaxRequestTarget target) {
//                target.add(getPageBase().getFeedbackPanel());
                FeedbackMessages messages = getPageBase().getFeedbackMessages();
                if (messages != null && !messages.isEmpty()) {
                    new Toast()
                            .cssClass("bg-danger m3")
                            .autohide(false)
                            .title(messages.first().getMessage().toString())
                            .show(target);
                }
            }

            @Override
            public void onSubmit(AjaxRequestTarget target) {
                changePasswordPerformed(target);
            }
        };
        changePasswordButton.add(new VisibleBehaviour(() -> !savedPassword));
        changePasswordButton.setOutputMarkupId(true);
        add(changePasswordButton);

    }

    protected void updateNewPasswordValuePerformed(AjaxRequestTarget target) {
        target.add(get(ID_PASSWORD_VALIDATION_PANEL));
    }

    private List<StringLimitationResult> getLimitationsForActualPassword(ProtectedStringType passwordValue) {
        ValuePolicyType valuePolicy = getValuePolicy();
        if (valuePolicy != null) {
            Task task = getPageBase().createAnonymousTask(OPERATION_VALIDATE_PASSWORD);
            try {
                return getPageBase().getModelInteractionService().validateValue(passwordValue == null ? new ProtectedStringType() : passwordValue,
                        valuePolicy, getModelObject().asPrismObject(), task, task.getResult());
            } catch (Exception e) {
                LOGGER.error("Couldn't validate password security policy", e);
            }
        }
        return new ArrayList<>();
    }

    protected boolean isCheckOldPassword() {
        return (getPasswordChangeSecurity() == null) ||
                (getPasswordChangeSecurity().equals(PasswordChangeSecurityType.OLD_PASSWORD) ||
                        (getPasswordChangeSecurity().equals(PasswordChangeSecurityType.OLD_PASSWORD_IF_EXISTS) &&
                                getModelObject().asPrismObject()
                                .findProperty(ItemPath.create(FocusType.F_CREDENTIALS, CredentialsType.F_PASSWORD, PasswordType.F_VALUE)) != null));
    }

    protected <F extends FocusType> ValuePolicyType getValuePolicy() {
        ValuePolicyType valuePolicyType = null;
        try {
            MidPointPrincipal user = AuthUtil.getPrincipalUser();
            if (getPageBase() != null) {
                if (user != null) {
                    Task task = getPageBase().createSimpleTask("load value policy");
                    valuePolicyType = getSearchValuePolicy(task);
                } else {
                    valuePolicyType = getPageBase().getSecurityContextManager().runPrivileged((Producer<ValuePolicyType>) () -> {
                        Task task = getPageBase().createAnonymousTask("load value policy");
                        return getSearchValuePolicy(task);
                    });
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Couldn't load security policy for focus " + getModelObject().asPrismObject(), e);
        }
        return valuePolicyType;
    }

    private ValuePolicyType getSearchValuePolicy(Task task) {
        CredentialsPolicyType credentialsPolicy = credentialsPolicyModel.getObject();
        if (credentialsPolicy != null && credentialsPolicy.getPassword() != null
                && credentialsPolicy.getPassword().getValuePolicyRef() != null) {
            PrismObject<ValuePolicyType> valuePolicy = WebModelServiceUtils.resolveReferenceNoFetch(
                    credentialsPolicy.getPassword().getValuePolicyRef(), getPageBase(), task, task.getResult());
            if (valuePolicy != null) {
                return valuePolicy.asObjectable();
            }
        }
        return null;
    }

    private PasswordChangeSecurityType getPasswordChangeSecurity() {
        CredentialsPolicyType credentialsPolicy = credentialsPolicyModel.getObject();
        return credentialsPolicy != null && credentialsPolicy.getPassword() != null ?
                credentialsPolicy.getPassword().getPasswordChangeSecurity() : null;
    }


    private void changePasswordPerformed(AjaxRequestTarget target) {
        ProtectedStringType currentPassword = null;
        if (isCheckOldPassword()) {
            LOGGER.debug("Check old password");
            if (currentPasswordValue == null || currentPasswordValue.trim().equals("")) {
//                warn(getString("PageSelfCredentials.specifyOldPasswordMessage"));
//                target.add(getPageBase().getFeedbackPanel());
                new Toast()
                        .cssClass("bg-warning m3")
                        .autohide(false)
                        .title(getString("PageSelfCredentials.specifyOldPasswordMessage"))
                        .show(target);
                return;
            } else {
                OperationResult checkPasswordResult = new OperationResult(OPERATION_CHECK_PASSWORD);
                Task checkPasswordTask = getPageBase().createSimpleTask(OPERATION_CHECK_PASSWORD);
                try {
                    currentPassword = new ProtectedStringType();
                    currentPassword.setClearValue(currentPasswordValue);
                    boolean isCorrectPassword = getPageBase().getModelInteractionService().checkPassword(getModelObject().getOid(), currentPassword,
                            checkPasswordTask, checkPasswordResult);
                    if (!isCorrectPassword) {
//                        error(getString("PageSelfCredentials.incorrectOldPassword"));
//                        target.add(getPageBase().getFeedbackPanel());
                        new Toast()
                                .cssClass("bg-danger m3")
                                .autohide(false)
                                .title(getString("PageSelfCredentials.incorrectOldPassword"))
                                .show(target);
                        return;
                    }
                } catch (Exception ex) {
                    LoggingUtils.logUnexpectedException(LOGGER, "Couldn't check password", ex);
                    checkPasswordResult.recordFatalError(
                            getString("PageAbstractSelfCredentials.message.onSavePerformed.fatalError", ex.getMessage()), ex);
//                    target.add(getPageBase().getFeedbackPanel());
                    new Toast()
                            .cssClass("bg-danger m3")
                            .autohide(false)
                            .title(getString("PageAbstractSelfCredentials.message.onSavePerformed.fatalError"))
                            .show(target);
                    return;
                } finally {
                    checkPasswordResult.computeStatus();
                }
            }
        }

        if (newPasswordValue == null || StringUtils.isEmpty(newPasswordValue.getClearValue())) {
//            warn(getString("PageSelfCredentials.emptyPasswordFiled"));
//            target.add(getPageBase().getFeedbackPanel());
            new Toast()
                    .cssClass("bg-warning m3")
                    .autohide(false)
                    .title(getString("PageSelfCredentials.emptyPasswordFiled"))
                    .show(target);
            return;
        }

        OperationResult result = new OperationResult(OPERATION_SAVE_PASSWORD);
        ProgressReporter reporter = new ProgressReporter(MidPointApplication.get());
        reporter.getProgress().clear();
        reporter.setWriteOpResultForProgressActivity(true);

        reporter.recordExecutionStart();
        boolean showFeedback = true;
        try {
            if (!newPasswordValue.isEncrypted()) {
                WebComponentUtil.encryptProtectedString(newPasswordValue, true, getPageBase().getMidpointApplication());
            }
            Collection<ObjectDelta<? extends ObjectType>> deltas = new ArrayList<>();
            ItemPath valuePath = ItemPath.create(SchemaConstantsGenerated.C_CREDENTIALS,
                    CredentialsType.F_PASSWORD, PasswordType.F_VALUE);
            collectDeltas(deltas, newPasswordValue, valuePath);
            getPageBase().getModelService().executeChanges(
                    deltas, null, getPageBase().createSimpleTask(OPERATION_SAVE_PASSWORD, SchemaConstants.CHANNEL_SELF_SERVICE_URI),
                    Collections.singleton(reporter), result);
            result.computeStatus();
        } catch (Exception ex) {
            setNullEncryptedPasswordData();
            LoggingUtils.logUnexpectedException(LOGGER, "Couldn't save password changes", ex);
            result.recordFatalError(getString("PageAbstractSelfCredentials.save.password.failed", ex.getMessage()), ex);
        } finally {
            reporter.recordExecutionStop();
            progress = reporter.getProgress();
            result.computeStatusIfUnknown();

            if (!result.isError()) {
                this.savedPassword = true;
                target.add(ChangePasswordPanel.this);
            }
        }

        finishChangePassword(result, target, showFeedback);
    }

    protected void collectDeltas(Collection<ObjectDelta<? extends ObjectType>> deltas, ProtectedStringType currentPassword, ItemPath valuePath) {
        SchemaRegistry registry = getPrismContext().getSchemaRegistry();

        PrismObjectDefinition<UserType> objDef = registry.findObjectDefinitionByCompileTimeClass(UserType.class);

        PropertyDelta<ProtectedStringType> delta = getPrismContext().deltaFactory().property()
                .createModificationReplaceProperty(valuePath, objDef, newPasswordValue);
        if (currentPassword != null) {
            delta.addEstimatedOldValue(getPrismContext().itemFactory().createPropertyValue(currentPassword));
        }
        deltas.add(getPrismContext().deltaFactory().object().createModifyDelta(getModelObject().getOid(), delta, UserType.class));
    }

    protected void setNullEncryptedPasswordData() {
        if (newPasswordValue != null) {
            newPasswordValue.setEncryptedData(null);
        }
    }

    protected void finishChangePassword(OperationResult result, AjaxRequestTarget target, boolean showFeedback) {
        if (!WebComponentUtil.isSuccessOrHandledError(result)) {
            setNullEncryptedPasswordData();
            if (showFeedback) {
//                getPageBase().showResult(result);
//                target.add(getPageBase().getFeedbackPanel());
                new Toast()
                        .cssClass("bg-warning m3")
                        .autohide(false)
                        .title(getString(result.getMessage()))
                        .show(target);
            }
        } else {
            new Toast()
                    .cssClass("bg-info m3")
                    .autohide(false)
                    .title(getString(result.getMessage()))
                    .show(target);
//            target.add(getPageBase().getFeedbackPanel());
        }
    }

}
