"""A small QR code generator (byte mode, error correction level M, versions 1-10) for the "Handy verbinden" link.

Follows ISO/IEC 18004: Reed-Solomon error correction over GF(256), block interleaving, function patterns,
the 8 data masks with the standard penalty rules, and BCH-coded format/version information.
Returns an SVG (as text or data URL), so no image library is needed.
"""
import base64

# per version 1..10 at level M: total codewords, ECC codewords per block, number of blocks
_TOTAL = [26, 44, 70, 100, 134, 172, 196, 242, 292, 346]
_ECC_PER_BLOCK = [10, 16, 26, 18, 24, 16, 18, 22, 22, 26]
_BLOCKS = [1, 1, 1, 2, 2, 4, 4, 4, 5, 5]
_ALIGNMENT = [[], [6, 18], [6, 22], [6, 26], [6, 30], [6, 34], [6, 22, 38], [6, 24, 42], [6, 26, 46], [6, 28, 50]]
_FORMAT_BITS_M = 0  # level M in the format information (L=1, M=0, Q=3, H=2)


def _gf_mul(x, y):
    z = 0
    for i in reversed(range(8)):
        z = (z << 1) ^ ((z >> 7) * 0x11D)
        z ^= ((y >> i) & 1) * x
    return z


def _rs_divisor(degree):
    result = [0] * (degree - 1) + [1]
    root = 1
    for _ in range(degree):
        for j in range(degree):
            result[j] = _gf_mul(result[j], root)
            if j + 1 < degree:
                result[j] ^= result[j + 1]
        root = _gf_mul(root, 0x02)
    return result


def _rs_remainder(data, divisor):
    result = [0] * len(divisor)
    for b in data:
        factor = b ^ result.pop(0)
        result.append(0)
        for i, coef in enumerate(divisor):
            result[i] ^= _gf_mul(coef, factor)
    return result


def _data_capacity(version):
    return _TOTAL[version - 1] - _ECC_PER_BLOCK[version - 1] * _BLOCKS[version - 1]


def _encode_data(payload, version):
    """Mode indicator, length, bytes, terminator and padding, as data codewords."""
    bits = []

    def put(value, length):
        bits.extend((value >> i) & 1 for i in reversed(range(length)))

    put(0b0100, 4)
    put(len(payload), 8 if version < 10 else 16)
    for b in payload:
        put(b, 8)
    capacity = _data_capacity(version) * 8
    put(0, min(4, capacity - len(bits)))
    put(0, (-len(bits)) % 8)
    codewords = [int("".join(map(str, bits[i:i + 8])), 2) for i in range(0, len(bits), 8)]
    pad = 0xEC
    while len(codewords) < _data_capacity(version):
        codewords.append(pad)
        pad ^= 0xEC ^ 0x11
    return codewords


def _add_ecc_and_interleave(data, version):
    blocks_count = _BLOCKS[version - 1]
    ecc_len = _ECC_PER_BLOCK[version - 1]
    total = _TOTAL[version - 1]
    short_blocks = blocks_count - total % blocks_count
    short_len = total // blocks_count
    divisor = _rs_divisor(ecc_len)
    blocks = []
    k = 0
    for i in range(blocks_count):
        size = short_len - ecc_len + (0 if i < short_blocks else 1)
        part = data[k:k + size]
        k += size
        ecc = _rs_remainder(part, divisor)
        if i < short_blocks:
            part = part + [0]  # placeholder so all blocks have the same length; skipped below
        blocks.append(part + ecc)
    result = []
    for i in range(len(blocks[0])):
        for j, block in enumerate(blocks):
            if i != short_len - ecc_len or j >= short_blocks:
                result.append(block[i])
    return result


