package com.evolveum.midpoint.ninja.upgrade;

import java.io.File;

import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.testng.annotations.Listeners;

@ContextConfiguration(locations = "classpath:ctx-ninja-test.xml")
@DirtiesContext
@Listeners({ com.evolveum.midpoint.tools.testng.AlphabeticalMethodInterceptor.class })
public class UpgradeFromLtsToLtsTest extends UpgradeTest {

    @Override
    protected File getScriptsDirectory() {
        return new File("./src/test/resources/upgrade/sql-scripts/4.4.5");
    }

    @Override
    protected String getOldSchemaChangeNumber() {
        return "1";
    }

    @Override
    protected String getOldSchemaAuditChangeNumber() {
        return "1";
    }
}
