# Lab 3 — SMTP, POP3 và PostgreSQL

## Máy chạy server

1. Cài JDK 21 và PostgreSQL. Trong pgAdmin, tạo role đăng nhập `lab3_mail` có mật khẩu, rồi tạo database `lab3_mail` với role đó làm owner.
2. Nếu phiên LANMailServer cũ còn mở, đóng cửa sổ đó để giải phóng cổng 2525 và 1110. Từ PowerShell 7 trong thư mục project, chạy:

   ```powershell
   $env:LAB3_DB_USER = 'lab3_mail'
   $env:LAB3_DB_PASSWORD = Read-Host 'Mật khẩu PostgreSQL' -MaskInput
   ./run.ps1 server
   ```

   JDBC dùng `jdbc:postgresql://127.0.0.1:5432/lab3_mail` mặc định. Có thể đặt `LAB3_DB_URL` nếu PostgreSQL chạy trên cổng khác. Server tự tạo bảng và nhập tài khoản/thư cũ từ các thư mục hộp thư trong `mail_data`. Dữ liệu nhập được đánh dấu để lần khởi động sau không nhập trùng hoặc phục hồi thư đã xóa. PostgreSQL chỉ cần chạy trên máy server.

## Gửi và đọc thư nội bộ

Cùng máy, mở hai cửa sổ PowerShell và chạy `./run.ps1 smtp` và `./run.ps1 pop3`. Trên máy bạn cùng LAN, thay `192.168.1.20` bằng IPv4 Wi-Fi hoặc Ethernet của máy chạy server:

```powershell
./run.ps1 smtp -Server 192.168.1.20
./run.ps1 pop3 -Server 192.168.1.20
```

Bạn cũng có thể nhập IP trong ứng dụng hoặc bấm **Tự động quét Server LAN**. Máy server cần cho Java nhận kết nối TCP `2525` (SMTP) và `1110` (POP3) trên mạng LAN tin cậy. Dùng các tài khoản mẫu `sv1@vku.udn.vn` và `sv2@vku.udn.vn` với mật khẩu `123456` nếu server tự tạo hai tài khoản. Tài khoản đã nhập từ dữ liệu cũ dùng mật khẩu cũ. Bạn cũng có thể đăng ký tài khoản thử mới bằng SMTP Client. Đăng nhập bên gửi, gửi thư đến tài khoản nhận rồi đăng nhập POP3 bằng tài khoản nhận. Trong Query Tool của pgAdmin có thể chạy:

```sql
SELECT id, recipient, received_at, content
FROM mail_messages
ORDER BY id DESC;
```

## Gửi qua Gmail

1. Mở `./run.ps1 smtp`, bấm **Dùng Gmail**.
2. Nhập địa chỉ Gmail và mật khẩu ứng dụng của chính tài khoản đó. [Tạo mật khẩu ứng dụng Google](https://support.google.com/accounts/answer/185833)
3. Đăng nhập, gửi một thư thử tới địa chỉ của bạn rồi xác nhận trong Gmail. SMTP Client kết nối trực tiếp đến `smtp.gmail.com:465` qua TLS. Hộp thư POP3 của Lab 3 dùng cho thư nội bộ. [Cấu hình SMTP của Google](https://support.google.com/a/answer/176600)
