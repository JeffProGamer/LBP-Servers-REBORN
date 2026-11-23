#!/usr/bin/env python3
"""
Parse a decrypted PS4 eboot.bin ELF64 file:
1. Parse PT_LOAD program headers (p_offset, p_vaddr, p_filesz, p_memsz, p_flags)
2. Search for null-terminated strings matching target domains/protocols
3. Convert file offsets to ELF virtual addresses (RVA)
4. Scan RW segment for GOT entries pointing to sceHttp functions
5. Print a clean table
"""

import struct
import sys
import os

ELF_MAGIC = b'\x7fELF'

# Target strings to search for
TARGET_STRINGS = [
    b'littlebigplanet.com',
    b'lbp3.online',
    b'scee.com',
    b'https://',
    b'http://',
    b'CustomServerDigest',
    b'gs-sec.ww.np.dl.playstation.net',
    b'static-resource.np.community.playstation.net',
    b'playstation.net',
    b'np.community',
]

# sceHttp function addresses (from ELF analysis)
SCEHTTP_FUNCTIONS = {
    0xEF3E18: 'sceHttpCreateConnection',
    0xEF3E63: 'sceHttpCreateRequest',
    0xEF3EFA: 'sceHttpSendRequest',
    0xEF3FA6: 'sceHttpAddCookie',
}

def read_elf_header(data):
    if len(data) < 64:
        print("File too small to be an ELF")
        sys.exit(1)

    magic = data[0:4]
    if magic != ELF_MAGIC:
        print(f"Not an ELF file. Magic: {magic!r} (expected {ELF_MAGIC!r})")
        sys.exit(1)

    ei_class = data[4]  # 1=32-bit, 2=64-bit
    if ei_class != 2:
        print(f"Only ELF64 supported, got class {ei_class}")
        sys.exit(1)

    ei_data = data[5]  # 1=little-endian, 2=big-endian
    endian = '<' if ei_data == 1 else '>'
    print(f"ELF64, {'little-endian' if ei_data == 1 else 'big-endian'}")

    # e_phoff: program header offset (8 bytes)
    e_phoff = struct.unpack_from(endian + 'Q', data, 0x20)[0]
    # e_phentsize: program header entry size (2 bytes)
    e_phentsize = struct.unpack_from(endian + 'H', data, 0x36)[0]
    # e_phnum: number of program headers (2 bytes)
    e_phnum = struct.unpack_from(endian + 'H', data, 0x38)[0]

    print(f"Program headers: offset=0x{e_phoff:X}, entry_size={e_phentsize}, count={e_phnum}")
    return e_phoff, e_phentsize, e_phnum, endian

def parse_pt_load(data, e_phoff, e_phentsize, e_phnum, endian):
    segments = []
    for i in range(e_phnum):
        off = e_phoff + i * e_phentsize
        # p_type (4 bytes), p_flags (4 bytes), p_offset (8), p_vaddr (8), p_paddr (8), p_filesz (8), p_memsz (8), p_align (8)
        p_type = struct.unpack_from(endian + 'I', data, off)[0]
        p_flags = struct.unpack_from(endian + 'I', data, off + 4)[0]
        p_offset = struct.unpack_from(endian + 'Q', data, off + 8)[0]
        p_vaddr = struct.unpack_from(endian + 'Q', data, off + 16)[0]
        p_paddr = struct.unpack_from(endian + 'Q', data, off + 24)[0]
        p_filesz = struct.unpack_from(endian + 'Q', data, off + 32)[0]
        p_memsz = struct.unpack_from(endian + 'Q', data, off + 40)[0]
        p_align = struct.unpack_from(endian + 'Q', data, off + 48)[0]

        if p_type == 1:  # PT_LOAD
            perm = ''
            perm += 'R' if p_flags & 4 else ''
            perm += 'W' if p_flags & 2 else ''
            perm += 'X' if p_flags & 1 else ''
            segments.append({
                'offset': p_offset,
                'vaddr': p_vaddr,
                'filesz': p_filesz,
                'memsz': p_memsz,
                'perm': perm,
            })
            print(f"PT_LOAD[{len(segments)-1}]: offset=0x{p_offset:X} vaddr=0x{p_vaddr:X} filesz=0x{p_filesz:X} memsz=0x{p_memsz:X} flags={perm}")
    return segments

def file_offset_to_vaddr(segments, file_offset):
    for seg in segments:
        if seg['offset'] <= file_offset < seg['offset'] + seg['filesz']:
            delta = file_offset - seg['offset']
            return seg['vaddr'] + delta, seg['perm']
    return None, None

