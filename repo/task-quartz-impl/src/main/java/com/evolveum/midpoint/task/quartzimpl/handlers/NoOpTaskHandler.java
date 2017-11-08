/*
 * Copyright (c) 2010-2013 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.evolveum.midpoint.task.quartzimpl.handlers;

import com.evolveum.midpoint.prism.PrismContainer;
import com.evolveum.midpoint.prism.PrismProperty;
import com.evolveum.midpoint.prism.PrismPropertyDefinition;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.*;
import com.evolveum.midpoint.task.api.TaskRunResult.TaskRunResultStatus;
import com.evolveum.midpoint.task.quartzimpl.TaskManagerQuartzImpl;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;

import java.util.List;

/**
 * @author Pavol Mederly
 *
 */
public class NoOpTaskHandler implements TaskHandler {
	
	private static final transient Trace LOGGER = TraceManager.getTrace(NoOpTaskHandler.class);
	public static final String HANDLER_URI = "http://midpoint.evolveum.com/xml/ns/public/task/noop/handler-3";

	private static NoOpTaskHandler instance = null;
	private TaskManagerQuartzImpl taskManagerImpl;
	
	private NoOpTaskHandler() {}
	
	public static void instantiateAndRegister(TaskManager taskManager) {
		if (instance == null)
			instance = new NoOpTaskHandler();
		taskManager.registerHandler(HANDLER_URI, instance);
		instance.taskManagerImpl = (TaskManagerQuartzImpl) taskManager;
	}

	@Override
	public TaskRunResult run(Task task) {
		
		OperationResult opResult = new OperationResult(NoOpTaskHandler.class.getName()+".run");
		TaskRunResult runResult = new TaskRunResult();
		runResult.setOperationResult(opResult);
        runResult.setRunResultStatus(TaskRunResultStatus.FINISHED);     // would be overwritten when problem is encountered

        PrismContainer taskExtension = task.getExtension();

        PrismProperty<Integer> delayProp = taskExtension != null ? taskExtension.findProperty(SchemaConstants.NOOP_DELAY_QNAME) : null;
        PrismProperty<Integer> stepsProp = taskExtension != null ? taskExtension.findProperty(SchemaConstants.NOOP_STEPS_QNAME) : null;

		PrismPropertyDefinition delayPropDef = taskManagerImpl.getPrismContext().getSchemaRegistry().findPropertyDefinitionByElementName(SchemaConstants.NOOP_DELAY_QNAME);
		PrismPropertyDefinition stepsPropDef = taskManagerImpl.getPrismContext().getSchemaRegistry().findPropertyDefinitionByElementName(SchemaConstants.NOOP_STEPS_QNAME);
		try {
			if (delayProp != null)
				delayProp.applyDefinition(delayPropDef);
			if (stepsProp != null)
				stepsProp.applyDefinition(stepsPropDef);
		} catch (SchemaException se) {
			LoggingUtils.logUnexpectedException(LOGGER, "Cannot apply Prism definition to delay and/or steps property, exiting immediately.", se);
			opResult.recordFatalError("Cannot apply Prism definition to delay and/or steps property, exiting immediately.", se);
			runResult.setRunResultStatus(TaskRunResultStatus.PERMANENT_ERROR);
			return runResult;
		}
        
        long delay;
        if (delayProp != null && !delayProp.getValues().isEmpty())
        	delay = delayProp.getValues().get(0).getValue();
        else
        	delay = 0;

        int steps;
        if (stepsProp != null && !stepsProp.getValues().isEmpty())
        	steps = stepsProp.getValues().get(0).getValue();
        else
        	steps = 1;

        LOGGER.info("NoOpTaskHandler run starting; progress = " + task.getProgress() + ", steps to be executed = " + steps + ", delay for one step = " + delay  + " in task " + task.getName());
        
        for (int i = 0; i < steps; i++) {
        	LOGGER.info("NoOpTaskHandler: executing step " + (i+1) + " of " + steps + " in task " + task.getName());

            // this strange construction is used to simulate non-interruptible execution of the task
        	long sleepUntil = System.currentTimeMillis() + delay;
        	for (;;) {
        		long delta = sleepUntil - System.currentTimeMillis();
        		if (delta > 0) {
                	try {
            			Thread.sleep(delta);
                	} catch (InterruptedException e) {
                	}
        		} else {
        			break;		// we have slept enough
        		}
			}

            task.incrementProgressAndStoreStatsIfNeeded();

            if (!task.canRun()) {
				LOGGER.info("NoOpTaskHandler: got a shutdown request, finishing task " + task.getName());
				break;
			}
        }

		opResult.computeStatusIfUnknown();
		
		LOGGER.info("NoOpTaskHandler run finishing; progress = " + task.getProgress() + " in task " + task.getName());
		return runResult;
	}

	@Override
	public Long heartbeat(Task task) {
		return null;		// not to overwrite progress information!
	}

	@Override
	public void refreshStatus(Task task) {
	}

    @Override
    public String getCategoryName(Task task) {
        return TaskCategory.DEMO;
    }

    @Override
    public List<String> getCategoryNames() {
        return null;
    }
}
