package dev.lsposed.fluidcloud;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.zip.ZipFile;

/** Reads field references in a selected plugin method. Discovery runs once, never during animation. */
final class DexFieldUse {
    private record Member(String owner, String name, String type) { }
    private final ByteBuffer data;
    private final int strings, types, fields, methods, protos;

    private DexFieldUse(byte[] bytes) throws IOException {
        data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (bytes.length < 112 || bytes[0] != 'd' || bytes[1] != 'e' || bytes[2] != 'x'
                || data.getInt(32) != bytes.length || data.getInt(40) != 0x12345678)
            throw new IOException("Unsupported plugin DEX");
        strings = data.getInt(60);
        types = data.getInt(68);
        protos = data.getInt(76);
        fields = data.getInt(84);
        methods = data.getInt(92);
    }

    static Set<Field> fields(Method method) throws ReflectiveOperationException {
        var result = new LinkedHashSet<Field>();
        try {
            for (String file : PluginClasses.of(method.getDeclaringClass()).paths) {
                Path path = Path.of(file);
                if (!Files.isRegularFile(path)) continue;
                try (ZipFile apk = new ZipFile(file)) {
                    var entries = apk.entries();
                    while (entries.hasMoreElements()) {
                        var entry = entries.nextElement();
                        if (!entry.getName().matches("classes[0-9]*\\.dex")) continue;
                        try (var input = apk.getInputStream(entry)) {
                            for (Member member : new DexFieldUse(input.readAllBytes()).references(method)) {
                                Class<?> owner = Reflect.type(method.getDeclaringClass().getClassLoader(), name(member.owner()));
                                Field field = owner.getDeclaredField(member.name());
                                if (!descriptor(field.getType()).equals(member.type()))
                                    throw new NoSuchFieldException("DEX field type changed");
                                field.setAccessible(true);
                                result.add(field);
                            }
                        }
                    }
                }
            }
        } catch (IOException | IndexOutOfBoundsException | IllegalArgumentException failure) {
            throw new ReflectiveOperationException("Plugin method references unavailable", failure);
        }
        return Set.copyOf(result);
    }

    private Set<Member> references(Method target) {
        String owner = descriptor(target.getDeclaringClass());
        int count = data.getInt(96), offset = data.getInt(100);
        for (int i = 0; i < count; i++) {
            int definition = offset + i * 32;
            if (!type(data.getInt(definition)).equals(owner)) continue;
            int classData = data.getInt(definition + 24);
            if (classData == 0) return Set.of();
            int[] cursor = {classData};
            int staticFields = uleb(cursor), instanceFields = uleb(cursor);
            int directMethods = uleb(cursor), virtualMethods = uleb(cursor);
            for (int n = 0; n < staticFields + instanceFields; n++) { uleb(cursor); uleb(cursor); }
            var result = new LinkedHashSet<Member>();
            for (int group : new int[]{directMethods, virtualMethods}) {
                int index = 0;
                for (int n = 0; n < group; n++) {
                    index += uleb(cursor);
                    uleb(cursor);
                    int code = uleb(cursor);
                    if (code != 0 && matches(index, target)) result.addAll(codeFields(code));
                }
            }
            return result;
        }
        return Set.of();
    }

    private boolean matches(int index, Method method) {
        int record = methods + index * 8;
        if (!string(data.getInt(record + 4)).equals(method.getName())) return false;
        int proto = protos + u16(record + 2) * 12;
        if (!type(data.getInt(proto + 4)).equals(descriptor(method.getReturnType()))) return false;
        int arguments = data.getInt(proto + 8);
        Class<?>[] expected = method.getParameterTypes();
        if (arguments == 0) return expected.length == 0;
        if (data.getInt(arguments) != expected.length) return false;
        for (int i = 0; i < expected.length; i++)
            if (!type(u16(arguments + 4 + i * 2)).equals(descriptor(expected[i]))) return false;
        return true;
    }

