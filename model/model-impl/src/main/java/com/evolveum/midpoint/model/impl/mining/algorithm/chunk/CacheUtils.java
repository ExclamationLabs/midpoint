/*
 * Copyright (C) 2010-2023 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.model.impl.mining.algorithm.chunk;

import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.evolveum.midpoint.model.api.ModelService;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.xml.ns._public.common.common_3.RoleType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.UserType;

import static com.evolveum.midpoint.model.impl.mining.utils.RoleAnalysisObjectUtils.getRoleTypeObject;
import static com.evolveum.midpoint.model.impl.mining.utils.RoleAnalysisObjectUtils.getUserTypeObject;

public class CacheUtils {

    protected static PrismObject<RoleType> cacheRole(ModelService modelService, OperationResult result,
            Map<String, PrismObject<RoleType>> roleExistCache, String roleOid, Task task) {
        PrismObject<RoleType> role = roleExistCache.get(roleOid);
        if (role == null) {
            role = getRoleTypeObject(modelService, roleOid, result, task);
            if (role == null) {
                return null;
            }
            roleExistCache.put(roleOid, role);
        }
        return role;
    }

    @Nullable
    protected static PrismObject<UserType> cacheUser(ModelService modelService, OperationResult result,
            Map<String, PrismObject<UserType>> userExistCache, String userOid, Task task) {
        PrismObject<UserType> user = userExistCache.get(userOid);
        if (user == null) {
            user = getUserTypeObject(modelService, userOid, result, task);
            if (user == null) {
                return null;
            }
            userExistCache.put(userOid, user);
        }
        return user;
    }

}