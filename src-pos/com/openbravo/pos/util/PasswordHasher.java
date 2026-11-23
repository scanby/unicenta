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

import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Password hashing based on PBKDF2-HMAC-SHA256 with a random per-password salt.
 *
 * <p>PBKDF2 is implemented on top of {@link Mac} (HmacSHA256 is available in every
 * supported JRE, including Java 6/7), so no external provider is required.</p>
 *
 * <p>Stored format:</p>
 * <pre>pbkdf2&lt;iterations&gt;$&lt;salt in hex&gt;$&lt;derived key in hex&gt;</pre>
 *
 * <p>Legacy values ({@code sha1:}, {@code plain:}, clear text and {@code empty:})
 * are still understood by {@link Hashcypher}, so existing installations keep working.
 * Use {@link #needsUpgrade(String)} to detect them and rehash on a successful login.</p>
 *
 * @see Hashcypher
 */
public final class PasswordHasher {

    /** Prefix stored in the database for the new hash format. */
    public static final String PREFIX = "pbkdf2";

    private static final char SEPARATOR = '$';
    private static final String ALGORITHM = "HmacSHA256";
    private static final int ITERATIONS = 120000;
    private static final int KEY_LENGTH_BYTES = 32;
    private static final int SALT_LENGTH_BYTES = 16;
    private static final int MAX_STORED_ITERATIONS = 1000000;

    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
    }

    /**
     * Creates a salted PBKDF2 hash for the given password.
     * An empty password keeps the legacy {@code empty:} marker for compatibility
     * with installations that rely on password-less users (see IMPROVEMENT_PLAN, Fase 2).
     *
     * @param sPassword clear text password
     * @return the value to store in PEOPLE.APPPASSWORD
     */
    public static String hash(String sPassword) {
        if (sPassword == null || sPassword.length() == 0) {
            return "empty:";
        }
        byte[] salt = new byte[SALT_LENGTH_BYTES];
        RANDOM.nextBytes(salt);
        try {
            byte[] derived = pbkdf2(sPassword, salt, ITERATIONS, KEY_LENGTH_BYTES);
            return PREFIX + SEPARATOR + ITERATIONS + SEPARATOR
                    + StringUtils.byte2hex(salt) + SEPARATOR
                    + StringUtils.byte2hex(derived);
        } catch (GeneralSecurityException e) {
            // HmacSHA256 must always be present in a compliant JRE. If it is not,
            // fail closed instead of silently downgrading to a weak hash.
            throw new IllegalStateException("PBKDF2 not available in this JRE", e);
        }
    }

    /**
     * Checks a clear text password against a PBKDF2 stored value.
     *
     * @param sPassword clear text password
     * @param sStored   stored value in PBKDF2 format
     * @return true when the password matches
     */
    public static boolean verifyPbkdf2(String sPassword, String sStored) {
        if (sPassword == null || sStored == null) {
            return false;
        }
        String[] parts = sStored.split("\\$");
        if (parts.length != 4 || !PREFIX.equals(parts[0])) {
            return false;
        }
        // hex2byte() does not reject invalid digits and would silently turn
        // "zz" into 0xFF. Enforce the exact sizes so a corrupted or truncated
        // stored value cannot downgrade the comparison to a single byte.
        if (!parts[2].matches("[0-9A-Fa-f]{" + (SALT_LENGTH_BYTES * 2) + "}")
                || !parts[3].matches("[0-9A-Fa-f]{" + (KEY_LENGTH_BYTES * 2) + "}")) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            if (iterations < 1000 || iterations > MAX_STORED_ITERATIONS) {
                return false;
            }
            byte[] salt = StringUtils.hex2byte(parts[2]);
            byte[] expected = StringUtils.hex2byte(parts[3]);
            if (expected.length == 0) {
                return false;
            }
            byte[] actual = pbkdf2(sPassword, salt, iterations, expected.length);
            return constantTimeEquals(expected, actual);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * @param sStored stored password value
     * @return true if the value uses the new PBKDF2 format
     */
    public static boolean isPbkdf2(String sStored) {
        return sStored != null && sStored.startsWith(PREFIX + SEPARATOR);
    }

    /**
     * A value needs upgrading when it is a legacy hash ({@code sha1:}, {@code plain:}
     * or clear text). Empty passwords cannot be upgraded because the clear text is unknown.
     *
     * @param sStored stored password value
     * @return true when it should be rehashed after a successful login
     */
    public static boolean needsUpgrade(String sStored) {
        return sStored != null && sStored.length() > 0
                && !isPbkdf2(sStored) && !sStored.startsWith("empty:");
    }

    /**
     * Derives a symmetric key from a passphrase using PBKDF2-HMAC-SHA256.
     * Used by the configuration encrypter so that no fixed/keyboard-derived
     * cipher key is ever used directly.
     *
     * @param sPassword  passphrase
     * @param salt       application-specific salt
     * @param iterations iteration count
     * @param dkLen      derived key length in bytes
     * @return the derived key
     */
    public static byte[] deriveKey(String sPassword, byte[] salt, int iterations, int dkLen) {
        try {
            return pbkdf2(sPassword, salt, iterations, dkLen);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 not available in this JRE", e);
        }
    }

    /**
     * PBKDF2 (RFC 2898) using HmacSHA256 as the pseudo-random function.
     */
    private static byte[] pbkdf2(String sPassword, byte[] salt, int iterations, int dkLen)
            throws GeneralSecurityException {
        byte[] passwordBytes;
        try {
            passwordBytes = sPassword.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            passwordBytes = sPassword.getBytes();
        }

        Mac mac = Mac.getInstance(ALGORITHM);
        SecretKeySpec key = new SecretKeySpec(passwordBytes, ALGORITHM);
        mac.init(key);

        int hLen = mac.getMacLength();
        int blocks = (dkLen + hLen - 1) / hLen;
        byte[] output = new byte[blocks * hLen];
        byte[] blockIndex = new byte[4];
        int offset = 0;

        for (int block = 1; block <= blocks; block++) {
            blockIndex[0] = (byte) (block >>> 24);
            blockIndex[1] = (byte) (block >>> 16);
            blockIndex[2] = (byte) (block >>> 8);
            blockIndex[3] = (byte) block;

            mac.update(salt);
            byte[] u = mac.doFinal(blockIndex);
            byte[] t = new byte[u.length];
            System.arraycopy(u, 0, t, 0, u.length);

            for (int i = 1; i < iterations; i++) {
                u = mac.doFinal(u);
                for (int j = 0; j < t.length; j++) {
                    t[j] ^= u[j];
                }
            }
            System.arraycopy(t, 0, output, offset, hLen);
            offset += hLen;
        }

        if (dkLen == output.length) {
            return output;
        }
        byte[] trimmed = new byte[dkLen];
        System.arraycopy(output, 0, trimmed, 0, dkLen);
        return trimmed;
    }

    /** Comparison that does not short-circuit on the first differing byte. */
    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length; i++) {
            diff |= a[i] ^ b[i];
        }
        return diff == 0;
    }
}
