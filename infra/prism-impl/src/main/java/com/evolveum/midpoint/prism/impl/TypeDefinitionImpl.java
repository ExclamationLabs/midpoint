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

package com.evolveum.midpoint.prism.impl;

import com.evolveum.midpoint.prism.ComplexTypeDefinition;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.TypeDefinition;
import org.jetbrains.annotations.NotNull;

import com.evolveum.midpoint.util.QNameUtil;

import javax.xml.namespace.QName;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * @author mederly
 */
public abstract class TypeDefinitionImpl extends DefinitionImpl implements TypeDefinition {

	protected QName superType;
	protected Class<?> compileTimeClass;
	@NotNull protected final Set<TypeDefinition> staticSubTypes = new HashSet<>();
	protected Integer instantiationOrder;

	public TypeDefinitionImpl(QName typeName, PrismContext prismContext) {
		super(typeName, prismContext);
	}

	@Override
	public QName getSuperType() {
		return superType;
	}

	public void setSuperType(QName superType) {
		this.superType = superType;
	}

	@Override
	@NotNull
	public Collection<TypeDefinition> getStaticSubTypes() {
		return staticSubTypes;
	}

	public void addStaticSubType(TypeDefinition subtype) {
		staticSubTypes.add(subtype);
	}

	@Override
	public Integer getInstantiationOrder() {
		return instantiationOrder;
	}

	public void setInstantiationOrder(Integer instantiationOrder) {
		this.instantiationOrder = instantiationOrder;
	}

	@Override
	public Class<?> getCompileTimeClass() {
		return compileTimeClass;
	}

	public void setCompileTimeClass(Class<?> compileTimeClass) {
		this.compileTimeClass = compileTimeClass;
	}

	protected void copyDefinitionData(TypeDefinitionImpl clone) {
		super.copyDefinitionData(clone);
		clone.superType = this.superType;
		clone.compileTimeClass = this.compileTimeClass;
	}
	
	@Override
	public boolean canRepresent(QName typeName) {
		if (QNameUtil.match(typeName, getTypeName())) {
			return true;
		}
		if (superType != null) {
			ComplexTypeDefinition supertypeDef = getPrismContext().getSchemaRegistry().findComplexTypeDefinitionByType(superType);
			return supertypeDef.canRepresent(typeName);
		} else {
			return false;
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof TypeDefinitionImpl))
			return false;
		if (!super.equals(o))
			return false;
		TypeDefinitionImpl that = (TypeDefinitionImpl) o;
		return Objects.equals(superType, that.superType) &&
				Objects.equals(compileTimeClass, that.compileTimeClass);
	}

	@Override
	public int hashCode() {
		return Objects.hash(super.hashCode(), superType, compileTimeClass);
	}
}
