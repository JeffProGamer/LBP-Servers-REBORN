import cwlib.io.*;
import cwlib.util.*;
import cwlib.types.*;
import cwlib.types.data.*;
import cwlib.resources.*;
import cwlib.structs.script.*;
import cwlib.io.streams.*;
import cwlib.io.serializer.*;

public class ParseLBPOnline {
    public static void main(String[] args) throws Exception {
        // Read the raw byte array from .java file
        byte[] raw = FileIO.read("a15f684e801516532279fb9148d62e16fc0710de.java");
        
        // Extract the byte array content (between { and })
        String content = new String(raw, "US-ASCII");
        int start = content.indexOf("{");
        int end = content.lastIndexOf("}");
        if (start >= 0 && end > start) {
            String arrayContent = content.substring(start + 1, end);
            String[] bytes = arrayContent.split(",");
            byte[] bytecode = new byte[bytes.length];
            for (int i = 0; i < bytes.length; i++) {
                String b = bytes[i].trim();
                b = b.replace("(byte)", "").replace("0x", "").trim();
                if (b.endsWith(",")) b = b.substring(0, b.length() - 1);
                if (b.isEmpty()) continue;
                try {
                    bytecode[i] = (byte) Integer.parseInt(b, 16);
                } catch (NumberFormatException e) {
                    System.err.println("Failed to parse: '" + bytes[i].trim() + "' at index " + i);
                    throw e;
                }
            }
            
            System.out.println("Extracted bytecode length: " + bytecode.length);
            
            // Find className (null-terminated string at start)
            int nameEnd = 0;
            while (nameEnd < bytecode.length && bytecode[nameEnd] != 0) nameEnd++;
            String className = new String(bytecode, 0, nameEnd, "US-ASCII");
            System.out.println("ClassName: '" + className + "' (ends at offset " + nameEnd + ")");
            
            // The rest is the serialized RScript data
            byte[] scriptData = new byte[bytecode.length - nameEnd - 1];
            System.arraycopy(bytecode, nameEnd + 1, scriptData, 0, scriptData.length);
            System.out.println("Script data length: " + scriptData.length);
            
            // Analyze as raw bytecode words (8 bytes each)
            if (scriptData.length % 8 == 0) {
                int wordCount = scriptData.length / 8;
                System.out.println("Bytecode words (8-byte aligned): " + wordCount);
                for (int i = 0; i < Math.min(50, wordCount); i++) {
                    long word = 0;
                    for (int j = 0; j < 8; j++) {
                        word |= ((long) (scriptData[i * 8 + j] & 0xFF)) << (j * 8);
                    }
                    int opcode = (int) (word & 0xFF);
                    long payload = word >>> 8;
                    System.out.printf("  Word %d: opcode=%d (0x%X), payload=0x%X\n", i, opcode, opcode, payload);
                }
            } else {
                System.out.println("Not 8-byte aligned, remainder: " + (scriptData.length % 8));
            }
            
        }
    }
}