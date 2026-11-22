import cwlib.enums.*;
import cwlib.resources.*;
import cwlib.structs.script.*;
import cwlib.structs.things.*;
import cwlib.structs.things.parts.*;
import cwlib.types.SerializedResource;
import cwlib.types.data.*;
import cwlib.util.FileIO;

import java.lang.reflect.Field;
import java.util.Arrays;

/**
 * Round-trips the generated .ff/.plan through toolkit-0.1.jar.
 * Exits 1 if anything is structurally wrong, so build.cmd / CI can gate on it.
 */
public class VerifyFinal {
    static int failures = 0;

    static void check(boolean ok, String what) {
        System.out.println("  [" + (ok ? " ok " : "FAIL") + "] " + what);
        if (!ok) failures++;
    }

    public static void main(String[] a) throws Exception {
        byte[] ff = FileIO.read("lbpwn.ff");
        SerializedResource rs = new SerializedResource(ff);
        Revision r = rs.getRevision();
        System.out.println("lbpwn.ff");
        System.out.println("  size       : " + ff.length + " B");
        check(new String(ff, 0, 4, "US-ASCII").equals("FSHb"), "magic is FSHb");
        check(r.getHead() == 0x0272, "head 0x0272 (PS4 1.28)");
        check((r.getBranchID() & 0xFFFF) == 0x4C44, "branch 0x4C44");
        check((r.getBranchRevision() & 0xFFFF) == 0x0017, "branch revision 0x0017");
        check(rs.getResourceType() == ResourceType.SCRIPT, "resource type is SCRIPT");

        RScript s = (RScript) rs.loadResource(RScript.class);
        check(s.className != null && !s.className.isEmpty(), "className present");
        check(s.sharedBytecode != null && s.sharedBytecode.size() > 0, "bytecode non-empty");
        check(s.stringATable != null && !s.stringATable.isEmpty(), "string table populated");
        System.out.println("  bytecode   : " + s.sharedBytecode.size() + " words");
        java.util.Map<String, Integer> ops = new java.util.TreeMap<>();
        for (Object o : s.sharedBytecode) ops.merge(o.getClass().getSimpleName(), 1, Integer::sum);
        System.out.println("  op mix     : " + ops);

        byte[] plan = FileIO.read("lbp_reborn_sticker.plan");
        SerializedResource rp = new SerializedResource(plan);
        System.out.println();
        System.out.println("lbp_reborn_sticker.plan");
        System.out.println("  size       : " + plan.length + " B");
        check(rp.getRevision().getHead() == 0x0272, "head 0x0272 (PS4 1.28)");
        check((rp.getRevision().getBranchID() & 0xFFFF) == 0x4C44, "branch 0x4C44");
        check(rp.getResourceType() == ResourceType.PLAN, "resource type is PLAN");

        RPlan p = (RPlan) rp.loadResource(RPlan.class);
        Thing[] things = p.getThings();
        check(things != null && things.length == 1, "exactly one Thing");

        boolean foundScript = false;
        Field f = Thing.class.getDeclaredField("parts");
        f.setAccessible(true);
        for (Thing t : things) {
            for (Object part : (Object[]) f.get(t)) {
                if (part instanceof PScript) {
                    ResourceDescriptor d = ((PScript) part).instance.script;
                    foundScript = true;
                    System.out.println("  PScript    : " + d);
                    check(d.isValid(), "PScript descriptor valid");
                    check(cwlib.util.Crypto.SHA1(ff).equals(d.getSHA1()), "plan points at the .ff we just built");
                }
            }
        }
        check(foundScript, "Thing carries a PScript component");

        byte[] again = SerializedResource.compress(p.build(new Revision(0x272, 0x4C44, 0x17), (byte) 0));
        check(Arrays.equals(plan, again), "plan re-serializes byte-identically");

        System.out.println();
        if (failures > 0) {
            System.out.println("VERIFY FAILED: " + failures + " check(s)");
            System.exit(1);
        }
        System.out.println("VERIFY OK");
    }
}
