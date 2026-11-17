# Lab 3 — SMTP, POP3 và PostgreSQL

## Chạy bằng VS Code

Mở thư mục chứa `pom.xml` bằng VS Code với JDK 21 và Extension Pack for Java. Chờ VS Code nạp project Maven, rồi bấm **Run Java** trên hàm `main` của file cần chạy:

| File | Dùng để |
|---|---|
| [LANMailServer.java](LANMailServer.java) | Bật SMTP `2525`, POP3 `1110`, cấu hình Gmail chuyển tiếp và xem thư đã gửi/file đính kèm. |
| [SMTPClient.java](SMTPClient.java) | Soạn và gửi thư nội bộ hoặc gửi trực tiếp qua Gmail. |
| [POP3Client.java](POP3Client.java) | Mở hộp thư đến/thư đã gửi của tài khoản nội bộ, đọc nội dung và lưu file. |

Máy server cần PostgreSQL và database `lab3_mail`. Nếu chưa có, tạo database trong pgAdmin và cấp quyền cho role sẽ dùng. Trong cửa sổ LANMailServer, nhập user và mật khẩu PostgreSQL. Server tự tạo bảng và nhập hộp thư cũ từ `mail_data`. Khi chạy bằng `./run.ps1 server`, server vẫn nhận cấu hình PostgreSQL đã lưu hoặc đặt qua biến môi trường.

## Gửi và đọc thư nội bộ

Giữ cửa sổ LANMailServer mở. Chạy SMTP Client bên gửi, POP3 Client bên nhận. Client cùng máy dùng `localhost`. Client máy khác dùng IPv4 Wi-Fi hoặc Ethernet của máy server.

Bạn cũng có thể nhập IP trong ứng dụng hoặc bấm **Tự động quét Server LAN**. Máy server cần cho Java nhận kết nối TCP `2525` (SMTP) và `1110` (POP3) trên mạng LAN tin cậy. Dùng các tài khoản mẫu `sv1@vku.udn.vn` và `sv2@vku.udn.vn` với mật khẩu `123456` nếu server tự tạo hai tài khoản. Tài khoản đã nhập từ dữ liệu cũ dùng mật khẩu cũ. Bạn cũng có thể đăng ký tài khoản thử mới bằng SMTP Client. Đăng nhập bên gửi, gửi thư đến tài khoản nhận rồi đăng nhập POP3 bằng tài khoản nhận. Trong Query Tool của pgAdmin có thể chạy:

```sql
SELECT id, recipient, received_at, content
FROM mail_messages
ORDER BY id DESC;
```

## Tài khoản nội bộ gửi thư và file tới Gmail thật

1. Chạy `main` của `LANMailServer.java`, kết nối PostgreSQL.
2. Trên cửa sổ server, bấm **Cấu hình Gmail chuyển thư...**. Nhập Gmail trung gian của bạn và **Mật khẩu ứng dụng Google** (không phải mật khẩu tài khoản nội bộ hoặc PostgreSQL). Bật xác minh hai bước để tạo mật khẩu ứng dụng: https://support.google.com/accounts/answer/185833 . Server kiểm tra kết nối TLS `smtp.gmail.com:465`. Thông tin Gmail chỉ giữ trong bộ nhớ đến khi tắt server, không ghi vào Git/DB.
3. Chạy `main` của `SMTPClient.java`, giữ host `localhost` hoặc IP máy server, port `2525`. Đăng nhập/đăng ký tài khoản nội bộ. Không chọn **Dùng Gmail** trong luồng này.
4. Nhập email thật của người nhận, tiêu đề, nội dung; bấm **Đính kèm file...** rồi **Gửi Email**. Mỗi thư một người nhận, một file tối đa 14 MiB; tổng MIME tối đa 20 MiB.
5. Người nhận mở Gmail kiểm tra Inbox/Spam và tải file. Có file được Gmail chặn theo chính sách của Google; server sẽ hiển thị phản hồi nếu SMTP từ chối.
6. Trên server, bấm **Thư đã gửi / File đính kèm**, chọn thư rồi **Xem thư / Lưu file đính kèm**. Mở lại bảng để làm mới. Hoặc chạy `POP3Client.java`, đăng nhập tài khoản nội bộ bên gửi, chọn **Thư đã gửi**, chọn thư và bấm **Xem thư / Lưu file**. Hộp thư đến vẫn dùng POP3 như trước.

