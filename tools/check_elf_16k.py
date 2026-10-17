#!/usr/bin/env python3
"""Check ELF LOAD segment alignment for Android 15+ 16KB page requirement.

Usage: check_elf_16k.py <file.so>
Exit 0 if all LOAD segments are 16KB-aligned, 1 otherwise.
"""
import struct
import sys

def main():
    path = sys.argv[1]
    with open(path, "rb") as f:
        e_ident = f.read(16)
        assert e_ident[:4] == b"\x7fELF", "not an ELF"
        is64 = e_ident[4] == 2
        le = e_ident[5] == 1
        endian = "<" if le else ">"
        f.seek(16)
        if is64:
            _, _, _, _, phoff, _, _, _, phentsize, phnum, _, _, _ = struct.unpack(endian + "HHIQQQIHHHHHH", f.read(48))
        else:
            _, _, _, phoff, _, _, _, phentsize, phnum, _, _, _ = struct.unpack(endian + "HHIIIIIHHHHHH", f.read(36))
        ok = True
        for i in range(phnum):
            f.seek(phoff + i * phentsize)
            if is64:
                p_type, _, p_offset, p_vaddr, _, p_filesz, _, _, p_align = struct.unpack(endian + "IIQQQQQQQ", f.read(56))
            else:
                p_type, p_offset, p_vaddr, _, p_filesz, _, _, p_align = struct.unpack(endian + "IIIIIIII", f.read(32))
            if p_type == 1:  # PT_LOAD
                aligned = (p_align >= 16384) and (p_vaddr % 16384 == 0) and (p_offset % 16384 == 0)
                print(f"LOAD #{i}: vaddr=0x{p_vaddr:x} off=0x{p_offset:x} align={p_align} -> {'OK' if aligned else 'NOT 16K'}")
                if not aligned:
                    ok = False
        print("PASS: 16KB aligned" if ok else "FAIL: needs 16KB-aligned proot build")
        return 0 if ok else 1

if __name__ == "__main__":
    raise SystemExit(main())
