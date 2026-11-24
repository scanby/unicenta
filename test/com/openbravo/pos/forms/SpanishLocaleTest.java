package com.openbravo.pos.forms;

import static org.junit.Assert.*;
import java.io.File;
import java.util.Locale;
import java.util.ResourceBundle;
import org.junit.Test;

/** Verifies that Spanish is the shipped default locale and that it is really bundled. */
public class SpanishLocaleTest {

    @Test
    public void spanishBundleIsAvailableOnTheClasspath() {
        ResourceBundle bundle = ResourceBundle.getBundle("pos_messages", new Locale("es", "ES"));
        assertEquals("Aceptar", bundle.getString("Button.OK"));
        assertEquals("Cancelar", bundle.getString("Button.Cancel"));
    }

    @Test
    public void reportBundleIsAvailableOnTheClasspath() {
        ResourceBundle bundle = ResourceBundle.getBundle(
                "com.openbravo.reports.taxes_messages", new Locale("es", "ES"));
        assertNotNull(bundle.getString("label.title"));
    }

    @Test
    public void newConfigurationDefaultsToSpanish() throws Exception {
        File file = File.createTempFile("unicenta-locale", ".properties");
        file.deleteOnExit();
        AppConfig config = new AppConfig(file);
        config.load();
        assertEquals("es", config.getProperty("user.language"));
        assertEquals("ES", config.getProperty("user.country"));
    }

    @Test
    public void oldEnglishConfigurationIsMigratedOnce() throws Exception {
        File file = File.createTempFile("unicenta-locale-migrate", ".properties");
        file.deleteOnExit();
        AppConfig config = new AppConfig(file);
        config.load();
        config.setProperty("user.language", "en");
        config.setProperty("user.country", "GB");
        config.setProperty("locale.migration", null);   // remove the marker
        config.save();

        AppConfig reloaded = new AppConfig(file);
        reloaded.load();
        assertEquals("en", reloaded.getProperty("user.language"));
        assertTrue(reloaded.ensureDefaultSpanish());
        assertEquals("es", reloaded.getProperty("user.language"));
        assertEquals("ES", reloaded.getProperty("user.country"));
        assertFalse("migration must run only once", reloaded.ensureDefaultSpanish());
    }
}
