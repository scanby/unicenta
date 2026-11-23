//    uniCenta oPOS  - Touch Friendly Point Of Sale
//    Copyright (c) 2009-2014 uniCenta
//    http://www.unicenta.com
//
//    This file is part of uniCenta oPOS
//
//    uniCenta oPOS is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation, either version 3 of the License, or
//    (at your option) any later version.
//
//   uniCenta oPOS is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with uniCenta oPOS.  If not, see <http://www.gnu.org/licenses/>.

package com.openbravo.pos.scripting;

import com.openbravo.pos.util.StringUtils;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Trust boundary for BeanShell scripts.
 *
 * <p>Historically the POS executed menu and ticket-event scripts read directly
 * from the RESOURCES table. Anyone able to write that table could execute
 * arbitrary Java. Since every shipped resource is seeded into the database
 * from a bundled template under {@code /com/openbravo/pos/templates/}, we can
 * verify a DB script against its bundled original before executing it.</p>
 *
 * <p>Policy:</p>
 * <ul>
 *   <li>if the DB value matches the bundled template, execute it;</li>
 *   <li>if it differs, log the tampering and execute the bundled version;</li>
 *   <li>if there is no bundled version, refuse to execute it unless the
 *       administrator explicitly sets {@code -Dunicenta.scripts.allowdb=true}.</li>
 * </ul>
 */
public final class ScriptSecurity {

    public static final String ALLOW_DB_PROPERTY = "unicenta.scripts.allowdb";
    public static final String EXPOSE_DB_PASSWORD_PROPERTY = "scripting.exposeDbPassword";

    private static final Logger logger = Logger.getLogger("com.openbravo.pos.scripting.ScriptSecurity");
    private static final String TEMPLATE_ROOT = "/com/openbravo/pos/templates/";
    private static final String[] SUFFIXES = {".txt", ".xml", ""};

    private ScriptSecurity() {
    }

    /**
     * @return true when the administrator explicitly allows DB-only scripts
     */
    public static boolean allowDbScripts() {
        return Boolean.getBoolean(ALLOW_DB_PROPERTY);
    }

    /**
     * Resolves a script that is about to be evaluated.
     *
     * @param resourceName logical resource name, e.g. {@code Menu.Root}
     * @param dbScript     value read from the RESOURCES table (may be null)
     * @param fallback     optional classpath resource used when no bundled
     *                     template matches the logical name
     * @return the script that is safe to evaluate, or null if none
     */
    public static String resolve(String resourceName, String dbScript, String fallback) {
        String trusted = loadTrusted(resourceName);
        if (trusted == null && fallback != null) {
            trusted = loadClasspath(fallback);
        }

        if (dbScript == null) {
            return trusted;
        }
        if (trusted != null && normalize(trusted).equals(normalize(dbScript))) {
            return dbScript;
        }
        if (trusted != null) {
            logger.log(Level.SEVERE,
                    "Resource {0} was modified in the database. Ignoring the stored script "
                    + "and using the bundled version. Set " + ALLOW_DB_PROPERTY
                    + "=true only if this customization is trusted.", resourceName);
            return trusted;
        }
        if (allowDbScripts()) {
            logger.log(Level.WARNING, "Executing DB-only script {0} because {1}=true",
                    new Object[] {resourceName, ALLOW_DB_PROPERTY});
            return dbScript;
        }
        logger.log(Level.SEVERE, "Refusing to execute DB-only script {0}: no bundled copy exists. "
                + "Add a bundled template or set {1}=true explicitly.",
                new Object[] {resourceName, ALLOW_DB_PROPERTY});
        return null;
    }

    /** Loads the bundled version of a resource, trying the usual extensions. */
    public static String loadTrusted(String resourceName) {
        if (resourceName == null || !resourceName.matches("[A-Za-z0-9._-]+")) {
            return null;
        }
        for (String suffix : SUFFIXES) {
            String script = loadClasspath(TEMPLATE_ROOT + resourceName + suffix);
            if (script != null) {
                return script;
            }
        }
        return null;
    }

    private static String loadClasspath(String path) {
        try {
            return StringUtils.readResource(path);
        } catch (IOException e) {
            return null;
        }
    }

    private static String normalize(String s) {
        return s.replace("\r\n", "\n").replace('\r', '\n').trim();
    }
}
