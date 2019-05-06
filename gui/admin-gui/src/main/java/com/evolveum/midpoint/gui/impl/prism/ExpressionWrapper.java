package com.evolveum.midpoint.gui.impl.prism;

import com.evolveum.midpoint.gui.api.prism.ItemStatus;
import com.evolveum.midpoint.prism.*;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

import org.jetbrains.annotations.Nullable;

/**
 * Created by honchar
 */
public class ExpressionWrapper extends PrismPropertyWrapperImpl<ExpressionType> {

    private static final Trace LOGGER = TraceManager.getTrace(ExpressionWrapper.class);
    private ConstructionType construction;

    public ExpressionWrapper(@Nullable PrismContainerValueWrapper parent, PrismProperty<ExpressionType> property, ItemStatus status) {
        super(parent, property, status);

        PrismContainerWrapperImpl outboundContainer = (PrismContainerWrapperImpl)parent.getParent();
        if (outboundContainer != null) {
            PrismContainerValueWrapperImpl outboundValue = (PrismContainerValueWrapperImpl) outboundContainer.getParent();
            if (outboundValue != null) {
                PrismContainerWrapperImpl associationContainer = (PrismContainerWrapperImpl) outboundValue.getParent();
                if (associationContainer != null) {
                    PrismContainerValueWrapperImpl constructionContainer = (PrismContainerValueWrapperImpl) associationContainer.getParent();
                    if (constructionContainer != null && constructionContainer.getRealValue() instanceof ConstructionType) {
                        construction = (ConstructionType) constructionContainer.getRealValue();
                    }
                }
            }
        }
    }

    public boolean isConstructionExpression(){
        PrismContainerWrapperImpl outboundContainer = getParent() != null ? (PrismContainerWrapperImpl)getParent().getParent() : null;
        if (outboundContainer != null && MappingType.class.equals(outboundContainer.getCompileTimeClass())) {
            PrismContainerValueWrapperImpl outboundValue = (PrismContainerValueWrapperImpl) outboundContainer.getParent();
            if (outboundValue != null) {
                PrismContainerWrapperImpl associationContainer = (PrismContainerWrapperImpl) outboundValue.getParent();
                if (associationContainer != null && 
                        (ResourceObjectAssociationType.class.equals(associationContainer.getCompileTimeClass()) ||
                                ResourceAttributeDefinitionType.class.equals(associationContainer.getCompileTimeClass()))) {
                    PrismContainerValueWrapperImpl constructionContainer = (PrismContainerValueWrapperImpl) associationContainer.getParent();
                    if (constructionContainer != null && constructionContainer.getRealValue() instanceof ConstructionType) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public ConstructionType getConstruction() {
        return construction;
    }

    public void setConstruction(ConstructionType construction) {
        this.construction = construction;
    }

//    @Override
//    public boolean hasChanged() {
//        for (ValueWrapperOld valueWrapper : getValues()) {
//            ExpressionType expression = (ExpressionType) ((PrismPropertyValue) valueWrapper.getValue()).getValue();
//            ExpressionType oldExpressionValue = (ExpressionType)((PrismPropertyValue)valueWrapper.getOldValue()).getValue();
//            try {
//                switch (valueWrapper.getStatus()) {
//                    case DELETED:
//                        return true;
//                    case ADDED:
//                    case NOT_CHANGED:
//                        if (ExpressionUtil.areAllExpressionValuesEmpty(oldExpressionValue, prismContext) && ExpressionUtil.areAllExpressionValuesEmpty(expression, prismContext)) {
//                            return false;
//                        } else if (!ExpressionUtil.areAllExpressionValuesEmpty(oldExpressionValue, prismContext) && ExpressionUtil.areAllExpressionValuesEmpty(expression, prismContext)) {
//                            return true;
//                        } else if (ExpressionUtil.areAllExpressionValuesEmpty(oldExpressionValue, prismContext) && !ExpressionUtil.areAllExpressionValuesEmpty(expression, prismContext)) {
//                            return true;
//                        } else if (valueWrapper.hasValueChanged()) {
//                            return true;
//                        }
//                }
//            } catch (SchemaException e) {
//                LoggingUtils.logException(LOGGER, "Cannot check changes of the expression value" + expression, e);
//                return false;
//            }
//        }

//        return false;
//    }

}
