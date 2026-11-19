package com.vanmypq.vangso;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

public class MainActivity extends Activity {
    static final int BG = 0xFF0E0A1A, CARD = 0xFF1A1330, TX = 0xFFF3EEFC, MUT = 0xFFA79CC4, AC = 0xFFFF3D9A, CY = 0xFF2DE2E6, WARN = 0xFFFFB36B;
    static final int[] IDX = {Dsp.MIC, Dsp.MASTER, Dsp.ECHO, Dsp.FB, Dsp.TIME, Dsp.REV, Dsp.SIZE, Dsp.BASS, Dsp.MID, Dsp.TRE};
    static final String[] LAB = {"Âm lượng míc", "Âm lượng tổng", "Mức echo", "Số lần lặp", "Khoảng cách lặp", "Mức vang", "Độ dài vang", "Trầm", "Trung (độ ấm)", "Cao (độ sáng)"};
    static final int[] MIN = {0, 0, 0, 0, 60, 0, 0, -12, -12, -12}, MAX = {200, 150, 100, 85, 800, 100, 100, 12, 12, 12};
    static final String[] UN = {"%", "%", "%", "%", " ms", "%", "%", " dB", " dB", " dB"};
    static final String[] GRP = {"Giọng hát", "Echo", "Reverb (vang)", "Âm sắc"};
    static final int[] GS = {0, 2, 5, 7, 10};
    static final String[] PN = {"Giọng mộc", "Phòng khách", "Bolero", "Hội trường", "Phòng tắm", "Sân khấu"};
    static final int[] PI = {Dsp.ECHO, Dsp.FB, Dsp.TIME, Dsp.REV, Dsp.SIZE, Dsp.BASS, Dsp.MID, Dsp.TRE};
    static final int[][] PV = {{5, 10, 200, 10, 15, 0, 0, 2}, {18, 25, 260, 28, 30, 2, 1, 3}, {30, 38, 340, 40, 45, 3, 2, 2},
            {25, 35, 420, 60, 80, 1, 0, 3}, {35, 30, 120, 55, 25, 3, -1, 4}, {40, 45, 380, 50, 65, 4, 1, 5}};

    final SeekBar[] sb = new SeekBar[10];
    final TextView[] val = new TextView[10];
    final Handler h = new Handler(Looper.getMainLooper());
    Switch sw, howlSw; int tickN; TextView status, hint; LevelBar bar; Button btn; SharedPreferences sp; boolean building;

