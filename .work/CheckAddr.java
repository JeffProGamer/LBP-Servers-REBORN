import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

/**
 * Verifies an EBOOT address: dumps the bytes just before that address (which is what a
 * gadget/function actually starts at on x86-64) plus the address itself.
 */
public class CheckAddr {
    public static void main(String[] args) throws Exception {
        byte[] d;
        try (RandomAccessFile f = new RandomAccessFile(new File(args[0]), "r")) {
            d = new byte[(int) f.length()];
            f.readFully(d);
        }
        for (int i = 1; i < args.length; i++) {
            long addr = Long.parseLong(args[i].replace("0x", ""), 16);
            System.out.printf("%n== 0x%06X ==", addr);
            for (int back = 6; back >= 0; back--) {
                int o = (int) (addr - back);
                StringBuilder sb = new StringBuilder();
                for (int j = 0; j < 12; j++) sb.append(String.format("%02X ", d[o + j]));
                // is the byte before the target an instruction end? show printable too
                int end = o;
                while (end < d.length && d[end] != 0) end++;
                String s = new String(d, o, Math.min(24, end - o), StandardCharsets.ISO_8859_1);
                System.out.printf("%n   %s(%2d) %s | %s", back == 0 ? "-> " : "   ",
                        back, sb.toString().trim(), s.replaceAll("[^\\x20-\\x7E]", "."));
            }
        }
    }
}
