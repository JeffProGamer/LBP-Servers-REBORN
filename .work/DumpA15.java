import java.io.*;
import java.util.*;

/** Dumps the string tables / header of an RScript (.ff) resource and prints the URLs it references. */
public class DumpA15 {
    static int p;

    public static void main(String[] args) throws Exception {
        String path = args.length > 0 ? args[0] : "a15f684e801516532279fb9148d62e16fc0710de.ff";
        byte[] d = read(path);
        System.out.println("size=" + d.length);
        p = 0;
        // header: class name (lc string)
        String cls = lcs(d);
        System.out.println("className='" + cls + "' (@p=" + p + ")");
        System.out.printf("next 64 bytes: %s%n", hex(d, p, 64));

        // RScript fields, compact
        System.out.println("typeReferences count=" + s32(d));
        dumpTables(d);
    }

    static void dumpTables(byte[] d) {
        // We don't know exact layout; brute-dump both string tables by scanning for plausible "AS" table
        System.out.println("remaining bytes=" + (d.length - p));
        // Print all printable runs >= 5 chars with their offsets that look like urls/domains
        p = 200;
        List<String> hits = new ArrayList<>();
        int start = -1;
        for (int i = 200; i < d.length; i++) {
            int c = d[i] & 0xFF;
            boolean printable = c >= 32 && c < 127;
            if (printable) {
                if (start < 0) start = i;
            } else {
                if (start >= 0) {
                    int len = i - start;
                    if (len >= 5) {
                        String s = new String(d, start, len, java.nio.charset.StandardCharsets.ISO_8859_1);
                        if (s.matches(".*(http|://|\\.com|\\.net|\\.org|\\.io|\\.me|scee|playstation|lbp).*")) {
                            hits.add(String.format("0x%06X  %s", start, s));
                        }
                    }
                    start = -1;
                }
            }
        }
        for (String h : hits) System.out.println(h);
        System.out.println("hits=" + hits.size());
    }

    static String lcs(byte[] d) {
        int start = p;
        while (d[p] != 0) p++;
        String s = new String(d, start, p - start, java.nio.charset.StandardCharsets.ISO_8859_1);
        p++;
        return s;
    }

    static int s32(byte[] d) {
        int v = (d[p] & 0xFF) | ((d[p + 1] & 0xFF) << 8) | ((d[p + 2] & 0xFF) << 16) | ((d[p + 3] & 0xFF) << 24);
        p += 4;
        return v;
    }

    static String hex(byte[] d, int off, int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = off; i < Math.min(d.length, off + len); i++) sb.append(String.format("%02X ", d[i]));
        return sb.toString();
    }

    static byte[] read(String path) throws Exception {
        File f = new File(path);
        byte[] data = new byte[(int) f.length()];
        try (FileInputStream fis = new FileInputStream(f)) {
            int r = fis.read(data);
            if (r != data.length) System.out.println("short read " + r);
        }
        return data;
    }
}
