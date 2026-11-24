package com.openbravo.pos.updates;

import static org.junit.Assert.*;
import com.openbravo.pos.forms.AppConfig;
import org.junit.Test;

/**
 * Tests for the silent update module (no network access required).
 */
public class UpdateServiceTest {

    @Test
    public void versionComparisonIgnoresReleaseSuffixes() {
        assertEquals(0, UpdateService.compareVersions("v3.81-hardened-1", "3.81"));
        assertEquals(0, UpdateService.compareVersions("3.81", "3.81"));
    }

    @Test
    public void versionComparisonDetectsNewerAndOlderReleases() {
        assertTrue(UpdateService.compareVersions("v3.90", "3.81") > 0);
        assertTrue(UpdateService.compareVersions("3.70", "3.81") < 0);
        assertTrue(UpdateService.compareVersions(null, "3.81") < 0);
    }

    @Test
    public void defaultsAreConservative() {
        AppConfig config = new AppConfig(new String[0]);
        UpdateSettings.installDefaults(config);
        assertFalse(UpdateSettings.isEnabled(config));
        assertTrue(UpdateSettings.isSilent(config));
        assertFalse(UpdateSettings.isAutoInstall(config));
        assertEquals(24, UpdateSettings.getIntervalHours(config));
        assertEquals(UpdateSettings.DEFAULT_URL, UpdateSettings.getUrl(config));
    }

    @Test
    public void configurationPanelRoundTripsItsSettings() {
        AppConfig config = new AppConfig(new String[0]);
        UpdateSettings.installDefaults(config);
        JPanelConfigUpdates panel = new JPanelConfigUpdates();
        panel.loadProperties(config);
        assertFalse(panel.hasChanged());
        config.setProperty(UpdateSettings.ENABLED, "true");
        panel.loadProperties(config);
        panel.saveProperties(config);
        assertEquals("true", config.getProperty(UpdateSettings.ENABLED));
        assertFalse(panel.hasChanged());
    }

    @Test
    public void invalidValuesFallBackToSafeDefaults() {
        AppConfig config = new AppConfig(new String[0]);
        config.setProperty(UpdateSettings.INTERVAL_HOURS, "not-a-number");
        config.setProperty(UpdateSettings.URL, "http://insecure.example.com");
        assertEquals(24, UpdateSettings.getIntervalHours(config));
        assertEquals(UpdateSettings.DEFAULT_URL, UpdateSettings.getUrl(config));
    }
}
