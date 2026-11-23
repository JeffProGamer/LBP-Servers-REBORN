import cwlib.util.FileIO;

import java.nio.charset.StandardCharsets;

/**
 * Decodes the string tables + type/function/field reference counts of the
 * LBPOnline patcher script (a15f...ff).
 *
 * Variant used by this file: RScript header = className, then
 *   u32 typeReferences, u32 stringA count, u32 stringW count,
 *   stringA entries, stringW entries, ref rows, bytecode...
 */
public class DecodeA15 {
    static int p;
    static byte[] d;

    public static void main(String[] args) throws Exception {
        String path = args.length > 0 ? args[0] : "a15f684e801516532279fb9148d62e16fc0710de.ff";
        d = FileIO.read(path);
        p = 0;
        while (d[p] != 0) p++;
        String cn = new String(d, 0, p, StandardCharsets.ISO_8859_1);
        p++;
        System.out.println("className='" + cn + "' headerEnd=" + p);
        int count = 167893248;
        int i = p;
        // entry = u32 length | u8 flags | u8 lengthHigh | length*2 UTF-16LE bytes
        for (int n = 0; n < 6; n++) {
            int low = u32At(i);
            int flags = d[i + 4] & 0xFF;
            int hi = d[i + 5] & 0xFF;
            long len = low + (hi << 32);
            System.out.printf("entry %d @%d: low=%d flags=0x%02X hi=%d -> len=%d%n", n, i, low, flags, hi, len);
            i += 6;
        }
    }

    static int u32At(int o) {
        return (d[o] & 0xFF) | ((d[o + 1] & 0xFF) << 8) | ((d[o + 2] & 0xFF) << 16) | ((d[o + 3] & 0xFF) << 24);
    }
}
