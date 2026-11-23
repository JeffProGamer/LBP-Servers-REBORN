import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

/**
 * Applies exactly the same in-memory URL patches the sticker performs, directly to a copy
 * of the retail eboot, then reads the patched strings back to prove the offsets/slots are right.
 *
 * Usage: java .work/SimulatePatch.java <eboot.bin> <domain>
 */
public class SimulatePatch {
    static byte[] d;

    public static void main(String[] args) throws Exception {
        String dom = args.length > 1 ? args[1] : "lbpreborn.onrender.com";
        try (RandomAccessFile f = new RandomAccessFile(new File(args[0]), "r")) {
            d = new byte[(int) f.length()];
            f.readFully(d);
        }

        // [offset, original string shown for sanity]
        long[][] slots = {
                {0x00E53C24L, 34}, // littlebigplanetps3.online.scee.com
                {0x00E6EDDFL, 30}, // a01.cdn.update.playstation.net
                {0x00E82961L, 71}, // https://littlebigplanetps3.online.scee.com:10061/LITTLEBIGPLANETPS3_XML
        };
        String[] repl = {dom, dom, "https://" + dom + "/lbp/xml"};

        System.out.println("=== before ===");
        for (long[] s : slots) System.out.printf("0x%06X  %s%n", s[0], str((int) s[0]));

        for (int i = 0; i < slots.length; i++) {
            int off = (int) slots[i][0];
            int slot = (int) slots[i][1];
            byte[] r = repl[i].getBytes(StandardCharsets.ISO_8859_1);
            if (r.length > slot) {
                System.out.printf("!! replacement too long for slot at 0x%06X (%d > %d)%n", slots[i][0], r.length, slot);
                return;
            }
            // what the script does: write every char, then unconditionally write the NUL
            for (int j = 0; j < r.length; j++) d[off + j] = r[j];
            d[off + r.length] = 0;
            // the slot's own trailing NUL must still be there (nothing after it gets clobbered)
            int tail = off + slot;
            System.out.printf("slot 0x%06X: remaining byte after new NUL = 0x%02X (expected 0x00), untouched tail = %s%n",
                    slots[i][0], d[off + r.length + 1], str(tail));
        }

        System.out.println();
        System.out.println("=== after ===");
        for (long[] s : slots) System.out.printf("0x%06X  %s%n", s[0], str((int) s[0]));
    }

    static String str(int o) {
        int e = o;
        while (e < d.length && d[e] != 0) e++;
        return new String(d, o, e - o, StandardCharsets.ISO_8859_1);
    }
}
