package com.openbravo.pos.util;

import static org.junit.Assert.*;
import java.io.File;
import java.security.Key;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests for the AES-GCM configuration encrypter and for reading values written
 * by the legacy 3DES/ECB implementation.
 */
public class SecureEncrypterTest {

    @BeforeClass
    public static void useIsolatedKeyStore() {
        File dir = new File(System.getProperty("java.io.tmpdir"), "unicenta-test-keystore");
        dir.mkdirs();
        System.setProperty("unicenta.keystore.dir", dir.getAbsolutePath());
    }

    @Test
    public void roundTripWithNewFormat() {
        String encrypted = SecureEncrypter.encrypt("cypherkeyadmin", "s3cretpass");
        assertTrue(encrypted, encrypted.startsWith("v2$"));
        assertEquals("s3cretpass", SecureEncrypter.decrypt("cypherkeyadmin", encrypted));
    }

    @Test
    public void wrongPassphraseCannotDecrypt() {
        String encrypted = SecureEncrypter.encrypt("cypherkeyadmin", "s3cretpass");
        assertNull(SecureEncrypter.decrypt("cypherkeyother", encrypted));
    }

    @Test
    public void tamperedCiphertextIsRejected() {
        String encrypted = SecureEncrypter.encrypt("cypherkeyadmin", "s3cretpass");
        String[] parts = encrypted.split("\\$");
        String last = parts[2];
        String tamperedLast = last.substring(0, last.length() - 1)
                + (last.endsWith("A") ? "B" : "A");
        assertNull(SecureEncrypter.decrypt("cypherkeyadmin",
                parts[0] + "$" + parts[1] + "$" + tamperedLast));
    }

    @Test
    public void randomNonceMakesTwoEncryptionsDifferent() {
        String a = SecureEncrypter.encrypt("cypherkeyadmin", "same");
        String b = SecureEncrypter.encrypt("cypherkeyadmin", "same");
        assertFalse("nonces must be random", a.equals(b));
    }

    @Test
    public void altEncrypterWritesV2AndUnderstandsLegacyValues() throws Exception {
        String passPhrase = "cypherkeyadmin";
        AltEncrypter encrypter = new AltEncrypter(passPhrase);

        String fresh = encrypter.encrypt("newpassword");
        assertTrue(fresh, fresh.startsWith("v2$"));
        assertEquals("newpassword", new AltEncrypter(passPhrase).decrypt("crypt:" + fresh));

        // Reproduce the old <=3.81 algorithm: 3DES/ECB with a key generated
        // from a SHA1PRNG seeded with the pass phrase.
        SecureRandom sr = SecureRandom.getInstance("SHA1PRNG");
        sr.setSeed(passPhrase.getBytes("UTF8"));
        KeyGenerator kGen = KeyGenerator.getInstance("DESEDE");
        kGen.init(168, sr);
        Key key = kGen.generateKey();
        Cipher legacy = Cipher.getInstance("DESEDE/ECB/PKCS5Padding");
        legacy.init(Cipher.ENCRYPT_MODE, key);
        String oldValue = StringUtils.byte2hex(legacy.doFinal("oldpassword".getBytes("UTF8")));

        assertEquals("oldpassword", encrypter.decrypt(oldValue));
    }

    @Test
    public void instanceTokenIsStable() {
        String token = com.openbravo.pos.instance.InstanceToken.get();
        assertEquals(32, token.length());
        assertEquals(token, com.openbravo.pos.instance.InstanceToken.get());
    }
}
