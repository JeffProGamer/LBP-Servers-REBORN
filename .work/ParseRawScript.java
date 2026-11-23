import cwlib.enums.ResourceType;
import cwlib.io.streams.MemoryInputStream;
import cwlib.resources.RScript;
import cwlib.structs.script.FunctionReferenceRow;
import cwlib.types.data.Revision;
import cwlib.types.data.SHA1;
import cwlib.util.FileIO;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses an uncompressed RScript (.ff with only the class-name header).
 * Usage: java -cp "toolkit-0.1.jar;." .work/ParseRawScript.java <file.ff>
 */
public class ParseRawScript {
    public static void main(String[] args) throws Exception {
        String path = args.length > 0 ? args[0] : "a15f684e801516532279fb9148d62e16fc0710de.ff";
        byte[] data = FileIO.read(path);
        System.out.println("size = " + data.length + " sha1=" + SHA1.fromBuffer(data));

        int headerEnd = 0;
        while (headerEnd < data.length && data[headerEnd] != 0) headerEnd++;
        String className = new String(data, 0, headerEnd, java.nio.charset.StandardCharsets.ISO_8859_1);
        headerEnd++;
        System.out.println("className = '" + className + "' headerEnd=" + headerEnd);

        Revision rev = new Revision(0x272, 0x4C44, 0x17);
        for (int align = 0; align < 8; align++) {
            byte[] body = new byte[data.length - headerEnd - align + 64];
            System.arraycopy(data, headerEnd + align, body, 0, data.length - headerEnd - align);
            MemoryInputStream ms = new MemoryInputStream(body);
            ms.setLittleEndian(true);
            try {
                RScript s = new RScript();
                s.className = className;
                cwlib.io.serializer.Serializer ser = new cwlib.io.serializer.Serializer(ms, rev);
                s.serialize(ser);
                System.out.println("align " + align + ": PARSED.");
                System.out.println("  stringA=" + (s.stringATable == null ? -1 : s.stringATable.size())
                        + " stringW=" + (s.stringWTable == null ? -1 : s.stringWTable.size())
                        + " bytecode=" + (s.sharedBytecode == null ? -1 : s.sharedBytecode.size())
                        + " fieldRefs=" + (s.fieldReferences == null ? -1 : s.fieldReferences.size())
                        + " funcRefs=" + (s.functionReferences == null ? -1 : s.functionReferences.size())
                        + " typeRefs=" + (s.typeReferences == null ? -1 : s.typeReferences.size())
                        + " funcDefs=" + (s.functionDefinitions == null ? -1 : s.functionDefinitions.size())
                        + " consumed=" + ms.getOffset() + "/" + body.length);
                if (align == -1) return;

                System.out.println("=== STRING A (filtered, non-garbage) ===");
                List<String> interesting = new ArrayList<>();
                for (int i = 0; i < s.stringATable.size(); i++) {
                    String str = s.stringATable.get(i);
                    if (str == null) continue;
                    boolean printable = true;
                    for (char c : str.toCharArray()) if (c < 32 || c > 126) { printable = false; break; }
                    if (!printable) continue;
                    if (str.matches("[A-Za-z_][A-Za-z0-9_\\.]*(<[^>]*>)?")
                            || str.matches("[A-Za-z_][A-Za-z0-9_]*\\.([A-Za-z_][A-Za-z0-9_]*)+")
                            || str.matches("[A-Za-z0-9_\\.\\-]+\\.[A-Za-z]{2,6}")
                            || str.contains("http") || str.contains("://")) {
                        interesting.add("  [" + i + "] " + str);
                    }
                }
                for (String l : interesting) System.out.println(l);

                System.out.println("=== FUNCTION REFERENCES ===");
                for (int i = 0; i < s.functionReferences.size(); i++) {
                    FunctionReferenceRow fr = s.functionReferences.get(i);
                    String name = fr.nameStringIdx >= 0 && fr.nameStringIdx < s.stringATable.size()
                            ? s.stringATable.get(fr.nameStringIdx) : "?";
                    System.out.println("  [" + i + "] nameIdx=" + fr.nameStringIdx + " typeRef=" + fr.typeReferenceIdx + " name=" + name);
                }
                return;
            } catch (Throwable t) {
                System.out.println("align " + align + ": " + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        }
    }
}
