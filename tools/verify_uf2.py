import struct, hashlib

import sys
path = sys.argv[1] if len(sys.argv) > 1 else 'firmware/pico_ble_hid_keyboard.uf2'
data = open(path, 'rb').read()
print("file size: %d bytes" % len(data))

assert len(data) % 512 == 0, "not a multiple of 512"
nblocks = len(data) // 512
print("total blocks: %d" % nblocks)

MAGIC0 = 0x0A324655
MAGIC1 = 0x9E5D5157
MAGIC_END = 0x0AB16F30

ok = 0
last_payload = None
targets = []
for i in range(nblocks):
    b = data[i*512:(i+1)*512]
    m0, m1 = struct.unpack_from('<II', b, 0)
    flags, addr, plen, seq = struct.unpack_from('<IIII', b, 8)
    m_end = struct.unpack_from('<I', b, 508)[0]
    assert m0 == MAGIC0 and m1 == MAGIC1 and m_end == MAGIC_END, "bad magic at block %d" % i
    assert seq == i, "bad sequence at block %d" % i
    assert plen <= 476, "bad payload len at block %d" % i
    ok += 1
    last_payload = plen
    targets.append((addr, plen))

print("magic/sequence OK for all %d blocks" % ok)
print("last block payload size: %d bytes (0x%X)" % (last_payload, last_payload))
final = data[(nblocks-1)*512:]
totalsize = struct.unpack_from('<I', final, 20)[0]
print("final block totalSize field: %d" % totalsize)

print("first block addr: 0x%08X" % targets[0][0])
print("last block addr:   0x%08X" % targets[-1][0])
total_payload = sum(p for _, p in targets)
print("total payload bytes: %d" % total_payload)
print("SHA256: %s" % hashlib.sha256(data).hexdigest())