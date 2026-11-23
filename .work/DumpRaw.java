import cwlib.util.FileIO;

import java.nio.charset.StandardCharsets;

/**
 * Reads a *raw* (already serialized, un-compressed) .ff RScript body and prints
 * every component with offsets so we can re-patch it in place.
 *
 *   [cs:className][s32 count][lcs typerefs...][s32 count][lcs stringA...]
 *   [refs...][bytecode...]
 *
 * Usage: java -cp "toolkit-0.1.jar;." .work/DumpRaw.java .work/lbpwn_raw.bin
 */
public class DumpRaw {
    static byte[] d;
    static int p;

    public static void main(String[] args) throws Exception {
        d = FileIO.read(args.length > 0 ? args[0] : ".work/lbpwn_raw.bin");
        p = 0;
        System.out.println("total=" + d.length);
        System.out.println("className   = '" + lcs() + "' @0-" + p);

        int typeCount = s32("typeReferences");
        System.out.println("typeReferences count = " + typeCount + " @ " + (p - 4));
        for (int i = 0; i < typeCount && i < 200; i++) {
            int flags = d[p] & 0xFF;
            int start = p;
            System.out.printf("  typeRef[%d] @%d flags=0x%02X len=%d%n", i, start, flags, flags & 0x0F);
            p += 1; // (probe: real layout printed by caller)
            break;
        }

        // stringATable
        p = 0;
        lcs();
        int tc = u32();
        System.out.println("after typeRefs count: p=" + p + " count=" + tc);
        byte[] probe = new byte[64];
        System.arraycopy(d, p, probe, 0, 64);
        System.out.println("next 64: " + hex(probe));
    }

    static String lcs() {
        int start = p;
        while (d[p] != 0) p++;
        String s = new String(d, start, p - start, StandardCharsets.ISO_8859_1);
        p++;
        return s;
    }

    static int s32(String what) {
        return u32();
    }

    static int u32() {
        int v = (d[p] & 0xFF) | ((d[p + 1] & 0xFF) << 8) | ((d[p + 2] & 0xFF) << 16) | ((d[p + 3] & 0xFF) << 24);
        p += 4;
        return v;
    }

    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < b.length; i++) sb.append(String.format("%02X ", b[i]));
        return sb.toString();
    }
}
