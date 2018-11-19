/*
 * Copyright (c) 2010-2017 Evolveum
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

package com.evolveum.midpoint.gui.impl.model;

import com.evolveum.midpoint.prism.*;
import com.evolveum.midpoint.web.component.prism.ContainerValueWrapper;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectPolicyConfigurationType;

import org.apache.wicket.model.IModel;

import javax.xml.namespace.QName;

/**
 * Model that returns RealValue model. This implementation works on ContainerValueWrapper models (not PrismObject).
 *
 * @author skublik
 * 
 */
public class RealValueOfSingleValuePropertyAsStringFromContainerValueWrapperModel<T,C extends Containerable> implements IModel<String> {

	private static final long serialVersionUID = 1L;
   
	private RealValueOfSingleValuePropertyFromSingleValueContainerValueWrapperModel<T, C> model;

    public RealValueOfSingleValuePropertyAsStringFromContainerValueWrapperModel(IModel<ContainerValueWrapper<C>> model, QName item) {
    	this.model = new RealValueOfSingleValuePropertyFromSingleValueContainerValueWrapperModel<T, C>(model, item);
    }

	@Override
	public void detach() {
	}

	@Override
	public String getObject() {
		
		if(this.model.getObject() == null){
			return null;
		}
		return objectToString(this.model.getObject());
	}

	protected String objectToString(T object) {
		return object.toString();
	}

	@Override
	public void setObject(String object) {
		throw new UnsupportedOperationException();
	}

}
