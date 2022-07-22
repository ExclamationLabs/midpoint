/*
 * Copyright (c) 2022 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.gui.impl.page.self.requestAccess;

import static com.evolveum.midpoint.xml.ns._public.common.common_3.PartialProcessingTypeType.SKIP;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;
import javax.xml.datatype.Duration;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;

import org.apache.commons.lang3.StringUtils;

import com.evolveum.midpoint.gui.api.page.PageBase;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.gui.api.util.WebModelServiceUtils;
import com.evolveum.midpoint.model.api.ModelExecuteOptions;
import com.evolveum.midpoint.model.api.context.*;
import com.evolveum.midpoint.prism.PrismContainerDefinition;
import com.evolveum.midpoint.prism.PrismContainerValue;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.delta.ContainerDelta;
import com.evolveum.midpoint.prism.delta.DeltaSetTriple;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.xml.XmlTypeConverter;
import com.evolveum.midpoint.schema.constants.ObjectTypes;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.MiscUtil;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.page.admin.users.component.ExecuteChangeOptionsDto;
import com.evolveum.midpoint.web.security.MidPointApplication;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

/**
 * Created by Viliam Repan (lazyman).
 */
public class RequestAccess implements Serializable {

    private static final Trace LOGGER = TraceManager.getTrace(RequestAccess.class);

    private static final String DOT_CLASS = RequestAccess.class.getName() + ".";
    private static final String OPERATION_COMPUTE_ALL_CONFLICTS = DOT_CLASS + "computeAllConflicts";
    private static final String OPERATION_COMPUTE_CONFLICT = DOT_CLASS + "computeConflicts";
    private static final String OPERATION_REQUEST_ASSIGNMENTS = DOT_CLASS + "requestAssignments";
    private static final String OPERATION_REQUEST_ASSIGNMENTS_SINGLE = DOT_CLASS + "requestAssignmentsSingle";

    public static final List<ValidityPredefinedValueType> DEFAULT_VALIDITY_PERIODS = Arrays.asList(
            new ValidityPredefinedValueType()
                    .duration(XmlTypeConverter.createDuration("P1D"))
                    .display(new DisplayType().label("RequestAccess.validity1Day")),
            new ValidityPredefinedValueType()
                    .duration(XmlTypeConverter.createDuration("P7D"))
                    .display(new DisplayType().label("RequestAccess.validity1Week")),
            new ValidityPredefinedValueType()
                    .duration(XmlTypeConverter.createDuration("P1M"))
                    .display(new DisplayType().label("RequestAccess.validity1Month")),
            new ValidityPredefinedValueType()
                    .duration(XmlTypeConverter.createDuration("P1Y"))
                    .display(new DisplayType().label("RequestAccess.validity1Year"))
    );

    public static final String VALIDITY_CUSTOM_LENGTH = "validityCustomLength";

    public static final String VALIDITY_CUSTOM_FOR_EACH = "validityCustomForEach";

    private Map<ObjectReferenceType, List<AssignmentType>> requestItems = new HashMap<>();

    /**
     * This map contains existing assignments from users that have to be removed to avoid conflicts when requesting
     */
    private Map<ObjectReferenceType, List<AssignmentType>> requestItemsExistingToRemove = new HashMap<>();

    private Set<AssignmentType> selectedAssignments = new HashSet<>();

    private QName relation;

    private QName defaultRelation = SchemaConstants.ORG_DEFAULT;

    /**
     * Used as backing field for combobox model. It can contain different values - string keys (custom length label), predefined values
     */
    private Object selectedValidity;

    private Duration validity;

    private String comment;

    private boolean conflictsDirty;

    private List<Conflict> conflicts;

    public Object getSelectedValidity() {
        return selectedValidity;
    }

    public void setSelectedValidity(Object selectedValidity) {
        if (selectedValidity instanceof ValidityPredefinedValueType) {
            ValidityPredefinedValueType predefined = (ValidityPredefinedValueType) selectedValidity;
            setValidity(predefined.getDuration());
        } else {
            setValidity(null);
        }

        this.selectedValidity = selectedValidity;
    }

    public Set<AssignmentType> getSelectedAssignments() {
        return Collections.unmodifiableSet(selectedAssignments);
    }

