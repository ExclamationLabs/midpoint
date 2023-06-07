package com.evolveum.midpoint.ninja.action.upgrade.handler;

import com.evolveum.midpoint.ninja.action.upgrade.UpgradePhase;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ResourceType;

public class AddRemoveAttributeValuesProcessor extends RemovedElementProcessor<ResourceType> {

    @Override
    public UpgradePhase getPhase() {
        return UpgradePhase.BEFORE;
    }

    @Override
    public <O extends ObjectType> boolean isApplicable(Class<O> type) {
        return ResourceType.class.isAssignableFrom(type);
    }
}