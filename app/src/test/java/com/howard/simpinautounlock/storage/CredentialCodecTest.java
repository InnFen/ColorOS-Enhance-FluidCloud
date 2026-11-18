package com.howard.simpinautounlock.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.IOException;
import java.nio.ByteBuffer;
import org.junit.Test;

public final class CredentialCodecTest {
    @Test
    public void roundTripsVersionedRecord() throws Exception {
        CredentialCodec.Payload input =
                new CredentialCodec.Payload(1, "8901000000000001234", "5678");
        CredentialCodec.Payload output =
                new CredentialCodec().decode(new CredentialCodec().encode(input));
        assertEquals(input, output);
    }

    @Test
    public void rejectsUnsupportedVersion() {
        byte[] encoded = ByteBuffer.allocate(Integer.BYTES).putInt(2).array();

        assertThrows(IOException.class, () -> new CredentialCodec().decode(encoded));
    }

    @Test
    public void rejectsTrailingData() throws Exception {
        CredentialCodec codec = new CredentialCodec();
        byte[] encoded = codec.encode(new CredentialCodec.Payload(0, "8901", "1234"));
        byte[] withTrailingByte = new byte[encoded.length + 1];
        System.arraycopy(encoded, 0, withTrailingByte, 0, encoded.length);

        assertThrows(IOException.class, () -> codec.decode(withTrailingByte));
    }
}
