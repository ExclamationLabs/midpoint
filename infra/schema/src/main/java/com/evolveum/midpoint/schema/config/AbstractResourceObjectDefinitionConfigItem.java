/*
 * Copyright (C) 2010-2023 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.schema.config;

import com.evolveum.midpoint.prism.path.ItemName;
import com.evolveum.midpoint.util.MiscUtil;
import com.evolveum.midpoint.util.QNameUtil;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ResourceObjectTypeDelineationType;

import org.jetbrains.annotations.NotNull;

import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.util.exception.ConfigurationException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ResourceAttributeDefinitionType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ResourceObjectTypeDefinitionType;

import org.jetbrains.annotations.Nullable;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.List;

import static com.evolveum.midpoint.util.MiscUtil.stateCheck;

/** Type or class definition in schema handling. */
public class AbstractResourceObjectDefinitionConfigItem
        extends ConfigurationItem<ResourceObjectTypeDefinitionType> {

    @SuppressWarnings({ "unused", "WeakerAccess" }) // called dynamically
    public AbstractResourceObjectDefinitionConfigItem(@NotNull ConfigurationItem<ResourceObjectTypeDefinitionType> original) {
        super(original);
    }

    void checkAttributeNames() throws ConfigurationException {
        for (var attrDefCI : getAttributes()) {
            attrDefCI.getAttributeName();
        }
    }

    public @NotNull List<QName> getAuxiliaryObjectClassNames() throws ConfigurationException {
        ResourceObjectTypeDelineationType delineation = value().getDelineation();
        List<QName> newValues = delineation != null ? delineation.getAuxiliaryObjectClass() : List.of();
        List<QName> legacyValues = value().getAuxiliaryObjectClass();
        // We do not want to compare the lists (etc) - we simply disallow specifying at both places.
        configCheck(newValues.isEmpty() || legacyValues.isEmpty(),
                "Auxiliary object classes must not be specified in both new and legacy ways in %s", DESC);
        if (!newValues.isEmpty()) {
            return newValues;
        } else {
            return legacyValues;
        }
    }

    public List<ResourceAttributeDefinitionConfigItem> getAttributes() {
        return children(
                value().getAttribute(),
                ResourceAttributeDefinitionConfigItem.class,
                ResourceObjectTypeDefinitionType.F_ATTRIBUTE);
    }

    public List<ResourceObjectAssociationConfigItem> getAssociations() {
        return children(
                value().getAssociation(),
                ResourceObjectAssociationConfigItem.class,
                ResourceObjectTypeDefinitionType.F_ASSOCIATION);
    }

    public @Nullable ResourceAttributeDefinitionConfigItem getAttributeDefinitionIfPresent(ItemName attrName)
            throws ConfigurationException {
        List<ResourceAttributeDefinitionConfigItem> matching = new ArrayList<>();
        for (var attrDef : getAttributes()) {
            if (QNameUtil.match(attrDef.getAttributeName(), attrName)) {
                matching.add(attrDef);
            }
        }
        return single(matching, "Duplicate definition of attribute '%s' in %s", attrName, DESC);
    }
}