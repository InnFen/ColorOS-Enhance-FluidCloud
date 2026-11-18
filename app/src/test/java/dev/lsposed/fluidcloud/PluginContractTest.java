package dev.lsposed.fluidcloud;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.Test;
import static org.junit.Assert.*;

public class PluginContractTest {
    @Test public void resolvesRenamedMembersByShapeAndRejectsAmbiguousMatches() throws Exception {
        assertEquals("renamed", Reflect.signature(Renamed.class, void.class, String.class, String.class).getName());
        assertEquals("moved", Reflect.uniqueField(Renamed.class,
                field -> Reflect.instance(field) && field.getType() == Number.class).getName());
        assertThrows(NoSuchMethodException.class,
                () -> Reflect.signature(Ambiguous.class, void.class, String.class, String.class));
        assertThrows(NoSuchFieldException.class,
                () -> Reflect.uniqueField(Ambiguous.class, field -> Reflect.instance(field) && field.getType() == Number.class));
    }

    @Test public void dexWalkerSkipsOperandsThatResembleFieldInstructions() {
        ByteBuffer code = ByteBuffer.allocate(18).order(ByteOrder.LITTLE_ENDIAN);
        // const-wide has four operand words. A field opcode in those words is data.
        code.putShort((short) 0x0018).putShort((short) 0x0052).putShort((short) 0x0060)
                .putShort((short) 0x1111).putShort((short) 0x2222);
        code.putShort((short) 0x0062).putShort((short) 0x0007).putShort((short) 0x000e);
        assertEquals(5, DexFieldUse.width(code, 0, 0));
        assertEquals(2, DexFieldUse.width(code, 0, 5));
        assertEquals(1, DexFieldUse.width(code, 0, 7));
    }

    @Test public void dexWalkerSkipsPayloadsAndWideMethodReferences() {
        ByteBuffer code = ByteBuffer.allocate(40).order(ByteOrder.LITTLE_ENDIAN);
        code.putShort((short) 0x0100).putShort((short) 2);
        assertEquals(8, DexFieldUse.width(code, 0, 0));
        code.putShort(0, (short) 0x0200);
        assertEquals(10, DexFieldUse.width(code, 0, 0));
        code.putShort(0, (short) 0x0300).putShort(2, (short) 1).putInt(4, 7);
        assertEquals(8, DexFieldUse.width(code, 0, 0));
        code.putShort(0, (short) 0x00fa);
        assertEquals(4, DexFieldUse.width(code, 0, 0));
    }

    static final class Renamed {
        Number moved;
        static Number unrelated;
        void renamed(String first, String second) { }
    }
    static final class Ambiguous {
        Number first, second;
        void first(String a, String b) { }
        void second(String a, String b) { }
    }
}
