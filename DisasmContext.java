import cwlib.io.*;
import cwlib.util.*;

public class DisasmContext {
    public static void main(String[] args) throws Exception {
        byte[] f = FileIO.read("E:\\LBPPC-0.1.5-windows\\LBPPC\\out\\eboot.elf");
        
        // Check context around 0xAD0 references
        int[] ad0Offsets = {0x402EA, 0xE4AAA, 0x14DC45, 0x14FDD7, 0x151743, 0x15193F, 0x151963, 0x1519BE, 0x1519EE, 0x151A13};
        
        for (int off : ad0Offsets) {
            int vma = off - 0x4000;
            System.out.printf("\n=== 0xAD0 at VMA 0x%X (off 0x%X) ===\n", vma, off);
            // Print 32 bytes before and after
            int start = Math.max(0x4000, off - 32);
            int end = Math.min(f.length, off + 32);
            for (int i = start; i < end; i++) {
                if (i == off) System.out.print(">> ");
                System.out.printf("%02X ", f[i]);
                if (i == off + 3) System.out.print(" <<");
            }
            System.out.println();
            
            // Try to interpret as instruction
            // Look for common patterns: mov reg, [reg+0xAD0] or lea reg, [reg+0xAD0]
        }
        
        // Also search for "GetWorld" or "Yellowhead" strings
        System.out.println("\n=== Searching for GetWorld/Yellowhead strings ===");
        String[] searchStrings = {"GetWorld", "Yellowhead", "ThingUIDCounter", "World"};
        for (String s : searchStrings) {
            byte[] bytes = s.getBytes();
            for (int i = 0; i < f.length - bytes.length; i++) {
                boolean match = true;
                for (int j = 0; j < bytes.length; j++) {
                    if (f[i + j] != bytes[j]) { match = false; break; }
                }
                if (match) {
                    long vma = i - 0x4000;
                    if (vma >= 0 && vma < 0x105CF20) {
                        System.out.printf("  '%s' at VMA 0x%X (off 0x%X)\n", s, vma, i);
                    }
                }
            }
        }
    }
}