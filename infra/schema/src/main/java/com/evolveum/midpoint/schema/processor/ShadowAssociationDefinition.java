/*
 * Copyright (c) 2010-2017 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.schema.processor;

import com.evolveum.midpoint.prism.PrismContainerDefinition;
import com.evolveum.midpoint.prism.delta.ContainerDelta;
import com.evolveum.midpoint.prism.query.ObjectFilter;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowAssociationValueType;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import javax.xml.namespace.QName;
import java.util.Collection;

/**
 * Definition of an association item, e.g., `ri:group`.
 *
 * The association can be native or simulated; it can point right to the target object (like `group` object class),
 * or to an intermediate - a.k.a. "associated" - one (like `groupMembership` object class).
 *
 * @see ShadowItemDefinition
 */
public interface ShadowAssociationDefinition
        extends
        PrismContainerDefinition<ShadowAssociationValueType>,
        ShadowItemDefinition<ShadowAssociation, ShadowAssociationValueType> {

    /** Returns "immediate neighbors". TODO */
    @NotNull Collection<AssociationParticipantType> getTargetParticipantTypes();

    /**
     * Creates a filter that provides all shadows eligible as the target value for this association.
     *
     * TODO are these immediate targets (associated objects, if present), or the "final" targets?
     */
    ObjectFilter createTargetObjectsFilter();

    /** TODO reconsider this: which definition should we provide as the representative one? There can be many. */
    ResourceObjectDefinition getRepresentativeTargetObjectDefinition();

    @TestOnly
    ShadowAssociationValue instantiateFromIdentifierRealValue(@NotNull QName identifierName, @NotNull Object realValue)
            throws SchemaException;

    ContainerDelta<ShadowAssociationValueType> createEmptyDelta();

    SimulatedShadowAssociationClassDefinition getSimulationDefinition();

    SimulatedShadowAssociationClassDefinition getSimulationDefinitionRequired();

    boolean isEntitlement();
}
