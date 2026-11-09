"""Parse terjemahan Kemenag 2019 dari teks PDF menjadi JSON per ayat.
Fallback ke terjemahan quran-json (id) untuk ayat yang gagal di-parse."""
import json, re, sys

raw_path, qj_path, out_path = sys.argv[1:4]
qj = json.load(open(qj_path, encoding='utf-8'))
counts = [s['total_verses'] for s in qj]

lines = open(raw_path, encoding='utf-8').read().split('\n')
start = next(i for i, l in enumerate(lines) if re.match(r'^JUZ 1 1 1\. AL-F', l.strip()))
lines = lines[start:]

AR = re.compile(r'[؀-ۿﭐ-ﻼݐ-ݿ]')
HDR1 = re.compile(r'^\d+\.\s+[A-ZĀĪŪḤṢṬẒḌŻ‘’\'\-\s]+\s+\d+\s+JUZ\s+\d+\s*$')
HDR2 = re.compile(r'^JUZ\s+\d+\s+\d+\s+\d+\.\s+[A-ZĀĪŪḤṢṬẒḌŻ‘’\'\-\s]+\s*$')
AYAH = re.compile(r'^(\d{1,3})(?:(\.)\s*|()\s+)(\S.*)$')
FOOT = re.compile(r'^\d{1,4}\)\s')
TITLE = re.compile(r'^(Makkiyyah|Madaniyyah)|^JUZ\s+\d+\s*$')
BASM = 'Dengan nama Allah Yang Maha Pengasih lagi Maha Penyayang'

def is_latin(s):
    a = sum(1 for c in s if c.isalpha() and not AR.match(c))
    b = len(AR.findall(s))
    return a >= 2 and a > b * 2

def all_caps(s):
    letters = [c for c in s if c.isalpha()]
    return letters and all(c.isupper() for c in letters)

s, a = 1, 0  # current surah, last ayah
verses = {}
cur = None  # (s, a)
buf = []
footmode = False

def flush():
    if cur is None:
        return
    blk = buf[:]
    # buang judul tema di akhir blok (baris tanpa tanda baca akhir, setelah kalimat selesai)
    term = tuple('.!?”’")')
    if len(blk) > 1 and len(blk[0]) < 30 and re.fullmatch(r"[A-Za-zĀĪŪāīūḤḥṢṣṬṭ‘’ ]+", blk[0].strip()):
        blk[0] = blk[0].strip() + '.'
    k = len(blk)
    while k > 1 and not blk[k-1].rstrip().endswith(term):
        k -= 1
    if k < len(blk) and k >= 1 and blk[k-1].rstrip().endswith(term):
        tail = ' '.join(blk[k:])
        if blk[k][:1].isupper() and len(tail) < 160:
            blk = blk[:k]
    text = ''
    for ln in blk:
        ln = ln.strip()
        if text.endswith('­') or text.endswith('-') and not text.endswith(' -'):
            text = text.rstrip('­') + ln if text.endswith('­') else text + ln
        else:
            text = (text + ' ' + ln).strip()
    text = re.sub(r'(\w{2,})\u00ad(\1)', r'\1-\2', text)
    text = text.replace('\u00ad', '')
    text = re.sub(r'(?<=[\w,.;:!?”’)])\d{1,4}\)', '', text)  # penanda catatan kaki
    text = AR.sub('', text).replace('\ufdfd','')
    text = re.sub(r'\b([a-z]{3,})\1', r'\1-\1', text)
    text = re.sub(r'\s+', ' ', text).strip()
    verses[cur] = text

for ln in lines:
    t = ln.strip().replace('\u00a0', ' ').replace('\x02', '\u00ad').replace('\ufdfd', '')
    if not t:
        continue
    if HDR1.match(t) or HDR2.match(t):
        footmode = False
        continue
    if not is_latin(t):
        continue
    m = AYAH.match(t)
    if m:
        n = int(m.group(1)); dot = m.group(2) == '.'
        ok = False
        if a < counts[s-1] and (n == a + 1 or (dot and a + 1 < n <= min(a + 4, counts[s-1]))):
            ns, na, ok = s, n, True
        elif s < 114 and a >= counts[s-1] - 4 and (n == 1 or (dot and n <= 3)) and (n == 1 or a >= counts[s-1]):
            ns, na, ok = s + 1, n, True
        if ok:
            flush()
            s, a = ns, na
            cur = (s, a)
            buf = [m.group(4)]
            footmode = False
            continue
    if FOOT.match(t):
        footmode = True
        continue
    if footmode:
        continue
    if TITLE.match(t) or all_caps(t) or t.startswith(BASM):
        continue
    if cur is not None:
        buf.append(t)
flush()

out = []
fallback = 0
for si, sur in enumerate(qj, 1):
    vs = []
    for v in sur['verses']:
        old = v['translation']
        k = verses.get((si, v['id']))
        src = 'kemenag2019'
        if not k or len(k) < 0.35 * len(old) or len(k) > 3.0 * len(old) + 40:
            k, src = old, 'quran-json'
            fallback += 1
        vs.append({'n': v['id'], 'ar': v['text'], 'id': k, 'src': src})
    out.append({'n': si, 'ar': sur['name'], 'latin': sur['transliteration'],
                'arti': sur['translation'], 'type': sur['type'], 'verses': vs})
json.dump(out, open(out_path, 'w', encoding='utf-8'), ensure_ascii=False)
print('parsed', len(verses), 'fallback', fallback, 'last', s, a)
