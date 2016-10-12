/*
 * Copyright (c) 2010-2016 Evolveum
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

package com.evolveum.midpoint.prism.query;

import com.evolveum.midpoint.prism.Item;
import com.evolveum.midpoint.prism.ItemDefinition;
import com.evolveum.midpoint.prism.Objectable;
import com.evolveum.midpoint.prism.PrismContainerValue;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismProperty;
import com.evolveum.midpoint.prism.PrismPropertyDefinition;
import com.evolveum.midpoint.prism.PrismPropertyValue;
import com.evolveum.midpoint.prism.PrismReferenceValue;
import com.evolveum.midpoint.prism.match.MatchingRule;
import com.evolveum.midpoint.prism.match.MatchingRuleRegistry;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.util.DebugUtil;
import com.evolveum.midpoint.util.exception.SchemaException;
import org.apache.commons.lang.Validate;

import javax.xml.namespace.QName;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class SubstringFilter<T> extends PropertyValueFilter<PrismPropertyValue<T>> {
	
	private boolean anchorStart = false;
	private boolean anchorEnd = false;

	SubstringFilter(ItemPath parentPath, ItemDefinition definition, QName matchingRule, List<PrismPropertyValue<T>> value) {
		super(parentPath, definition, matchingRule, value);
	}
	
	SubstringFilter(ItemPath parentPath, ItemDefinition definition, QName matchingRule) {
		super(parentPath, definition, matchingRule);
	}
	
	SubstringFilter(ItemPath parentPath, ItemDefinition definition, QName matchingRule, List<PrismPropertyValue<T>> value, boolean anchorStart, boolean anchorEnd) {
		super(parentPath, definition, matchingRule, value);
		this.anchorStart = anchorStart;
		this.anchorEnd = anchorEnd;
	}
	
	public static <T> SubstringFilter createSubstring(ItemPath path, PrismProperty<T> item, QName matchingRule,
			boolean anchorStart, boolean anchorEnd) {
		List<PrismPropertyValue<T>> values = item.getValues();
		PrismPropertyValue<T> value;
		if (values.size() > 1) {
			throw new IllegalArgumentException("Expected at most 1 value, got " + values);
		} else if (values.size() == 1) {
			value = values.get(0).clone();
		} else {
			value = null;
		}
		return createSubstring(path, item.getDefinition(), matchingRule, value, anchorStart, anchorEnd);
	}
		
	public static <T> SubstringFilter<T> createSubstring(ItemPath path, PrismPropertyDefinition<T> itemDefinition, QName matchingRule, T realValues) {
		return createSubstring(path, itemDefinition, matchingRule, realValues, false, false);
	}

	public static <T> SubstringFilter<T> createSubstring(ItemPath path, PrismPropertyDefinition<T> itemDefinition, QName matchingRule, T realValues, boolean anchorStart, boolean anchorEnd) {
		if (realValues == null){
			return createNullSubstring(path, itemDefinition, matchingRule);
		}
		List<PrismPropertyValue<T>> pValues = realValueToPropertyList(itemDefinition, realValues);
		SubstringFilter<T> substringFilter = new SubstringFilter<T>(path, itemDefinition, matchingRule, pValues, anchorStart, anchorEnd);
		for (PrismPropertyValue<T> pVal: pValues) {
			pVal.setParent(substringFilter);
		}
		return substringFilter;
	}
	
	public static <T> SubstringFilter<T> createSubstring(ItemPath path, PrismPropertyDefinition<T> itemDefinition, QName matchingRule,
			PrismPropertyValue<T> values, boolean anchorStart, boolean anchorEnd) {
		Validate.notNull(path, "Item path in substring filter must not be null.");
		Validate.notNull(itemDefinition, "Item definition in substring filter must not be null.");
		
		if (values == null){
			return createNullSubstring(path, itemDefinition, matchingRule);
		}
		
		List<PrismPropertyValue<T>> pValues = createPropertyList(itemDefinition, values);
				
		SubstringFilter<T> substringFilter =  new SubstringFilter<>(path, itemDefinition, matchingRule, pValues, anchorStart, anchorEnd);
		for (PrismPropertyValue<T> pVal: pValues) {
			pVal.setParent(substringFilter);
		}
		return substringFilter;
	}
	
	public static <O extends Objectable, T> SubstringFilter<T> createSubstring(ItemPath path, Class<O> clazz, PrismContext prismContext, T realValue) {
		return createSubstring(path, clazz, prismContext, null, realValue);
	}
	
	public static <O extends Objectable, T> SubstringFilter<T> createSubstring(ItemPath path, Class<O> clazz, PrismContext prismContext, QName matchingRule, T realValue) {
		
		ItemDefinition itemDefinition = FilterUtils.findItemDefinition(path, clazz, prismContext);
		
		if (!(itemDefinition instanceof PrismPropertyDefinition)){
			throw new IllegalStateException("Bad definition. Expected property definition, but got " + itemDefinition);
		}
		
		if (realValue == null){
			return createNullSubstring(path, (PrismPropertyDefinition<T>) itemDefinition, matchingRule);
		}
		
		List<PrismPropertyValue<T>> pVals = realValueToPropertyList((PrismPropertyDefinition<T>) itemDefinition, realValue);
		
		SubstringFilter<T> substringFilter = new SubstringFilter<>(path, itemDefinition, matchingRule, pVals);
		for (PrismPropertyValue<T> pVal: pVals) {
			pVal.setParent(substringFilter);
		}
		return substringFilter;
	}
	
	public static <O extends Objectable> SubstringFilter<String> createSubstring(QName propertyName, Class<O> clazz, PrismContext prismContext, String value) {
		return createSubstring(propertyName, clazz, prismContext, null, value);
    }

    public static <O extends Objectable> SubstringFilter<String> createSubstring(QName propertyName, Class<O> clazz, PrismContext prismContext, QName matchingRule, String value) {
        return createSubstring(new ItemPath(propertyName), clazz, prismContext, matchingRule, value);
    }
    
    private static <T> SubstringFilter<T> createNullSubstring(ItemPath itemPath, PrismPropertyDefinition propertyDef, QName matchingRule){
		return new SubstringFilter<>(itemPath, propertyDef, matchingRule);
		
	}

	public boolean isAnchorStart() {
		return anchorStart;
	}

	public boolean isAnchorEnd() {
		return anchorEnd;
	}

    public void setAnchorStart(boolean anchorStart) {
        this.anchorStart = anchorStart;
    }

    public void setAnchorEnd(boolean anchorEnd) {
        this.anchorEnd = anchorEnd;
    }

    @Override
	public SubstringFilter<T> clone() {
    	List<PrismPropertyValue<T>> clonedValues = getCloneValuesList();
        SubstringFilter<T> filter = new SubstringFilter<>(getFullPath(), getDefinition(), getMatchingRule(), clonedValues);
        for (PrismPropertyValue<T> clonedValue: clonedValues) {
        	clonedValue.setParent(filter);
        }
        filter.anchorStart = anchorStart;
        filter.anchorEnd = anchorEnd;

        return filter;
	}

	@Override
	public String debugDump() {
		return debugDump(0);
	}

	@Override
	public String debugDump(int indent) {
		StringBuilder sb = new StringBuilder();
		DebugUtil.indentDebugDump(sb, indent);
		sb.append("SUBSTRING:");
		if (anchorStart) {
			sb.append(" anchorStart");
		}
		if (anchorEnd) {
			sb.append(" anchorEnd");
		}
		return debugDump(indent, sb);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("SUBSTRING: ");
		String rv = toString(sb);
		if (anchorStart) {
			rv += ",S";
		}
		if (anchorEnd) {
			rv += ",E";
		}
		return rv;
	}

	@Override
	public boolean match(PrismContainerValue containerValue, MatchingRuleRegistry matchingRuleRegistry) throws SchemaException {
		Item item = getObjectItem(containerValue);
		
		MatchingRule matching = getMatchingRuleFromRegistry(matchingRuleRegistry, item);
		
		for (Object val : item.getValues()){
			if (val instanceof PrismPropertyValue){
				Object value = ((PrismPropertyValue) val).getValue();
				Iterator<String> iterator = (Iterator<String>) toRealValues().iterator();
				while(iterator.hasNext()){
					StringBuilder sb = new StringBuilder();
					if (!anchorStart) {
						sb.append(".*");
					}
					sb.append(Pattern.quote(iterator.next()));
					if (!anchorEnd) {
						sb.append(".*");
					}
					if (matching.matchRegex(value, sb.toString())){
						return true;
					}
				}
			}
			if (val instanceof PrismReferenceValue) {
				throw new UnsupportedOperationException(
						"matching substring on the prism reference value not supported yet");
			}
		}
		
		return false;
	}

	private Set<T> toRealValues(){
		 return PrismPropertyValue.getRealValuesOfCollection(getValues());
	}
	
	@Override
	public PrismContext getPrismContext() {
		return getDefinition().getPrismContext();
	}

	@Override
	public ItemPath getPath() {
		return getFullPath();
	}
	
	@Override
	public PrismPropertyDefinition<T> getDefinition() {
		return (PrismPropertyDefinition<T>) super.getDefinition();
	}

	@Override
	public boolean equals(Object o, boolean exact) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		if (!super.equals(o, exact))
			return false;

		SubstringFilter<?> that = (SubstringFilter<?>) o;

		if (anchorStart != that.anchorStart)
			return false;
		return anchorEnd == that.anchorEnd;

	}

	@Override
	public int hashCode() {
		int result = super.hashCode();
		result = 31 * result + (anchorStart ? 1 : 0);
		result = 31 * result + (anchorEnd ? 1 : 0);
		return result;
	}
}
