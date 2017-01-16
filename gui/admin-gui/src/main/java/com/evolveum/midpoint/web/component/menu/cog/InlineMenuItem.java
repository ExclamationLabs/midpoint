package com.evolveum.midpoint.web.component.menu.cog;

import com.evolveum.midpoint.web.component.data.column.DoubleButtonColumn;
import org.apache.wicket.model.IModel;

import java.io.Serializable;

/**
 * @author lazyman
 */
public class InlineMenuItem implements Serializable {

    private IModel<String> label;
    private IModel<Boolean> enabled;
    private IModel<Boolean> visible;
    private InlineMenuItemAction action;
    private boolean submit;
    private int id = -1;
    private String buttonIconCssClass;
    private String buttonColorCssClass;

    public InlineMenuItem() {
        this(null, null);
    }

    public InlineMenuItem(IModel<String> label) {
        this(label, null);
    }

    public InlineMenuItem(IModel<String> label, InlineMenuItemAction action) {
        this(label, false, action);
    }

    public InlineMenuItem(IModel<String> label, boolean submit, InlineMenuItemAction action) {
        this(label, null, null, submit, action, -1, "", "");
    }

    public InlineMenuItem(IModel<String> label, boolean submit, InlineMenuItemAction action, int id) {
        this(label, null, null, submit, action, id, "", DoubleButtonColumn.BUTTON_COLOR_CLASS.DEFAULT.toString());
    }

    public InlineMenuItem(IModel<String> label, boolean submit, InlineMenuItemAction action, int id,
                          String buttonIconCssClass) {
        this(label, null, null, submit, action, id, buttonIconCssClass, DoubleButtonColumn.BUTTON_COLOR_CLASS.DEFAULT.toString());
    }

    public InlineMenuItem(IModel<String> label, boolean submit, InlineMenuItemAction action, int id,
                          String buttonIconCssClass, String buttonColorCssClass) {
        this(label, null, null, submit, action, id, buttonIconCssClass, buttonColorCssClass);
    }

    public InlineMenuItem(IModel<String> label, IModel<Boolean> enabled, IModel<Boolean> visible,
                          InlineMenuItemAction action) {
        this(label, enabled, visible, false, action, -1, "", "");
    }

    public InlineMenuItem(IModel<String> label, IModel<Boolean> enabled, IModel<Boolean> visible, boolean submit,
                          InlineMenuItemAction action) {
        this(label, enabled, visible, submit, action, -1, "", "");
    }

    public InlineMenuItem(IModel<String> label, IModel<Boolean> enabled, IModel<Boolean> visible, boolean submit,
                          InlineMenuItemAction action, int id, String buttonIconCssClass, String buttonColorCssClass) {
        this.label = label;
        this.enabled = enabled;
        this.visible = visible;
        this.action = action;
        this.submit = submit;
        this.id = id;
        this.buttonIconCssClass = buttonIconCssClass;
        this.buttonColorCssClass = buttonColorCssClass;
    }

    public InlineMenuItemAction getAction() {
        return action;
    }

    public IModel<Boolean> getEnabled() {
        return enabled;
    }

    public IModel<String> getLabel() {
        return label;
    }

    /**
     * if true, link must be rendered as submit link button, otherwise normal ajax link
     */
    public boolean isSubmit() {
        return submit;
    }

    public IModel<Boolean> getVisible() {
        return visible;
    }

    public boolean isDivider() {
        return label == null && action == null;
    }

    public boolean isMenuHeader() {
        return label != null && action == null;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getButtonIconCssClass() {
        return buttonIconCssClass;
    }

    public void setButtonIconCssClass(String buttonIconCssClass) {
        this.buttonIconCssClass = buttonIconCssClass;
    }

    public String getButtonColorCssClass() {
        return buttonColorCssClass;
    }

    public void setButtonColorCssClass(String buttonColorCssClass) {
        this.buttonColorCssClass = buttonColorCssClass;
    }
}
