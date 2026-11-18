package com.howard.simpinautounlock.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.IOException;
import java.util.Arrays;
import org.junit.Test;

public final class PendingPinCodecTest {
    private final PendingPinCodec codec = new PendingPinCodec();

    @Test
    public void roundTripsStagedPayload() throws Exception {
        PendingPinCodec.Payload value = new PendingPinCodec.Payload(0, "1234", "");
        assertEquals(value, codec.decode(codec.encode(value)));
    }

    @Test
    public void roundTripsClaimedPayload() throws Exception {
        PendingPinCodec.Payload value = new PendingPinCodec.Payload(1, "5678", "token-1");
        assertEquals(value, codec.decode(codec.encode(value)));
    }

    @Test
    public void rejectsWrongVersion() throws Exception {
        byte[] encoded = codec.encode(new PendingPinCodec.Payload(0, "1234", ""));
        encoded[3] = 2;
        assertThrows(IOException.class, () -> codec.decode(encoded));
    }

    @Test
    public void rejectsTrailingBytes() throws Exception {
        byte[] encoded = codec.encode(new PendingPinCodec.Payload(0, "1234", ""));
        byte[] withTrailingByte = Arrays.copyOf(encoded, encoded.length + 1);
        assertThrows(IOException.class, () -> codec.decode(withTrailingByte));
    }
}
