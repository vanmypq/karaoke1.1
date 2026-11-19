# Vang số Karaoke (Android native)
Java thuần, không thư viện ngoài. minSdk 26 (Android 8+), chạy tốt trên Android 10.
## Lấy file APK (không cần Android Studio)
1. Tạo repo GitHub, tải toàn bộ thư mục này lên (giữ nguyên thư mục .github).
2. Vào tab Actions > Build APK > Run workflow (hoặc tự chạy khi push).
3. Khi xong, mở lần chạy đó, tải mục Artifacts "VangSoKaraoke-apk" (zip), giải nén lấy app-debug.apk.
4. Cài lên điện thoại (cho phép cài từ nguồn không rõ).
## Hoặc dùng Android Studio: mở thư mục, Build > Build APK(s).
## Dùng với loa kéo Bluetooth
Nối loa kéo qua Bluetooth trong Cài đặt điện thoại, mở app, bấm Bật míc. App tự dùng míc điện thoại và ép âm thanh ra loa Bluetooth.
Phát nhạc nền từ YouTube/Zing trên chính điện thoại để giọng và nhạc khớp nhau. Bluetooth có độ trễ riêng (~150–300 ms).
Nếu bị hú: đứng xa loa, hạ "Âm lượng míc", giữ bật "Chống hú tự động".