Luồng: tài khoản nội bộ → SMTP server của bài lab → Gmail SMTP trung gian → mail thật của người nhận. PostgreSQL lưu bản MIME đầy đủ gồm file đính kèm trong bảng `mail_sent`. Gmail dùng địa chỉ trung gian làm `From`; tên hiển thị và `X-Lab-Sender` ghi tài khoản nội bộ. Reply từ Gmail sẽ về Gmail trung gian, không tự về tài khoản ảo.

Địa chỉ ảo đăng ký trong bài lab không trở thành địa chỉ Google thật. Nếu tạo tài khoản nội bộ trùng email người nhận, thư đến địa chỉ đó được xử lý nội bộ; hãy dùng miền riêng cho tài khoản lab như `an@lab.test`.

### Trạng thái lưu trên server

- `LOCAL_DELIVERED`: đã lưu vào hộp thư người nhận nội bộ.
- `RELAY_ACCEPTED`: Gmail SMTP đã chấp nhận chuyển tiếp, chưa phải bằng chứng đã vào Inbox của người nhận.
- `PENDING`: bản sao đã lưu trước khi gửi; nếu ứng dụng dừng giữa chừng cần kiểm tra Gmail trước khi gửi lại.
- `AUTH_FAILED`: Gmail từ chối xác thực.
- `UNKNOWN_CHECK_GMAIL`: có lỗi khi chuyển thư, có thể chưa gửi hoặc mất kết nối sau khi Gmail đã nhận; kiểm tra Gmail trước khi gửi lại. Ứng dụng không tự gửi lại để tránh trùng thư.

```sql
SELECT id, sender, recipients, status, created_at FROM mail_sent ORDER BY id DESC;
```

Chỉ máy server cần PostgreSQL/Gmail trung gian. Máy bạn dùng client qua LAN. POP3 dùng lệnh mở rộng `XBOX SENT` cho thư đã gửi, `XBOX INBOX` cho hộp thư đến; thư đã gửi chỉ đọc, không áp dụng `DELE`.

Nút **Dùng Gmail** ở SMTP Client vẫn hỗ trợ gửi trực tiếp qua tài khoản Gmail nhưng bỏ qua server bài lab và không lưu vào PostgreSQL. Khi demo yêu cầu vừa tới mail thật vừa xem trên hệ thống, dùng luồng qua server ở trên.

## Kiểm thử

`tests/MailIntegrationTest.java` là bài kiểm tra tích hợp dùng schema PostgreSQL tạm và relay giả; không gửi Internet mail. Cần biến môi trường `LAB3_DB_USER`, `LAB3_DB_PASSWORD` và tùy chọn `LAB3_DB_URL`; tài khoản kiểm thử cần quyền tạo schema. Chạy từ thư mục project:

```text
mvn compile dependency:copy-dependencies
javac -encoding UTF-8 -cp "target/classes;target/dependency/*" -d target/test-classes tests/MailIntegrationTest.java
java -cp "target/test-classes;target/classes;target/dependency/*" MailIntegrationTest
```

Kiểm tra SMTP AUTH, thư nội bộ, chuyển tiếp thành công/thất bại, tiếng Việt, file khớp từng byte, PostgreSQL lưu trạng thái và POP3 phân quyền thư đã gửi. Demo gửi Gmail thật cần tài khoản trung gian hợp lệ và xác nhận ở hộp thư người nhận.
