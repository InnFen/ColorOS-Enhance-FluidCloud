package dev.howard.colorosenhance.settings;
import static org.junit.Assert.*;
import org.junit.Test;
public class NativeNumberInputTest {
    @Test public void acceptsExactTenthsAndSignedOffsets() {
        assertEquals(Integer.valueOf(-15),NativeNumberInput.parse("-1.5",-30,30,1,10));
        assertEquals(Integer.valueOf(100),NativeNumberInput.parse("10,0",60,140,1,10));
        assertEquals(Integer.valueOf(650),NativeNumberInput.parse("650",100,1000,10,1));
    }
    @Test public void rejectsOutOfRangeOffStepAndFractionalUnits() {
        for (String raw : new String[]{"NaN","Infinity","651","99","1001","6e2",""})
            assertNull(NativeNumberInput.parse(raw,100,1000,10,1));
        assertNull(NativeNumberInput.parse("1.25",-30,30,1,10));
    }
}
