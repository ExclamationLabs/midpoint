/*
 * Copyright (c) 2010-2018 Evolveum
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
package com.evolveum.midpoint.schema;

import com.evolveum.midpoint.prism.*;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.util.PrismAsserts;
import com.evolveum.midpoint.prism.util.PrismTestUtil;
import com.evolveum.midpoint.schema.constants.MidPointConstants;
import com.evolveum.midpoint.schema.util.SchemaTestConstants;
import com.evolveum.midpoint.util.DOMUtil;
import com.evolveum.midpoint.util.PrettyPrinter;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowType;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;
import org.xml.sax.SAXException;

import javax.xml.namespace.QName;
import java.io.IOException;

import static com.evolveum.midpoint.prism.SerializationOptions.createSerializeForExport;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertNotNull;

/**
 * @author mederly
 *
 */
public class TestExport {

	@BeforeSuite
	public void setup() throws SchemaException, SAXException, IOException {
		PrettyPrinter.setDefaultNamespacePrefix(MidPointConstants.NS_MIDPOINT_PUBLIC_PREFIX);
		PrismTestUtil.resetPrismContext(MidPointPrismContextFactory.FACTORY);
	}

	@Test
	public void testExportShadow() throws Exception {
		System.out.println("===[ testExportShadow ]===");

		PrismContext prismContext = PrismTestUtil.getPrismContext();
		PrismObject<ShadowType> shadow = prismContext.createObjectable(ShadowType.class)
				.name("shadow1")
				.asPrismObject();
		PrismContainer<Containerable> attributes = shadow.findOrCreateContainer(ShadowType.F_ATTRIBUTES);

		final QName INT_ATTRIBUTE_NAME = new QName(MidPointConstants.NS_RI, "intAttribute");
		MutablePrismPropertyDefinition<Integer> intAttributeDef = prismContext.definitionFactory().createPropertyDefinition(
				INT_ATTRIBUTE_NAME, DOMUtil.XSD_INT);
		intAttributeDef.setRuntimeSchema(true);
		PrismProperty<Integer> intAttribute = intAttributeDef.instantiate();
		intAttribute.addRealValue(101);
		attributes.add(intAttribute);

		final QName STRING_ATTRIBUTE_NAME = new QName(MidPointConstants.NS_RI, "stringAttribute");
		MutablePrismPropertyDefinition<String> stringAttributeDef = prismContext.definitionFactory().createPropertyDefinition(
				STRING_ATTRIBUTE_NAME, DOMUtil.XSD_STRING);
		stringAttributeDef.setRuntimeSchema(true);
		PrismProperty<String> stringAttribute = stringAttributeDef.instantiate();
		stringAttribute.addRealValue("abc");
		attributes.add(stringAttribute);

		// intentionally created ad-hoc, not retrieved from the registry
		MutablePrismPropertyDefinition<Long> longTypeExtensionDef = prismContext.definitionFactory().createPropertyDefinition(
				SchemaTestConstants.EXTENSION_LONG_TYPE_ELEMENT, DOMUtil.XSD_LONG);
		longTypeExtensionDef.setRuntimeSchema(true);
		PrismProperty<Long> longExtension = longTypeExtensionDef.instantiate();
		longExtension.addRealValue(110L);
		shadow.addExtensionItem(longExtension);

		//noinspection unchecked
		PrismPropertyDefinition<Double> doubleTypeExtensionDef = prismContext.getSchemaRegistry()
				.findItemDefinitionByElementName(SchemaTestConstants.EXTENSION_DOUBLE_TYPE_ELEMENT, PrismPropertyDefinition.class);
		PrismProperty<Double> doubleExtension = doubleTypeExtensionDef.instantiate();
		doubleExtension.addRealValue(-1.0);
		shadow.addExtensionItem(doubleExtension);

		String xml = prismContext.xmlSerializer().options(createSerializeForExport()).serialize(shadow);
		System.out.println("Serialized:\n" + xml);

		PrismObject<ShadowType> shadowReparsed = prismContext.parseObject(xml);
		System.out.println("Reparsed:\n" + shadowReparsed.debugDump());
		PrismAsserts.assertEquals("objects differ", shadow, shadowReparsed);

		Item<?, ?> intAttributeReparsed = shadowReparsed.findItem(ItemPath.create(ShadowType.F_ATTRIBUTES, INT_ATTRIBUTE_NAME));
		assertNotNull(intAttributeReparsed);
		assertFalse(intAttributeReparsed.getAnyValue().isRaw());
		Item<?, ?> stringAttributeReparsed = shadowReparsed.findItem(ItemPath.create(ShadowType.F_ATTRIBUTES, STRING_ATTRIBUTE_NAME));
		assertNotNull(stringAttributeReparsed);
		assertFalse(stringAttributeReparsed.getAnyValue().isRaw());
		Item<?, ?> longExtensionReparsed = shadowReparsed.findItem(ItemPath.create(ShadowType.F_EXTENSION, SchemaTestConstants.EXTENSION_LONG_TYPE_ELEMENT));
		assertNotNull(longExtensionReparsed);
		assertFalse(longExtensionReparsed.getAnyValue().isRaw());
		Item<?, ?> doubleExtensionReparsed = shadowReparsed.findItem(ItemPath.create(ShadowType.F_EXTENSION, SchemaTestConstants.EXTENSION_DOUBLE_TYPE_ELEMENT));
		assertNotNull(doubleExtensionReparsed);
		assertFalse(doubleExtensionReparsed.getAnyValue().isRaw());
	}

}
