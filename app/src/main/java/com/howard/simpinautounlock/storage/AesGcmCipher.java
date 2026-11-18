package com.howard.simpinautounlock.storage;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class AesGcmCipher {
    private static final byte ENVELOPE_VERSION = 1;
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    public byte[] encrypt(SecretKey key, byte[] plaintext) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] iv = cipher.getIV();
        byte[] ciphertext = cipher.doFinal(plaintext);
        ByteBuffer result = ByteBuffer.allocate(2 + iv.length + ciphertext.length);
        result.put(ENVELOPE_VERSION);
        result.put((byte) iv.length);
        result.put(iv);
        result.put(ciphertext);
        return result.array();
    }

    public byte[] decrypt(SecretKey key, byte[] sealed) throws GeneralSecurityException {
        ByteBuffer input = ByteBuffer.wrap(sealed);
        if (input.remaining() < 2 || input.get() != ENVELOPE_VERSION) {
            throw new GeneralSecurityException("invalid envelope");
        }
        int ivLength = Byte.toUnsignedInt(input.get());
        if (ivLength != IV_LENGTH || input.remaining() <= ivLength) {
            throw new GeneralSecurityException("invalid envelope");
        }
        byte[] iv = new byte[ivLength];
        input.get(iv);
        byte[] ciphertext = new byte[input.remaining()];
        input.get(ciphertext);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
        return cipher.doFinal(ciphertext);
    }
}
