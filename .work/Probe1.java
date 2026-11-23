import cwlib.structs.script.*;
import cwlib.io.streams.MemoryInputStream;
import cwlib.types.data.Revision;
import cwlib.util.FileIO;

public class Probe1 {
    public static void main(String[] a) throws Exception {
        byte[] d = FileIO.read("a15f684e801516532279fb9148d62e16fc0710de.ff");
        int h = 0; while (d[h] != 0) h++; h++;
        Revision rev = new Revision(0x272, 0x4C44, 0x17);
        byte[] body = new byte[d.length - h + 1024];
        System.arraycopy(d, h, body, 0, d.length - h);
        MemoryInputStream ms = new MemoryInputStream(body);
        ms.setLittleEndian(true);
        System.out.println("start off=" + ms.getOffset());
        System.out.println("typeReferences count=" + ms.i32());
        System.out.println("stringATable count=" + ms.i32());
        System.out.println("off now=" + ms.getOffset());
        System.out.println("next 32 bytes: " + hex(body, ms.getOffset(), 32));
        // try reading first few stringA entries as lc strings
        for (int i = 0; i < 3; i++) {
            int save = ms.getOffset();
            try { System.out.println("  str: " + ms.str()); } catch (Throwable t) { System.out.println("  str fail " + t); }
            ms.seek(save);
            System.out.println("  u32=" + ms.i32());
            ms.seek(save);
            System.out.println("  u8 =0x" + Integer.toHexString(ms.u8()));
            ms.seek(save);
        }
    }
    static String hex(byte[] b, int o, int n) { StringBuilder s = new StringBuilder(); for (int i = o; i < o + n && i < b.length; i++) s.append(String.format("%02X ", b[i])); return s.toString(); }
}
