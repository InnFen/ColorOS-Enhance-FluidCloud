package com.howard.simpinautounlock.storage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public final class CredentialCodec {
    private static final int FORMAT_VERSION = 1;

    public record Payload(int slotId, String iccid, String pin) {}

    public byte[] encode(Payload payload) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(FORMAT_VERSION);
            out.writeInt(payload.slotId());
            out.writeUTF(payload.iccid());
            out.writeUTF(payload.pin());
        }
        return bytes.toByteArray();
    }

    public Payload decode(byte[] encoded) throws IOException {
        try (DataInputStream in =
                new DataInputStream(new ByteArrayInputStream(encoded))) {
            int version = in.readInt();
            if (version != FORMAT_VERSION) {
                throw new IOException("unsupported credential format");
            }
            Payload payload = new Payload(in.readInt(), in.readUTF(), in.readUTF());
            if (in.available() != 0) {
                throw new IOException("trailing credential data");
            }
            return payload;
        }
    }
}
