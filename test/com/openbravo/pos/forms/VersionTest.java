package com.openbravo.pos.forms;

import static org.junit.Assert.*;
import com.openbravo.pos.updates.UpdateService;
import org.junit.Test;

/** The build version must always be visible and greater than the schema version. */
public class VersionTest {

    @Test
    public void buildVersionHasThreeNumericParts() {
        assertTrue(AppLocal.APP_BUILD, AppLocal.APP_BUILD.matches("\\d+\\.\\d+\\.\\d+"));
    }

    @Test
    public void buildVersionExtendsTheSchemaVersion() {
        assertTrue(AppLocal.APP_BUILD.startsWith(AppLocal.APP_VERSION + "."));
        assertTrue(UpdateService.compareVersions(AppLocal.APP_BUILD, AppLocal.APP_VERSION) > 0);
    }
}
