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

package com.openbravo.pos.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Minimal local secret store. Each secret is a random byte sequence persisted
 * in the user's home directory with owner-only permissions where the platform
 * supports them (POSIX). Windows users must restrict the ACL manually; a
 * warning is logged once.
 *
 * <p><strong>Operational note:</strong> these files are part of the installation's
 * security. Back them up together with {@code unicentaopos.properties}: losing
 * them makes values encrypted with the v2 format unrecoverable.</p>
 */
public final class SecureKeyStore {

    private static final Logger logger = Logger.getLogger("com.openbravo.pos.util.SecureKeyStore");

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Map<String, byte[]> CACHE = new HashMap<String, byte[]>();

    private SecureKeyStore() {
    }

    /**
     * Returns the secret for the given file name, creating it on first use.
     *
     * @param fileName file name inside user.home (must not contain a path separator)
     * @param size     number of random bytes for a newly created secret
     * @return the secret bytes (cached and never modified by callers)
     * @throws IOException if the file cannot be read or created
     */
    public static synchronized byte[] get(String fileName, int size) throws IOException {
        if (fileName == null || fileName.length() == 0
                || fileName.indexOf('/') >= 0 || fileName.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("Invalid secret file name");
        }
        byte[] cached = CACHE.get(fileName);
        if (cached != null) {
            return cached;
        }

        String directory = System.getProperty("unicenta.keystore.dir");
        File file = directory == null
                ? new File(System.getProperty("user.home"), fileName)
                : new File(new File(directory), fileName);
        byte[] secret;
        if (file.isFile()) {
            secret = readFully(file);
        } else {
            secret = new byte[size];
            RANDOM.nextBytes(secret);
            writeSecret(file, secret);
        }
        CACHE.put(fileName, secret);
        return secret;
    }

    private static byte[] readFully(File file) throws IOException {
        FileInputStream in = new FileInputStream(file);
        try {
            byte[] data = new byte[(int) file.length()];
            int read = 0;
            while (read < data.length) {
                int n = in.read(data, read, data.length - read);
                if (n < 0) {
                    break;
                }
                read += n;
            }
            if (read != data.length) {
                throw new IOException("Incomplete secret file: " + file.getAbsolutePath());
            }
            return data;
        } finally {
            in.close();
        }
    }

    private static void writeSecret(File file, byte[] secret) throws IOException {
        FileOutputStream out = new FileOutputStream(file);
        try {
            out.write(secret);
        } finally {
            out.close();
        }
        restrictPermissions(file);
    }

    private static void restrictPermissions(File file) {
        try {
            Set<PosixFilePermission> perms = EnumSet.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(file.toPath(), perms);
        } catch (UnsupportedOperationException e) {
            logger.warning("POSIX permissions not supported: restrict access to "
                    + file.getAbsolutePath() + " manually (owner only).");
        } catch (IOException e) {
            logger.log(Level.WARNING, "Cannot restrict permissions of " + file.getAbsolutePath(), e);
        }
    }
}