    public List<Conflict> getConflicts() {
        if (conflicts == null) {
            conflicts = new ArrayList<>();
        }
        return conflicts;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public List<ObjectReferenceType> getPersonOfInterest() {
        return List.copyOf(new ArrayList<>(requestItems.keySet()));
    }

    public void addPersonOfInterest(ObjectReferenceType ref) {
        addPersonOfInterest(List.of(ref));
    }

    public void addPersonOfInterest(List<ObjectReferenceType> refs) {
        if (refs == null) {
            return;
        }

        Set<String> newOids = refs.stream().map(o -> o.getOid()).collect(Collectors.toSet());

        Set<ObjectReferenceType> existing = new HashSet<>(requestItems.keySet());
        Set<String> existingOids = existing.stream().map(o -> o.getOid()).collect(Collectors.toSet());

        boolean changed = false;

        // add new persons if they're not in set yet
        for (ObjectReferenceType ref : refs) {
            if (existingOids.contains(ref.getOid())) {
                continue;
            }

            List<AssignmentType> assignments = this.selectedAssignments.stream().map(a -> a.clone()).collect(Collectors.toList());
            requestItems.put(ref, assignments);

            changed = true;
        }

        // remove persons that were not selected (or were unselected)
        for (ObjectReferenceType ref : existing) {
            if (newOids.contains(ref.getOid())) {
                continue;
            }

            requestItems.remove(ref);

            changed = true;
        }

        if (changed) {
            markConflictsDirty();
        }
    }

    public void addAssignments(List<AssignmentType> assignments) {
        if (assignments == null || assignments.isEmpty()) {
            return;
        }

        assignments.stream()
                .filter(a -> !selectedAssignments.contains(a))
                .forEach(a -> selectedAssignments.add(a.clone()));

        for (List<AssignmentType> list : requestItems.values()) {
            assignments.forEach(a -> list.add(a.clone()));
        }

        markConflictsDirty();
    }

    public void removeAssignments(List<AssignmentType> assignments) {
        if (assignments == null || assignments.isEmpty()) {
            return;

        }
        for (AssignmentType a : assignments) {
            this.selectedAssignments.remove(a);

            for (List<AssignmentType> list : requestItems.values()) {
                list.remove(a);
            }
        }

        markConflictsDirty();
    }

    public List<AssignmentType> getShoppingCartAssignments() {
        Set<AssignmentType> assignments = new HashSet<>();

        requestItems.values().stream().forEach(list -> assignments.addAll(list));

        return List.copyOf(assignments);
    }

    public List<AssignmentType> getShoppingCartAssignments(ObjectReferenceType personOfInterestRef) {
        return getAssignments(personOfInterestRef, requestItems);
    }

    private List<AssignmentType> getAssignmentsToBeRemoved(ObjectReferenceType personOfInterestRef) {
        return getAssignments(personOfInterestRef, requestItemsExistingToRemove);
    }

    private List<AssignmentType> getAssignments(ObjectReferenceType personOfInterestRef, Map<ObjectReferenceType, List<AssignmentType>> map) {
        if (personOfInterestRef == null) {
            return new ArrayList<>();
        }

        List<AssignmentType> assignments = map.get(personOfInterestRef);
        return assignments != null ? assignments : new ArrayList<>();
    }

    public List<RequestAccessItem> getRequestAccessItems() {
        return requestItems.entrySet().stream()
                .map(e -> new RequestAccessItem(e.getKey(), e.getValue()))
                .sorted()
                .collect(Collectors.toUnmodifiableList());
    }

    public List<ShoppingCartItem> getShoppingCartItems() {
        return getShoppingCartAssignmentCounts().entrySet().stream()
                .map(e -> new ShoppingCartItem(e.getKey(), e.getValue()))
                .sorted()
                .collect(Collectors.toUnmodifiableList());
    }

    private Map<AssignmentType, Integer> getShoppingCartAssignmentCounts() {
        Map<AssignmentType, Integer> counts = new HashMap<>();

        for (ObjectReferenceType ref : requestItems.keySet()) {
            List<AssignmentType> assignments = requestItems.get(ref);
            for (AssignmentType a : assignments) {
                Integer count = counts.get(a);
                if (count == null) {
                    count = 0;
                    counts.put(a, count);
                }

                counts.replace(a, count + 1);
            }
        }

        return counts;
    }

    public QName getRelation() {
        return relation;
    }

    public void setRelation(QName relation) {
        if (relation == null) {
            relation = defaultRelation;
        }
        this.relation = relation;

        selectedAssignments.forEach(a -> a.getTargetRef().setRelation(this.relation));
        for (List<AssignmentType> list : requestItems.values()) {
            list.forEach(a -> a.getTargetRef().setRelation(this.relation));
        }

        markConflictsDirty();
    }

    public QName getDefaultRelation() {
        return defaultRelation;
    }

    public void setDefaultRelation(QName defaultRelation) {
        if (defaultRelation == null) {
            defaultRelation = SchemaConstants.ORG_DEFAULT;
        }
        this.defaultRelation = defaultRelation;
    }

    public long getWarningCount() {
        return getConflicts().stream().filter(c -> c.isWarning() && c.getState() != ConflictState.SOLVED).count();
    }

    public long getErrorCount() {
        return getConflicts().stream().filter(c -> !c.isWarning() && c.getState() != ConflictState.SOLVED).count();
    }

    public void clearCart() {
        requestItems.clear();
        requestItemsExistingToRemove.clear();

        selectedAssignments.clear();
        relation = null;

        selectedValidity = null;
        validity = null;

        comment = null;

        conflictsDirty = false;
        conflicts.clear();
    }

    public boolean canSubmit() {
        return getErrorCount() == 0 && !requestItems.isEmpty();
    }

    private void markConflictsDirty() {
        conflictsDirty = true;
    }

    public void computeConflicts(PageBase page) {
        if (!conflictsDirty) {
            return;
        }

        Task task = page.createSimpleTask(OPERATION_COMPUTE_ALL_CONFLICTS);
        OperationResult result = task.getResult();

        List<Conflict> allConflicts = new ArrayList<>();
        for (ObjectReferenceType ref : getPersonOfInterest()) {
            List<Conflict> conflicts = computeConflictsForOnePerson(ref, task, page);
            allConflicts.addAll(conflicts);
        }

        result.computeStatusIfUnknown();
        if (!WebComponentUtil.isSuccessOrHandledError(result)) {
            page.error(result);
        }

        this.conflicts = allConflicts;
        conflictsDirty = false;
    }

    private ObjectReferenceType createRef(PrismObject object) {
        if (object == null) {
            return null;
        }

        ObjectType obj = (ObjectType) object.asObjectable();

        return new ObjectReferenceType()
                .oid(object.getOid())
                .type(ObjectTypes.getObjectType(obj.getClass()).getTypeQName())
                .targetName(obj.getName());
    }

    public List<Conflict> computeConflictsForOnePerson(ObjectReferenceType ref, Task task, PageBase page) {
        OperationResult result = task.getResult().createSubresult(OPERATION_COMPUTE_CONFLICT);
        try {
            PrismObject<UserType> user = WebModelServiceUtils.loadObject(ref, page);
            ObjectReferenceType userRef = createRef(user);

            ObjectDelta<UserType> delta = createUserDelta(user);

            PartialProcessingOptionsType processing = new PartialProcessingOptionsType();
            processing.setInbound(SKIP);
            processing.setProjection(SKIP);

            ModelExecuteOptions options = ModelExecuteOptions.create().partialProcessing(processing);

            MidPointApplication mp = MidPointApplication.get();
            ModelContext<UserType> ctx = mp.getModelInteractionService()
                    .previewChanges(MiscUtil.createCollection(delta), options, task, result);

            DeltaSetTriple<? extends EvaluatedAssignment> evaluatedAssignmentTriple = ctx.getEvaluatedAssignmentTriple();

            Map<String, Conflict> conflicts = new HashMap<>();
            if (evaluatedAssignmentTriple != null) {
                Collection<? extends EvaluatedAssignment> addedAssignments = evaluatedAssignmentTriple.getPlusSet();
                for (EvaluatedAssignment<UserType> evaluatedAssignment : addedAssignments) {
                    for (EvaluatedPolicyRule policyRule : evaluatedAssignment.getAllTargetsPolicyRules()) {
                        if (!policyRule.containsEnabledAction()) {
                            continue;
                        }

                        // everything other than 'enforce' is a warning
                        boolean warning = !policyRule.containsEnabledAction(EnforcementPolicyActionType.class);

                        createConflicts(userRef, conflicts, evaluatedAssignment, policyRule.getAllTriggers(), warning);
                    }
                }
            } else if (!result.isSuccess() && StringUtils.isNotEmpty(getSubresultWarningMessages(result))) {
                String msg = page.getString("PageAssignmentsList.conflictsWarning", getSubresultWarningMessages(result));
                page.warn(msg);
            }
            return new ArrayList(conflicts.values());
        } catch (Exception ex) {
            LoggingUtils.logUnexpectedException(LOGGER, "Couldn't get assignments conflicts. Reason: ", ex);

            result.recordFatalError("Couldn't get assignments conflicts", ex);
        } finally {
            result.computeStatusIfUnknown();
        }

        return new ArrayList<>();
    }

    private <F extends FocusType> void createConflicts(ObjectReferenceType userRef, Map<String, Conflict> conflicts, EvaluatedAssignment<UserType> evaluatedAssignment,
            EvaluatedExclusionTrigger trigger, boolean warning) {

        EvaluatedAssignment<F> conflictingAssignment = trigger.getConflictingAssignment();
        PrismObject<F> addedAssignmentTargetObj = (PrismObject<F>) evaluatedAssignment.getTarget();
        PrismObject<F> exclusionTargetObj = (PrismObject<F>) conflictingAssignment.getTarget();

        final String key = StringUtils.join(addedAssignmentTargetObj.getOid(), "/", exclusionTargetObj.getOid());
        final String alternateKey = StringUtils.join(exclusionTargetObj.getOid(), "/", addedAssignmentTargetObj.getOid());

        ConflictItem added = new ConflictItem(evaluatedAssignment.getAssignment(), WebComponentUtil.getDisplayNameOrName(addedAssignmentTargetObj),
                evaluatedAssignment.getAssignment(true) != null);
        ConflictItem exclusion = new ConflictItem(conflictingAssignment.getAssignment(), WebComponentUtil.getDisplayNameOrName(exclusionTargetObj),
                conflictingAssignment.getAssignment(true) != null);

        String message = null;
        if (trigger.getMessage() != null) {
            MidPointApplication mp = MidPointApplication.get();
            message = mp.getLocalizationService().translate(trigger.getMessage());
        }

        Conflict conflict = new Conflict(userRef, added, exclusion, message, warning);

        if (!conflicts.containsKey(key) && !conflicts.containsKey(alternateKey)) {
            conflicts.put(key, conflict);
        } else if (!warning) {
            if (conflicts.containsKey(key)) {
                conflicts.replace(key, conflict);
            }
            if (conflicts.containsKey(alternateKey)) {
                conflicts.replace(alternateKey, conflict);
            }
        }
    }

    private void createConflicts(ObjectReferenceType userRef, Map<String, Conflict> conflicts, EvaluatedAssignment<UserType> evaluatedAssignment,
            Collection<EvaluatedPolicyRuleTrigger<?>> triggers, boolean warning) {

        for (EvaluatedPolicyRuleTrigger<?> trigger : triggers) {
            if (trigger instanceof EvaluatedExclusionTrigger) {
                createConflicts(userRef, conflicts, evaluatedAssignment, (EvaluatedExclusionTrigger) trigger, warning);
            } else if (trigger instanceof EvaluatedCompositeTrigger) {
                EvaluatedCompositeTrigger compositeTrigger = (EvaluatedCompositeTrigger) trigger;
                Collection<EvaluatedPolicyRuleTrigger<?>> innerTriggers = compositeTrigger.getInnerTriggers();
                createConflicts(userRef, conflicts, evaluatedAssignment, innerTriggers, warning);
            }
        }
    }

    private String getSubresultWarningMessages(OperationResult result) {
        if (result == null || result.getSubresults() == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        result.getSubresults().forEach(subresult -> {
            if (subresult.isWarning()) {
                sb.append(subresult.getMessage());
                sb.append("\n");
            }
        });
        return sb.toString();
    }

    private ObjectDelta<UserType> createUserDelta(PrismObject<UserType> user) throws SchemaException {

        ObjectReferenceType userRef = createRef(user);
        List<AssignmentType> assignmentsToAdd = getShoppingCartAssignments(userRef);
        List<AssignmentType> assignmentsToRemove = getAssignmentsToBeRemoved(userRef);

        ObjectDelta<UserType> delta = user.createModifyDelta();

        PrismContainerDefinition def = user.getDefinition().findContainerDefinition(UserType.F_ASSIGNMENT);

        addAssignmentDeltas(delta, assignmentsToAdd, def, true);
        addAssignmentDeltas(delta, assignmentsToRemove, def, false);

        return delta;
    }

    private ContainerDelta addAssignmentDeltas(ObjectDelta<UserType> focusDelta, List<AssignmentType> assignments,
            PrismContainerDefinition def, boolean addAssignments) throws SchemaException {

        PrismContext ctx = def.getPrismContext();
        //todo use this probably def.createEmptyDelta(ItemPath.EMPTY_PATH)
        ContainerDelta delta = ctx.deltaFactory().container().create(ItemPath.EMPTY_PATH, def.getItemName(), def);

        for (AssignmentType a : assignments) {
            PrismContainerValue newValue = a.asPrismContainerValue();

            newValue.applyDefinition(def, false);
            if (addAssignments) {
                delta.addValueToAdd(newValue.clone());
            } else {
                delta.addValueToDelete(newValue.clone());
            }
        }

        if (!delta.isEmpty()) {
            delta = focusDelta.addModification(delta);
        }

        return delta;
    }

    private void addExistingToBeRemoved(ObjectReferenceType ref, AssignmentType assignment) {
        List<AssignmentType> list = requestItemsExistingToRemove.get(ref);
        if (list == null) {
            list = new ArrayList<>();
        }
        requestItemsExistingToRemove.put(ref, list);

        list.add(assignment);
    }

    public void solveConflict(Conflict conflict, ConflictItem toRemove) {
        if (toRemove.isExisting()) {
            addExistingToBeRemoved(conflict.getPersonOfInterest(), toRemove.getAssignment());
        }

        List<AssignmentType> assignments = requestItems.get(conflict.getPersonOfInterest());
        assignments.remove(toRemove.getAssignment());

        conflict.setState(ConflictState.SOLVED);

        markConflictsDirty();
    }

    public boolean isAllConflictsSolved() {
        return getConflicts().stream().filter(c -> c.getState() == ConflictState.UNRESOLVED).count() == 0;
    }

    public OperationResult submitRequest(PageBase page) {
        int usersCount = requestItems.keySet().size();
        if (usersCount == 0) {
            return null;
        }

        Task task = page.createSimpleTask(OPERATION_REQUEST_ASSIGNMENTS);
        OperationResult result = task.getResult();

        for (ObjectReferenceType poiRef : requestItems.keySet()) {
            OperationResult subresult = result.createSubresult(OPERATION_REQUEST_ASSIGNMENTS_SINGLE);

            ObjectDelta<UserType> delta;
            try {
                PrismObject<UserType> user = WebModelServiceUtils.loadObject(poiRef, page);
                delta = createUserDelta(user);

                // todo add async flag
                ModelExecuteOptions options = createSubmitModelOptions(page.getPrismContext());
                options.initialPartialProcessing(new PartialProcessingOptionsType().inbound(SKIP).projection(SKIP));
                page.getModelService().executeChanges(Collections.singletonList(delta), options, task, subresult);

                subresult.recordSuccess();
            } catch (Exception e) {
                subresult.recordFatalError(e);
                subresult.setMessage(page.createStringResource("PageAssignmentsList.requestError").getString());
                LoggingUtils.logUnexpectedException(LOGGER, "Could not save assignments ", e);
            } finally {
                subresult.recomputeStatus();
            }
        }

        result.computeStatusIfUnknown();

        return result;
    }

    private ModelExecuteOptions createSubmitModelOptions(PrismContext ctx) {
        OperationBusinessContextType businessContextType;

        if (StringUtils.isNotEmpty(comment)) {
            businessContextType = new OperationBusinessContextType();
            businessContextType.setComment(comment);
        } else {
            businessContextType = null;
        }

        ModelExecuteOptions options = ExecuteChangeOptionsDto.createFromSystemConfiguration().createOptions(ctx);
        options.requestBusinessContext(businessContextType);

        return options;
    }

    public Duration getValidity() {
        return validity;
    }

    public void setValidity(Duration validity) {
        if (Objects.equals(this.validity, validity)) {
            return;
        }

        this.validity = validity;

        XMLGregorianCalendar from = validity != null ? XmlTypeConverter.createXMLGregorianCalendar() : null;

        XMLGregorianCalendar to = null;
        if (validity != null) {
            to = XmlTypeConverter.createXMLGregorianCalendar(from);
            to.add(validity);
        }

        setValidity(from, to);
    }

    public void setValidity(XMLGregorianCalendar from, XMLGregorianCalendar to) {
        for (List<AssignmentType> list : requestItems.values()) {
            list.forEach(a -> {
                if (from == null && to == null) {
                    a.setActivation(null);
                } else {
                    ActivationType activation = a.getActivation();
                    if (activation == null) {
                        activation = new ActivationType();
                        a.setActivation(activation);
                    }

                    activation.validFrom(from).validTo(to);
                }
            });
        }

        markConflictsDirty();
    }
}
