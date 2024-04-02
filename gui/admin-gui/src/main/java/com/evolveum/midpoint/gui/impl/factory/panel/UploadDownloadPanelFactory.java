/*
 * Copyright (C) 2010-2020 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.gui.impl.factory.panel;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Serializable;
import javax.annotation.PostConstruct;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.markup.html.form.upload.FileUpload;
import org.springframework.stereotype.Component;

import com.evolveum.midpoint.gui.api.page.PageBase;
import com.evolveum.midpoint.gui.api.prism.wrapper.ItemWrapper;
import com.evolveum.midpoint.util.DOMUtil;
import com.evolveum.midpoint.web.component.input.UploadDownloadPanel;
import com.evolveum.midpoint.web.component.prism.InputPanel;

/**
 * @author katkav
 */
//FIXME serializable
@Component
public class UploadDownloadPanelFactory<T> extends AbstractInputGuiComponentFactory<T> implements Serializable {

    @PostConstruct
    public void register() {
        getRegistry().addToRegistry(this);
    }

    @Override
    public <IW extends ItemWrapper<?, ?>> boolean match(IW wrapper) {
        return DOMUtil.XSD_BASE64BINARY.equals(wrapper.getTypeName());
    }

    @Override
    protected InputPanel getPanel(PrismPropertyPanelContext<T> panelCtx) {
        return new UploadDownloadPanel(panelCtx.getComponentId(), false) { //getModel().getObject().isReadonly()

            private static final long serialVersionUID = 1L;

            @Override
            public InputStream getStream() {
                T object = panelCtx.getRealValueModel().getObject();
                if (object instanceof String) {
                    return new ByteArrayInputStream(((String) object).getBytes());
                }
                return object != null ? new ByteArrayInputStream((byte[]) object) : new ByteArrayInputStream(new byte[0]);
            }

            @Override
            public void updateValue(FileUpload file) {
                panelCtx.getRealValueModel().setObject((T) file.getBytes());
            }

            @Override
            public void uploadFileFailed(AjaxRequestTarget target) {
                super.uploadFileFailed(target);
                target.add(((PageBase) getPage()).getFeedbackPanel());
            }
        };
    }
}