    static class LevelBar extends View {
        float lv; final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        LevelBar(Context c) { super(c); }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), hh = getHeight(), r = hh / 2;
            p.setColor(0xFF33294F); c.drawRoundRect(new RectF(0, 0, w, hh), r, r, p);
            p.setColor(lv > 0.9f ? 0xFFFF5252 : AC); c.drawRoundRect(new RectF(0, 0, Math.max(hh, w * Math.min(1f, lv)), hh), r, r, p);
        }
    }

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    TextView tv(String t, int sp, int color, boolean bold) {
        TextView v = new TextView(this); v.setText(t); v.setTextSize(sp); v.setTextColor(color);
        if (bold) v.setTypeface(null, android.graphics.Typeface.BOLD); return v;
    }

    GradientDrawable shape(int color, int radius, boolean oval) {
        GradientDrawable g = new GradientDrawable(); g.setColor(color);
        if (oval) g.setShape(GradientDrawable.OVAL); else g.setCornerRadius(dp(radius)); return g;
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("vang", MODE_PRIVATE);
        Dsp.Params P = Engine.I.params;
        for (int i = 0; i < Dsp.N; i++) P.v[i] = sp.getFloat("p" + i, P.v[i]);
        P.ver++;
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);

        ScrollView root = new ScrollView(this); root.setBackgroundColor(BG); root.setFillViewport(true);
        LinearLayout col = new LinearLayout(this); col.setOrientation(LinearLayout.VERTICAL); col.setPadding(dp(16), dp(20), dp(16), dp(32));
        root.addView(col);
        col.addView(tv("Vang số Karaoke", 24, TX, true));
        TextView sub = tv("Míc điện thoại thành vang số: echo, reverb, EQ, nén tiếng.", 13, MUT, false); sub.setPadding(0, 0, 0, dp(10)); col.addView(sub);
        hint = tv("", 13, WARN, false); hint.setBackground(shape(CARD, 10, false)); hint.setPadding(dp(12), dp(10), dp(12), dp(10)); col.addView(hint);

        bar = new LevelBar(this); LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, dp(10)); bl.setMargins(0, dp(18), 0, dp(14)); col.addView(bar, bl);
        btn = new Button(this); btn.setAllCaps(false); btn.setTextSize(20); btn.setTypeface(null, android.graphics.Typeface.BOLD); btn.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams kl = new LinearLayout.LayoutParams(dp(150), dp(150)); kl.gravity = Gravity.CENTER_HORIZONTAL; col.addView(btn, kl);
        btn.setOnClickListener(v -> toggle());
        status = tv("Bấm “Bật míc” để bắt đầu.", 13, MUT, false); status.setGravity(Gravity.CENTER); status.setPadding(0, dp(10), 0, dp(10)); col.addView(status);

        HorizontalScrollView hs = new HorizontalScrollView(this); hs.setHorizontalScrollBarEnabled(false);
        LinearLayout pr = new LinearLayout(this); hs.addView(pr);
        for (int i = 0; i < PN.length; i++) {
            final int pi = i; Button pb = new Button(this); pb.setAllCaps(false); pb.setText(PN[i]); pb.setTextColor(TX); pb.setBackground(shape(CARD, 20, false));
            pb.setOnClickListener(v -> { for (int k = 0; k < PI.length; k++) Engine.I.params.set(PI[k], PV[pi][k]); refresh(); });
            LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(-2, dp(42)); pl.setMargins(0, 0, dp(8), 0); pr.addView(pb, pl);
        }
        col.addView(hs);

        for (int g = 0; g < GRP.length; g++) {
            LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setBackground(shape(CARD, 14, false)); card.setPadding(dp(14), dp(10), dp(14), dp(6));
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-1, -2); cl.setMargins(0, dp(12), 0, 0); col.addView(card, cl);
            card.addView(tv(GRP[g], 15, TX, true));
            for (int i = GS[g]; i < GS[g + 1] && i < 10; i++) {
                final int k = i;
                LinearLayout line = new LinearLayout(this); line.setPadding(0, dp(8), 0, 0);
                TextView lb = tv(LAB[i], 13, MUT, false); line.addView(lb, new LinearLayout.LayoutParams(0, -2, 1f));
                val[i] = tv("", 13, TX, false); line.addView(val[i]); card.addView(line);
                sb[i] = new SeekBar(this); sb[i].setMax(MAX[i] - MIN[i]);
                sb[i].setProgressTintList(ColorStateList.valueOf(AC)); sb[i].setThumbTintList(ColorStateList.valueOf(AC));
                sb[i].setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                        if (building) return; Engine.I.params.set(IDX[k], MIN[k] + p); val[k].setText((MIN[k] + p) + UN[k]);
                    }
                    @Override public void onStartTrackingTouch(SeekBar s) {}
                    @Override public void onStopTrackingTouch(SeekBar s) {}
                });
                card.addView(sb[i]);
            }
            if (g == 3) {
                sw = new Switch(this); sw.setText("Nén tiếng (giọng đều, chắc hơn)"); sw.setTextColor(TX); sw.setPadding(0, dp(8), 0, dp(8));
                sw.setOnCheckedChangeListener((v, on) -> { if (!building) Engine.I.params.set(Dsp.COMP, on ? 1 : 0); });
                card.addView(sw);
                howlSw = new Switch(this); howlSw.setText("Chống hú tự động (khi loa kéo ở gần)"); howlSw.setTextColor(TX); howlSw.setPadding(0, dp(4), 0, dp(10));
                howlSw.setOnCheckedChangeListener((v, on) -> { if (!building) Engine.I.params.set(Dsp.HOWL, on ? 1 : 0); });
                card.addView(howlSw);
            }
        }
        LinearLayout acts = new LinearLayout(this); LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(-1, -2); al.setMargins(0, dp(14), 0, 0); col.addView(acts, al);
        Button save = new Button(this), reset = new Button(this);
        for (Button x : new Button[]{save, reset}) { x.setAllCaps(false); x.setTextColor(TX); x.setBackground(shape(CARD, 12, false)); }
        save.setText("Lưu cài đặt"); reset.setText("Đặt lại");
        LinearLayout.LayoutParams a1 = new LinearLayout.LayoutParams(0, dp(48), 1f); a1.setMargins(0, 0, dp(8), 0);
        acts.addView(save, a1); acts.addView(reset, new LinearLayout.LayoutParams(0, dp(48), 1f));
        save.setOnClickListener(v -> {
            SharedPreferences.Editor e = sp.edit(); for (int i = 0; i < Dsp.N; i++) e.putFloat("p" + i, Engine.I.params.v[i]); e.apply();
            Toast.makeText(this, "Đã lưu cài đặt", Toast.LENGTH_SHORT).show();
        });
        reset.setOnClickListener(v -> { for (int i = 0; i < Dsp.N; i++) Engine.I.params.set(i, Dsp.DEF[i]); refresh(); });
        setContentView(root);
        refresh();
        tick.run();
    }

    void refresh() {
        building = true;
        for (int i = 0; i < 10; i++) { int v = Math.round(Engine.I.params.v[IDX[i]]); sb[i].setProgress(v - MIN[i]); val[i].setText(v + UN[i]); }
        sw.setChecked(Engine.I.params.v[Dsp.COMP] > 0.5f); howlSw.setChecked(Engine.I.params.v[Dsp.HOWL] > 0.5f);
        building = false;
    }

    final Runnable tick = new Runnable() {
        @Override public void run() {
            Engine e = Engine.I;
            bar.lv = e.level; bar.invalidate();
            boolean on = e.running;
            btn.setText(on ? "Tắt míc" : "Bật míc"); btn.setBackground(shape(on ? AC : 0xFF6D6585, 0, true));
            if (!e.error.isEmpty()) { status.setTextColor(WARN); status.setText(e.error); }
            else { status.setTextColor(MUT); status.setText(on ? e.info : "Bấm “Bật míc” để bắt đầu."); }
            if ((tickN++ & 15) == 0) updateHint();
            h.postDelayed(this, 60);
        }
    };

    @Override protected void onResume() { super.onResume(); updateHint(); }
    @Override protected void onDestroy() { h.removeCallbacks(tick); super.onDestroy(); }

    void updateHint() {
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        boolean wired = false; String bt = null;
        for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            int t = d.getType();
            if (t == AudioDeviceInfo.TYPE_WIRED_HEADSET || t == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || t == AudioDeviceInfo.TYPE_USB_HEADSET) wired = true;
            if (t == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) bt = String.valueOf(d.getProductName());
        }
        if (bt != null) {
            hint.setTextColor(CY);
            hint.setText("Đang nối loa Bluetooth: " + bt + ". Giọng hát sẽ ra loa qua míc điện thoại. Nhạc phát từ chính điện thoại này sẽ khớp với giọng. Đứng xa loa, nếu bị hú thì hạ âm lượng míc hoặc bật Chống hú.");
        } else if (wired) { hint.setTextColor(CY); hint.setText("Đã nhận tai nghe có dây. Sẵn sàng hát."); }
        else { hint.setTextColor(WARN); hint.setText("Chưa thấy loa Bluetooth hay tai nghe. Hãy nối loa kéo qua Bluetooth trong Cài đặt rồi quay lại. Nếu hát bằng loa điện thoại sẽ bị hú."); }
    }

    void toggle() {
        if (Engine.I.running) { startService(new Intent(this, VangService.class).setAction(VangService.STOP)); return; }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            String[] ps = Build.VERSION.SDK_INT >= 33 ? new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS}
                    : new String[]{Manifest.permission.RECORD_AUDIO};
            requestPermissions(ps, 7); return;
        }
        Engine.I.error = "";
        startForegroundService(new Intent(this, VangService.class).setAction(VangService.START));
    }

    @Override public void onRequestPermissionsResult(int rc, String[] ps, int[] gr) {
        super.onRequestPermissionsResult(rc, ps, gr);
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) toggle();
        else Engine.I.error = "Cần cấp quyền micro trong Cài đặt > Ứng dụng > Vang số Karaoke > Quyền.";
    }
}
