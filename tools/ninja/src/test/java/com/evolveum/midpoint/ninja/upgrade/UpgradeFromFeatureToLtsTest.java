package com.evolveum.midpoint.ninja.upgrade;

import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.testng.annotations.Listeners;

import java.io.File;

@ContextConfiguration(locations = "classpath:ctx-ninja-test.xml")
@DirtiesContext
@Listeners({ com.evolveum.midpoint.tools.testng.AlphabeticalMethodInterceptor.class })
public class UpgradeFromFeatureToLtsTest extends UpgradeTest {

    @Override
    protected File getScriptsDirectory() {
        return new File("./src/test/resources/upgrade/sql-scripts/4.7.1");
    }

    @Override
    protected String getOldSchemaChangeNumber() {
        return "15";
    }

    @Override
    protected String getOldSchemaAuditChangeNumber() {
        return "3";
    }
}
