// ES5 (Nashorn) probe: are the a15f...ff segments encrypted with a repeating XOR key?
// Run: C:\Program Files\Java\jre1.8.0_513\bin\jjs.exe -scripting .work\xorprobe.js

var File = Java.type("java.io.RandomAccessFile");
var f = new File("a15f684e801516532279fb9148d62e16fc0710de.ff", "r");
var data = Java.type("java.lang.reflect.Array").newInstance(java.lang.Byte.TYPE, 1017371);
f.readFully(data);
f.close();

var buf = [];
for (var i = 0; i < data.length; i++) buf.push(data[i] & 0xFF);

function bytesAt(off, len) {
    var out = [];
    for (var i = 0; i < len; i++) out.push(buf[off + i]);
    return out;
}

// Known plaintext candidates for each segment start (from the class name / structure):
var trials = [];
var known = " LBPOnlinePatcher\0";
for (var s = 0; s < known.length; s++) trials.push([s, known.charCodeAt(s)]);

// Also try: header 18 = 0x02 (cwlib), and LBPOnline segment @0x18 should decode to something visible
for (var o = 0; o < 40; o++) {
    trials.push([o, 0x00]);
    trials.push([o, 0x7F]);
}

print("== byte tests at each offset 0..24 (value -> implied key if key repeats every 1/2/4/8) ==");
for (var o = 0; o < 24; o++) {
    var line = "off " + o + ": 0x" + buf[o].toString(16);
    print(line);
}

print("");
print("== does ANY repeating key of length 1..16 map the data to mostly printable text? ==");
// Score: assume payload is Fish bytecode/refs; we instead check the *string regions* we found.
// We know "$XorBlock$" appears in plaintext -> look for it encrypted.
var plain = "$XorBlock$";
var target = [];
for (var i = 0; i < plain.length; i++) target.push(plain.charCodeAt(i));
// Derive key from first 10 bytes of the file if the plaintext started there (it doesn't), so instead
// search: for each candidate key stream derived from known " LBPOnlinePatcher\0" at offset 0
var key = [];
for (var i = 0; i < 18; i++) key.push(buf[i] ^ known.charCodeAt(i));
print("derived key (offset 0..17) hex: " + key.map(function (k) { return ("0" + k.toString(16)).slice(-2); }).join(" "));

print("");
print("== try key lengths 1..16 fitted to the FIRST 18 bytes, then report printable ratio ==");
for (var klen = 1; klen <= 16; klen++) {
    var k = [];
    for (var i = 0; i < klen; i++) k.push(buf[i] ^ known.charCodeAt(i));
    var printable = 0, total = 0;
    for (var i = 18; i < 4000; i++) {
        var v = buf[i] ^ k[i % klen];
        total++;
        if (v >= 32 && v < 127) printable++;
    }
    print("  klen=" + klen + " printable=" + printable + "/" + total + " (" + (100.0 * printable / total).toFixed(1) + "%)");
}
