import cwlib.io.streams.MemoryInputStream;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Lists an ELF64's program headers and converts vaddr <-> file offset,
 * so EBOOT addresses from the sticker script can be checked against the real binary.
 *
 * Usage: java .work/ElfMap.java <eboot.bin> [addr...]
 */
public class ElfMap {
    public static void main(String[] args) throws Exception {
        byte[] d;
        try (RandomAccessFile f = new RandomAccessFile(new File(args[0]), "r")) {
            d = new byte[(int) f.length()];
            f.readFully(d);
        }
        MemoryInputStream ms = new MemoryInputStream(d);
        ms.seek(0x20);
        long phoff = ms.u64();
        ms.seek(0x36);
        int phentsize = ms.u16();
        int phnum = ms.u16();
        System.out.printf("phoff=0x%X phentsize=%d phnum=%d fileSize=0x%X%n", phoff, phentsize, phnum, d.length);

        long[][] segs = new long[phnum][];
        for (int i = 0; i < phnum; i++) {
            long base = phoff + (long) i * phentsize;
            ms.seek((int) base);
            long type = ms.u32();
            long flags = ms.u32();
            long off = ms.u64();
            long vaddr = ms.u64();
            long paddr = ms.u64();
            long filesz = ms.u64();
            long memsz = ms.u64();
            segs[i] = new long[]{type, flags, off, vaddr, filesz, memsz};
            System.out.printf("ph[%d] type=0x%X flags=0x%X off=0x%X vaddr=0x%X filesz=0x%X memsz=0x%X%n",
                    i, type, flags, off, vaddr, filesz, memsz);
        }

        for (int i = 1; i < args.length; i++) {
            long a = Long.parseLong(args[i].replace("0x", ""), 16);
            String res = "  (no segment)";
            for (long[] s : segs) {
                if (s[0] == 1 && a >= s[3] && a < s[3] + s[4]) {
                    long foff = s[2] + (a - s[3]);
                    int end = (int) foff;
                    StringBuilder sb = new StringBuilder();
                    for (int j = 0; j < 16 && end + j < d.length; j++) sb.append(String.format("%02X ", d[end + j]));
                    int z = end;
                    while (z < d.length && d[z] != 0 && z < end + 80) z++;
                    String str = new String(d, end, Math.max(0, z - end), StandardCharsets.ISO_8859_1);
                    res = String.format("file=0x%06X  %s  |%s", foff, sb.toString().trim(), str.replaceAll("[^\\x20-\\x7E]", "."));
                    break;
                }
            }
            System.out.printf("0x%06X -> %s%n", a, res);
        }
    }
}

