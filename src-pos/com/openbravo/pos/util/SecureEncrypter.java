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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Authenticated encryption (AES-128-GCM) for locally stored configuration
 * secrets such as database or payment-gateway passwords.
 *
 * <p>Stored format: {@code v2$<nonce hex>$<ciphertext+tag hex>}. The cipher key
 * is derived with PBKDF2 from a random local secret (see {@link SecureKeyStore})
 * combined with the pass-phrase supplied by the caller, so knowing the database
 * user alone is no longer enough to decrypt a password.</p>
 */
public final class SecureEncrypter {

    private static final Logger logger = Logger.getLogger("com.openbravo.pos.util.SecureEncrypter");

    public static final String PREFIX = "v2";
    private static final String SEPARATOR = "\\$";

    private static final String KEY_FILE = ".unicentaopos.key";
    private static final byte[] KEY_SALT = "unicentaopos.crypt.v2".getBytes();
    private static final int KEY_ITERATIONS = 60000;
    private static final int KEY_LENGTH_BYTES = 16;   // AES-128 works without JCE unlimited policy

    private static final int NONCE_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private static final SecureRandom RANDOM = new SecureRandom();

    private SecureEncrypter() {
    }

    /**
     * Encrypts a value with AES-GCM.
     *
     * @param sPassPhrase caller supplied pass-phrase (typically "cypherkey" + user)
     * @param sPlain      clear text
     * @return versioned, hex encoded ciphertext
     */
    public static String encrypt(String sPassPhrase, String sPlain) {
        try {
            byte[] key = deriveKey(sPassPhrase);
            byte[] nonce = new byte[NONCE_LENGTH_BYTES];
            RANDOM.nextBytes(nonce);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(sPlain.getBytes("UTF-8"));

            return PREFIX + "$" + StringUtils.byte2hex(nonce) + "$" + StringUtils.byte2hex(ciphertext);
        } catch (GeneralSecurityException | IOException e) {
            logger.log(Level.SEVERE, "Cannot encrypt configuration secret", e);
            return null;
        }
    }

    /**
     * Decrypts a value produced by {@link #encrypt(String, String)}.
     *
     * @param sPassPhrase caller supplied pass-phrase
     * @param sStored     stored value (with or without the outer {@code crypt:} prefix)
     * @return clear text, or null when the value is invalid or the key is missing
     */
    public static String decrypt(String sPassPhrase, String sStored) {
        if (sStored == null) {
            return null;
        }
        String value = sStored.startsWith("crypt:") ? sStored.substring(6) : sStored;
        if (!value.startsWith(PREFIX + "$")) {
            return null;
        }
        try {
            String[] parts = value.split(SEPARATOR);
            if (parts.length != 3
                    || !parts[1].matches("[0-9A-Fa-f]{" + (NONCE_LENGTH_BYTES * 2) + "}")
                    || !parts[2].matches("[0-9A-Fa-f]{32,}")) {
                return null;
            }
            byte[] key = deriveKey(sPassPhrase);
            byte[] nonce = StringUtils.hex2byte(parts[1]);
            byte[] ciphertext = StringUtils.hex2byte(parts[2]);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            return new String(cipher.doFinal(ciphertext), "UTF-8");
        } catch (GeneralSecurityException | IOException | IllegalArgumentException e) {
            logger.log(Level.WARNING, "Cannot decrypt configuration secret (wrong key or tampered value)", e);
            return null;
        }
    }

    private static byte[] deriveKey(String sPassPhrase) throws IOException {
        byte[] secret = SecureKeyStore.get(KEY_FILE, 32);
        String material = StringUtils.byte2hex(secret) + ":" + (sPassPhrase == null ? "" : sPassPhrase);
        return PasswordHasher.deriveKey(material, KEY_SALT, KEY_ITERATIONS, KEY_LENGTH_BYTES);
    }
}
