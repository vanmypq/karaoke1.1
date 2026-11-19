package com.vanmypq.vangso;

/** Bộ xử lý âm thanh thuần Java: HPF -> EQ 3 dải -> nén -> echo + reverb -> limiter. */
public final class Dsp {
    private Dsp() {}
    public static final int MIC = 0, MASTER = 1, ECHO = 2, FB = 3, TIME = 4, REV = 5, SIZE = 6, BASS = 7, MID = 8, TRE = 9, COMP = 10, HOWL = 11, N = 12;
    public static final float[] DEF = {100, 90, 22, 30, 300, 30, 35, 0, 0, 0, 1, 1};

    public static final class Params {
        public final float[] v = DEF.clone();
        public volatile int ver = 1;
        public void set(int i, float x) { v[i] = x; ver++; }
    }

    static final class Biquad {
        double b0 = 1, b1, b2, a1, a2, z1, z2;
        float run(float x) { double y = b0 * x + z1; z1 = b1 * x - a1 * y + z2; z2 = b2 * x - a2 * y; return (float) y; }
        void set(double nb0, double nb1, double nb2, double a0, double na1, double na2) {
            b0 = nb0 / a0; b1 = nb1 / a0; b2 = nb2 / a0; a1 = na1 / a0; a2 = na2 / a0;
        }
        void highpass(double fs, double f, double q) {
            double w = 2 * Math.PI * f / fs, c = Math.cos(w), al = Math.sin(w) / (2 * q);
            set((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + al, -2 * c, 1 - al);
        }
        void peaking(double fs, double f, double q, double db) {
            double A = Math.pow(10, db / 40), w = 2 * Math.PI * f / fs, c = Math.cos(w), al = Math.sin(w) / (2 * q);
            set(1 + al * A, -2 * c, 1 - al * A, 1 + al / A, -2 * c, 1 - al / A);
        }
        void lowShelf(double fs, double f, double db) {
            double A = Math.pow(10, db / 40), w = 2 * Math.PI * f / fs, c = Math.cos(w);
            double be = 2 * Math.sqrt(A) * (Math.sin(w) / 2 * Math.sqrt(2));
            set(A * ((A + 1) - (A - 1) * c + be), 2 * A * ((A - 1) - (A + 1) * c), A * ((A + 1) - (A - 1) * c - be),
                (A + 1) + (A - 1) * c + be, -2 * ((A - 1) + (A + 1) * c), (A + 1) + (A - 1) * c - be);
        }
        void highShelf(double fs, double f, double db) {
            double A = Math.pow(10, db / 40), w = 2 * Math.PI * f / fs, c = Math.cos(w);
            double be = 2 * Math.sqrt(A) * (Math.sin(w) / 2 * Math.sqrt(2));
            set(A * ((A + 1) + (A - 1) * c + be), -2 * A * ((A - 1) + (A + 1) * c), A * ((A + 1) + (A - 1) * c - be),
                (A + 1) - (A - 1) * c + be, 2 * ((A - 1) - (A + 1) * c), (A + 1) - (A - 1) * c - be);
        }
    }

    static final class Comb {
        final float[] b; int i; float store, fb = 0.84f, damp = 0.4f;
        Comb(int n) { b = new float[n]; }
        float run(float in) { float o = b[i]; store = o * (1 - damp) + store * damp; b[i] = in + store * fb; if (++i == b.length) i = 0; return o; }
    }

    static final class Allpass {
        final float[] b; int i;
        Allpass(int n) { b = new float[n]; }
        float run(float in) { float bo = b[i]; float o = -in + bo; b[i] = in + bo * 0.5f; if (++i == b.length) i = 0; return o; }
    }


    /** Chống hú: tìm tiếng "tít" thuần, kéo dài, rồi tự đặt bộ lọc khuyết (notch) đúng tần số đó. */
    static final class Howl {
        static final int NF = 1024, HOP = 512, SLOTS = 5, TTL = 2800; // TTL ~30 giây
        final int fs; final float[] ring = new float[NF]; int pos = 0, cnt = 0, lastK = -1, streak = 0; float duckT = 1f, duck = 1f;
        final double[] re = new double[NF], im = new double[NF], win = new double[NF], mag = new double[NF / 2];
        final Biquad[] nt = new Biquad[SLOTS]; final double[] nf = new double[SLOTS]; final int[] ttl = new int[SLOTS];
        Howl(int fs) { this.fs = fs; for (int i = 0; i < NF; i++) win[i] = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / NF); for (int i = 0; i < SLOTS; i++) nt[i] = new Biquad(); }

