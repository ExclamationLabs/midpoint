/*
 * Copyright (C) 2010-2022 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.task.api;

import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.schema.result.OperationResult;

import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public interface ObjectProcessingListener {

    /**
     * Called after a specific object was processed. (Whatever that means.)
     *
     * @param stateBefore The state of the object before the processing
     * @param executedDelta (Aggregated) delta that was executed (or attempted to be executed) - if any
     * @param simulatedDelta (Aggregated) delta that would be executed if the execution mode was real - if any
     * @param eventTags Event tags connected with the object processing
     * @param result Operation result under which the necessary actions are carried out
     */
    <O extends ObjectType> void onObjectProcessed(
            @Nullable O stateBefore,
            @Nullable ObjectDelta<O> executedDelta,
            @Nullable ObjectDelta<O> simulatedDelta,
            @NotNull Collection<String> eventTags,
            @NotNull Task task,
            @NotNull OperationResult result) throws SchemaException;
}
