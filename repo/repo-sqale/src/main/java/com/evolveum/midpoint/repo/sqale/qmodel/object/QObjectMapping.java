/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.repo.sqale.qmodel.object;

import static com.evolveum.midpoint.xml.ns._public.common.common_3.AssignmentHolderType.*;

import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import com.evolveum.midpoint.prism.*;
import com.evolveum.midpoint.prism.path.ItemName;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.path.PathSet;
import com.evolveum.midpoint.prism.path.UniformItemPath;
import com.evolveum.midpoint.repo.sqlbase.SqlBaseOperationTracker;
import com.evolveum.midpoint.repo.sqale.mapping.SqaleMappingMixin;
import com.evolveum.midpoint.repo.sqale.qmodel.common.*;

import com.evolveum.midpoint.repo.sqale.qmodel.ref.MReference;
import com.evolveum.midpoint.repo.sqale.qmodel.ref.QReference;
import com.evolveum.midpoint.repo.sqale.qmodel.ref.QReferenceMapping;
import com.evolveum.midpoint.repo.sqlbase.SqlQueryContext;
import com.evolveum.midpoint.repo.sqlbase.mapping.ResultListRowTransformer;

import com.evolveum.midpoint.repo.sqlbase.querydsl.FlexibleRelationalPathBase;
import com.evolveum.midpoint.schema.RetrieveOption;
import com.evolveum.midpoint.util.exception.SystemException;

import com.google.common.collect.*;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.Predicate;
import org.jetbrains.annotations.NotNull;

import com.evolveum.midpoint.repo.api.RepositoryObjectDiagnosticData;
import com.evolveum.midpoint.repo.api.RepositoryService;
import com.evolveum.midpoint.repo.sqale.SqaleRepoContext;
import com.evolveum.midpoint.repo.sqale.SqaleUtils;
import com.evolveum.midpoint.repo.sqale.mapping.SqaleTableMapping;
import com.evolveum.midpoint.repo.sqale.qmodel.ext.MExtItemHolderType;
import com.evolveum.midpoint.repo.sqale.qmodel.focus.QUserMapping;
import com.evolveum.midpoint.repo.sqale.qmodel.org.QOrgMapping;
import com.evolveum.midpoint.repo.sqale.qmodel.ref.QObjectReferenceMapping;
import com.evolveum.midpoint.repo.sqlbase.JdbcSession;
import com.evolveum.midpoint.repo.sqlbase.mapping.RepositoryMappingException;
import com.evolveum.midpoint.schema.GetOperationOptions;
import com.evolveum.midpoint.schema.SelectorOptions;
import com.evolveum.midpoint.util.MiscUtil;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.MetadataType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectReferenceType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.OperationExecutionType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.TriggerType;
import com.evolveum.prism.xml.ns._public.types_3.PolyStringType;

import org.jetbrains.annotations.VisibleForTesting;

import javax.xml.namespace.QName;

/**
 * Mapping between {@link QObject} and {@link ObjectType}.
 *
 * @param <S> schema type of the object
 * @param <Q> type of entity path
 * @param <R> row type related to the {@link Q}
 */
