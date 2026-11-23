import cwlib.io.*;
import cwlib.util.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class SearchELF2 {
    public static void main(String[] args) throws Exception {
        byte[] f = FileIO.read("E:\\LBPPC-0.1.5-windows\\LBPPC\\out\\eboot.elf");
        
        // VMA to file offset mapping
        java.util.function.LongFunction<Integer> vmaToOffset = (vma) -> {
            if (vma >= 0x0L && vma < 0x105CF20L) {
                return (int) (vma - 0x0L + 0x4000L);
            }
            if (vma >= 0x1060000L && vma < 0x1060000L + 0x6DCA8L) {
                return (int) (vma - 0x1060000L + 0x1064000L);
            }
            return -1;
        };
        
        // Check format string addresses
        System.out.println("=== Format string addresses (NOT patched in current sticker) ===");
        long[] fmtVMAs = {
            0x00E79ED9L,  // https://res%c.%s/r/%s
            0x00E79EEFL,  // http://%s/r/%s
            0x00E91997L,  // http://lbp.me/v/%s
            0x00E919AAL,  // http://beta.lbp.me/v/%s
            0x00ED7DB2L,  // https://auth.lbp.me/auth/signin?r=http://lbp.me/
        };
        String[] fmtDesc = {
            "https://res%c.%s/r/%s",
            "http://%s/r/%s",
            "http://lbp.me/v/%s",
            "http://beta.lbp.me/v/%s",
            "https://auth.lbp.me/auth/signin?r=http://lbp.me/"
        };
        
        for (int i = 0; i < fmtVMAs.length; i++) {
            int off = vmaToOffset.apply(fmtVMAs[i]);
            if (off >= 0 && off + 80 < f.length) {
                StringBuilder sb = new StringBuilder();
                for (int j = 0; j < 80 && off + j < f.length; j++) {
                    byte b = f[off + j];
                    if (b == 0) break;
                    if (b >= 32 && b < 127) sb.append((char)b);
                    else sb.append('.');
                }
                System.out.printf("VMA 0x%X: %s\n", fmtVMAs[i], sb.toString());
                System.out.printf("  Expected: %s\n", fmtDesc[i]);
            } else {
                System.out.printf("VMA 0x%X -> invalid offset\n", fmtVMAs[i]);
            }
        }
        
        // Search for references to 0x0148A4F0 in code (as 32-bit or 64-bit immediate)
        System.out.println("\n=== Searching for references to 0x0148A4F0 in code ===");
        // As 32-bit little-endian: F0 A4 48 01
        byte[] leak32 = new byte[] {(byte)0xF0, (byte)0xA4, (byte)0x48, (byte)0x01};
        // As 64-bit: F0 A4 48 01 00 00 00 00
        byte[] leak64 = new byte[] {(byte)0xF0, (byte)0xA4, (byte)0x48, (byte)0x01, 0, 0, 0, 0};
        
        searchPattern(f, 0x4000, 0x105CF20, "leak32", leak32);
        searchPattern(f, 0x4000, 0x105CF20, "leak64", leak64);
        
        // Search for World+0xAD0 access pattern - look for 0xAD0 constant
        System.out.println("\n=== Searching for 0xAD0 constant ===");
        byte[] ad0 = new byte[] {(byte)0xD0, (byte)0x0A, 0, 0}; // 32-bit little-endian
        searchPattern(f, 0x4000, 0x105CF20, "0xAD0", ad0);
        
        // Search for mov eax, [0x017C6BC8] pattern - 8B 05 (rip-rel) or A1 (absolute)
        System.out.println("\n=== Searching for mov eax,[0x017C6BC8] pattern ===");
        // A1 xx xx xx xx = mov eax, [absolute_addr]
        // 8B 05 xx xx xx xx = mov eax, [rip+offset]
        // The address 0x017C6BC8 in little-endian: C8 6B 7C 01
        byte[] movEaxAddr = new byte[] {(byte)0xC8, (byte)0x6B, (byte)0x7C, (byte)0x01};
        searchPattern(f, 0x4000, 0x105CF20, "mov eax,[0x017C6BC8]", movEaxAddr);
        
        // Also search for the GOT addresses as 32-bit values in code
        System.out.println("\n=== Searching for GOT addresses as immediates in code ===");
        long[] gotAddrs = {
            0x014CCEE8L, 0x014CCF10L, 0x014CCF60L, 
            0x014CCE80L, 0x014CCF08L, 0x014CCD20L
        };
        String[] gotNames = {
            "sceHttpsSetSslCallback", "sceHttpCreateConnection", "sceHttpDeleteConnection",
            "sceHttpSetCookieEnabled", "sceHttpAddRequestHeader", "sceFiosDHCloseSync"
        };
        
        for (int i = 0; i < gotAddrs.length; i++) {
            byte[] addr32 = new byte[] {
                (byte)(gotAddrs[i] & 0xFF),
                (byte)((gotAddrs[i] >> 8) & 0xFF),
                (byte)((gotAddrs[i] >> 16) & 0xFF),
                (byte)((gotAddrs[i] >> 24) & 0xFF)
            };
            searchPattern(f, 0x4000, 0x105CF20, gotNames[i] + " (32-bit)", addr32);
        }
        
        // Search for data addresses
        System.out.println("\n=== Searching for data addresses as immediates in code ===");
        long[] dataAddrs = {0x01590E68L, 0x01590EF0L, 0x017C6BC8L};
        String[] dataNames = {"DATA_HTTP_FLAG", "DATA_TMPL_ID", "DATA_CONN_ID"};
        
        for (int i = 0; i < dataAddrs.length; i++) {
            byte[] addr32 = new byte[] {
                (byte)(dataAddrs[i] & 0xFF),
                (byte)((dataAddrs[i] >> 8) & 0xFF),
                (byte)((dataAddrs[i] >> 16) & 0xFF),
                (byte)((dataAddrs[i] >> 24) & 0xFF)
            };
            searchPattern(f, 0x4000, 0x105CF20, dataNames[i] + " (32-bit)", addr32);
        }
        
        // Search for FN_HTTP_INIT references
        System.out.println("\n=== Searching for calls to FN_HTTP_INIT (0x00421E80) ===");
        byte[] fnHttpInit = new byte[] {(byte)0x80, (byte)0x1E, (byte)0x42, 0x00}; // 32-bit
        searchPattern(f, 0x4000, 0x105CF20, "FN_HTTP_INIT", fnHttpInit);
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