        float run(float x, boolean on) {
            ring[pos] = x; pos = (pos + 1) & (NF - 1);
            if (!on) return x;
            if (++cnt >= HOP) { cnt = 0; analyze(); }
            for (int i = 0; i < SLOTS; i++) if (ttl[i] > 0) x = nt[i].run(x);
            duck += (duckT - duck) * 0.002f;
            return x * duck;
        }

        void analyze() {
            for (int i = 0; i < NF; i++) { re[i] = ring[(pos + i) & (NF - 1)] * win[i]; im[i] = 0; }
            fft(re, im);
            for (int k = 0; k < NF / 2; k++) mag[k] = Math.hypot(re[k], im[k]);
            int k0 = (int) Math.ceil(300.0 * NF / fs), k1 = Math.min(NF / 2 - 2, (int) (8000.0 * NF / fs)), bk = k0;
            for (int k = k0; k <= k1; k++) if (mag[k] > mag[bk]) bk = k;
            double sum = 0; int c = 0;
            for (int j = bk - 20; j <= bk + 20; j++) { if (j < 1 || j >= NF / 2 || Math.abs(j - bk) < 6) continue; sum += mag[j]; c++; }
            double floor = sum / Math.max(1, c) + 1e-9, h2 = 0;
            for (int j = 2 * bk - 1; j <= 2 * bk + 1; j++) if (j < NF / 2) h2 = Math.max(h2, mag[j]);
            // tiếng hú: đỉnh nhọn hơn nền 20 dB, không có họa âm bậc 2 (khác giọng hát), biên độ > ~0,02
            boolean tone = mag[bk] > 10 * floor && mag[bk] > 10 * h2 && mag[bk] > 5;
            if (tone && lastK >= 0 && Math.abs(bk - lastK) <= 2) streak++; else streak = tone ? 1 : 0;
            lastK = tone ? bk : -1;
            for (int i = 0; i < SLOTS; i++) if (ttl[i] > 0 && --ttl[i] == 0) nt[i].set(1, 0, 0, 1, 0, 0);
            if (!tone) duckT = Math.min(1f, duckT + 0.002f); // hồi lại từ từ (~4 giây) khi hết hú
            if (streak >= 30) { // giữ nguyên ~0,3 giây
                streak = 0;
                if (mag[bk] > 25) duckT = Math.max(0.25f, duckT * 0.7f); // hú to (biên độ > ~0,1): hạ tiếng míc tối đa ~12 dB
                double a = Math.log(mag[bk - 1] + 1e-12), b = Math.log(mag[bk] + 1e-12), cc = Math.log(mag[bk + 1] + 1e-12);
                double d = 0.5 * (a - cc) / (a - 2 * b + cc); if (Double.isNaN(d) || Math.abs(d) > 0.5) d = 0;
                double f = (bk + d) * fs / NF;
                int slot = 0; boolean have = false;
                for (int i = 0; i < SLOTS; i++) {
                    if (ttl[i] > 0 && Math.abs(nf[i] - f) < 1.5 * fs / NF) have = true;
                    if (ttl[i] < ttl[slot]) slot = i;
                }
                if (!have) { nt[slot].peaking(fs, f, 20, -15); nf[slot] = f; ttl[slot] = TTL; }
            }
        }
    }

