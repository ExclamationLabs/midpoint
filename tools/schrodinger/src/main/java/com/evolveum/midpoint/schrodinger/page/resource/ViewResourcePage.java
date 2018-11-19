package com.evolveum.midpoint.schrodinger.page.resource;

import com.codeborne.selenide.Condition;
import com.codeborne.selenide.SelenideElement;
import com.evolveum.midpoint.schrodinger.MidPoint;
import com.evolveum.midpoint.schrodinger.component.common.FeedbackBox;
import com.evolveum.midpoint.schrodinger.component.resource.ResourceAccountsTab;
import com.evolveum.midpoint.schrodinger.component.resource.ResourceConfigurationTab;
import com.evolveum.midpoint.schrodinger.page.BasicPage;
import com.evolveum.midpoint.schrodinger.util.Schrodinger;
import org.openqa.selenium.By;

import static com.codeborne.selenide.Selenide.$;


public class ViewResourcePage extends BasicPage {

    public ResourceConfigurationTab clickEditResourceConfiguration() {

        $(Schrodinger.byDataResourceKey("a", "pageResource.button.configurationEdit")).waitUntil(Condition.appears, MidPoint.TIMEOUT_DEFAULT_2_S).click();

        return new ResourceConfigurationTab(new EditResourceConfigurationPage(), null);
    }

    public ResourceWizardPage clickShowUsingWizard() {

        $(Schrodinger.byDataResourceKey("a", "pageResource.button.wizardShow")).waitUntil(Condition.appears, MidPoint.TIMEOUT_DEFAULT_2_S).click();

        return new ResourceWizardPage();
    }

    public ResourceAccountsTab<ViewResourcePage> clicAccountsTab() {

        $(Schrodinger.byDataResourceKey("schrodinger", "PageResource.tab.content.account")).parent()
                .waitUntil(Condition.visible, MidPoint.TIMEOUT_DEFAULT_2_S).click();

        SelenideElement tabContent = $(By.cssSelector(".tab-pane.active"))
                .waitUntil(Condition.visible, MidPoint.TIMEOUT_DEFAULT_2_S);

        return new ResourceAccountsTab<>(this, tabContent);
    }

    public ViewResourcePage refreshSchema() {
        $(Schrodinger.byDataResourceKey("a", "pageResource.button.refreshSchema")).waitUntil(Condition.appears, MidPoint.TIMEOUT_DEFAULT_2_S).click();

        return this;
    }

}
