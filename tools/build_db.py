"""Bangun database SQLite untuk aplikasi: Al-Qur'an + 9 kitab hadits + FTS + keterkaitan."""
import glob, json, os, random, re, sqlite3, sys, unicodedata
from collections import defaultdict

SRC_DIR, QURAN_JSON, OUT = sys.argv[1:4]

KITAB = [  # (slug, nama, penanda di header)
    ('bukhari', 'Shahih Bukhari'), ('muslim', 'Shahih Muslim'), ('abu-daud', 'Sunan Abu Daud'),
    ('tirmidzi', 'Sunan Tirmidzi'), ('nasai', 'Sunan Nasai'), ('ibnu-majah', 'Sunan Ibnu Majah'),
    ('ahmad', 'Musnad Ahmad'), ('darimi', 'Sunan Darimi'), ('malik', 'Muwatha Malik'),
]

# ---------- normalisasi (HARUS sama dengan TextNorm.kt) ----------
AR_CHARS = re.compile(r'[؀-ۿݐ-ݿﭐ-﷿ﹰ-ﻼ]')
HARAKAT = re.compile(r'[ؐ-ًؚ-ٰٟۖ-ۭـ]')

def ar_skeleton(word):
    w = HARAKAT.sub('', word)
    w = re.sub('[آأإٱٲٳ]', 'ا', w)  # alif
    w = w.replace('ة', 'ه')  # ta marbutah -> ha
    w = re.sub('[اويىءؤئیۥۦ]', '', w)  # buang huruf lemah
    return w

def norm_id(s):
    s = unicodedata.normalize('NFKD', s.lower())
    s = ''.join(c for c in s if not unicodedata.combining(c))
    s = re.sub(r"['‘’`ʼ]", '', s)
    s = re.sub(r'[^a-z0-9\s]', ' ', s)
    return ' '.join(s.split())

def norm_ar(s):
    toks = re.findall(r'[؀-ۿݐ-ݿﭐ-﷿ﹰ-ﻼ]+', s)
    out = [ar_skeleton(t) for t in toks]
    return ' '.join(t for t in out if t)

SUF1 = ['lah', 'kah', 'pun', 'nya', 'ku', 'mu']
SUF2 = ['kan', 'an']
PRE = ['memper', 'member', 'meng', 'meny', 'mem', 'men', 'ber', 'ter', 'per', 'peng', 'pen', 'pem', 'me', 'di', 'ke']

def stem(w):
    if len(w) < 6 or not w.isalpha():
        return w
    for p in PRE:
        if w.startswith(p) and len(w) - len(p) >= 3:
            w = w[len(p):]
            break
    for group in (SUF1, SUF2):
        for s in group:
            if w.endswith(s) and len(w) - len(s) >= 4:
                w = w[:-len(s)]
                break
    return w

def index_id(s):
    ws = norm_id(s).split()
    extra = [stem(w) for w in ws]
    return ' '.join(ws + [e for e, w in zip(extra, ws) if e != w])

# ---------- Al-Qur'an ----------
quran = json.load(open(QURAN_JSON, encoding='utf-8'))

# ---------- Hadits ----------
def is_ar(line):
    a = len(AR_CHARS.findall(line)); l = sum(1 for c in line if c.isalpha()) or 1
    return a / l > 0.5

hadith = []  # (kitab_idx, number, ar, idn)
for f in glob.glob(os.path.join(SRC_DIR, '*.txt')):
    txt = open(f, encoding='utf-8').read()
    first = txt.split('\n', 1)[0]
    ki = next((i for i, (_, n) in enumerate(KITAB) if n in first), None)
    if ki is None:
        continue
    parts = re.split(r'^### .+? No\. (\d+)\s*$', txt, flags=re.M)
    for i in range(1, len(parts), 2):
        num = int(parts[i]); body = parts[i + 1]
        ar, idn = [], []
        for ln in body.split('\n'):
            t = ln.strip()
            if not t or t.startswith('# ') or t.startswith('Sumber:'):
                continue
            (ar if is_ar(t) else idn).append(t)
        hadith.append((ki, num, ' '.join(ar), '\n'.join(idn)))
hadith.sort()
print('hadits', len(hadith))

def matn(idn):
    t = idn.replace('\n', ' ')
    m = re.search(r'["“]', t)
    if m and m.start() < len(t) * 0.7:
        return t[m.start():]
    return t

def ringkas(idn):
    t = idn.replace('\n', ' ')
    i = t.find('bersabda')
    s = t[i + 8:].strip(' :,;') if 0 <= i < len(t) * 0.7 else matn(idn)
    s = s.strip()
    return s if len(s) <= 220 else s[:217].rsplit(' ', 1)[0] + '…'

