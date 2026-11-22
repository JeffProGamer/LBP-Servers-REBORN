import java.io.*;
import java.nio.*;
import java.util.*;

public class RawFfParser {
    // Opcodes from BuildSticker.java
    static final int OP_LCb = 1, OP_LCi = 3, OP_LCf = 4;
    static final int OP_ADDi = 42, OP_SUBi = 43;
    static final int OP_GET_SP_MEMBER = 149, OP_GET_RP_MEMBER = 150;
    static final int OP_SET_SP_MEMBER = 151, OP_SET_RP_MEMBER = 152;
    static final int OP_GET_SP_NATIVE_MEMBER = 187;
    static final int OP_ARG = 163, OP_CALL = 164, OP_RETURN = 165;

    static String opcodeName(int op) {
        switch(op) {
            case OP_LCb: return "LCb";
            case OP_LCi: return "LCi";
            case OP_LCf: return "LCf";
            case OP_ADDi: return "ADDi";
            case OP_SUBi: return "SUBi";
            case OP_GET_SP_MEMBER: return "GET_SP_MEMBER";
            case OP_GET_RP_MEMBER: return "GET_RP_MEMBER";
            case OP_SET_SP_MEMBER: return "SET_SP_MEMBER";
            case OP_SET_RP_MEMBER: return "SET_RP_MEMBER";
            case OP_GET_SP_NATIVE_MEMBER: return "GET_SP_NATIVE_MEMBER";
            case OP_ARG: return "ARG";
            case OP_CALL: return "CALL";
            case OP_RETURN: return "RETURN";
            default: return "OP_" + op;
        }
    }

    public static void main(String[] args) throws Exception {
        String path = args.length > 0 ? args[0] : "a15f684e801516532279fb9148d62e16fc0710de.ff";
        byte[] data = readFile(path);
        System.out.println("File size: " + data.length + " bytes");

        // Find the end of the text header (null-terminated class name)
        int headerEnd = 0;
        while (headerEnd < data.length && data[headerEnd] != 0) headerEnd++;
        headerEnd++; // skip null
        String className = new String(data, 0, headerEnd - 1, "US-ASCII");
        System.out.println("Class name: " + className);
        System.out.println("Header ends at offset: " + headerEnd);

        // Parse remaining as 8-byte little-endian words
        int remaining = data.length - headerEnd;
        int wordCount = remaining / 8;
        System.out.println("Remaining bytes: " + remaining + ", word count: " + wordCount);

        // Look for LCi instructions loading ASCII printable chars (potential URL construction)
        List<String> urlChars = new ArrayList<>();
        int lciCount = 0;
        int asciiLciCount = 0;

        for (int i = 0; i < wordCount; i++) {
            int offset = headerEnd + i * 8;
            long word = 0;
            for (int j = 0; j < 8; j++) {
                word |= ((long) (data[offset + j] & 0xFF)) << (8 * j);
            }
            int opcode = (int) (word & 0xFF);
            long payload = word >>> 8;

            if (opcode == OP_LCi) {
                lciCount++;
                int dest = (int) (payload & 0xFFFF);
                int value = (int) ((payload >>> 16) & 0xFFFFFFFFL);
                if (value >= 32 && value <= 126) {
                    asciiLciCount++;
                    char c = (char) value;
                    urlChars.add(String.format("[%d] LCi r%d, %d ('%c')", i, dest, value, c));
                }
            }
        }

        System.out.println("LCi count: " + lciCount + ", ASCII LCi count: " + asciiLciCount);

        // Print ASCII LCi sequences that might form URLs (look for 'h', 't', 't', 'p' or '.', '/')
        System.out.println("\n=== ASCII LCi constants (first 100) ===");
        int printed = 0;
        for (int i = 0; i < wordCount && printed < 100; i++) {
            int offset = headerEnd + i * 8;
            long word = 0;
            for (int j = 0; j < 8; j++) {
                word |= ((long) (data[offset + j] & 0xFF)) << (8 * j);
            }
            int opcode = (int) (word & 0xFF);
            if (opcode != OP_LCi) continue;
            long payload = word >>> 8;
            int dest = (int) (payload & 0xFFFF);
            int value = (int) ((payload >>> 16) & 0xFFFFFFFFL);
            if (value >= 32 && value <= 126) {
                System.out.println(String.format("  [%d] LCi r%d, %d ('%c')", i, dest, value, (char) value));
                printed++;
            }
        }

        // Search for common URL characters in sequence: 'h','t','t','p'
        System.out.println("\n=== Searching for URL-like sequences ===");
        for (int i = 0; i < wordCount - 3; i++) {
            int offset = headerEnd + i * 8;
            long word = 0;
            for (int j = 0; j < 8; j++) {
                word |= ((long) (data[offset + j] & 0xFF)) << (8 * j);
            }
            if ((int) (word & 0xFF) != OP_LCi) continue;
            long payload = word >>> 8;
            int v1 = (int) ((payload >>> 16) & 0xFFFFFFFFL);

            // Check next word
            offset += 8;
            long word2 = 0;
            for (int j = 0; j < 8; j++) {
                word2 |= ((long) (data[offset + j] & 0xFF)) << (8 * j);
            }
            if ((int) (word2 & 0xFF) != OP_LCi) continue;
            long payload2 = word2 >>> 8;
            int v2 = (int) ((payload2 >>> 16) & 0xFFFFFFFFL);

            // Check 'h' then 't'
            if (v1 == 'h' && v2 == 't') {
                // Check third word for 't'
                offset += 8;
                long word3 = 0;
                for (int j = 0; j < 8; j++) {
                    word3 |= ((long) (data[offset + j] & 0xFF)) << (8 * j);
                }
                if ((int) (word3 & 0xFF) == OP_LCi) {
                    long payload3 = word3 >>> 8;
                    int v3 = (int) ((payload3 >>> 16) & 0xFFFFFFFFL);
                    if (v3 == 't') {
                        System.out.println("Found 'htt' at word index " + i);
                    }
                }
            }
        }
    }

    static byte[] readFile(String path) throws Exception {
        File f = new File(path);
        byte[] data = new byte[(int) f.length()];
        FileInputStream fis = new FileInputStream(f);
        fis.read(data);
        fis.close();
        return data;
    }
}
