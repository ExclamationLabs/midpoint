/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.provisioning.ucf.impl.connid.query;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.xml.namespace.QName;

import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.OperationalAttributes;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;

import com.evolveum.midpoint.prism.PrismPropertyValue;
import com.evolveum.midpoint.prism.PrismValue;
import com.evolveum.midpoint.prism.query.*;
import com.evolveum.midpoint.provisioning.ucf.impl.connid.ConnIdUtil;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ActivationStatusType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ActivationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.LockoutStatusType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowType;

import static com.evolveum.midpoint.provisioning.ucf.impl.connid.ConnIdNameMapper.ucfAttributeNameToConnId;

public class ValueOperation extends Operation {

    public ValueOperation(FilterInterpreter interpreter) {
        super(interpreter);
    }

    @Override
    public <T> Filter interpret(ObjectFilter objectFilter) throws SchemaException {
        ValueFilter valueFilter = (ValueFilter) objectFilter;
        if (valueFilter.getParentPath().isEmpty()) {
            throw new UnsupportedOperationException("Empty path is not supported (filter: " + objectFilter + ")");
        }
        if (valueFilter.getParentPath().equivalent(ShadowType.F_ATTRIBUTES)) {
            try {
                QName propName = valueFilter.getDefinition().getItemName();
                String icfName = ucfAttributeNameToConnId(
                        propName, getInterpreter().getObjectDefinition(), "(attribute in the filter)");

                if (objectFilter instanceof EqualFilter) {
                    EqualFilter<T> eq = (EqualFilter<T>) objectFilter;

                    Collection<Object> convertedValues = convertValues(propName, eq.getValues());
                    if (convertedValues == null || convertedValues.isEmpty()) {
                        Attribute attr = AttributeBuilder.build(icfName);
                        return FilterBuilder.equalTo(attr);
                    } else {
                        Attribute attr = AttributeBuilder.build(icfName, convertedValues);
                        if (valueFilter.getDefinition().isSingleValue()) {
                            return FilterBuilder.equalTo(attr);
                        } else {
                            // TODO: If multiple filter values are provided, this is deviation from
                            //  "contains any" (or "any IN") semantics used for repository and in-memory.
                            // Works fine for single filter value, because then it means:
                            // "Values stored under specified key on the resource contain this single value."
                            return FilterBuilder.containsAllValues(attr);
                        }
                    }

                } else if (objectFilter instanceof SubstringFilter) {
                    SubstringFilter substring = (SubstringFilter) objectFilter;
                    Collection<Object> convertedValues = convertValues(propName, substring.getValues());
                    if (convertedValues == null || convertedValues.isEmpty()) {
                        throw new IllegalArgumentException("Substring filter with null value makes no sense");
                    } else {
                        if (convertedValues.size() != 1) {
                            throw new IllegalArgumentException("Substring filter with multiple values makes no sense");
                            //maybe it does, through OR clauses
                        }

                        if (substring.isAnchorStart() && !substring.isAnchorEnd()) {
                            return FilterBuilder.startsWith(AttributeBuilder.build(icfName, convertedValues.iterator().next()));
                        } else if (!substring.isAnchorStart() && substring.isAnchorEnd()) {
                            return FilterBuilder.endsWith(AttributeBuilder.build(icfName, convertedValues.iterator().next()));
                        } else if (!substring.isAnchorStart() && !substring.isAnchorEnd()) {
                            return FilterBuilder.contains(AttributeBuilder.build(icfName, convertedValues.iterator().next()));
                        } else {
                            return FilterBuilder.equalTo(AttributeBuilder.build(icfName, convertedValues.iterator().next()));
                        }
                    }
                } else if (objectFilter instanceof ComparativeFilter) {
                    ComparativeFilter comparativeFilter = (ComparativeFilter) objectFilter;
                    Collection<Object> convertedValues = convertValues(propName, comparativeFilter.getValues());
                    if (convertedValues == null || convertedValues.isEmpty()) {
                        throw new IllegalArgumentException("Comparative filter with null value makes no sense");
                    } else {
                        if (convertedValues.size() != 1) {
                            throw new IllegalArgumentException("Comparative filter with multiple values makes no sense");
                        }
                        Attribute attribute = AttributeBuilder.build(icfName, convertedValues.iterator().next());
                        if (comparativeFilter instanceof GreaterFilter) {
                            if (comparativeFilter.isEquals()) {
                                return FilterBuilder.greaterThanOrEqualTo(attribute);
                            } else {
                                return FilterBuilder.greaterThan(attribute);
                            }
                        } else if (comparativeFilter instanceof LessFilter) {
                            if (comparativeFilter.isEquals()) {
                                return FilterBuilder.lessThanOrEqualTo(attribute);
                            } else {
                                return FilterBuilder.lessThan(attribute);
                            }
                        } else {
                            throw new UnsupportedOperationException("Unsupported filter type: " + objectFilter);
                        }
                    }
                } else {
                    throw new UnsupportedOperationException("Unsupported filter type: " + objectFilter);
                }
            } catch (SchemaException ex) {
                throw ex;

            }
        } else if (valueFilter.getParentPath().equivalent(ShadowType.F_ACTIVATION)) {

            if (objectFilter instanceof EqualFilter) {
                QName propName = valueFilter.getDefinition().getItemName();
                EqualFilter<T> eq = (EqualFilter<T>) objectFilter;
                List<PrismPropertyValue<T>> values = eq.getValues();
                if (values == null || values.size() != 1) {
                    throw new SchemaException("Unexpected number of values in filter " + objectFilter);
                }
                PrismPropertyValue<T> pval = values.get(0);
                String icfName;
                Object convertedValue;
                if (propName.equals(ActivationType.F_ADMINISTRATIVE_STATUS)) {
                    icfName = OperationalAttributes.ENABLE_NAME;
                    convertedValue = pval.getValue() == ActivationStatusType.ENABLED;
                } else if (propName.equals(ActivationType.F_LOCKOUT_STATUS)) {
                    icfName = OperationalAttributes.LOCK_OUT_NAME;
                    convertedValue = pval.getValue() == LockoutStatusType.LOCKED;
                } else {
                    throw new UnsupportedOperationException("Unsupported activation property " + propName + " in filter: " + objectFilter);
                }
                Attribute attr = AttributeBuilder.build(icfName, convertedValue);
                if (valueFilter.getDefinition().isSingleValue()) {
                    return FilterBuilder.equalTo(attr);
                } else {
                    return FilterBuilder.containsAllValues(attr);
                }

            } else {
                throw new UnsupportedOperationException("Unsupported filter type in filter: " + objectFilter);
            }
        } else {
            throw new UnsupportedOperationException("Unsupported parent path " + valueFilter.getParentPath() + " in filter: " + objectFilter);
        }
    }

    private <T> Collection<Object> convertValues(QName propName, List<PrismPropertyValue<T>> values) throws SchemaException {
        if (values == null) {
            return null;
        }
        Collection<Object> convertedValues = new ArrayList<>();
        for (PrismValue value : values) {
            Object converted = ConnIdUtil.convertValueToConnId(value, null, propName);
            convertedValues.add(converted);
        }

        return convertedValues;
    }
}