# ---------- DB ----------
if os.path.exists(OUT):
    os.remove(OUT)
db = sqlite3.connect(OUT)
c = db.cursor()
c.executescript('''
CREATE TABLE android_metadata (locale TEXT);
INSERT INTO android_metadata VALUES ('id_ID');
CREATE TABLE surah(n INTEGER PRIMARY KEY, ar TEXT, latin TEXT, arti TEXT, type TEXT, ayat INTEGER);
CREATE TABLE ayah(id INTEGER PRIMARY KEY, surah INTEGER, ayah INTEGER, ar TEXT, idn TEXT);
CREATE TABLE kitab(id INTEGER PRIMARY KEY, slug TEXT, nama TEXT, jumlah INTEGER);
CREATE TABLE hadith(id INTEGER PRIMARY KEY, kitab INTEGER, nomor INTEGER, ar TEXT, idn TEXT, ringkas TEXT);
CREATE INDEX hadith_kitab ON hadith(kitab, nomor);
CREATE VIRTUAL TABLE quran_fts USING fts4(content="", txt);
CREATE VIRTUAL TABLE hadith_fts USING fts4(content="", txt);
CREATE TABLE link(ayah_id INTEGER, hadith_id INTEGER, jenis INTEGER, skor REAL);
CREATE TABLE daily(jenis INTEGER, urut INTEGER, ref INTEGER, PRIMARY KEY(jenis, urut));
CREATE TABLE topik(id INTEGER PRIMARY KEY, nama TEXT, kata TEXT);
''')

ayah_rows = []
for s in quran:
    c.execute('INSERT INTO surah VALUES (?,?,?,?,?,?)',
              (s['n'], s['ar'], s['latin'], s['arti'], s['type'], len(s['verses'])))
    for v in s['verses']:
        aid = s['n'] * 1000 + v['n']
        ayah_rows.append((aid, s['n'], v['n'], v['ar'], v['id']))
        c.execute('INSERT INTO ayah VALUES (?,?,?,?,?)', ayah_rows[-1])
        c.execute('INSERT INTO quran_fts(docid, txt) VALUES (?,?)',
                  (aid, index_id(v['id'] + ' ' + s['latin']) + ' ' + norm_ar(v['ar'])))

counts = defaultdict(int)
for ki, num, ar, idn in hadith:
    counts[ki] += 1
for i, (slug, nama) in enumerate(KITAB):
    c.execute('INSERT INTO kitab VALUES (?,?,?,?)', (i + 1, slug, nama, counts[i]))
hid_of = {}
for ki, num, ar, idn in hadith:
    hid = (ki + 1) * 100000 + num
    hid_of[(ki, num)] = hid
    c.execute('INSERT INTO hadith VALUES (?,?,?,?,?,?)', (hid, ki + 1, num, ar, idn, ringkas(idn)))
    c.execute('INSERT INTO hadith_fts(docid, txt) VALUES (?,?)', (hid, index_id(idn) + ' ' + norm_ar(ar)))
db.commit()

# ---------- Keterkaitan 1: hadits yang mengutip ayat (pencocokan teks Arab) ----------
N = 5
gram_ayah = defaultdict(set)
ayah_sk = {}
for aid, s, a, ar, idn in ayah_rows:
    w = norm_ar(ar).split()
    ayah_sk[aid] = w
    for i in range(len(w) - N + 1):
        gram_ayah[tuple(w[i:i + N])].add(aid)
gram_ayah = {g: v for g, v in gram_ayah.items() if len(v) <= 3}

quote = defaultdict(lambda: defaultdict(int))  # aid -> hid -> jumlah gram cocok
gram_hits = defaultdict(int)
for ki, num, ar, idn in hadith:
    w = norm_ar(ar).split()
    seen = set()
    for i in range(len(w) - N + 1):
        g = tuple(w[i:i + N])
        if g in gram_ayah and g not in seen:
            seen.add(g)
            for aid in gram_ayah[g]:
                quote[aid][hid_of[(ki, num)]] += 1
nq = 0
for aid, hs in quote.items():
    L = max(1, len(ayah_sk[aid]) - N + 1)
    for hid, k in hs.items():
        cover = k / L
        if (k >= 2 and cover >= 0.5) or k >= 6 or (L <= 2 and k >= L):
            c.execute('INSERT INTO link VALUES (?,?,1,?)', (aid, hid, round(min(1.0, cover), 3)))
            nq += 1
print('link kutipan', nq)
db.commit()