def scan_got_entries(data, segments):
    """Scan RW segment for 8-byte pointers matching known sceHttp function addresses"""
    # Find RW segment
    rw_segment = None
    for seg in segments:
        if seg['perm'] == 'RW':
            rw_segment = seg
            break
    
    if not rw_segment:
        print("No RW segment found for GOT scanning")
        return
    
    print(f"\n{'=' * 80}")
    print("GOT ENTRY SCAN (RW segment)")
    print(f"{'=' * 80}")
    print(f"RW segment: vaddr=0x{rw_segment['vaddr']:X} offset=0x{rw_segment['offset']:X} filesz=0x{rw_segment['filesz']:X}")
    print(f"{'File Offset':<16} {'Virtual Address':<20} {'Points To':<20} {'Function'}")
    print("-" * 80)
    
    # Read RW segment data
    rw_start = rw_segment['offset']
    rw_end = rw_start + rw_segment['filesz']
    rw_data = data[rw_start:rw_end]
    
    found_any = False
    # Scan 8-byte aligned pointers
    for i in range(0, len(rw_data) - 7, 8):
        ptr = struct.unpack_from('<Q', rw_data, i)[0]
        if ptr in SCEHTTP_FUNCTIONS:
            file_off = rw_start + i
            vaddr = rw_segment['vaddr'] + i
            func_name = SCEHTTP_FUNCTIONS[ptr]
            found_any = True
            print(f"0x{file_off:08X}    0x{vaddr:016X}     0x{ptr:016X}     {func_name}")
    
    if not found_any:
        print("(No GOT entries found pointing to known sceHttp functions)")
    
    # Also scan for any pointers into the .text segment (RX)
    print(f"\n{'=' * 80}")
    print("ALL POINTERS IN RW SEGMENT POINTING TO RX SEGMENT")
    print(f"{'=' * 80}")
    
    # Find RX segment(s)
    rx_segments = [s for s in segments if 'X' in s['perm']]
    if not rx_segments:
        print("No RX segments found")
        return
    
    print(f"{'File Offset':<16} {'Virtual Address':<20} {'Points To':<20} {'In Segment'}")
    print("-" * 80)
    
    for i in range(0, len(rw_data) - 7, 8):
        ptr = struct.unpack_from('<Q', rw_data, i)[0]
        for rx in rx_segments:
            if rx['vaddr'] <= ptr < rx['vaddr'] + rx['memsz']:
                file_off = rw_start + i
                vaddr = rw_segment['vaddr'] + i
                print(f"0x{file_off:08X}    0x{vaddr:016X}     0x{ptr:016X}     RX@{rx['vaddr']:X}")
                break

def extract_strings(data, segments):
    """Find null-terminated ASCII strings of length >= 4"""
    strings = []
    i = 0
    n = len(data)
    while i < n:
        # Skip non-printable
        if data[i] < 0x20 or data[i] > 0x7E:
            i += 1
            continue
        start = i
        while i < n and 0x20 <= data[i] <= 0x7E:
            i += 1
        if i - start >= 4:
            s = data[start:i].decode('ascii', errors='replace')
            strings.append((start, s))
    return strings

def main():
    if len(sys.argv) < 2:
        print("Usage: python parse_eboot.py <eboot.bin>")
        sys.exit(1)

    path = sys.argv[1]
    if not os.path.exists(path):
        print(f"File not found: {path}")
        sys.exit(1)

    with open(path, 'rb') as f:
        data = f.read()

    e_phoff, e_phentsize, e_phnum, endian = read_elf_header(data)
    segments = parse_pt_load(data, e_phoff, e_phentsize, e_phnum, endian)

    print("\n" + "=" * 100)
    print("STRING SEARCH RESULTS")
    print("=" * 100)
    print(f"{'String':<45} {'File Offset':<16} {'Virtual Address':<20} {'Perm'}")
    print("-" * 100)

    strings = extract_strings(data, segments)
    found_any = False

    for offset, s in strings:
        lower = s.lower()
        match = False
        for target in TARGET_STRINGS:
            if target.decode('ascii', errors='replace').lower() in lower:
                match = True
                break
        if not match:
            continue

        vaddr, perm = file_offset_to_vaddr(segments, offset)
        if vaddr is not None:
            found_any = True
            print(f"{s:<45} 0x{offset:08X}    0x{vaddr:016X}     {perm}")
        else:
            print(f"{s:<45} 0x{offset:08X}    (not in PT_LOAD segment)     --")

    if not found_any:
        print("(No matching strings found in PT_LOAD segments)")

    # Scan RW segment for GOT entries
    scan_got_entries(data, segments)

    print("\nDone.")

if __name__ == '__main__':
    main()
