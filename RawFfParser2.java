import java.io.*;

public class RawFfParser2 {
    static final int OP_LCi = 3;

    public static void main(String[] args) throws Exception {
        String path = args.length > 0 ? args[0] : "a15f684e801516532279fb9148d62e16fc0710de.ff";
        byte[] data = readFile(path);
        System.out.println("File size: " + data.length + " bytes");

        // Find header end (null-terminated class name)
        int headerEnd = 0;
        while (headerEnd < data.length && data[headerEnd] != 0) headerEnd++;
        headerEnd++; // skip null
        System.out.println("Header ends at offset: " + headerEnd + " (class name: " + new String(data, 0, headerEnd - 1, "US-ASCII") + ")");

        // Try different alignments for the word data
        for (int align = 0; align < 8; align++) {
            int start = headerEnd + align;
            int remaining = data.length - start;
            if (remaining < 8) continue;
            int wordCount = remaining / 8;
            int lciCount = 0;
            int otherCount = 0;
            int[] opcodeHist = new int[256];

            for (int i = 0; i < Math.min(wordCount, 5000); i++) {
                int offset = start + i * 8;
                long word = 0;
                for (int j = 0; j < 8; j++) {
                    word |= ((long) (data[offset + j] & 0xFF)) << (8 * j);
                }
                int opcode = (int) (word & 0xFF);
                opcodeHist[opcode]++;
                if (opcode == OP_LCi) lciCount++;
                else otherCount++;
            }

            // Count distinct opcodes
            int distinct = 0;
            for (int c : opcodeHist) if (c > 0) distinct++;

            System.out.println("Align " + align + ": distinctOpcodes=" + distinct + " lciCount=" + lciCount + " otherCount=" + otherCount + " (first 5000 words)");
            if (distinct <= 20) {
                System.out.println("  Opcode histogram:");
                for (int op = 0; op < 256; op++) {
                    if (opcodeHist[op] > 0) {
                        System.out.println("    0x" + Integer.toHexString(op) + "=" + opcodeHist[op]);
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