    private Set<Member> codeFields(int code) {
        int length = data.getInt(code + 12), start = code + 16;
        var result = new LinkedHashSet<Member>();
        for (int unit = 0; unit < length;) {
            int instruction = u16(start + unit * 2), opcode = instruction & 255;
            if (opcode >= 0x52 && opcode <= 0x6d) {
                int index = u16(start + (unit + 1) * 2), field = fields + index * 8;
                result.add(new Member(type(u16(field)), string(data.getInt(field + 4)), type(u16(field + 2))));
            }
            int width = width(data, start, unit);
            if (width <= 0 || width > length - unit) throw new IllegalArgumentException("Invalid DEX instruction");
            unit += width;
        }
        return result;
    }

    static int width(ByteBuffer data, int start, int unit) {
        int word = Short.toUnsignedInt(data.getShort(start + unit * 2)), opcode = word & 255;
        if (opcode == 0 && word != 0) {
            int size = Short.toUnsignedInt(data.getShort(start + (unit + 1) * 2));
            return switch (word) {
                case 0x0100 -> 4 + size * 2;
                case 0x0200 -> 2 + size * 4;
                case 0x0300 -> 4 + Math.toIntExact(((long) size * Integer.toUnsignedLong(data.getInt(start + (unit + 2) * 2)) + 1) / 2);
                default -> throw new IllegalArgumentException("Unknown DEX payload");
            };
        }
        if (opcode == 0x18) return 5;
        if (opcode == 0x03 || opcode == 0x06 || opcode == 0x09 || opcode == 0x14 || opcode == 0x17
                || opcode == 0x1b || opcode == 0x24 || opcode == 0x25 || opcode == 0x26
                || opcode == 0x2a || opcode == 0x2b || opcode == 0x2c
                || opcode >= 0x6e && opcode <= 0x72 || opcode >= 0x74 && opcode <= 0x78
                || opcode == 0xfc || opcode == 0xfd) return 3;
        if (opcode == 0xfa || opcode == 0xfb) return 4;
        if (opcode == 0x02 || opcode == 0x05 || opcode == 0x08 || opcode == 0x13 || opcode == 0x15
                || opcode == 0x16 || opcode == 0x19 || opcode == 0x1a || opcode == 0x1c
                || opcode == 0x1f || opcode == 0x20 || opcode == 0x22 || opcode == 0x23 || opcode == 0x29
                || opcode >= 0x2d && opcode <= 0x3d || opcode >= 0x44 && opcode <= 0x6d
                || opcode >= 0x90 && opcode <= 0xaf || opcode >= 0xd0 && opcode <= 0xe2
                || opcode == 0xfe || opcode == 0xff) return 2;
        return 1;
    }

    private int u16(int offset) { return Short.toUnsignedInt(data.getShort(offset)); }
    private int uleb(int[] cursor) {
        int result = 0;
        for (int shift = 0; shift <= 28; shift += 7) {
            int value = Byte.toUnsignedInt(data.get(cursor[0]++));
            result |= (value & 127) << shift;
            if ((value & 128) == 0) return result;
        }
        throw new IllegalArgumentException("Invalid DEX integer");
    }
    private String type(int index) { return string(data.getInt(types + index * 4)); }
    private String string(int index) {
        int[] cursor = {data.getInt(strings + index * 4)};
        uleb(cursor);
        int begin = cursor[0], end = begin;
        while (data.get(end) != 0) end++;
        byte[] bytes = new byte[end - begin];
        data.get(begin, bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
    private static String name(String descriptor) { return descriptor.substring(1, descriptor.length() - 1).replace('/', '.'); }
    private static String descriptor(Class<?> type) {
        if (type.isArray()) return type.getName().replace('.', '/');
        if (!type.isPrimitive()) return "L" + type.getName().replace('.', '/') + ";";
        if (type == void.class) return "V";
        if (type == boolean.class) return "Z";
        if (type == byte.class) return "B";
        if (type == char.class) return "C";
        if (type == short.class) return "S";
        if (type == int.class) return "I";
        if (type == long.class) return "J";
        if (type == float.class) return "F";
        return "D";
    }
}
