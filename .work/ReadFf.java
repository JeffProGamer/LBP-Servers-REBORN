import cwlib.util.Compressor;
import cwlib.util.FileIO;

import java.io.ByteArrayOutputStream;
import java.util.zip.Inflater;

/**
 * Decompresses the payload of an .ff written by SerializedResource.compress()
 * (FSHb + header) so the raw RScript can be scanned for strings.
 */
public class ReadFf {
    public static void main(String[] args) throws Exception {
        String path = args.length > 0 ? args[0] : "lbpwn.ff";
        byte[] d = FileIO.read(path);
        System.out.println("size=" + d.length + " magic=" + new String(d, 0, 4, "US-ASCII"));
        for (int off = 4; off < Math.min(64, d.length); off++) {
            for (int skip = 0; skip < 4; skip++) {
                try {
                    Inflater inf = new Inflater(true);
                    inf.setInput(d, off + skip, d.length - off - skip);
                    ByteArrayOutputStream bo = new ByteArrayOutputStream();
                    byte[] buf = new byte[65536];
                    while (!inf.finished()) {
                        int n = inf.inflate(buf);
                        if (n == 0) {
                            if (inf.needsInput() || inf.needsDictionary()) break;
                        }
                        bo.write(buf, 0, n);
                    }
                    byte[] out = bo.toByteArray();
                    if (out.length > 32) {
                        System.out.println("raw-deflate OK at off=" + (off + skip) + " -> " + out.length + " bytes");
                        FileIO.write(out, ".work/lbpwn_raw.bin");
                        return;
                    }
                    inf.end();
                } catch (Throwable ignored) {
                }
            }
        }
        System.out.println("no raw-deflate stream found");
    }
}
