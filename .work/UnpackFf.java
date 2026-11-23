import cwlib.types.SerializedResource;
import cwlib.types.data.SHA1;
import cwlib.util.FileIO;

import java.io.File;

/**
 * Decompresses an .ff so it can be inspected / edited with the toolkit.
 * Usage: java -cp "toolkit-0.1.jar;." .work/UnpackFf.java <in.ff> <out.bin>
 */
public class UnpackFf {
    public static void main(String[] args) throws Exception {
        String in = args.length > 0 ? args[0] : "a15f684e801516532279fb9148d62e16fc0710de.ff";
        String out = args.length > 1 ? args[1] : ".work/a15_uncompressed.bin";

        byte[] raw = FileIO.read(in);
        System.out.println("in size  = " + raw.length);
        System.out.println("sha1     = " + SHA1.fromBuffer(raw));

        SerializedResource res = new SerializedResource(new File(in).getAbsolutePath());
        System.out.println("revision = " + res.getRevision());
        System.out.println("compression flag byte = 0x" + Integer.toHexString(res.getCompressionFlags() & 0xFF));
        System.out.println("resource type = " + res.getResourceType());
        System.out.println("serialization type = " + res.getSerializationType());
        System.out.println("dependencies = " + res.getDependencies().length);

        byte[] buf = res.getStream().getBuffer();
        FileIO.write(buf, out);
        System.out.println("out size = " + buf.length + " -> " + out);
    }
}
