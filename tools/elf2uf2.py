#!/usr/bin/env python3
"""Minimal ELF->UF2 converter for Raspberry Pi RP2040/RP2350 flash images.

Produces standard UF2: every block is exactly 512 bytes
(32-byte header + 476-byte payload area + 4-byte end magic).
"""
import struct
import sys

UF2_MAGIC_START0 = 0x0A324655
UF2_MAGIC_START1 = 0x9E5D5157
UF2_MAGIC_END = 0x0AB16F30
UF2_FLAG_FAMILY_ID = 0x00002000

UF2_BLOCK_SIZE = 512          # total on-disk size of one block
UF2_PAYLOAD_SIZE = 256        # payload bytes used per block
UF2_DATA_FIELD = 476          # max data field allowed by the spec
RP2040_FAMILY_ID = 0xE48BFF56


def read_elf_segments(path):
    with open(path, "rb") as f:
        data = f.read()

    if data[:4] != b"\x7fELF":
        raise ValueError("not an ELF file")

    ei_class = data[4]
    if ei_class != 1:
        raise ValueError("only ELF32 is supported")

    e_phoff = struct.unpack_from("<I", data, 28)[0]
    e_phentsize = struct.unpack_from("<H", data, 42)[0]
    e_phnum = struct.unpack_from("<H", data, 44)[0]

    segments = []
    for i in range(e_phnum):
        off = e_phoff + i * e_phentsize
        p_type, p_offset, p_vaddr, p_paddr, p_filesz, p_memsz, p_flags, p_align = \
            struct.unpack_from("<IIIIIIII", data, off)
        if p_type == 1 and p_filesz > 0 and 0x10000000 <= p_paddr < 0x11000000:
            segments.append((p_paddr, data[p_offset:p_offset + p_filesz]))

    if not segments:
        raise ValueError("no loadable flash segments found")
    segments.sort(key=lambda x: x[0])
    return segments


def write_uf2(segments, out_path):
    start = segments[0][0]
    end = max(addr + len(buf) for addr, buf in segments)
    start &= ~(UF2_PAYLOAD_SIZE - 1)
    total_blocks = (end - start + UF2_PAYLOAD_SIZE - 1) // UF2_PAYLOAD_SIZE

    with open(out_path, "wb") as out:
        for block_no in range(total_blocks):
            target = start + block_no * UF2_PAYLOAD_SIZE
            payload = bytearray([0xFF] * UF2_PAYLOAD_SIZE)
            for addr, buf in segments:
                seg_end = addr + len(buf)
                overlap_start = max(target, addr)
                overlap_end = min(target + UF2_PAYLOAD_SIZE, seg_end)
                if overlap_start < overlap_end:
                    dst = overlap_start - target
                    src = overlap_start - addr
                    n = overlap_end - overlap_start
                    payload[dst:dst + n] = buf[src:src + n]

            # 32-byte header + 256-byte payload + 220-byte zero padding + 4-byte end magic = 512.
            header = struct.pack(
                "<IIIIIIII",
                UF2_MAGIC_START0,
                UF2_MAGIC_START1,
                UF2_FLAG_FAMILY_ID,
                target,
                UF2_PAYLOAD_SIZE,
                block_no,
                total_blocks,
                RP2040_FAMILY_ID,
            )
            out.write(header)
            out.write(payload)
            out.write(b"\x00" * (UF2_BLOCK_SIZE - 32 - UF2_PAYLOAD_SIZE - 4))
            out.write(struct.pack("<I", UF2_MAGIC_END))


def main():
    if len(sys.argv) != 3:
        print("usage: elf2uf2.py input.elf output.uf2", file=sys.stderr)
        return 2
    segments = read_elf_segments(sys.argv[1])
    write_uf2(segments, sys.argv[2])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
