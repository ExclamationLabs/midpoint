/*
 * Copyright (C) 2010-2022 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.model.api.correlation;

import static com.evolveum.midpoint.xml.ns._public.common.common_3.CorrelationSituationType.*;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.evolveum.midpoint.prism.Containerable;
import com.evolveum.midpoint.util.DebugUtil;
import com.evolveum.midpoint.xml.ns._public.common.common_3.CorrelationSituationType;

/**
 * Result of a sub-object correlation.
 *
 * TEMPORARY, just PoC for now
 */
public class SimplifiedCorrelationResult extends AbstractCorrelationResult<Containerable> {

    private SimplifiedCorrelationResult(
            @NotNull CorrelationSituationType situation,
            @Nullable Containerable owner) {
        super(situation, owner);
    }

    public static SimplifiedCorrelationResult existingOwner(@NotNull Containerable owner) {
        return new SimplifiedCorrelationResult(EXISTING_OWNER, owner);
    }

    public static SimplifiedCorrelationResult noOwner() {
        return new SimplifiedCorrelationResult(NO_OWNER, null);
    }

    public static SimplifiedCorrelationResult uncertain() {
        return new SimplifiedCorrelationResult(UNCERTAIN, null);
    }

    public boolean isUncertain() {
        return situation == UNCERTAIN;
    }

    public boolean isError() {
        return situation == ERROR;
    }

    @SuppressWarnings("WeakerAccess")
    public boolean isExistingOwner() {
        return situation == EXISTING_OWNER;
    }

    @SuppressWarnings("WeakerAccess")
    public boolean isNoOwner() {
        return situation == NO_OWNER;
    }

    public boolean isDone() {
        return isExistingOwner() || isNoOwner();
    }

    @Override
    public String debugDump(int indent) {
        StringBuilder sb = DebugUtil.createTitleStringBuilderLn(getClass(), indent);
        DebugUtil.debugDumpWithLabel(sb, "situation", situation, indent + 1);
        if (owner != null) {
            sb.append("\n");
            DebugUtil.debugDumpWithLabel(sb, "owner", String.valueOf(owner), indent + 1);
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                "situation=" + situation +
                ", owner=" + owner +
                '}';
    }
}