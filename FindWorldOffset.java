import cwlib.io.*;
import cwlib.util.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class FindWorldOffset {
    public static void main(String[] args) throws Exception {
        byte[] f = FileIO.read("E:\\LBPPC-0.1.5-windows\\LBPPC\\out\\eboot.elf");
        
        // Search for World structure initialization - look for writes to [reg+0xAD0]
        System.out.println("=== Searching for writes to [reg+0xAD0] ===");
        // Pattern: mov [reg+0xAD0], reg or mov [reg+0xAD0], imm
        // 48 89 87 D0 0A 00 00 = mov [rdi+0xAD0], rax
        // 48 89 86 D0 0A 00 00 = mov [rsi+0xAD0], rax
        // 48 C7 87 D0 0A 00 00 = mov qword [rdi+0xAD0], imm32
        byte[] movRdiAd0 = new byte[] {(byte)0x48, (byte)0x89, (byte)0x87, (byte)0xD0, (byte)0x0A, 0, 0};
        byte[] movRsiAd0 = new byte[] {(byte)0x48, (byte)0x89, (byte)0x86, (byte)0xD0, (byte)0x0A, 0, 0};
        byte[] movImmRdiAd0 = new byte[] {(byte)0x48, (byte)0xC7, (byte)0x87, (byte)0xD0, (byte)0x0A, 0, 0};
        byte[] movImmRsiAd0 = new byte[] {(byte)0x48, (byte)0xC7, (byte)0x86, (byte)0xD0, (byte)0x0A, 0, 0};
        
        searchPattern(f, 0x4000, 0x105CF20, "mov [rdi+0xAD0],rax", movRdiAd0);
        searchPattern(f, 0x4000, 0x105CF20, "mov [rsi+0xAD0],rax", movRsiAd0);
        searchPattern(f, 0x4000, 0x105CF20, "mov [rdi+0xAD0],imm", movImmRdiAd0);
        searchPattern(f, 0x4000, 0x105CF20, "mov [rsi+0xAD0],imm", movImmRsiAd0);
        
        // Search for lea reg, [rip+offset] where offset points to data around 0x014xxxxx
        System.out.println("\n=== Searching for lea to high addresses (0x01xxxxxx) ===");
        // 48 8D 05 xx xx xx xx = lea rax, [rip+disp32]
        // 48 8D 3D xx xx xx xx = lea rdi, [rip+disp32]
        // 48 8D 35 xx xx xx xx = lea rsi, [rip+disp32]
        for (int i = 0x4000; i < 0x4000 + 0x105CF20 - 7; i++) {
            if (f[i] == (byte)0x48 && (f[i+1] == (byte)0x8D || f[i+1] == (byte)0x8D)) {
                int modrm = f[i+2] & 0xFF;
                // Check if it's lea reg, [rip+disp32] (mod=00, rm=101)
                if ((modrm & 0xC7) == 0x05) { // mod=00, rm=101
                    int disp = ByteBuffer.wrap(f, i+3, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
                    long target = (i - 0x4000) + 7 + disp; // VMA of target
                    if (target >= 0x01400000 && target < 0x01800000) {
                        int reg = (modrm >> 3) & 7;
                        String regName = new String[]{"rax","rcx","rdx","rbx","rsp","rbp","rsi","rdi"}[reg];
                        System.out.printf("  lea %s, [rip+0x%X] -> target 0x%X at VMA 0x%X\n", regName, disp, target, i - 0x4000);
                    }
                }
            }
        }
        
        // Search for the specific GOT addresses as 64-bit values in data segment
        System.out.println("\n=== Scanning data segment for pointers to code ===");
        // Data segment: file offset 0x1064000 to 0x1064000+0x6DCA8
        for (int i = 0x1064000; i < 0x1064000 + 0x6DCA8 - 8; i += 8) {
            ByteBuffer buf = ByteBuffer.wrap(f, i, 8).order(ByteOrder.LITTLE_ENDIAN);
            long val = buf.getLong();
            // Check if it points into code segment (0x0 - 0x105CF20)
            if (val >= 0x1000 && val < 0x105CF20) {
                System.out.printf("  Data off 0x%X (VMA 0x%X) -> code 0x%X\n", i, 0x1060000 + (i - 0x1064000), val);
            }
            // Check if it points to high addresses 0x014xxxxx
            if (val >= 0x01400000 && val < 0x01800000) {
                System.out.printf("  Data off 0x%X (VMA 0x%X) -> high 0x%X\n", i, 0x1060000 + (i - 0x1064000), val);
            }
        }
    }
    
    static void searchPattern(byte[] f, long baseOff, long size, String name, byte[] pattern) {
        int maxOff = (int) (baseOff + size - pattern.length);
        int count = 0;
        for (int i = (int) baseOff; i <= maxOff && count < 10; i++) {
            boolean match = true;
            for (int j = 0; j < pattern.length; j++) {
                if (f[i + j] != pattern[j]) { match = false; break; }
            }
            if (match) {
                long vma = i - 0x4000;
                System.out.printf("  %s: 0x%X (off 0x%X)\n", name, vma, i);
                count++;
            }
        }
        if (count == 0) {
            System.out.printf("  %s: NOT FOUND\n", name);
        }
    }
}