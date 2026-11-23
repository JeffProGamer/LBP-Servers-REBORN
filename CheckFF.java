import cwlib.types.*;
import cwlib.util.*;
public class CheckFF {
    public static void main(String[] args) throws Exception {
        byte[] ff = FileIO.read("a15f684e801516532279fb9148d62e16fc0710de.mod");
        SerializedResource rs = new SerializedResource(ff);
        System.out.println("Resource type: " + rs.getResourceType());
        System.out.println("Revision: " + rs.getRevision());
    }
}