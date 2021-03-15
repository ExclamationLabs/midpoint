/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.repo.sqale.qmodel.task;

import org.jetbrains.annotations.NotNull;

import com.evolveum.midpoint.repo.sqale.qmodel.object.ObjectSqlTransformer;
import com.evolveum.midpoint.repo.sqlbase.JdbcSession;
import com.evolveum.midpoint.repo.sqlbase.SqlTransformerSupport;
import com.evolveum.midpoint.xml.ns._public.common.common_3.TaskType;

public class TaskSqlTransformer extends ObjectSqlTransformer<TaskType, QTask, MTask> {

    public TaskSqlTransformer(SqlTransformerSupport transformerSupport, QTaskMapping mapping) {
        super(transformerSupport, mapping);
    }

    @Override
    public @NotNull MTask toRowObjectWithoutFullObject(
            TaskType schemaObject, JdbcSession jdbcSession) {
        MTask row = super.toRowObjectWithoutFullObject(schemaObject, jdbcSession);

        // TODO other attributes

        return row;
    }
}
