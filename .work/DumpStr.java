import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

/** Prints the NUL-terminated ASCII string at each given eboot offset. */
public class DumpStr {
    public static void main(String[] args) throws Exception {
        byte[] d;
        try (RandomAccessFile f = new RandomAccessFile(new File(args[0]), "r")) {
            d = new byte[(int) f.length()];
            f.readFully(d);
        }
        for (int i = 1; i < args.length; i++) {
            long off = Long.parseLong(args[i].replace("0x", "").replace("0X", ""), 16);
            int o = (int) off;
            int end = o;
            while (end < d.length && d[end] != 0) end++;
            String s = new String(d, o, end - o, StandardCharsets.ISO_8859_1);
            System.out.printf("0x%06X  len=%3d  %s%n", off, s.length(), s);
        }
    }
}
