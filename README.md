# Lab 3 — SMTP, POP3 và PostgreSQL

## Chạy bằng VS Code

Mở thư mục chứa `pom.xml` bằng VS Code với JDK 21 và Extension Pack for Java. Chờ VS Code nạp project Maven, rồi bấm **Run Java** trên hàm `main` của file cần chạy:

| File | Dùng để |
|---|---|
| [LANMailServer.java](LANMailServer.java) | Bật Mail Server nội bộ, SMTP cổng `2525` và POP3 cổng `1110`. |
| [SMTPClient.java](SMTPClient.java) | Soạn và gửi thư nội bộ hoặc gửi trực tiếp qua Gmail. |
| [POP3Client.java](POP3Client.java) | Mở hộp thư đến của tài khoản trên Mail Server nội bộ. |

Máy server cần PostgreSQL và database `lab3_mail`. Nếu chưa có, tạo database trong pgAdmin và cấp quyền cho role sẽ dùng. Trong cửa sổ LANMailServer, nhập user và mật khẩu PostgreSQL. Server tự tạo bảng và nhập hộp thư cũ từ `mail_data`. Khi chạy bằng `./run.ps1 server`, server vẫn nhận cấu hình PostgreSQL đã lưu hoặc đặt qua biến môi trường.

## Gửi và đọc thư nội bộ

Giữ cửa sổ LANMailServer mở. Chạy SMTP Client bên gửi, POP3 Client bên nhận. Client cùng máy dùng `localhost`. Client máy khác dùng IPv4 Wi-Fi hoặc Ethernet của máy server.

Bạn cũng có thể nhập IP trong ứng dụng hoặc bấm **Tự động quét Server LAN**. Máy server cần cho Java nhận kết nối TCP `2525` (SMTP) và `1110` (POP3) trên mạng LAN tin cậy. Dùng các tài khoản mẫu `sv1@vku.udn.vn` và `sv2@vku.udn.vn` với mật khẩu `123456` nếu server tự tạo hai tài khoản. Tài khoản đã nhập từ dữ liệu cũ dùng mật khẩu cũ. Bạn cũng có thể đăng ký tài khoản thử mới bằng SMTP Client. Đăng nhập bên gửi, gửi thư đến tài khoản nhận rồi đăng nhập POP3 bằng tài khoản nhận. Trong Query Tool của pgAdmin có thể chạy:

```sql
SELECT id, recipient, received_at, content
FROM mail_messages
ORDER BY id DESC;
```

## Gửi qua Gmail

1. Chạy `main` trong `SMTPClient.java`, bấm **Dùng Gmail**.
2. Nhập địa chỉ Gmail và mật khẩu ứng dụng của chính tài khoản đó. [Tạo mật khẩu ứng dụng Google](https://support.google.com/accounts/answer/185833)
3. Đăng nhập, gửi một thư thử tới địa chỉ của bạn rồi xác nhận trong Gmail. SMTP Client kết nối trực tiếp đến `smtp.gmail.com:465` qua TLS. Hộp thư POP3 của Lab 3 dùng cho thư nội bộ. [Cấu hình SMTP của Google](https://support.google.com/a/answer/176600)
