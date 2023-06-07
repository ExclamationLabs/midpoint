package com.evolveum.midpoint.ninja.action.upgrade.handler;

import com.evolveum.midpoint.ninja.action.upgrade.UpgradeObjectProcessor;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;

public abstract class RemovedElementProcessor<T extends ObjectType> implements UpgradeObjectProcessor<T> {

    @Override
    public boolean processObject(PrismObject<T> object, OperationResult result) {
        return true;
    }
}