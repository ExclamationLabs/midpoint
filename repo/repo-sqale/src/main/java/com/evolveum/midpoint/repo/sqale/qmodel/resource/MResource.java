/*
 * Copyright (C) 2010-2022 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.repo.sqale.qmodel.resource;

import java.util.UUID;

import com.evolveum.midpoint.repo.sqale.qmodel.object.MObject;
import com.evolveum.midpoint.repo.sqale.qmodel.object.MObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AdministrativeAvailabilityStatusType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AvailabilityStatusType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ResourceAdministrativeStateType;

/**
 * Querydsl "row bean" type related to {@link QResource}.
 */
public class MResource extends MObject {

    public ResourceAdministrativeStateType businessAdministrativeState;
    // administrativeOperationalState/administrativeAvailabilityStatus
    public AdministrativeAvailabilityStatusType administrativeOperationalStateAdministrativeAvailabilityStatus;
    // operationalState/lastAvailabilityStatus
    public AvailabilityStatusType operationalStateLastAvailabilityStatus;
    public UUID connectorRefTargetOid;
    public MObjectType connectorRefTargetType;
    public Integer connectorRefRelationId;
}
