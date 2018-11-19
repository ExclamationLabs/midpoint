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

import com.evolveum.midpoint.gui.impl.page.admin.configuration.component.ObjectPolicyConfigurationTabPanel;
import com.evolveum.midpoint.prism.*;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.component.prism.ContainerValueWrapper;
import com.evolveum.midpoint.web.component.prism.ContainerWrapper;
import com.evolveum.midpoint.xml.ns._public.common.common_3.LifecycleStateModelType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectPolicyConfigurationType;

import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;

import javax.xml.namespace.QName;

/**
 * Model that returns RealValue model. This implementation works on parent of ContainerValueWrapper models (not PrismObject).
 *
 * @author skublik
 * 
 */
public class RealContainerValueFromContainerValueWrapperModel<C extends Containerable> implements IModel<C> {

	private static final long serialVersionUID = 1L;
	
	private static final Trace LOGGER = TraceManager.getTrace(RealContainerValueFromContainerValueWrapperModel.class);
   
	private IModel<ContainerValueWrapper<C>> model;

    public RealContainerValueFromContainerValueWrapperModel(IModel<ContainerValueWrapper<C>> model) {
    	this.model = model;
    }
    
    public RealContainerValueFromContainerValueWrapperModel(ContainerValueWrapper<C> value) {
    	this.model = Model.of(value);
    }

	@Override
	public void detach() {
	}

	@Override
	public C getObject() {
		
		if(model == null || model.getObject() == null ||  model.getObject().getContainerValue() == null) {
			return null;
		}
		return model.getObject().getContainerValue().getValue();
	}

	@Override
	public void setObject(C object) {
		throw new UnsupportedOperationException();
	}

}
