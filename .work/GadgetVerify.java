/**
 * Checks the LBPWN write-up's gadgets + absolute addresses against the decompressed image.
 *
 * Usage: java .work/GadgetVerify.java "E:\...\ImageMemory.bin"
 *
 * Addresses come from the write-up, which says "you still must add the executable ASLR slide",
 * so they are compared against address + slide for the slide implied by the image base.
 */
public class GadgetVerify {
    public static void main(String[] args) throws Exception {
        Image img = new Image(args[0]);
        System.out.printf("ImageMemory: %d bytes, baseVaddr=0x%X, baseOffset=0x%X, segments=%d%n",
                img.length(), img.baseVaddr(), img.baseVaddr(), img.segments().size());
        for (long[] s : img.segments())
            System.out.printf("  seg vaddr=0x%-9X memsz=0x%-9X off=0x%-9X filesz=0x%X%n", s[0], s[1], s[2], s[3]);

        long base = img.baseVaddr();          // image is dumped at its ORIGINAL vaddrs (0x400000)
        long slide = base - 0x400000L;        // what the game adds before any absolute address
        System.out.printf("%nslide implied by image base = 0x%X%n", slide);

        Object[][] gadgets = {
                {"xor eax,eax ; ret", 0xEBB67FL, new int[]{0x31, 0xC0, 0xC3}},
                {"push rsi ; jmp [rsi]", 0x1364C72L, new int[]{0x56, 0xFF, 0x26}},
                {"pop rsp ; ret", 0xE18F3AL, new int[]{0x5C, 0xC3}},
                {"pop rdi ; ret", 0xF8F2F7L, new int[]{0x5F, 0xC3}},
                {"pop rsi ; ret", 0xD4FD70L, new int[]{0x5E, 0xC3}},
                {"pop rdx ; ret", 0xB5C742L, new int[]{0x5A, 0xC3}},
                {"pop rcx ; ret", 0xD20C2AL, new int[]{0x59, 0xC3}},
                {"pop r8 ; ret", 0x48F073L, new int[]{0x41, 0x58, 0xC3}},
                {"mov [rdi],rax ; ret", 0x41FC3BL, new int[]{0x48, 0x89, 0x07, 0xC3}},
                {"mov rdi,rax ; rep movsd ; ret", 0xEF76E3L, new int[]{0x48, 0x89, 0xC7, 0xF3, 0xA5, 0xC3}},
                {"lea rsp,[rbp-0x10];pop rbx;r14;rbp;ret", 0xA95F0DL,
                        new int[]{0x48, 0x8D, 0x65, 0xF0, 0x5B, 0x41, 0x5E, 0x5D, 0xC3}},
        };

        int ok = 0;
        for (Object[] g : gadgets) {
            String name = (String) g[0];
            long addr = (Long) g[1];
            int[] exp = (int[]) g[2];
            long va = addr; // slide is 0 when image base is 0x400000
            boolean mapped = img.mapped(va) && img.mapped(va + exp.length - 1);
            if (!mapped) {
                System.out.printf("  [ - ] %-40s 0x%06X  UNMAPPED%n", name, addr);
                continue;
            }
            StringBuilder got = new StringBuilder();
            boolean match = true;
            for (int i = 0; i < exp.length; i++) {
                int b = img.u8(va + i);
                got.append(String.format("%02X ", b));
                if (b != exp[i]) match = false;
            }
            System.out.printf("  [%s] %-40s 0x%06X  %s %s%n", match ? " ok" : "BAD", name, addr,
                    got.toString().trim(), match ? "" : "(expected " + hex(exp) + ")");
            if (match) ok++;
        }
        System.out.printf("%n%d/%d gadgets verified byte-for-byte%n", ok, gadgets.length);

        System.out.println("\nabsolute addresses the script needs:");
        long[] abs = {0x0148A4F0L, 0x0124B207L, 0x014CCEE8L, 0x014CCF10L, 0x014CCF60L,
                0x014CCE80L, 0x014CCF08L, 0x014CCD20L, 0x00421E80L, 0x01590E68L,
                0x01590EF0L, 0x017C6BC8L, 0x014714F8L};
        for (long a : abs) {
            if (!img.mapped(a)) {
                System.out.printf("  0x%06X  UNMAPPED (never written to image)%n", a);
                continue;
            }
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < 8; i++) b.append(String.format("%02X ", img.u8(a + i)));
            StringBuilder s = new StringBuilder();
            for (int i = 0; i < 24; i++) {
                int c = img.u8(a + i);
                if (c == 0) break;
                s.append(c >= 32 && c < 127 ? (char) c : '.');
            }
            System.out.printf("  0x%06X  %-24s |%s|%n", a, b.toString().trim(), s);
        }
    }

    static String hex(int[] a) {
        StringBuilder sb = new StringBuilder();
        for (int v : a) sb.append(String.format("%02X ", v));
        return sb.toString().trim();
    }
}