public class QObjectMapping<S extends ObjectType, Q extends QObject<R>, R extends MObject>
        extends SqaleTableMapping<S, Q, R> {

    public static final String DEFAULT_ALIAS_NAME = "o";

    private static QObjectMapping<?, ?, ?> instance;
    private PathSet fullObjectSkips;

    // Explanation in class Javadoc for SqaleTableMapping
    public static QObjectMapping<?, ?, ?> initObjectMapping(@NotNull SqaleRepoContext repositoryContext) {
        instance = new QObjectMapping<>(
                QObject.TABLE_NAME, DEFAULT_ALIAS_NAME,
                ObjectType.class, QObject.CLASS,
                repositoryContext);
        return instance;
    }

    private Map<ItemName, FullObjectItemMapping> separatellySerializedItems = new HashMap<>();

    // Explanation in class Javadoc for SqaleTableMapping
    public static QObjectMapping<?, ?, ?> getObjectMapping() {
        return Objects.requireNonNull(instance);
    }


    private boolean storeSplitted = true;


    protected QObjectMapping(
            @NotNull String tableName,
            @NotNull String defaultAliasName,
            @NotNull Class<S> schemaType,
            @NotNull Class<Q> queryType,
            @NotNull SqaleRepoContext repositoryContext) {
        super(tableName, defaultAliasName, schemaType, queryType, repositoryContext);

        addItemMapping(PrismConstants.T_ID, uuidMapper(q -> q.oid));
        addItemMapping(F_NAME, polyStringMapper(
                q -> q.nameOrig, q -> q.nameNorm));
        addRefMapping(F_TENANT_REF,
                q -> q.tenantRefTargetOid,
                q -> q.tenantRefTargetType,
                q -> q.tenantRefRelationId,
                QOrgMapping::getOrgMapping);

        addRefMapping(F_EFFECTIVE_MARK_REF,
                QObjectReferenceMapping.initForEffectiveMark(repositoryContext));

        addItemMapping(F_LIFECYCLE_STATE, stringMapper(q -> q.lifecycleState));
        // version/cidSeq is not mapped for queries or deltas, it's managed by repo explicitly

        addItemMapping(F_POLICY_SITUATION, multiUriMapper(q -> q.policySituations));
        addItemMapping(F_SUBTYPE, multiStringMapper(q -> q.subtypes));
        // full-text is not item mapping, but filter on the whole object
        addExtensionMapping(F_EXTENSION, MExtItemHolderType.EXTENSION, q -> q.ext);

        addNestedMapping(F_METADATA, MetadataType.class)
                .addRefMapping(MetadataType.F_CREATOR_REF,
                        q -> q.creatorRefTargetOid,
                        q -> q.creatorRefTargetType,
                        q -> q.creatorRefRelationId,
                        QUserMapping::getUserMapping)
                .addItemMapping(MetadataType.F_CREATE_CHANNEL,
                        uriMapper(q -> q.createChannelId))
                .addItemMapping(MetadataType.F_CREATE_TIMESTAMP,
                        timestampMapper(q -> q.createTimestamp))
                .addRefMapping(MetadataType.F_MODIFIER_REF,
                        q -> q.modifierRefTargetOid,
                        q -> q.modifierRefTargetType,
                        q -> q.modifierRefRelationId,
                        QUserMapping::getUserMapping)
                .addItemMapping(MetadataType.F_MODIFY_CHANNEL,
                        uriMapper(q -> q.modifyChannelId))
                .addItemMapping(MetadataType.F_MODIFY_TIMESTAMP,
                        timestampMapper(q -> q.modifyTimestamp))
                .addRefMapping(MetadataType.F_CREATE_APPROVER_REF,
                        QObjectReferenceMapping.initForCreateApprover(repositoryContext))
                .addRefMapping(MetadataType.F_MODIFY_APPROVER_REF,
                        QObjectReferenceMapping.initForModifyApprover(repositoryContext));

        addRefMapping(F_PARENT_ORG_REF,
                QObjectReferenceMapping.initForParentOrg(repositoryContext));

        addContainerTableMapping(F_OPERATION_EXECUTION,
                QOperationExecutionMapping.init(repositoryContext),
                joinOn((o, trg) -> o.oid.eq(trg.ownerOid))); // TODO: separate fullObject fields
        addContainerTableMapping(F_TRIGGER,
                QTriggerMapping.init(repositoryContext),
                joinOn((o, trg) -> o.oid.eq(trg.ownerOid)));
    }

    @Override
    public @NotNull Path<?>[] selectExpressions(
            Q entity, Collection<SelectorOptions<GetOperationOptions>> options) {
        // TODO: there is currently no support for index-only extensions (from entity.ext).
        //  See how QShadowMapping.loadIndexOnly() is used, and probably compose the result of this call
        //  using super... call in the subclasses. (joining arrays? providing mutable list?)
        return new Path[] { entity.oid, entity.objectType, entity.fullObject };
    }

    @Override
    protected Q newAliasInstance(String alias) {
        //noinspection unchecked
        return (Q) new QObject<>(MObject.class, alias);
    }

    @Override
    public R newRowObject() {
        //noinspection unchecked
        return (R) new MObject();
    }

    // region transformation
    @Override
    public S toSchemaObject(
            @NotNull Tuple row,
            @NotNull Q entityPath,
            @NotNull JdbcSession jdbcSession,
            Collection<SelectorOptions<GetOperationOptions>> options)
            throws SchemaException {
        byte[] fullObject = Objects.requireNonNull(row.get(entityPath.fullObject));
        UUID oid = Objects.requireNonNull(row.get(entityPath.oid));
        S ret = parseSchemaObject(fullObject, oid.toString());
        if (GetOperationOptions.isAttachDiagData(SelectorOptions.findRootOptions(options))) {
            RepositoryObjectDiagnosticData diagData = new RepositoryObjectDiagnosticData(fullObject.length);
            ret.asPrismContainer().setUserData(RepositoryService.KEY_DIAG_DATA, diagData);
        }
        return ret;
    }

    /**
     * The same function as in overridden method, but softer exception handling.
     * This targets cases like {@link RepositoryService#searchObjects} where single wrong object
     * should not spoil the whole result list.
     */
    @Override
    public S toSchemaObjectCompleteSafe(
            Tuple tuple,
            Q entityPath,
            Collection<SelectorOptions<GetOperationOptions>> options,
            @NotNull JdbcSession jdbcSession,
            boolean forceFull) {
        var result = SqlBaseOperationTracker.parsePrimary();
        try {
            return toSchemaObjectComplete(tuple, entityPath, options, jdbcSession, forceFull);
        } catch (SchemaException e) {
            try {
                PrismObject<S> errorObject = prismContext().createObject(schemaType());
                //noinspection ConstantConditions - this must not be null, the column is not
                String oid = tuple.get(entityPath.oid).toString();
                errorObject.setOid(oid);
                errorObject.asObjectable().setName(PolyStringType.fromOrig("Unreadable object"));
                logger.warn("Unreadable object with OID {}, reason: {}\n"
                        + "Surrogate object with error message as a name will be used.", oid, e.toString());
                return errorObject.asObjectable();
            } catch (SchemaException ex) {
                throw new RepositoryMappingException("Schema exception [" + ex + "] while handling schema exception: " + e, e);
            }
        } finally {
            result.close();
        }
    }

    /**
     * Override this to fill additional row attributes after calling this super version.
     *
     * *This must be called with active JDBC session* so it can create new {@link QUri} rows.
     * As this is intended for inserts *DO NOT* set {@link MObject#objectType} to any value,
     * it must be NULL otherwise the DB will complain about the value for the generated column.
     *
     * OID may be null, hence the method does NOT create any sub-entities, see
     * {@link #storeRelatedEntities(MObject, ObjectType, JdbcSession)}.
     * Try to keep order of fields here, in M-class (MObject for this one) and in SQL the same.
     */
    @SuppressWarnings("DuplicatedCode") // see comment for metadata lower
    @NotNull
    public R toRowObjectWithoutFullObject(S schemaObject, JdbcSession jdbcSession) {
        R row = newRowObject();

        row.oid = SqaleUtils.oidToUuid(schemaObject.getOid());
        // objectType MUST be left NULL for INSERT, it's determined by PG
        setPolyString(schemaObject.getName(), o -> row.nameOrig = o, n -> row.nameNorm = n);
        // fullObject is managed outside this method
        setReference(schemaObject.getTenantRef(),
                o -> row.tenantRefTargetOid = o,
                t -> row.tenantRefTargetType = t,
                r -> row.tenantRefRelationId = r);
        row.lifecycleState = schemaObject.getLifecycleState();
        // containerIdSeq is managed outside this method
        row.version = SqaleUtils.objectVersionAsInt(schemaObject);

        // complex DB fields
        row.policySituations = processCacheableUris(schemaObject.getPolicySituation());
        row.subtypes = stringsToArray(schemaObject.getSubtype());
        row.fullTextInfo = repositoryContext().fullTextIndex(schemaObject);
        row.ext = processExtensions(schemaObject.getExtension(), MExtItemHolderType.EXTENSION);

        // This is duplicate code with QAssignmentMapping.insert, but making interface
        // and needed setters (fields are not "interface-able") would create much more code.
        MetadataType metadata = schemaObject.getMetadata();
        if (metadata != null) {
            setReference(metadata.getCreatorRef(),
                    o -> row.creatorRefTargetOid = o,
                    t -> row.creatorRefTargetType = t,
                    r -> row.creatorRefRelationId = r);
            row.createChannelId = processCacheableUri(metadata.getCreateChannel());
            row.createTimestamp = MiscUtil.asInstant(metadata.getCreateTimestamp());

            setReference(metadata.getModifierRef(),
                    o -> row.modifierRefTargetOid = o,
                    t -> row.modifierRefTargetType = t,
                    r -> row.modifierRefRelationId = r);
            row.modifyChannelId = processCacheableUri(metadata.getModifyChannel());
            row.modifyTimestamp = MiscUtil.asInstant(metadata.getModifyTimestamp());
        }
        return row;
    }

    /**
     * Stores other entities related to the main object row like containers, references, etc.
     * This is not part of {@link #toRowObjectWithoutFullObject} because it requires known OID
     * which is not assured before calling that method.
     *
     * *Always call this super method first in overriding methods.*
     *
     * @param row master row for the added object("aggregate root")
     * @param schemaObject schema objects for which the details are stored
     * @param jdbcSession JDBC session used to insert related rows
     */
    public void storeRelatedEntities(
            @NotNull R row, @NotNull S schemaObject, @NotNull JdbcSession jdbcSession) throws SchemaException {
        Objects.requireNonNull(row.oid);

        // We're after insert, we can set this for the needs of owned entities (assignments).
        row.objectType = MObjectType.fromSchemaType(schemaObject.getClass());

        MetadataType metadata = schemaObject.getMetadata();
        if (metadata != null) {
            storeRefs(row, metadata.getCreateApproverRef(),
                    QObjectReferenceMapping.getForCreateApprover(), jdbcSession);
            storeRefs(row, metadata.getModifyApproverRef(),
                    QObjectReferenceMapping.getForModifyApprover(), jdbcSession);
        }

        // complete effective marks
        storeRefs(row, getEffectiveMarks(schemaObject), QObjectReferenceMapping.getForEffectiveMark(), jdbcSession);

        List<TriggerType> triggers = schemaObject.getTrigger();
        if (!triggers.isEmpty()) {
            triggers.forEach(t -> QTriggerMapping.get().insert(t, row, jdbcSession));
        }

        List<OperationExecutionType> operationExecutions = schemaObject.getOperationExecution();
        if (!operationExecutions.isEmpty()) {
            for (var oe : operationExecutions) {
                QOperationExecutionMapping.get().insert(oe, row, jdbcSession);
            }
        }

        storeRefs(row, schemaObject.getParentOrgRef(),
                QObjectReferenceMapping.getForParentOrg(), jdbcSession);

        // FIXME: Store fullObjects here?
    }

    private @NotNull List<ObjectReferenceType> getEffectiveMarks(@NotNull S schemaObject) {
        // TODO: Should we also add marks from statementPolicy (include?) - that way they would be available
        // for search even without recompute.
        // Just adding them directly here, will break delta add / delete
        //        List<ObjectReferenceType> ret = new ArrayList<>();
        //
        //        for (PolicyStatementType policy : schemaObject.getPolicyStatement()) {
        //            if (PolicyStatementTypeType.APPLY.equals(policy.getType())) {
        //                // We ensure mark is in effective marks list indexed in repository
        //                var mark = policy.getMarkRef();
        //                if (mark != null) {
        //                    ret.add(mark);
        //                }
        //            }
        //        }
        return schemaObject.getEffectiveMarkRef();


    }

    /**
     * Serializes schema object and sets {@link R#fullObject}.
     */
    public void setFullObject(R row, S schemaObject) throws SchemaException {
        if (schemaObject.getOid() == null || schemaObject.getVersion() == null) {
            throw new IllegalArgumentException(
                    "Serialized object must have assigned OID and version: " + schemaObject);
        }

        row.fullObject = createFullObject(schemaObject);
    }

    @Override
    public <C extends Containerable, TQ extends QContainer<TR, R>, TR extends MContainer> SqaleMappingMixin<S, Q, R> addContainerTableMapping(
            @NotNull ItemName itemName, @NotNull QContainerMapping<C, TQ, TR, R> containerMapping, @NotNull BiFunction<Q, TQ, Predicate> joinPredicate) {
        if (containerMapping instanceof QContainerWithFullObjectMapping mappingWithFullObject) {
            return addFullObjectContainerTableMapping(itemName, (QContainerWithFullObjectMapping) containerMapping, true, (BiFunction) joinPredicate);
        }
        return super.addContainerTableMapping(itemName, containerMapping, joinPredicate);
    }

    @Override
    public <TQ extends QReference<TR, R>, TR extends MReference> SqaleMappingMixin<S, Q, R> addRefMapping(@NotNull QName itemName, @NotNull QReferenceMapping<TQ, TR, Q, R> referenceMapping) {
        if (referenceMapping instanceof QSeparatelySerializedItem<?,?> casted) {
            separatellySerializedItems.put((ItemName) itemName, new FullObjectItemMapping(casted, true));
        }
        return super.addRefMapping(itemName, referenceMapping);
    }

    public <C extends Containerable, TQ extends QContainerWithFullObject<TR, R>, TR extends MContainerWithFullObject> SqaleMappingMixin<S, Q, R> addFullObjectContainerTableMapping(
            @NotNull ItemName itemName, @NotNull QContainerWithFullObjectMapping<C, TQ, TR, R> containerMapping, boolean includeByDefault, @NotNull BiFunction<Q, TQ, Predicate> joinPredicate) {
        separatellySerializedItems.put(itemName, new FullObjectItemMapping(containerMapping, includeByDefault));
        return super.addContainerTableMapping(itemName, containerMapping, joinPredicate);
    }

        @Override
    protected final PathSet fullObjectItemsToSkip() {
        if (fullObjectSkips == null) {
            var pathSet = new PathSet();
            if (storeSplitted) {
                for (var mapping : separatellySerializedItems.values()) {
                    pathSet.add(mapping.getPath());
                }
            }
            customizeFullObjectItemsToSkip(pathSet);
            pathSet.freeze();
            fullObjectSkips = pathSet;
        }
        return fullObjectSkips;
    }


    private class FullObjectItemMapping<IQ extends FlexibleRelationalPathBase<IR>, IR> {

        protected final QSeparatelySerializedItem<IQ,IR> mapping;
        protected final boolean includedByDefault;

        public FullObjectItemMapping(QSeparatelySerializedItem mapping, boolean includedByDefault) {
            this.mapping = mapping;
            this.includedByDefault = includedByDefault;
        }

        public ItemPath getPath() {
            return mapping.getItemPath();
        }

        public boolean isIncluded(Collection<SelectorOptions<GetOperationOptions>> options) {
            if (includedByDefault) {
                var retrieveOptions = SelectorOptions.findOptionsForPath(options, UniformItemPath.from(this.getPath()));
                if (retrieveOptions.stream().anyMatch(o -> RetrieveOption.EXCLUDE.equals(o.getRetrieve()))) {
                    // There is at least one exclude for options
                    return false;
                }
                return true;
            }
            return SelectorOptions.hasToFetchPathNotRetrievedByDefault(getPath(), options);
        }

        public Multimap<UUID, Tuple> fetchChildren(Collection<UUID> oidList, JdbcSession jdbcSession) throws SchemaException {
            Multimap<UUID, Tuple> ret = MultimapBuilder.hashKeys().arrayListValues().build();

            var q = mapping.createAlias();
            var query = jdbcSession.newQuery()
                    .from(q)
                    .select(mapping.fullObjectExpressions(q)) // no complications here, we load it whole
                    .where(mapping.allOwnedBy(q, oidList))
                    .orderBy(mapping.orderSpecifier(q));
            for (var row : query.fetch()) {
                // All assignments should have full object present / legacy assignments should be kept
                if (mapping.hasFullObject(row,q)) {
                    ret.put(mapping.getOwner(row,q), row);
                }
            }
            return ret;
        }

        public void applyToSchemaObject(S target, Collection<Tuple> values) throws SchemaException {
            if (values.isEmpty()) {
                // Do not create empty items
                return;
            }
            var container = target.asPrismObject().findOrCreateItem(getPath(), (Class) mapping.getPrismItemType());
            var alias = mapping.createAlias();
            container.setIncomplete(false);
            for (var val : values) {
                var containerable = mapping.toSchemaObjectEmbedded(val, alias);
                // FIXME: Some better addition method should be necessary.
                ((Item) container).addIgnoringEquivalents(containerable);
            }
        }
    }

    protected void customizeFullObjectItemsToSkip(PathSet mutableSet) {
        // NOOP for overrides
    }

    @Override
    public ResultListRowTransformer<S, Q, R> createRowTransformer(SqlQueryContext<S, Q, R> sqlQueryContext, JdbcSession jdbcSession, Collection<SelectorOptions<GetOperationOptions>> options) {
        // here we should load external objects

        Map<MObjectType, Set<FullObjectItemMapping>> itemsToFetch = new HashMap<>();
        Multimap<FullObjectItemMapping, UUID> oidsToFetch = HashMultimap.create();

        Map<FullObjectItemMapping, Multimap<UUID, PrismValue>> mappingToData = new HashMap<>();
        return new ResultListRowTransformer<S, Q, R>() {

            @Override
            public void beforeTransformation(List<Tuple> tuples, Q entityPath) throws SchemaException {
                for (var tuple : tuples) {
                    var objectType = tuple.get(entityPath.objectType);
                    var fetchItems = itemsToFetch.get(objectType);

                    // If we did not resolved list of items to already fetch based on object type, we resolve it now.
                    if (fetchItems == null) {
                        var objMapping = (QObjectMapping) sqlQueryContext.repositoryContext().getMappingByQueryType((Class) objectType.getQueryType());

                        if (objMapping.storeSplitted) {
                            fetchItems = new HashSet<>();
                            for (var rawMapping : objMapping.separatellySerializedItems.values()) {
                                @SuppressWarnings("unchecked")
                                var mapping = (FullObjectItemMapping) rawMapping;
                                if (mapping.isIncluded(options)) {
                                    mappingToData.put(mapping, ImmutableMultimap.of());
                                    fetchItems.add(mapping);
                                }
                            }
                        } else {
                            fetchItems = Collections.emptySet();
                        }
                    }

                    // For each item to fetch we maintain seperate entry in map
                    for (var item : fetchItems) {
                        oidsToFetch.put(item, tuple.get(entityPath.oid));
                    }
                }

                for (var mapping : mappingToData.entrySet()) {
                    var result = SqlBaseOperationTracker.fetchChildren(mapping.getKey().mapping.tableName());
                    try {
                        mapping.setValue(mapping.getKey().fetchChildren(oidsToFetch.get(mapping.getKey()), jdbcSession));
                    } finally {
                        result.close();
                    }
                }
            }

            @Override
            public S transform(Tuple tuple, Q entityPath) {
                // Parsing full object
                S baseObject = toSchemaObjectCompleteSafe(tuple, entityPath, options, jdbcSession, false);
                var uuid = tuple.get(entityPath.oid);
                if (!storeSplitted) {
                    return baseObject;
                }
                var childrenResult = SqlBaseOperationTracker.parseChildren("all");
                try {
                    for (var entry : mappingToData.entrySet()) {
                        var mapping = entry.getKey();
                        try {
                            mapping.applyToSchemaObject(baseObject, entry.getValue().get(uuid));
                        } catch (SchemaException e) {
                            throw new SystemException(e);
                        }
                    }
                } finally {
                    childrenResult.close();
                }
                resolveReferenceNames(baseObject, jdbcSession, options);
                return baseObject;
            }
        };
    }

    @VisibleForTesting
    public int additionalSelectsByDefault() {
        if (storeSplitted) {
            return (int) separatellySerializedItems.entrySet().stream().filter(e -> e.getValue().includedByDefault).count();
        }
        return 0;
    }

    public void setStoreSplitted(boolean storeSplitted) {
        this.storeSplitted = storeSplitted;
        fullObjectSkips = null; // Needs to be recomputed
    }

    // endregion
}
