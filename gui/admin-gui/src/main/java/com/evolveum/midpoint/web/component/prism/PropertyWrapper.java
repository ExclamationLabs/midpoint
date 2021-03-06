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

package com.evolveum.midpoint.web.component.prism;

import com.evolveum.midpoint.prism.*;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.polystring.PolyString;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.util.DebugDumpable;
import com.evolveum.midpoint.util.DebugUtil;
import com.evolveum.midpoint.util.PrettyPrinter;
import com.evolveum.midpoint.xml.ns._public.common.common_3.LookupTableType;

import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author lazyman
 */
public class PropertyWrapper<T> extends PropertyOrReferenceWrapper<PrismProperty<T>, PrismPropertyDefinition<T>> implements Serializable, DebugDumpable {

	private static final long serialVersionUID = -6347026284758253783L;
	private LookupTableType predefinedValues;

	public PropertyWrapper(@Nullable ContainerValueWrapper container, PrismProperty<T> property, boolean readonly, ValueStatus status, PrismContext prismContext) {
		super(container, property, readonly, status, null, prismContext);

        values = createValues();
    }
	
	public PropertyWrapper(@Nullable ContainerValueWrapper container, PrismProperty<T> property, boolean readonly,
			ValueStatus status, ItemPath path, PrismContext prismContext) {
		super(container, property, readonly, status, path, prismContext);

        values = createValues();
    }

	// TODO consider unifying with ReferenceWrapper.createValues  (difference is in oldValue in ValueWrapper constructor: null vs. prismValue)
    private List<ValueWrapper> createValues() {
        List<ValueWrapper> values = new ArrayList<>();

        for (PrismValue prismValue : item.getValues()) {
            values.add(new ValueWrapper<T>(this, prismValue, ValueStatus.NOT_CHANGED, prismContext));
        }

        int minOccurs = getItemDefinition().getMinOccurs();
        while (values.size() < minOccurs) {
            values.add(createAddedValue());
        }

        if (values.isEmpty()) {
            values.add(createAddedValue());
        }

        return values;
    }

	@Override
    public ValueWrapper<T> createAddedValue() {
        ItemDefinition definition = item.getDefinition();

        ValueWrapper wrapper;
        if (SchemaConstants.T_POLY_STRING_TYPE.equals(definition.getTypeName())) {
            wrapper = new ValueWrapper(this, prismContext.itemFactory().createPropertyValue(new PolyString("")),
                    prismContext.itemFactory().createPropertyValue(new PolyString("")), ValueStatus.ADDED,
		            prismContext);
        } else {
            wrapper = new ValueWrapper(this, prismContext.itemFactory().createPropertyValue(), ValueStatus.ADDED,
		            prismContext);
        }

        return wrapper;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("PropertyWrapper(");
        builder.append(getDisplayName());
        builder.append(" (");
        builder.append(status);
        builder.append(") ");
        builder.append(getValues() == null ? null :  getValues().size());
		builder.append(" values)");
        builder.append(")");
        return builder.toString();
    }

	@Override
	public String debugDump() {
		return debugDump(0);
	}

	@Override
	public String debugDump(int indent) {
		StringBuilder sb = new StringBuilder();
		DebugUtil.indentDebugDump(sb, indent);
		sb.append(getDebugName());
		sb.append(": ").append(PrettyPrinter.prettyPrint(getName())).append("\n");
		DebugUtil.debugDumpWithLabel(sb, "displayName", displayName, indent+1);
		sb.append("\n");
		DebugUtil.debugDumpWithLabel(sb, "status", status == null?null:status.toString(), indent+1);
		sb.append("\n");
		DebugUtil.debugDumpWithLabel(sb, "readonly", readonly, indent+1);
		sb.append("\n");
		DebugUtil.debugDumpWithLabel(sb, "itemDefinition", getItemDefinition() == null?null:getItemDefinition().toString(), indent+1);
		sb.append("\n");
		DebugUtil.debugDumpWithLabel(sb, "property", item == null?null:item.toString(), indent+1);
		sb.append("\n");
		DebugUtil.debugDumpLabel(sb, "values", indent+1);
		sb.append("\n");
		DebugUtil.debugDump(sb, values, indent+2, false);
		return sb.toString();
	}

	protected String getDebugName() {
		return "PropertyWrapper";
	}
	
	public void setPredefinedValues(LookupTableType predefinedValues) {
		this.predefinedValues = predefinedValues;
	}
	
	public LookupTableType getPredefinedValues() {
		return predefinedValues;
	}
}

	