# ---------- Keterkaitan 2: kemiripan tema (TF-IDF terjemahan) ----------
from sklearn.feature_extraction.text import TfidfVectorizer
import numpy as np

STOP = set('''yang dan di ke dari ini itu dengan untuk pada adalah dalam tidak kami kamu mereka dia ia
akan atau juga telah sudah oleh karena maka bagi sebagai saja apa ada bahwa jika apabila agar supaya
kepada kepadanya kepadamu kepada kami beliau berkata bersabda menceritakan mengabarkan telah
kemudian lalu hingga sampai seraya ketika saat tersebut tentang antara orang seorang para pun
hai wahai ya ia nya lah kah tetapi namun bukan sungguh sesungguhnya benar benar sangat lebih
dapat bisa mau ingin hal suatu sesuatu setiap semua seluruh banyak sedikit siapa mana bagaimana
mengapa kapan begitu demikian sebagaimana seperti sedang sambil serta yaitu yakni setelah sebelum
shallallahu alaihi wasallam radliallahu anhu anha anhuma rasulullah nabi saw ibnu bin binti abu
abdullah muhammad umar aisyah hurairah anas abbas malik hadits riwayat haddatsana akhbarana
dari kami kepada menceritakan isnad sanad semisalnya semisal lafadz jangan janganlah kalian
hendaklah allah tuhan aku engkau kita ayat turun berfirman firman ucapan berkata katakanlah
katanya bertanya menjawab dia hadis hasan shahih gharib isa'''.split())

def matn(idn):
    t = idn.replace('\n', ' ')
    m = re.search(r'["“]', t)
    if m and m.start() < len(t) * 0.7:
        return t[m.start():]
    return t

docs_q = [norm_id(r[4]) for r in ayah_rows]
docs_h = [norm_id(matn(h[3])) for h in hadith]
vec = TfidfVectorizer(stop_words=list(STOP), min_df=3, max_df=0.08, sublinear_tf=True,
                      token_pattern=r'(?u)\b[a-z]{3,}\b')
vec.fit(docs_q + docs_h)
Xq = vec.transform(docs_q); Xh = vec.transform(docs_h)
hids = np.array([hid_of[(h[0], h[1])] for h in hadith])
K = 6
nt = 0
for start in range(0, Xq.shape[0], 500):
    S = (Xq[start:start + 500] @ Xh.T).toarray()
    for r in range(S.shape[0]):
        row = S[r]
        top = np.argpartition(-row, K)[:K]
        top = top[np.argsort(-row[top])]
        aid = ayah_rows[start + r][0]
        for j in top:
            if row[j] >= 0.12:
                c.execute('INSERT INTO link VALUES (?,?,2,?)', (aid, int(hids[j]), round(float(row[j]), 3)))
                nt += 1
print('link tema', nt)
c.executescript('CREATE INDEX link_ayah ON link(ayah_id, jenis, skor); CREATE INDEX link_hadith ON link(hadith_id, jenis, skor);')
db.commit()

# ---------- Topik untuk pencarian cepat ----------
TOPIK = [
    ('Shalat', 'shalat salat sholat'), ('Puasa', 'puasa shaum ramadhan ramadan'), ('Zakat', 'zakat sedekah infak'),
    ('Haji', 'haji umrah'), ('Sabar', 'sabar'), ('Syukur', 'syukur bersyukur'), ('Taubat', 'taubat tobat ampunan'),
    ('Doa', 'doa berdoa'), ('Orang Tua', 'ibu bapak orang tua'), ('Ilmu', 'ilmu'), ('Jujur', 'jujur dusta'),
    ('Rezeki', 'rezeki rizki'), ('Akhlak', 'akhlak'), ('Riba', 'riba'), ('Jihad', 'jihad'), ('Niat', 'niat'),
    ('Surga', 'surga'), ('Neraka', 'neraka'), ('Kematian', 'mati kematian'), ('Tetangga', 'tetangga'),
    ('Anak Yatim', 'yatim'), ('Pernikahan', 'nikah menikah istri suami'), ('Zikir', 'zikir dzikir'),
    ('Tawakal', 'tawakal bertawakal'),
]
for i, (n, k) in enumerate(TOPIK, 1):
    c.execute('INSERT INTO topik VALUES (?,?,?)', (i, n, k))

