/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.repo.common.activity.definition;

import com.evolveum.midpoint.util.DebugDumpable;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ExecutionModeType;

import org.jetbrains.annotations.NotNull;

import javax.xml.namespace.QName;

/**
 * Defines the work that is to be done within an activity.
 */
public interface WorkDefinition extends DebugDumpable {

    @NotNull ExecutionModeType getExecutionMode();

    ActivityDefinition<?> getOwningActivityDefinition();

    void setOwningActivityDefinition(ActivityDefinition<?> activityDefinition);

    // TODO decide on this
    @NotNull ActivityTailoring getActivityTailoring();
}
