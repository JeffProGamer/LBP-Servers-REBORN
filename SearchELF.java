import cwlib.io.*;
import cwlib.util.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class SearchELF {
    public static void main(String[] args) throws Exception {
        byte[] f = FileIO.read("E:\\LBPPC-0.1.5-windows\\LBPPC\\out\\eboot.elf");
        
        // VMA to file offset mapping
        java.util.function.LongFunction<Integer> vmaToOffset = (vma) -> {
            // PH[0]: vaddr=0x0, offset=0x4000, filesz=0x105CF20
            if (vma >= 0x0L && vma < 0x105CF20L) {
                return (int) (vma - 0x0L + 0x4000L);
            }
            // PH[1]: vaddr=0x1060000, offset=0x1064000, filesz=0x6DCA8
            if (vma >= 0x1060000L && vma < 0x1060000L + 0x6DCA8L) {
                return (int) (vma - 0x1060000L + 0x1064000L);
            }
            return -1;
        };
        
        // Search for LEAK_POINTER_VALUE 0x0148A4F0 - try both segments
        System.out.println("=== Searching for LEAK_POINTER_VALUE 0x0148A4F0 ===");
        byte[] leakBytes = new byte[] {(byte)0xF0, (byte)0xA4, (byte)0x48, (byte)0x01, 0, 0, 0, 0};
        for (int i = 0; i < f.length - 8; i++) {
            boolean match = true;
            for (int j = 0; j < 8; j++) {
                if (f[i + j] != leakBytes[j]) { match = false; break; }
            }
            if (match) {
                // Calculate VMA
                long vma = -1;
                if (i >= 0x4000 && i < 0x4000 + 0x105CF20L) {
                    vma = i - 0x4000;
                } else if (i >= 0x1064000 && i < 0x1064000 + 0x6DCA8L) {
                    vma = i - 0x1064000 + 0x1060000L;
                }
                System.out.printf("Found at file offset 0x%X (VMA 0x%X)\n", i, vma);
            }
        }
        
        // Search for GOT entries in data segment (PH[1] vaddr=0x1060000)
        System.out.println("\n=== GOT entries in data segment (0x1060000+) ===");
        long[] gotCandidates = {
            0x014CCEE8L,  // sceHttpsSetSslCallback
            0x014CCF10L,  // sceHttpCreateConnection
            0x014CCF60L,  // sceHttpDeleteConnection
            0x014CCE80L,  // sceHttpSetCookieEnabled
            0x014CCF08L,  // sceHttpAddRequestHeader
            0x014CCD20L,  // sceFiosDHCloseSync
        };
        String[] gotNames = {
            "sceHttpsSetSslCallback",
            "sceHttpCreateConnection", 
            "sceHttpDeleteConnection",
            "sceHttpSetCookieEnabled",
            "sceHttpAddRequestHeader",
            "sceFiosDHCloseSync"
        };
        
        for (int i = 0; i < gotCandidates.length; i++) {
            int off = vmaToOffset.apply(gotCandidates[i]);
            if (off >= 0 && off + 8 <= f.length) {
                ByteBuffer buf = ByteBuffer.wrap(f, off, 8).order(ByteOrder.LITTLE_ENDIAN);
                long val = buf.getLong();
                System.out.printf("%s @ 0x%X (off 0x%X) = 0x%X\n", gotNames[i], gotCandidates[i], off, val);
            } else {
                System.out.printf("%s @ 0x%X -> invalid offset\n", gotNames[i], gotCandidates[i]);
            }
        }
        
        // Data addresses (in data segment)
        System.out.println("\n=== Data addresses in data segment ===");
        long[] dataAddrs = {
            0x01590E68L,  // DATA_HTTP_FLAG
            0x01590EF0L,  // DATA_TMPL_ID
            0x017C6BC8L,  // DATA_CONN_ID
        };
        String[] dataNames = {"DATA_HTTP_FLAG", "DATA_TMPL_ID", "DATA_CONN_ID"};
        
        for (int i = 0; i < dataAddrs.length; i++) {
            int off = vmaToOffset.apply(dataAddrs[i]);
            if (off >= 0 && off + 8 <= f.length) {
                ByteBuffer buf = ByteBuffer.wrap(f, off, 8).order(ByteOrder.LITTLE_ENDIAN);
                long val = buf.getLong();
                System.out.printf("%s @ 0x%X (off 0x%X) = 0x%X\n", dataNames[i], dataAddrs[i], off, val);
            } else {
                System.out.printf("%s @ 0x%X -> invalid offset (segment ends at 0x10CDCA8)\n", dataNames[i], dataAddrs[i]);
            }
        }
        
        // Also check if these are in the first segment
        System.out.println("\n=== Checking data addresses in code segment ===");
        for (int i = 0; i < dataAddrs.length; i++) {
            long vma = dataAddrs[i];
            if (vma >= 0x0L && vma < 0x105CF20L) {
                int off = (int) (vma + 0x4000);
                if (off + 8 <= f.length) {
                    ByteBuffer buf = ByteBuffer.wrap(f, off, 8).order(ByteOrder.LITTLE_ENDIAN);
                    long val = buf.getLong();
                    System.out.printf("%s @ 0x%X (off 0x%X) = 0x%X\n", dataNames[i], dataAddrs[i], off, val);
                }
            }
        }
        
        // FN_HTTP_INIT
        System.out.println("\n=== FN_HTTP_INIT ===");
        int off = vmaToOffset.apply(0x00421E80L);
        if (off >= 0 && off + 16 <= f.length) {
            System.out.printf("At VMA 0x00421E80 (off 0x%X):\n", off);
            for (int j = 0; j < 16; j++) {
                System.out.printf("%02X ", f[off + j]);
            }
            System.out.println();
        }
        
        // Search for ROP gadgets - let's look for some common patterns
        System.out.println("\n=== Searching for ROP gadgets in code segment ===");
        // xor eax, eax ; ret = 31 C0 C3
        byte[] xorEaxRet = new byte[] {(byte)0x31, (byte)0xC0, (byte)0xC3};
        // pop rsp ; ret = 5C C3
        byte[] popRspRet = new byte[] {(byte)0x5C, (byte)0xC3};
        // pop rdi ; ret = 5F C3
        byte[] popRdiRet = new byte[] {(byte)0x5F, (byte)0xC3};
        // pop rsi ; ret = 5E C3
        byte[] popRsiRet = new byte[] {(byte)0x5E, (byte)0xC3};
        // pop rdx ; ret = 5A C3
        byte[] popRdxRet = new byte[] {(byte)0x5A, (byte)0xC3};
        // pop rcx ; ret = 59 C3
        byte[] popRcxRet = new byte[] {(byte)0x59, (byte)0xC3};
        // pop r8 ; ret = 41 58 C3
        byte[] popR8Ret = new byte[] {(byte)0x41, (byte)0x58, (byte)0xC3};
        // mov [rdi], rax ; ret = 48 89 07 C3
        byte[] movRdiRaxRet = new byte[] {(byte)0x48, (byte)0x89, (byte)0x07, (byte)0xC3};
        // mov rdi, rax ; rep movsd ; ret = 48 89 C7 F3 A5 C3
        byte[] movRdiRaxRepMovsdRet = new byte[] {(byte)0x48, (byte)0x89, (byte)0xC7, (byte)0xF3, (byte)0xA5, (byte)0xC3};
        // lea rsp,[rbp-0x10] ; pop rbx ; pop r14 ; pop rbp ; ret = 48 8D 65 F0 5B 41 5E 5D C3
        byte[] restoreRsp = new byte[] {(byte)0x48, (byte)0x8D, (byte)0x65, (byte)0xF0, (byte)0x5B, (byte)0x41, (byte)0x5E, (byte)0x5D, (byte)0xC3};
        // mov eax,[0x017C6BC8] ; ret = 8B 05 (rip-relative) or A1 (absolute) + C3
        
        searchPattern(f, 0x4000, 0x105CF20, "xor eax,eax;ret", xorEaxRet);
        searchPattern(f, 0x4000, 0x105CF20, "pop rsp;ret", popRspRet);
        searchPattern(f, 0x4000, 0x105CF20, "pop rdi;ret", popRdiRet);
        searchPattern(f, 0x4000, 0x105CF20, "pop rsi;ret", popRsiRet);
        searchPattern(f, 0x4000, 0x105CF20, "pop rdx;ret", popRdxRet);
        searchPattern(f, 0x4000, 0x105CF20, "pop rcx;ret", popRcxRet);
        searchPattern(f, 0x4000, 0x105CF20, "pop r8;ret", popR8Ret);
        searchPattern(f, 0x4000, 0x105CF20, "mov [rdi],rax;ret", movRdiRaxRet);
        searchPattern(f, 0x4000, 0x105CF20, "mov rdi,rax;rep movsd;ret", movRdiRaxRepMovsdRet);
        searchPattern(f, 0x4000, 0x105CF20, "restore rsp", restoreRsp);
    }
    
    static void searchPattern(byte[] f, long baseOff, long size, String name, byte[] pattern) {
        int maxOff = (int) (baseOff + size - pattern.length);
        int count = 0;
        for (int i = (int) baseOff; i <= maxOff && count < 5; i++) {
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