# ---------- Ayat & hadits pilihan untuk notifikasi harian ----------
AYAT_PILIHAN = '''2:45 2:152 2:153 2:155 2:156 2:186 2:201 2:216 2:255 2:261 2:277 2:286 3:8 3:26 3:31 3:92 3:103
3:104 3:110 3:133 3:134 3:139 3:159 3:173 3:185 3:190 3:191 3:200 4:36 4:58 4:135 5:2 5:8 6:162 7:23 7:31
7:56 7:199 8:2 9:51 9:105 10:57 11:6 11:88 12:87 13:11 13:28 14:7 14:40 16:18 16:90 16:97 16:125 16:128
17:23 17:24 17:32 17:36 17:80 18:10 18:46 20:114 21:87 21:107 23:1 23:115 25:63 25:74 28:77 29:45 29:69
31:13 31:14 31:18 31:19 33:21 33:41 33:70 39:10 39:53 40:60 41:30 42:30 49:10 49:11 49:12 49:13 51:56
55:13 57:20 59:18 62:10 64:16 65:2 65:3 66:6 67:2 93:5 94:5 94:6 94:7 94:8 96:1 103:3 112:1'''.split()
picked_a = [int(s) * 1000 + int(a) for s, a in (x.split(':') for x in AYAT_PILIHAN)]
rnd = random.Random(1447)
pool_a = [r[0] for r in ayah_rows if 90 <= len(r[4]) <= 260 and r[0] not in picked_a and r[1] > 1]
rnd.shuffle(pool_a)
seq_a = picked_a + pool_a[:366 - len(picked_a)]
rnd.shuffle(seq_a)

HADITS_KUNCI = ['amal itu tergantung', 'islam dibangun di atas lima', 'mencintai saudaranya sebagaimana',
    'berkata baik atau diam', 'jangan marah', 'selamat dari lisan dan tangannya', 'senyum',
    'kebersihan', 'mudahkanlah dan jangan kalian persulit', 'sebaik-baik kalian adalah yang mempelajari al quran',
    'barangsiapa menempuh suatu jalan untuk mencari ilmu', 'malu adalah sebagian dari iman',
    'orang kuat bukanlah', 'tangan di atas lebih baik', 'dunia adalah penjara', 'jadilah engkau di dunia',
    'orang mukmin yang kuat', 'tinggalkan apa yang meragukanmu', 'sedekah tidak akan mengurangi',
    'agama itu adalah nasihat', 'sebaik-baik manusia', 'kasih sayang', 'pemimpin dan akan dimintai',
    'iman itu ada tujuh puluh', 'surga di bawah', 'ibumu', 'menyambung silaturrahim', 'shalat berjamaah lebih utama',
    'doa', 'istighfar', 'tujuh golongan', 'bertakwalah kepada allah di mana saja']
def sabda(idn):
    t = idn.replace('\n', ' ')
    i = t.find('bersabda')
    return t[i + 8:].strip(' :,;') if i >= 0 else ''
MORAL = ('barangsiapa sebaik-baik iman surga pahala sedekah akhlak amal dosa ampun kasih sayang doa sabar '
         'shalat puasa zakat tetangga ilmu jujur kebaikan malu sabar syukur taubat berbakti ibu lisan '
         'senyum mukmin muslim allah mencintai dicintai keutamaan rahmat dzikir berbuat baik zalim').split()
hcand = []
for h in hadith:
    if h[0] not in (0, 1):
        continue
    s = sabda(h[3])
    if not (60 <= len(s) <= 420) or 'semisal' in h[3] or 'seperti hadits' in h[3]:
        continue
    sl = s.lower()
    score = sum(1 for k in MORAL if k in sl)
    if score >= 2:
        hcand.append((score, hid_of[(h[0], h[1])], h))
hcand.sort(key=lambda x: (-x[0], x[1]))
hcand = [(hid, h) for _, hid, h in hcand]
picked_h = []
for k in HADITS_KUNCI:
    for hid, h in hcand:
        if k in h[3].lower() and hid not in picked_h:
            picked_h.append(hid); break
pool_h = [hid for hid, h in hcand[:900] if hid not in picked_h]
rnd.shuffle(pool_h)
seq_h = picked_h + pool_h[:366 - len(picked_h)]
rnd.shuffle(seq_h)
for i, r in enumerate(seq_a):
    c.execute('INSERT INTO daily VALUES (1,?,?)', (i, r))
for i, r in enumerate(seq_h):
    c.execute('INSERT INTO daily VALUES (2,?,?)', (i, r))
print('pilihan', len(seq_a), len(seq_h), 'kunci hadits cocok', len(picked_h))

db.commit()
c.execute("INSERT INTO quran_fts(quran_fts) VALUES('optimize')")
c.execute("INSERT INTO hadith_fts(hadith_fts) VALUES('optimize')")
db.commit()
c.execute('PRAGMA user_version = 1')
db.execute('VACUUM')
db.close()
print('OK', os.path.getsize(OUT) // 1024 // 1024, 'MB')
