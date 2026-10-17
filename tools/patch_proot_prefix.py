#!/usr/bin/env python3
"""Replace hardcoded Termux prefix with a same-length placeholder.

Usage: patch_proot_prefix.py <input.so> <output.so>
"""
import sys

OLD = b"/data/data/com.termux/files/usr"
# Same length (31 bytes): placeholder never used because app passes -r/-b.
NEW = b"/data/data/neurabuild.placehold"

assert len(OLD) == len(NEW), (len(OLD), len(NEW))

def main():
    if len(sys.argv) != 3:
        print(__doc__)
        return 1
    src, dst = sys.argv[1], sys.argv[2]
    data = open(src, "rb").read()
    n = data.count(OLD)
    if n == 0:
        print("OLD prefix not found; binary may already be patched or is not Termux proot.")
        return 2
    data = data.replace(OLD, NEW)
    open(dst, "wb").write(data)
    print(f"patched {n} occurrence(s): {src} -> {dst}")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
