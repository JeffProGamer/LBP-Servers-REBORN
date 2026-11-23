import cwlib.io.*;
import cwlib.util.*;
public class CheckEboot {
    public static void main(String[] args) throws Exception {
        byte[] f = FileIO.read("E:\\LBPPC-0.1.5-windows\\LBPPC\\out\\eboot.elf");
        System.out.println("Length: " + f.length);
        if (f.length >= 16) {
            String magic = new String(f, 0, 4, "US-ASCII");
            System.out.println("Magic: " + magic);
            for (int i = 0; i < 16; i++) {
                System.out.printf("%02X ", f[i]);
            }
            System.out.println();
        }
    }
}