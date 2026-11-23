import java.util.ArrayList;
import java.util.List;

/**
 * Typed memory-at-VADDR accessor backed by the decompressed "ImageMemory" image format
 * dump (starts with the header string "ImageMemory"):
 *
 *   0x00        "ImageMemory\0..."  (8 bytes, then 0x18 bytes padding)
 *   0x20        u64 dataBaseVaddr   (e.g. 0x400000)
 *   0x28        u64 dataBaseOffset  (file offset where vaddr dataBase starts)
 *   0x30        u32 segmentEntrySize (0x20)
 *   0x34        u32 segmentCount
 *   0x38        segment table (count * 0x20): u64 vaddr, u64 memsz, u64 offset, u64 filesz
 */
public final class Image {
    private final byte[] file;
    private final long baseVaddr;
    private final long baseOffset;
    private final List<long[]> segs = new ArrayList<>();

    public Image(String path) throws Exception {
        try (java.io.RandomAccessFile f = new java.io.RandomAccessFile(new java.io.File(path), "r")) {
            this.file = new byte[(int) f.length()];
            f.readFully(this.file);
        }
        String magic = new String(this.file, 0, 11, java.nio.charset.StandardCharsets.ISO_8859_1);
        if (!magic.startsWith("ImageMemory")) throw new IllegalStateException("not an ImageMemory dump: " + magic);

        this.baseVaddr = u64(0x20);
        this.baseOffset = u64(0x28);
        int entrySize = (int) u32(0x30);
        int count = (int) u32(0x34);
        for (int i = 0; i < count; i++) {
            int o = 0x38 + i * entrySize;
            this.segs.add(new long[]{u64(o), u64(o + 8), u64(o + 16), u64(o + 24)});
        }
    }

    public long baseVaddr() { return this.baseVaddr; }
    public List<long[]> segments() { return this.segs; }
    public int length() { return this.file.length; }

    public byte[] read(long vaddr, int len) {
        byte[] out = new byte[len];
        for (int i = 0; i < len; i++) out[i] = (byte) u8(vaddr + i);
        return out;
    }

    public int u8(long vaddr) {
        for (long[] s : this.segs) {
            if (vaddr >= s[0] && vaddr < s[0] + s[1]) {
                long foff = this.baseOffset + s[2] + (vaddr - s[0]);
                return this.file[(int) foff] & 0xFF;
            }
        }
        return 0xFF; // unmapped
    }

    public boolean mapped(long vaddr) {
        for (long[] s : this.segs) if (vaddr >= s[0] && vaddr < s[0] + s[1]) return true;
        return false;
    }

    private long u32(int o) {
        return (this.file[o] & 0xFFL) | ((this.file[o + 1] & 0xFFL) << 8)
                | ((this.file[o + 2] & 0xFFL) << 16) | ((this.file[o + 3] & 0xFFL) << 24);
    }

    private long u64(int o) { return u32(o) | (u32(o + 4) << 32); }
}
