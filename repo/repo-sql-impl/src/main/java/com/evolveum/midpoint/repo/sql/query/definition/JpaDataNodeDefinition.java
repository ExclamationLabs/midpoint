/*
 * Copyright (C) 2010-2020 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.repo.sql.query.definition;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.evolveum.midpoint.prism.ItemDefinition;
import com.evolveum.midpoint.prism.Visitable;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.repo.sqlbase.QueryException;
import com.evolveum.midpoint.repo.sql.query.resolution.DataSearchResult;
import com.evolveum.midpoint.util.DebugDumpable;

/**
 * Defines piece of JPA data - entity, property, reference, or "any" container. Used to convert ItemPath to HQL query,
 * or, specifically, to a property path with left outer joins where appropriate.
 *
 * The conversion works like running state machine where data definitions are states, and transitions are labeled
 * with non-empty ItemPaths. Input paths are used to navigate through states until all of input path is consumed.
 *
 * In addition to recognize input paths, this automaton produces HQL path and property joins. That's why
 * each possible transition is labeled with (ItemPath prefix, JPA name, other transition data) tuple.
 * ItemPath prefix is used to match the input path, while JPA name + other transition data, along
 * with target state information (potentially) are used to generate HQL property path with appropriate join,
 * if necessary.
 *
 * Note that some transitions may have empty JPA name - when the data is contained directly in owner entity
 * (e.g. object extension, shadow attributes). Most transitions have single item paths. However, some have two,
 * e.g. construction/resourceRef, owner/id, metadata/*.
 *
 * By other transition data we currently mean: collection specification, or "embedded" flag.
 *
 * Terminology:
 *
 * - state ~ data node (JpaDataNodeDefinition -> JpaEntityDefinition, JpaPropertyDefinition, ...)
 * - transition ~ link node (JpaLinkDefinition)
 */
public abstract class JpaDataNodeDefinition
        implements DebugDumpable, Visitable<JpaDataNodeDefinition> {

    /**
     * JPA class - either "composite" (RObject, RUser, RAssignment, ...) or "primitive" (String, Integer, int, ...)
     */
    @NotNull private final Class<?> jpaClass;

    /**
     * JAXB class - either "composite" (ObjectType, UserType, AssignmentType, ...)
     * or "primitive" (String, Integer, int, ...).
     * Null if not known.
     */
    @Nullable private final Class<?> jaxbClass;

    public JpaDataNodeDefinition(@NotNull Class<?> jpaClass, @Nullable Class<?> jaxbClass) {
        this.jpaClass = jpaClass;
        this.jaxbClass = jaxbClass;
    }

    @NotNull
    public Class<?> getJpaClass() {
        return jpaClass;
    }

    public String getJpaClassName() {
        return jpaClass.getSimpleName();
    }

    @Nullable
    public Class<?> getJaxbClass() {
        return jaxbClass;
    }

    /**
     * Tries to find "next step" in the translation process for a given ItemPath.
     *
     * @param path A path to be resolved. Always non-null and non-empty. Should produce at least one transition.
     * @param itemDefinition Item definition for the item being sought. Needed only for "any" items.
     * @return - Normally it returns the search result containing next item definition (entity, collection, ...) in the chain
     * and the unresolved remainder of the path. The transition may be empty ("self") e.g. for metadata or construction.
     * - If the search was not successful, returns null.
     */
    public abstract DataSearchResult<?> nextLinkDefinition(ItemPath path, ItemDefinition<?> itemDefinition) throws QueryException;

    public String toString() {
        return getShortInfo();
    }

    protected abstract String getDebugDumpClassName();

    public String getShortInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append(getDebugDumpClassName()).append(':').append(getJpaClassName());
        if (jaxbClass != null) {
            sb.append(" (jaxb=").append(jaxbClass.getSimpleName()).append(")");
        }
        return sb.toString();
    }
}
