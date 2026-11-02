# HƯỚNG DẪN DEMO LAB 3: HỆ THỐNG EMAIL CLIENT & SERVER (SMTP & POP3)
**Học phần**: Lập trình mạng (VKU)  
**Tiêu chuẩn**: Giao thức SMTP RFC 5321 (cổng 2525) & POP3 RFC 1939 (cổng 1110), gửi được thư trên Localhost và Gmail thật (smtp.gmail.com:465 SSL), lưu trữ CSDL mail trên Server.

---

## 📌 1. CÁC TỆP NGUỒN (KHÔNG DÙNG FILE BAT ĐÓNG GÓI)
- `LANMailServer.java`: Máy chủ Mail Server đồ họa Java Swing lắng nghe song song hai cổng TCP:
  - Cổng SMTP `2525`: Nhận lệnh `EHLO`, `AUTH LOGIN`, `MAIL FROM`, `RCPT TO`, `DATA`, `QUIT`.
  - Cổng POP3 `1110`: Nhận lệnh `USER`, `PASS`, `STAT`, `LIST`, `RETR`, `DELE`, `QUIT`.
  - CSDL lưu trữ: Thư mục `mail_data/` phân chia từng hộp thư người dùng (`inbox/`, `sent/`, `account_info.txt`).
- `SMTPClient.java`: Trình gửi thư Client đồ họa. Có sẵn 2 chế độ:
  - Chế độ Local: Gửi qua `LANMailServer` cổng 2525.
  - Chế độ Gmail thật: Gửi trực tiếp qua máy chủ Google `smtp.gmail.com:465` (kết nối bảo mật SSLSocket).
- `POP3Client.java`: Trình nhận thư Client đồ họa. Kết nối tới cổng 1110, tải danh sách thư, đọc nội dung thư offline và xóa thư.

---

## 🚀 2. LỆNH CHẠY TRỰC TIẾP (TERMINAL HOẶC IDE)

### Biên dịch:
```bash
cd "D:\Lập trình mạng\network-smtp-pop3-mail"
javac -encoding UTF-8 LANMailServer.java SMTPClient.java POP3Client.java
```

### Bước 1: Khởi động LAN Mail Server
```bash
java LANMailServer
```
*(Server hiển thị IP LAN và tự động nạp các tài khoản: `sv1@vku.udn.vn`, `sv2@vku.udn.vn`, mật khẩu: `123456`)*

### Bước 2: Khởi động SMTP Client (Gửi thư)
```bash
java SMTPClient
```
- **Demo gửi Localhost**: Bấm nút `💻 Cổng 2525 (Local)` -> Bấm `Đăng nhập ngay` -> Nhập người nhận `sv2@vku.udn.vn` -> Soạn thư -> Bấm `Gửi Thư Qua Giao Thức SMTP`.
- **Demo gửi Gmail thật**: Bấm nút `🌐 Cổng 465 (Gmail Thật)` -> Nhập tài khoản Gmail và Mật khẩu ứng dụng (App Password 16 chữ số) -> Bấm `Đăng nhập ngay` -> Gửi thư tới địa chỉ Gmail bất kỳ (ví dụ gửi tới chính điện thoại của bạn hoặc thầy).

### Bước 3: Khởi động POP3 Client (Nhận & Đọc thư)
```bash
java POP3Client
```
- Nhập Host `localhost`, Port `1110`, Email `sv2@vku.udn.vn`, Pass `123456` -> Bấm `Đăng nhập`.
- Danh sách thư vừa gửi xuất hiện ngay lập tức trong Inbox, bấm vào từng thư để đọc nội dung!
