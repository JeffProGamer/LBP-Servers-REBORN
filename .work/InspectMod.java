import java.io.*;
import java.util.zip.*;

/**
 * Inspects the *.mod (zip) LBPOnline patcher package: lists entries and reports
 * which ones are deflate-compressed vs stored, so we can locate the raw resource.
 */
public class InspectMod {
    public static void main(String[] args) throws Exception {
        String path = args.length > 0 ? args[0] : "a15f684e801516532279fb9148d62e16fc0710de.mod";
        try (ZipFile zip = new ZipFile(path)) {
            for (java.util.Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements(); ) {
                ZipEntry ze = e.nextElement();
                System.out.printf("%-70s method=%d size=%d compressed=%d extra=%d%n",
                        ze.getName(), ze.getMethod(), ze.getSize(), ze.getCompressedSize(), ze.getExtra() == null ? -1 : ze.getExtra().length);
                if (ze.getName().endsWith(".farc") || ze.getName().endsWith(".plank")) {
                    byte[] data = readAll(zip.getInputStream(ze));
                    System.out.println("  first bytes: " + hex(data, 0, Math.min(48, data.length)));
                    System.out.println("  ascii head : " + ascii(data, 0, Math.min(64, data.length)));
                }
            }
        }
    }

    static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int r;
        while ((r = in.read(buf)) > 0) bo.write(buf, 0, r);
        return bo.toByteArray();
    }

    static String hex(byte[] d, int off, int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = off; i < off + len; i++) sb.append(String.format("%02X ", d[i]));
        return sb.toString();
    }

    static String ascii(byte[] d, int off, int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = off; i < off + len; i++) {
            int c = d[i] & 0xFF;
            sb.append(c >= 32 && c < 127 ? (char) c : '.');
        }
        return sb.toString();
    }
}
