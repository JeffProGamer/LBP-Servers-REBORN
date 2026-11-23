import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses the PS4 SELF container used by LBP3 v1.28 eboot.bin (the ELF is nested inside)
 * and reports the virtual-address -> file-offset map, so the EBOOT addresses baked into
 * the sticker script can be checked against the real binary.
 *
 * Usage: java .work/SelfMap.java <eboot.bin> [vaddr...]
 */
public class SelfMap {
    static byte[] d;

    public static void main(String[] args) throws Exception {
        try (RandomAccessFile f = new RandomAccessFile(new File(args[0]), "r")) {
            d = new byte[(int) f.length()];
            f.readFully(d);
        }
        System.out.println("self size = 0x" + Long.toHexString(d.length) + " magic=" + hex(0, 4));

        int p = 0x18;
        int numEntries = u16(p);
        System.out.printf("numEntries=%d%n", numEntries);
        int elfOff = -1, elfSize = 0;
        List<long[]> segs = new ArrayList<>();
        for (int i = 0; i < numEntries; i++) {
            int o = 0x20 + i * 0x20;
            long id = u64(o);
            long off = u64(o + 8);
            long size = u64(o + 16);
            long enc = u64(o + 24);
            System.out.printf("  entry[%d] id=0x%X off=0x%X size=0x%X enc=0x%X  %s%n",
                    i, id, off, size, enc, id == 1 ? "(SELF header)" : "");
            if (id == 2 && elfOff < 0) { elfOff = (int) off; elfSize = (int) size; }
        }
        if (elfOff < 0) { System.out.println("no ELF segment"); return; }
        System.out.printf("%nELF at 0x%X size 0x%X  magic=%s%n", elfOff, elfSize, hex(elfOff, 4));

        // ELF64 header
        int e = elfOff;
        long phoff = u64(e + 0x20);
        int phentsize = u16(e + 0x36);
        int phnum = u16(e + 0x38);
        System.out.printf("phoff=0x%X phentsize=%d phnum=%d%n", phoff, phentsize, phnum);
        for (int i = 0; i < phnum; i++) {
            int o = e + (int) phoff + i * phentsize;
            long type = u32(o);
            long flags = u32(o + 4);
            long off = u64(o + 8);
            long vaddr = u64(o + 0x10);
            long filesz = u64(o + 0x20);
            long memsz = u64(o + 0x28);
            if (type == 1) {
                segs.add(new long[]{off, vaddr, filesz});
                System.out.printf("  LOAD  off=0x%-9X vaddr=0x%-9X filesz=0x%-9X memsz=0x%-9X flags=0x%X%n",
                        off, vaddr, filesz, memsz, flags);
            }
        }

        for (int i = 1; i < args.length; i++) {
            long a = Long.parseLong(args[i].replace("0x", ""), 16);
            String res = "(not in a LOAD segment)";
            for (long[] s : segs) {
                if (a >= s[1] && a < s[1] + s[2]) {
                    long foff = elfOff + s[0] + (a - s[1]);
                    int z = (int) foff;
                    while (z < d.length && d[z] != 0 && z < foff + 80) z++;
                    String str = new String(d, (int) foff, (int) (z - foff), StandardCharsets.ISO_8859_1);
                    res = String.format("file=0x%06X %s |%s", foff, hex((int) foff, 12), str.replaceAll("[^\\x20-\\x7E]", "."));
                    break;
                }
            }
            System.out.printf("0x%06X -> %s%n", a, res);
        }
    }

    static int u16(int o) { return (d[o] & 0xFF) | ((d[o + 1] & 0xFF) << 8); }
    static long u32(int o) { return (d[o] & 0xFFL) | ((d[o + 1] & 0xFFL) << 8) | ((d[o + 2] & 0xFFL) << 16) | ((d[o + 3] & 0xFFL) << 24); }
    static long u64(int o) { return u32(o) | (u32(o + 4) << 32); }
    static String hex(int o, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(String.format("%02X ", d[o + i]));
        return sb.toString().trim();
    }
}