    static void fft(double[] re, double[] im) {
        int n = re.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1; for (; (j & bit) != 0; bit >>= 1) j ^= bit; j ^= bit;
            if (i < j) { double t = re[i]; re[i] = re[j]; re[j] = t; t = im[i]; im[i] = im[j]; im[j] = t; }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double ang = -2 * Math.PI / len, wr = Math.cos(ang), wi = Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                double cr = 1, ci = 0;
                for (int j = 0; j < len / 2; j++) {
                    int a = i + j, b = i + j + len / 2;
                    double tr = re[b] * cr - im[b] * ci, ti = re[b] * ci + im[b] * cr;
                    re[b] = re[a] - tr; im[b] = im[a] - ti; re[a] += tr; im[a] += ti;
                    double nc = cr * wr - ci * wi; ci = cr * wi + ci * wr; cr = nc;
                }
            }
        }
    }

    public static final class Chain {
        static final int[] CT = {1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617}, AT = {556, 441, 341, 225};
        static final float THR = 0.05f; // -26 dB
        final Params P; final int fs; int seen = 0;
        final Biquad hp = new Biquad(), bass = new Biquad(), mid = new Biquad(), tre = new Biquad();
        final float[] eb; int ew = 0; float elp = 0;
        final Comb[] combs = new Comb[CT.length]; final Allpass[] aps = new Allpass[AT.length];
        final Howl howl; boolean howlOn; float env = 0, gMic, gMaster, gEcho, gRev, tMic, tMaster, tEcho, tRev, fbk, atk, rel; int delay = 1; boolean comp;
        public volatile float peak = 0;

        public Chain(int fs, Params p) {
            this.fs = fs; P = p; eb = new float[fs + 8]; howl = new Howl(fs);
            for (int i = 0; i < CT.length; i++) combs[i] = new Comb(Math.max(8, CT[i] * fs / 44100));
            for (int i = 0; i < AT.length; i++) aps[i] = new Allpass(Math.max(8, AT[i] * fs / 44100));
            atk = (float) (1 - Math.exp(-1.0 / (0.005 * fs))); rel = (float) (1 - Math.exp(-1.0 / (0.2 * fs)));
            recalc(); gMic = tMic; gMaster = tMaster; gEcho = tEcho; gRev = tRev;
        }

        void recalc() {
            float[] v = P.v;
            hp.highpass(fs, 90, 0.707); bass.lowShelf(fs, 200, v[BASS]); mid.peaking(fs, 1800, 0.9, v[MID]); tre.highShelf(fs, 5000, v[TRE]);
            tMic = v[MIC] / 100f; tMaster = v[MASTER] / 100f; tEcho = v[ECHO] / 100f; tRev = v[REV] / 100f * 1.2f; fbk = v[FB] / 100f;
            delay = Math.max(1, Math.min(eb.length - 1, (int) (v[TIME] * fs / 1000f)));
            comp = v[COMP] > 0.5f; howlOn = v[HOWL] > 0.5f;
            float rf = 0.70f + v[SIZE] / 100f * 0.28f; for (Comb c : combs) c.fb = rf;
        }

        public void process(short[] in, short[] out, int n) {
            int ver = P.ver; if (ver != seen) { seen = ver; recalc(); }
            float pk = 0; final int len = eb.length;
            for (int k = 0; k < n; k++) {
                gMic += (tMic - gMic) * 0.003f; gMaster += (tMaster - gMaster) * 0.003f; gEcho += (tEcho - gEcho) * 0.003f; gRev += (tRev - gRev) * 0.003f;
                float x = hp.run(in[k] / 32768f); x = bass.run(x); x = mid.run(x); x = tre.run(x);
                x = howl.run(x, howlOn);
                if (comp) {
                    float a = Math.abs(x); env += (a > env ? atk : rel) * (a - env);
                    float g = env > THR ? (float) Math.pow(env / THR, -0.75) : 1f;
                    x *= g * 1.4f;
                }
                x *= gMic;
                int r = ew - delay; if (r < 0) r += len;
                elp += 0.35f * (eb[r] - elp);
                eb[ew] = x + elp * fbk; if (++ew == len) ew = 0;
                float rin = x * 0.015f + 1e-9f, rs = 0;
                for (Comb c : combs) rs += c.run(rin);
                for (Allpass a : aps) rs = a.run(rs);
                float y = (x + elp * gEcho + rs * 3f * gRev) * gMaster;
                float ay = Math.abs(y);
                if (ay > 0.7f) y = Math.signum(y) * (0.7f + 0.3f * (float) Math.tanh((ay - 0.7f) / 0.3f));
                if (Float.isNaN(y)) y = 0;
                out[k] = (short) (y * 32767f); if (ay > pk) pk = ay;
            }
            peak = Math.max(Math.min(pk, 1f), peak * 0.85f);
        }
    }
}
