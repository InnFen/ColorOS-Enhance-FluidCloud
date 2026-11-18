package com.howard.simpinautounlock.storage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public final class PendingPinCodec {
    private static final int FORMAT_VERSION = 1;

    public record Payload(int slotId, String pin, String claimToken) {}

    public byte[] encode(Payload payload) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(FORMAT_VERSION);
            out.writeInt(payload.slotId());
            out.writeUTF(payload.pin());
            out.writeUTF(payload.claimToken());
        }
        return bytes.toByteArray();
    }

    public Payload decode(byte[] encoded) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(encoded))) {
            int version = in.readInt();
            if (version != FORMAT_VERSION) {
                throw new IOException("unsupported pending PIN format");
            }
            Payload payload = new Payload(in.readInt(), in.readUTF(), in.readUTF());
            if (in.available() != 0) {
                throw new IOException("trailing pending PIN data");
            }
            return payload;
        }
    }
}
