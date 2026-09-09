"""Regenerate embedded SPIR-V shader arrays in strata_sdl3_wrapper.h.

Usage: python tools/regen_shaders.py
Reads src/shaders/voxel.vert.spv and voxel.frag.spv (compile first with glslc),
replaces the static const uint32_t arrays in the C wrapper header, and rewrites
the *_uint32.txt / *_hex.txt documentation dumps.
"""
import re
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SHADERS = ROOT / "src" / "shaders"
HEADER = ROOT / "src" / "nativeInterop" / "cinterop" / "strata_sdl3_wrapper.h"


def spv_words(data: bytes) -> list[int]:
    assert len(data) % 4 == 0, "SPIR-V size must be a multiple of 4"
    return list(struct.unpack(f"<{len(data) // 4}I", data))


def c_array(name: str, data: bytes) -> str:
    words = spv_words(data)
    lines = []
    for i in range(0, len(words), 4):
        lines.append("    " + ",".join(f"0x{w:08x}" for w in words[i : i + 4]) + ",")
    return f"static const uint32_t {name}[] = {{\n" + "\n".join(lines) + "\n};"


def replace_array(header: str, name: str, data: bytes) -> str:
    new = c_array(name, data)
    pattern = re.compile(
        r"static const uint32_t " + re.escape(name) + r"\[\] = \{[^}]*\};", re.DOTALL
    )
    match = pattern.search(header)
    assert match, f"array {name} not found in header"
    return header[: match.start()] + new + header[match.end():]


def byte_hex(data: bytes) -> str:
    words = [f"0x{b:02x}" for b in data]
    return "\n".join(",".join(words[i : i + 16]) for i in range(0, len(words), 16))


def main() -> None:
    vert = (SHADERS / "voxel.vert.spv").read_bytes()
    frag = (SHADERS / "voxel.frag.spv").read_bytes()

    header = HEADER.read_text(encoding="utf-8", errors="replace")
    header = replace_array(header, "voxel_vert_spv", vert)
    header = replace_array(header, "voxel_frag_spv", frag)
    HEADER.write_text(header, encoding="utf-8")

    for name, data, fn in [
        ("voxel_vert_spv", vert, SHADERS / "voxel_vert_uint32.txt"),
        ("voxel_frag_spv", frag, SHADERS / "voxel_frag_uint32.txt"),
        (None, vert, SHADERS / "voxel_vert_hex.txt"),
        (None, frag, SHADERS / "voxel_frag_hex.txt"),
    ]:
        if fn.name.endswith("_hex.txt"):
            fn.write_text(byte_hex(data))
        else:
            fn.write_text(c_array(name, data))

    print(f"Header updated: vert {len(vert)//4} words, frag {len(frag)//4} words")


if __name__ == "__main__":
    main()
