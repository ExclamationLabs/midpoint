/**
 * Copyright (c) 2018 Evolveum
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
package com.evolveum.midpoint.test.asserter;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertNull;
import static org.testng.AssertJUnit.assertTrue;

import javax.xml.datatype.XMLGregorianCalendar;

import com.evolveum.icf.dummy.resource.DummyAccount;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.schema.DeltaConvertor;
import com.evolveum.midpoint.test.DummyResourceContoller;
import com.evolveum.midpoint.test.IntegrationTestTools;
import com.evolveum.midpoint.test.util.TestUtil;
import com.evolveum.midpoint.xml.ns._public.common.common_3.OperationResultStatusType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.PendingOperationExecutionStatusType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.PendingOperationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowType;
import com.evolveum.prism.xml.ns._public.types_3.ObjectDeltaType;

/**
 * @author semancik
 *
 */
public class DummyAccountAsserter<R> extends DummyObjectAsserter<DummyAccount,R> {
	
	public DummyAccountAsserter(DummyAccount dummyAccount, String dummyResourceName) {
		super(dummyAccount, dummyResourceName);
	}
	
	public DummyAccountAsserter(DummyAccount dummyAccount, String dummyResourceName, String details) {
		super(dummyAccount, dummyResourceName, details);
	}
	
	public DummyAccountAsserter(DummyAccount dummyAccount, String dummyResourceName, R returnAsserter, String details) {
		super(dummyAccount, dummyResourceName, returnAsserter, details);
	}
	
	@Override
	public DummyAccountAsserter<R> assertName(String expected) {
		super.assertName(expected);
		return this;
	}
	
	@Override
	public <T> DummyAccountAsserter<R> assertAttribute(String attrName, T... expected) {
		super.assertAttribute(attrName, expected);
		return this;
	}
	
	@Override
	public <T> DummyAccountAsserter<R> assertNoAttribute(String attrName) {
		super.assertNoAttribute(attrName);
		return this;
	}
	
	@Override
	public DummyAccountAsserter<R> assertEnabled() {
		super.assertEnabled();
		return this;
	}
	
	@Override
	public DummyAccountAsserter<R> assertLastModifier(String expected) {
		super.assertLastModifier(expected);
		return this;
	}
	
	public DummyAccountAsserter<R> assertFullName(String expected) {
		assertAttribute(DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_FULLNAME_NAME, expected);
		return this;
	}
		
	public DummyAccountAsserter<R> assertPassword(String expected) {
		assertEquals("Wrong password in "+desc(), expected, getDummyObjectAssertExists().getPassword());
		return this;
	}
	
	public DummyAccountAsserter<R> display() {
		super.display();
		return this;
	}
	
	public DummyAccountAsserter<R> display(String message) {
		super.display(message);
		return this;
	}
}
