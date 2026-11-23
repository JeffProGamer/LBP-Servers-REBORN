import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Scans the decrypted LBP3 PS4 v1.28 eboot.bin for needle strings and prints every
 * file offset so EBOOT addresses in the sticker script can be verified.
 *
 * Usage: java .work/ScanEboot.java <eboot.bin> [needle...]
 */
public class ScanEboot {
    public static void main(String[] args) throws Exception {
        String path = args[0];
        byte[] d = readAll(path);
        System.out.println("eboot size = " + d.length);
        List<String> needles = new ArrayList<>();
        if (args.length > 1) {
            for (int i = 1; i < args.length; i++) needles.add(args[i]);
        } else {
            needles.add("littlebigplanetps3.online.scee.com");
            needles.add("LITTLEBIGPLANETPS3_XML");
            needles.add("cdn.update.playstation.net");
            needles.add("lbp.me");
            needles.add("auth.lbp.me");
            needles.add("HTTPS");
            needles.add("http://%s/r/%s");
            needles.add("https://res%c.%s/r/%s");
        }
        for (String n : needles) {
            byte[] pat = n.getBytes(StandardCharsets.ISO_8859_1);
            List<Integer> hits = findAll(d, pat);
            System.out.printf("%-42s hits=%d  %s%n", n, hits.size(),
                    hits.stream().limit(12).map(o -> String.format("0x%06X", o)).reduce((a, b) -> a + " " + b).orElse(""));
        }
    }

    static List<Integer> findAll(byte[] d, byte[] pat) {
        List<Integer> out = new ArrayList<>();
        outer:
        for (int i = 0; i <= d.length - pat.length; i++) {
            for (int j = 0; j < pat.length; j++) {
                if (d[i + j] != pat[j]) continue outer;
            }
            out.add(i);
        }
        return out;
    }

    static byte[] readAll(String path) throws Exception {
        try (RandomAccessFile f = new RandomAccessFile(new File(path), "r")) {
            byte[] b = new byte[(int) f.length()];
            f.readFully(b);
            return b;
        }
    }
}
