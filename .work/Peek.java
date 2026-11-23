import java.io.File;
import java.io.RandomAccessFile;

/** Dumps raw bytes at fixed file offsets of the eboot (for gadget triage). */
public class Peek {
    public static void main(String[] args) throws Exception {
        byte[] d;
        try (RandomAccessFile f = new RandomAccessFile(new File(args[0]), "r")) {
            d = new byte[(int) f.length()];
            f.readFully(d);
        }
        for (int i = 1; i < args.length; i++) {
            long a = Long.parseLong(args[i].replace("0x", ""), 16);
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < 16; j++) sb.append(String.format("%02X ", d[(int) a + j]));
            System.out.printf("0x%06X  %s%n", a, sb.toString().trim());
        }
    }
}
