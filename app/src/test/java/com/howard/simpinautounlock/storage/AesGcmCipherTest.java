package com.howard.simpinautounlock.storage;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import org.junit.Test;

public final class AesGcmCipherTest {
    @Test
    public void roundTripsPlaintext() throws Exception {
        SecretKey key = newKey();
        AesGcmCipher cipher = new AesGcmCipher();
        byte[] plaintext = "1234".getBytes(StandardCharsets.UTF_8);

        assertArrayEquals(plaintext, cipher.decrypt(key, cipher.encrypt(key, plaintext)));
    }

    @Test
    public void rejectsTamperedCiphertext() throws Exception {
        SecretKey key = newKey();
        AesGcmCipher cipher = new AesGcmCipher();
        byte[] sealed = cipher.encrypt(key, "1234".getBytes(StandardCharsets.UTF_8));
        sealed[sealed.length - 1] ^= 0x01;

        assertThrows(GeneralSecurityException.class, () -> cipher.decrypt(key, sealed));
    }

    @Test
    public void rejectsMalformedEnvelope() throws Exception {
        SecretKey key = newKey();

        assertThrows(
                GeneralSecurityException.class,
                () -> new AesGcmCipher().decrypt(key, new byte[] {1, 12, 0}));
    }

    @Test
    public void usesFreshIvForRepeatedEncryption() throws Exception {
        SecretKey key = newKey();
        AesGcmCipher cipher = new AesGcmCipher();
        byte[] plaintext = "1234".getBytes(StandardCharsets.UTF_8);

        byte[] first = cipher.encrypt(key, plaintext);
        byte[] second = cipher.encrypt(key, plaintext);

        assertFalse(Arrays.equals(
                Arrays.copyOfRange(first, 2, 14),
                Arrays.copyOfRange(second, 2, 14)));
    }

    private static SecretKey newKey() throws GeneralSecurityException {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256);
        return generator.generateKey();
    }
}
