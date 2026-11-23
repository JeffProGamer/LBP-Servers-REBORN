import java.io.File;
import java.io.RandomAccessFile;

/**
 * For each gadget the sticker relies on, show the actual bytes at that address and whether they
 * could plausibly be the claimed instruction sequence. Anything that starts with printable ASCII
 * inside .rodata is definitely not code.
 */
public class GadgetAudit {
    public static void main(String[] args) throws Exception {
        byte[] d;
        try (RandomAccessFile f = new RandomAccessFile(new File(args[0]), "r")) {
            d = new byte[(int) f.length()];
            f.readFully(d);
        }
        Object[][] g = {
                {"G_XOR_EAX_RET", 0x00EBB67FL, "31 c0 c3 (xor eax,eax; ret)"},
                {"G_PUSH_RSI_JMP_RSI", 0x01364C72L, "56 ff 26 (push rsi; jmp [rsi])  [PAST END OF FILE: 0x1364C72 > 0x11A6315]"},
                {"G_POP_RSP_RET", 0x00E18F3AL, "5c c3 (pop rsp; ret)"},
                {"G_POP_RDI_RET", 0x00F8F2F7L, "5f c3 (pop rdi; ret)"},
                {"G_POP_RSI_RET", 0x00D4FD70L, "5e c3 (pop rsi; ret)"},
                {"G_POP_RDX_RET", 0x00B5C742L, "5a c3 (pop rdx; ret)"},
                {"G_POP_RCX_RET", 0x00D20C2AL, "59 c3 (pop rcx; ret)"},
                {"G_POP_R8_RET", 0x0048F073L, "41 58 c3 (pop r8; ret)"},
                {"G_MOV_PTR_RAX_RET", 0x0041FC3BL, "48 89 07 c3 (mov [rdi],rax; ret)"},
                {"G_MOV_RDI_RAX_REPMOV", 0x00EF76E3L, "48 89 c7 f3 a5 c3"},
                {"G_RESTORE_RSP", 0x00A95F0DL, "48 8d 65 f0 5b 41 5e 5d c3"},
                {"G_MOV_EAX_CONN_RET", 0x00DE49E1L, "8b 05 ?? ?? ?? ?? c3"},
        };

        for (Object[] row : g) {
            String name = (String) row[0];
            long addr = (Long) row[1];
            String claim = (String) row[2];
            if (addr + 9 >= d.length) {
                System.out.printf("%-22s 0x%06X  %-27s |%-9s| %-20s OUTSIDE THE BINARY (file is 0x%X)%n",
                        name, addr, "(no such address)", "", "N/A", d.length);
                continue;
            }
            int a = (int) addr;
            StringBuilder hex = new StringBuilder();
            for (int j = 0; j < 9; j++) hex.append(String.format("%02X ", d[a + j]));
            StringBuilder asc = new StringBuilder();
            for (int j = 0; j < 9; j++) {
                int c = d[a + j] & 0xFF;
                asc.append(c >= 32 && c < 127 ? (char) c : '.');
            }
            String region = isText(d, a) ? "STRING/.rodata" : "code?";
            System.out.printf("%-22s 0x%06X  %-27s |%-9s| %-20s %s%n",
                    name, addr, hex.toString().trim(), asc.toString(), region,
                    region.startsWith("STRING") ? "<-- NOT A GADGET (claimed: " + claim + ")" : "");
        }
    }

    static boolean isText(byte[] d, int a) {
        for (int j = 0; j < 9; j++) {
            int c = d[a + j] & 0xFF;
            if (c < 32 || c >= 127) return false;
        }
        return true;
    }
}
