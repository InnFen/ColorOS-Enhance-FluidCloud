package dev.howard.colorosenhance.settings;
import java.math.BigDecimal;
public final class NativeNumberInput {
    public static Integer parse(String raw, int min, int max, int step, int divisor) {
        if (raw == null || step <= 0 || divisor <= 0) return null;
        String text = raw.trim().replace(',', '.');
        if (!text.matches("[+-]?[0-9]+(?:\\.[0-9]+)?")) return null;
        try {
            int value = new BigDecimal(text).multiply(BigDecimal.valueOf(divisor)).intValueExact();
            return value >= min && value <= max && (value - min) % step == 0 ? value : null;
        } catch (ArithmeticException | NumberFormatException ignored) { return null; }
    }
}
