package com.vanmypq.vangso;

import android.content.Context;
import android.media.*;
import android.os.Process;

/** Thu micro -> Dsp -> phát ra tai nghe, độ trễ thấp (AudioRecord + AudioTrack LOW_LATENCY). */
public final class Engine {
    public static final Engine I = new Engine();
    public final Dsp.Params params = new Dsp.Params();
    public volatile boolean running;
    public volatile String error = "", info = "";
    public volatile float level;
    private volatile boolean stopFlag;
    private Thread th;
    private AudioRecord rec;
    private AudioTrack trk;
    private Dsp.Chain chain;

    private static int num(String s, int d) { try { return Integer.parseInt(s); } catch (Exception e) { return d; } }

    public synchronized boolean start(Context c) {
        if (running) return true;
        error = "";
        try {
            AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            int sr = num(am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE), 48000);
            final int chunk = Math.max(64, num(am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER), 192));
            int minIn = AudioRecord.getMinBufferSize(sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            int minOut = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (minIn <= 0 || minOut <= 0) throw new IllegalStateException("Máy không hỗ trợ cấu hình âm thanh này");
            boolean raw = "true".equals(am.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED));
            AudioDeviceInfo mic = null, bt = null, wired = null;
            for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_INPUTS)) if (d.getType() == AudioDeviceInfo.TYPE_BUILTIN_MIC) { mic = d; break; }
            for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                int t = d.getType();
                if (t == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) bt = d;
                else if (t == AudioDeviceInfo.TYPE_WIRED_HEADSET || t == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || t == AudioDeviceInfo.TYPE_USB_HEADSET) wired = d;
            }
            rec = new AudioRecord(raw ? MediaRecorder.AudioSource.UNPROCESSED : MediaRecorder.AudioSource.MIC, sr,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(minIn, chunk * 4));
            if (rec.getState() != AudioRecord.STATE_INITIALIZED) throw new IllegalStateException("Không mở được micro (đã cấp quyền chưa?)");
            trk = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sr).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(Math.max(minOut, chunk * (bt != null ? 12 : 4)))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY).build();
            if (mic != null) rec.setPreferredDevice(mic);          // luôn dùng míc của điện thoại, không dùng míc của loa
            AudioDeviceInfo out = bt != null ? bt : wired;
            if (out != null) trk.setPreferredDevice(out);          // ép đầu ra sang loa Bluetooth / tai nghe
            chain = new Dsp.Chain(sr, params);
            int est = (int) (1000L * (chunk * 2 + trk.getBufferSizeInFrames()) / sr);
            String dest = bt != null ? "loa Bluetooth " + bt.getProductName() : wired != null ? "tai nghe có dây" : "loa điện thoại";
            info = "Đang hát → " + dest + " · độ trễ máy ≥ " + est + " ms" + (bt != null ? " (+ Bluetooth ~150–300 ms)" : "");
            stopFlag = false;
            running = true;
            th = new Thread(() -> loop(chunk), "vang-audio");
            th.start();
            return true;
        } catch (Throwable e) {
            error = "Không bật được: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            release();
            running = false;
            return false;
        }
    }

    private void loop(int chunk) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
        short[] in = new short[chunk], out = new short[chunk];
        try {
            rec.startRecording();
            trk.play();
            while (!stopFlag) {
                int n = rec.read(in, 0, chunk);
                if (n < 0) { error = "Micro bị ngắt (mã " + n + ")"; break; }
                if (n == 0) continue;
                chain.process(in, out, n);
                level = chain.peak;
                trk.write(out, 0, n);
            }
        } catch (Throwable e) {
            error = "Lỗi âm thanh: " + e.getMessage();
        } finally {
            release();
            level = 0;
            running = false;
        }
    }

    private void release() {
        try { if (rec != null) { rec.stop(); rec.release(); } } catch (Throwable ignored) {}
        try { if (trk != null) { trk.stop(); trk.release(); } } catch (Throwable ignored) {}
        rec = null; trk = null;
    }

    public void stop() {
        stopFlag = true;
        Thread t = th;
        if (t != null && t != Thread.currentThread()) { try { t.join(600); } catch (InterruptedException ignored) {} }
        th = null;
    }
}
