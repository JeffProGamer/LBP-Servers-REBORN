import cwlib.io.*;
import cwlib.util.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class VerifyAddresses {
    public static void main(String[] args) throws Exception {
        byte[] f = FileIO.read("E:\\LBPPC-0.1.5-windows\\LBPPC\\out\\eboot.elf");
        ByteBuffer buf = ByteBuffer.wrap(f).order(ByteOrder.LITTLE_ENDIAN);
        
        // Parse ELF header
        buf.position(0);
        byte[] ident = new byte[16];
        buf.get(ident);
        System.out.print("Ident: ");
        for (byte b : ident) System.out.printf("%02X ", b);
        System.out.println();
        
        short type = buf.getShort();
        short machine = buf.getShort();
        int version = buf.getInt();
        long entry = buf.getLong();
        long phoff = buf.getLong();
        long shoff = buf.getLong();
        int flags = buf.getInt();
        short ehsize = buf.getShort();
        short phentsize = buf.getShort();
        short phnum = buf.getShort();
        short shentsize = buf.getShort();
        short shnum = buf.getShort();
        short shstrndx = buf.getShort();
        
        System.out.printf("Type: 0x%X, Machine: 0x%X, Version: %d\n", type, machine, version);
        System.out.printf("Entry: 0x%X\n", entry);
        System.out.printf("PH offset: 0x%X, size: %d, count: %d\n", phoff, phentsize, phnum);
        System.out.printf("SH offset: 0x%X, size: %d, count: %d\n", shoff, shentsize, shnum);
        
        // Read raw bytes at PH offset
        if (phoff < f.length && phnum > 0) {
            buf.position((int) phoff);
            for (int i = 0; i < phnum; i++) {
                int ptype = buf.getInt();
                int pflags = buf.getInt();
                long offset = buf.getLong();
                long vaddr = buf.getLong();
                long paddr = buf.getLong();
                long filesz = buf.getLong();
                long memsz = buf.getLong();
                long align = buf.getLong();
                System.out.printf("PH[%d]: type=%d vaddr=0x%X offset=0x%X filesz=0x%X memsz=0x%X flags=%X\n", 
                    i, ptype, vaddr, offset, filesz, memsz, pflags);
            }
        } else {
            System.out.println("No program headers or invalid offset");
            // Check section headers
            if (shoff < f.length && shnum > 0) {
                buf.position((int) shoff);
                for (int i = 0; i < shnum; i++) {
                    int name = buf.getInt();
                    int stype = buf.getInt();
                    long sflags = buf.getLong();
                    long addr = buf.getLong();
                    long offset = buf.getLong();
                    long size = buf.getLong();
                    int link = buf.getInt();
                    int info = buf.getInt();
                    long addralign = buf.getLong();
                    long entsize = buf.getLong();
                    System.out.printf("SH[%d]: name=%d type=%d addr=0x%X offset=0x%X size=0x%X flags=%X\n", 
                        i, name, stype, addr, offset, size, sflags);
                }
            }
        }
    }
}