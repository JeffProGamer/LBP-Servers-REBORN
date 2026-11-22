import cwlib.resources.RScript;
import cwlib.types.SerializedResource;
import cwlib.enums.ResourceType;
import cwlib.util.FileIO;
import cwlib.structs.script.*;
import java.util.*;

public class DumpScript {
    public static void main(String[] args) throws Exception {
        String path = args.length > 0 ? args[0] : "a15f684e801516532279fb9148d62e16fc0710de.ff";
        byte[] ff = FileIO.read(path);
        SerializedResource rs = new SerializedResource(ff);
        System.out.println("resourceType: " + rs.getResourceType());
        if (rs.getResourceType() != ResourceType.SCRIPT) {
            System.out.println("Not a script resource.");
            return;
        }
        RScript s = (RScript) rs.loadResource(RScript.class);
        System.out.println("className: " + s.className);
        System.out.println("stringATable size: " + (s.stringATable != null ? s.stringATable.size() : 0));
        System.out.println("stringWTable size: " + (s.stringWTable != null ? s.stringWTable.size() : 0));
        System.out.println("sharedBytecode size: " + (s.sharedBytecode != null ? s.sharedBytecode.size() : 0));
        System.out.println("fieldReferences size: " + (s.fieldReferences != null ? s.fieldReferences.size() : 0));
        System.out.println("functionReferences size: " + (s.functionReferences != null ? s.functionReferences.size() : 0));
        System.out.println("typeReferences size: " + (s.typeReferences != null ? s.typeReferences.size() : 0));
        System.out.println("=== STRING A TABLE ===");
        if (s.stringATable != null) {
            for (int i = 0; i < s.stringATable.size(); i++) {
                String str = s.stringATable.get(i);
                System.out.println("  [" + i + "] " + str);
            }
        }
        System.out.println("=== FUNCTION REFERENCES ===");
        if (s.functionReferences != null) {
            for (int i = 0; i < s.functionReferences.size(); i++) {
                FunctionReferenceRow fr = s.functionReferences.get(i);
                String name = fr.nameStringIdx >= 0 && s.stringATable != null ? s.stringATable.get(fr.nameStringIdx) : "?";
                System.out.println("  [" + i + "] nameIdx=" + fr.nameStringIdx + " name=" + name + " typeRef=" + fr.typeReferenceIdx);
            }
        }
        System.out.println("=== BYTECODE (first 60 words) ===");
        if (s.sharedBytecode != null) {
            int lim = Math.min(60, s.sharedBytecode.size());
            for (int i = 0; i < lim; i++) {
                Object ins = s.sharedBytecode.get(i);
                System.out.println("  [" + i + "] " + ins.getClass().getSimpleName() + " " + ins);
            }
        }
    }
}
