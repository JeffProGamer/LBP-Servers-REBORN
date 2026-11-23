import cwlib.enums.ResourceType;
import cwlib.io.streams.MemoryInputStream;
import cwlib.resources.RScript;
import cwlib.types.data.Revision;

import java.io.File;
import java.io.RandomAccessFile;

/**
 * Can the toolkit read LBPOnline's a15f...ff at all?  Prints the exact failure so the
 * answer is measured rather than assumed.
 */
public class OpenA15 {
    public static void main(String[] args) throws Exception {
        String[] paths = {
                "a15f684e801516532279fb9148d62e16fc0710de.ff",
                ".work/a15mod/config.json",
        };
        byte[] d;
        try (RandomAccessFile f = new RandomAccessFile(new File(paths[0]), "r")) {
            d = new byte[(int) f.length()];
            f.readFully(d);
        }

        // 1. as a SerializedResource (what the server / game loader path uses)
        try {
            cwlib.types.SerializedResource rs = new cwlib.types.SerializedResource(d);
            System.out.println("SerializedResource: OK -> " + rs.getResourceType() + " " + rs.getRevision());
        } catch (Throwable t) {
            System.out.println("SerializedResource: FAILS -> " + t.getMessage());
        }

        // 2. as a raw RScript body after the class-name header (what BuildSticker emits)
        int h = 0;
        while (d[h] != 0) h++;
        h++;
        System.out.println("className header = '" + new String(d, 0, h - 1, "ISO-8859-1") + "', body starts at " + h);
        for (int align = 0; align < 4; align++) {
            byte[] body = new byte[d.length - h - align + 1024];
            System.arraycopy(d, h + align, body, 0, d.length - h - align);
            try {
                MemoryInputStream ms = new MemoryInputStream(body);
                ms.setLittleEndian(true);
                RScript s = new RScript();
                s.className = "LBPOnlinePatcher";
                s.serialize(new cwlib.io.serializer.Serializer(ms, new Revision(0x272, 0x4C44, 0x17)));
                System.out.println("raw RScript align " + align + ": OK, bytecode="
                        + (s.sharedBytecode == null ? -1 : s.sharedBytecode.size()));
                return;
            } catch (Throwable t) {
                System.out.println("raw RScript align " + align + ": " + t.getClass().getSimpleName() + " " + t.getMessage());
            }
        }
    }
}
