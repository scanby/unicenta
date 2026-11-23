package com.openbravo.pos.util;

import static org.junit.Assert.*;
import org.junit.Test;

/**
 * Tests for the PBKDF2 password hashes and for backwards compatibility with
 * the legacy formats stored by older uniCenta/Openbravo POS versions.
 */
public class PasswordHasherTest {

    @Test
    public void hashUsesPbkdf2Format() {
        String hash = PasswordHasher.hash("secret");
        assertTrue(hash, hash.startsWith("pbkdf2$"));
        assertEquals(4, hash.split("\\$").length);
    }

    @Test
    public void verifiesCorrectPassword() {
        String hash = PasswordHasher.hash("secret");
        assertTrue(PasswordHasher.verifyPbkdf2("secret", hash));
        assertTrue(Hashcypher.authenticate("secret", hash));
    }

    @Test
    public void rejectsWrongPassword() {
        String hash = PasswordHasher.hash("secret");
        assertFalse(PasswordHasher.verifyPbkdf2("wrong", hash));
        assertFalse(Hashcypher.authenticate("wrong", hash));
        assertFalse(PasswordHasher.verifyPbkdf2(null, hash));
    }

    @Test
    public void samePasswordProducesDifferentHashesBecauseOfTheSalt() {
        assertFalse("two hashes of the same password must differ",
                PasswordHasher.hash("secret").equals(PasswordHasher.hash("secret")));
    }

    @Test
    public void emptyPasswordKeepsLegacyMarker() {
        assertEquals("empty:", PasswordHasher.hash(null));
        assertEquals("empty:", PasswordHasher.hash(""));
        assertTrue(Hashcypher.authenticate(null, "empty:"));
        assertTrue(Hashcypher.authenticate("", "empty:"));
        assertFalse(Hashcypher.authenticate("secret", "empty:"));
    }

    @Test
    public void legacySha1PasswordStillAuthenticates() {
        // SHA-1("password") in upper case, as produced by StringUtils.byte2hex
        String legacy = "sha1:5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8";
        assertTrue(Hashcypher.authenticate("password", legacy));
        assertFalse(Hashcypher.authenticate("wrong", legacy));
        assertTrue(PasswordHasher.needsUpgrade(legacy));
    }

    @Test
    public void legacyPlainPasswordStillAuthenticates() {
        assertTrue(Hashcypher.authenticate("password", "plain:password"));
        assertFalse(Hashcypher.authenticate("wrong", "plain:password"));
        assertTrue(PasswordHasher.needsUpgrade("plain:password"));
    }

    @Test
    public void detectsValuesThatNeedUpgrading() {
        assertTrue(PasswordHasher.needsUpgrade("sha1:ABCD"));
        assertTrue(PasswordHasher.needsUpgrade("clearText"));
        assertFalse(PasswordHasher.needsUpgrade("empty:"));
        assertFalse(PasswordHasher.needsUpgrade(null));
        assertFalse(PasswordHasher.needsUpgrade(PasswordHasher.hash("secret")));
        assertTrue(PasswordHasher.isPbkdf2(PasswordHasher.hash("secret")));
    }

    @Test
    public void corruptStoredValueIsRejectedInsteadOfThrowing() {
        assertFalse(PasswordHasher.verifyPbkdf2("secret", "pbkdf2$notANumber$00$FF"));
        assertFalse(PasswordHasher.verifyPbkdf2("secret", "pbkdf2$120000$zz$FF"));
        // A truncated 1-byte digest must be rejected (no downgrade to 1/256).
        assertFalse(PasswordHasher.verifyPbkdf2("secret", "pbkdf2$120000$000102030405060708090A0B0C0D0E0F$FF"));
        // Too few iterations are rejected as well.
        assertFalse(PasswordHasher.verifyPbkdf2("secret",
                "pbkdf2$1$000102030405060708090A0B0C0D0E0F$"
                + "000102030405060708090A0B0C0D0E0F000102030405060708090A0B0C0D0E0F"));
        assertFalse(PasswordHasher.verifyPbkdf2("secret", null));
        assertFalse(PasswordHasher.verifyPbkdf2("secret", ""));
    }
}
