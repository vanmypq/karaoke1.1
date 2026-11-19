package com.vanmypq.vangso;

import android.app.*;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/** Dịch vụ chạy nền để míc vẫn hoạt động khi bạn mở YouTube/Zing phát nhạc. */
public class VangService extends Service {
    public static final String START = "vang.start", STOP = "vang.stop";

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent in, int flags, int id) {
        if (in != null && STOP.equals(in.getAction())) {
            Engine.I.stop();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel("vang", "Vang số", NotificationManager.IMPORTANCE_LOW));
        PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, "vang").setContentTitle("Vang số đang bật")
                .setContentText("Chạm để mở và chỉnh âm").setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentIntent(pi).setOngoing(true).build();
        if (Build.VERSION.SDK_INT >= 30) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        else startForeground(1, n);
        if (!Engine.I.start(this)) { stopForeground(true); stopSelf(); }
        return START_NOT_STICKY;
    }

    @Override public void onDestroy() { Engine.I.stop(); super.onDestroy(); }
}