class _Matrix:
    def __init__(self, version):
        self.version = version
        self.size = version * 4 + 17
        self.dark = [[False] * self.size for _ in range(self.size)]
        self.function = [[False] * self.size for _ in range(self.size)]

    def set_function(self, x, y, dark):
        self.dark[y][x] = dark
        self.function[y][x] = True

    def draw_function_patterns(self):
        n = self.size
        for i in range(n):
            self.set_function(6, i, i % 2 == 0)
            self.set_function(i, 6, i % 2 == 0)
        for cx, cy in ((3, 3), (n - 4, 3), (3, n - 4)):
            for dy in range(-4, 5):
                for dx in range(-4, 5):
                    x, y = cx + dx, cy + dy
                    if 0 <= x < n and 0 <= y < n:
                        dist = max(abs(dx), abs(dy))
                        self.set_function(x, y, dist not in (2, 4))
        positions = _ALIGNMENT[self.version - 1]
        last = len(positions) - 1
        for i, ax in enumerate(positions):
            for j, ay in enumerate(positions):
                if (i == 0 and j == 0) or (i == 0 and j == last) or (i == last and j == 0):
                    continue  # these corners are taken by the finder patterns
                for dy in range(-2, 3):
                    for dx in range(-2, 3):
                        self.set_function(ax + dx, ay + dy, max(abs(dx), abs(dy)) != 1)
        self.draw_format(0)  # reserve the area; the real bits come after masking
        self.draw_version()

    def draw_format(self, mask):
        data = _FORMAT_BITS_M << 3 | mask
        rem = data
        for _ in range(10):
            rem = (rem << 1) ^ ((rem >> 9) * 0x537)
        bits = (data << 10 | rem) ^ 0x5412
        bit = lambda i: (bits >> i) & 1 == 1  # noqa: E731
        n = self.size
        for i in range(0, 6):
            self.set_function(8, i, bit(i))
        self.set_function(8, 7, bit(6))
        self.set_function(8, 8, bit(7))
        self.set_function(7, 8, bit(8))
        for i in range(9, 15):
            self.set_function(14 - i, 8, bit(i))
        for i in range(0, 8):
            self.set_function(n - 1 - i, 8, bit(i))
        for i in range(8, 15):
            self.set_function(8, n - 15 + i, bit(i))
        self.set_function(8, n - 8, True)  # the dark module

    def draw_version(self):
        if self.version < 7:
            return
        rem = self.version
        for _ in range(12):
            rem = (rem << 1) ^ ((rem >> 11) * 0x1F25)
        bits = self.version << 12 | rem
        for i in range(18):
            dark = (bits >> i) & 1 == 1
            a = self.size - 11 + i % 3
            b = i // 3
            self.set_function(a, b, dark)
            self.set_function(b, a, dark)

    def draw_codewords(self, codewords):
        n = self.size
        i = 0
        total_bits = len(codewords) * 8
        right = n - 1
        while right >= 1:
            if right == 6:
                right = 5
            for vert in range(n):
                for j in range(2):
                    x = right - j
                    upward = ((right + 1) & 2) == 0
                    y = n - 1 - vert if upward else vert
                    if not self.function[y][x] and i < total_bits:
                        self.dark[y][x] = (codewords[i >> 3] >> (7 - (i & 7))) & 1 == 1
                        i += 1
            right -= 2

    def apply_mask(self, mask):
        for y in range(self.size):
            for x in range(self.size):
                if self.function[y][x]:
                    continue
                if mask == 0:
                    flip = (x + y) % 2 == 0
                elif mask == 1:
                    flip = y % 2 == 0
                elif mask == 2:
                    flip = x % 3 == 0
                elif mask == 3:
                    flip = (x + y) % 3 == 0
                elif mask == 4:
                    flip = (x // 3 + y // 2) % 2 == 0
                elif mask == 5:
                    flip = x * y % 2 + x * y % 3 == 0
                elif mask == 6:
                    flip = (x * y % 2 + x * y % 3) % 2 == 0
                else:
                    flip = ((x + y) % 2 + x * y % 3) % 2 == 0
                if flip:
                    self.dark[y][x] = not self.dark[y][x]

    def penalty(self):
        n = self.size
        grid = self.dark
        score = 0
        lines = [grid[y] for y in range(n)] + [[grid[y][x] for y in range(n)] for x in range(n)]
        pattern_a = [True, False, True, True, True, False, True, False, False, False, False]
        pattern_b = pattern_a[::-1]
        for line in lines:
            run = 1
            for i in range(1, n):
                if line[i] == line[i - 1]:
                    run += 1
                else:
                    if run >= 5:
                        score += 3 + run - 5
                    run = 1
            if run >= 5:
                score += 3 + run - 5
            for i in range(n - 10):
                window = line[i:i + 11]
                if window == pattern_a or window == pattern_b:
                    score += 40
        for y in range(n - 1):
            for x in range(n - 1):
                c = grid[y][x]
                if c == grid[y][x + 1] == grid[y + 1][x] == grid[y + 1][x + 1]:
                    score += 3
        dark = sum(row.count(True) for row in grid)
        total = n * n
        k = (abs(dark * 20 - total * 10) + total - 1) // total - 1
        score += max(0, k) * 10
        return score


def encode(text):
    """The QR matrix (list of rows of booleans, True = dark) for a text of up to 213 bytes."""
    payload = text.encode("utf-8")
    version = next((v for v in range(1, 11) if len(payload) <= _data_capacity(v) - (2 if v < 10 else 3)), None)
    if version is None:
        raise ValueError("Text zu lang für den QR-Code")
    codewords = _add_ecc_and_interleave(_encode_data(payload, version), version)
    best = None
    for mask in range(8):
        m = _Matrix(version)
        m.draw_function_patterns()
        m.draw_codewords(codewords)
        m.apply_mask(mask)
        m.draw_format(mask)
        score = m.penalty()
        if best is None or score < best[0]:
            best = (score, m)
    return best[1].dark


def svg(text, border=4, dark="#1b1310", light="#ffffff"):
    """The QR code as SVG text (crisp at any size)."""
    grid = encode(text)
    n = len(grid) + border * 2
    parts = []
    for y, row in enumerate(grid):
        for x, cell in enumerate(row):
            if cell:
                parts.append(f"M{x + border} {y + border}h1v1h-1z")
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {n} {n}" shape-rendering="crispEdges">'
            f'<rect width="{n}" height="{n}" fill="{light}"/><path d="{"".join(parts)}" fill="{dark}"/></svg>')


def svg_data_url(text):
    return "data:image/svg+xml;base64," + base64.b64encode(svg(text).encode("utf-8")).decode("ascii")
