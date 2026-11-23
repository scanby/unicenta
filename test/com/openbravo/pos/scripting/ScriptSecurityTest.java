package com.openbravo.pos.scripting;

import static org.junit.Assert.*;
import org.junit.Test;

/**
 * Verifies that scripts stored in RESOURCES cannot replace the bundled ones.
 */
public class ScriptSecurityTest {

    @Test
    public void bundledTemplateIsFound() {
        assertNotNull(ScriptSecurity.loadTrusted("Menu.Root"));
        assertNotNull(ScriptSecurity.loadTrusted("script.linediscount"));
    }

    @Test
    public void matchingDbScriptIsAccepted() {
        String trusted = ScriptSecurity.loadTrusted("Menu.Root");
        assertEquals(trusted, ScriptSecurity.resolve("Menu.Root", trusted, null));
    }

    @Test
    public void modifiedDbScriptIsIgnored() {
        String trusted = ScriptSecurity.loadTrusted("Menu.Root");
        String malicious = trusted + "\nRuntime.getRuntime().exec(\"calc\");";
        String result = ScriptSecurity.resolve("Menu.Root", malicious, null);
        assertEquals(trusted, result);
        assertFalse(result.contains("Runtime.getRuntime"));
    }

    @Test
    public void dbOnlyScriptIsRejectedByDefault() {
        System.clearProperty(ScriptSecurity.ALLOW_DB_PROPERTY);
        assertNull(ScriptSecurity.resolve("custom.evil.script", "print('hola');", null));
    }

    @Test
    public void dbOnlyScriptRequiresExplicitOptIn() {
        try {
            System.setProperty(ScriptSecurity.ALLOW_DB_PROPERTY, "true");
            assertEquals("print('hola');",
                    ScriptSecurity.resolve("custom.evil.script", "print('hola');", null));
        } finally {
            System.clearProperty(ScriptSecurity.ALLOW_DB_PROPERTY);
        }
    }

    @Test
    public void invalidResourceNamesAreNotLoaded() {
        assertNull(ScriptSecurity.loadTrusted("../secret"));
        assertNull(ScriptSecurity.loadTrusted("/etc/passwd"));
    }
}
