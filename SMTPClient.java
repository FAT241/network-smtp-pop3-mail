
import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class SMTPClient extends JFrame {

    private JTextField txtServer, txtPort, txtUsername, txtTo, txtSubject;
    private JPasswordField txtPassword;
    private JTextArea txtMessage, txtProtocolLog;
    private JButton btnRegister, btnSendMail;

    public SMTPClient() {
        setTitle("SMTP Client - Có Đăng Ký & Đăng Nhập AUTH LOGIN (RFC 4954)");
        setSize(850, 680);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(8, 8));

        // 1. Khu vực thông tin Server, Tài khoản đăng nhập & Người nhận
        JPanel formPanel = new JPanel(new GridLayout(6, 2, 5, 5));
        formPanel.setBorder(BorderFactory.createTitledBorder("Thông tin Đăng nhập SMTP & Người nhận"));

        txtServer = new JTextField("localhost");
        txtPort = new JTextField("2525");
        txtUsername = new JTextField("sv1@vku.udn.vn");
        txtPassword = new JPasswordField("123456");
        txtTo = new JTextField("sv2@vku.udn.vn");
        txtSubject = new JTextField("Thử nghiệm gửi thư có xác thực đăng nhập");

        formPanel.add(new JLabel("SMTP Server Host:"));
        formPanel.add(txtServer);
        formPanel.add(new JLabel("SMTP Port:"));
        formPanel.add(txtPort);
        formPanel.add(new JLabel("Tài khoản người gửi (Email):"));
        formPanel.add(txtUsername);
        formPanel.add(new JLabel("Mật khẩu (Password):"));
        formPanel.add(txtPassword);
        formPanel.add(new JLabel("Người nhận (RCPT TO):"));
        formPanel.add(txtTo);
        formPanel.add(new JLabel("Tiêu đề (Subject):"));
        formPanel.add(txtSubject);

        add(formPanel, BorderLayout.NORTH);

        // 2. Khu vực nội dung thư và Nhật ký giao thức
        txtMessage = new JTextArea("Xin chao,\nDay la email duoc gui sau khi xac thuc AUTH LOGIN thanh cong!");
        JScrollPane scrollMsg = new JScrollPane(txtMessage);
        scrollMsg.setBorder(BorderFactory.createTitledBorder("Nội dung Email (Body)"));

        txtProtocolLog = new JTextArea();
        txtProtocolLog.setEditable(false);
        txtProtocolLog.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollLog = new JScrollPane(txtProtocolLog);
        scrollLog.setBorder(BorderFactory.createTitledBorder("Nhật ký giao thức SMTP (Bao gồm bước AUTH LOGIN Base64)"));

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, scrollMsg, scrollLog);
        splitPane.setDividerLocation(160);
        add(splitPane, BorderLayout.CENTER);

        // 3. Các nút bấm chức năng
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 5));
        btnRegister = new JButton("1. Đăng ký tài khoản mới (Từ ô Tài khoản & Mật khẩu)");
        btnSendMail = new JButton("2. Đăng nhập (AUTH LOGIN) & Gửi Email");
        bottomPanel.add(btnRegister);
        bottomPanel.add(btnSendMail);
        add(bottomPanel, BorderLayout.SOUTH);

        btnRegister.addActionListener(e -> registerNewAccount());
        btnSendMail.addActionListener(e -> loginAndSendEmail());
    }

    // Chức năng 1: Đăng ký tài khoản mới trên Mail Server
    private void registerNewAccount() {
        String host = txtServer.getText().trim();
        int port = Integer.parseInt(txtPort.getText().trim());
        String user = txtUsername.getText().trim();
        String pass = new String(txtPassword.getPassword());

        txtProtocolLog.setText("");
        new Thread(() -> {
            try (Socket socket = new Socket(host, port);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                 PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {

                readResponse(in, "220");
                sendCommand(out, in, "REGISTER " + user + " " + pass, "250");
                sendCommand(out, in, "QUIT", "221");
                log("\n===> ĐĂNG KÝ TÀI KHOẢN '" + user + "' THÀNH CÔNG!");

            } catch (Exception ex) {
                log("\n[LỖI ĐĂNG KÝ]: " + ex.getMessage());
            }
        }).start();
    }

    // Chức năng 2: Đăng nhập xác thực AUTH LOGIN và Gửi Email
    private void loginAndSendEmail() {
        String host = txtServer.getText().trim();
        int port = Integer.parseInt(txtPort.getText().trim());
        String user = txtUsername.getText().trim();
        String pass = new String(txtPassword.getPassword());
        String to = txtTo.getText().trim();
        String subject = txtSubject.getText().trim();
        String body = txtMessage.getText();

        txtProtocolLog.setText("");
        btnSendMail.setEnabled(false);

        new Thread(() -> {
            try (Socket socket = new Socket(host, port);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                 PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {

                // B1: Đọc lời chào 220
                readResponse(in, "220");

                // B2: Gửi lệnh EHLO
                sendCommand(out, in, "EHLO vku.udn.vn", "250");

                // B3: Thực hiện đăng nhập bằng lệnh AUTH LOGIN (RFC 4954)
                sendCommand(out, in, "AUTH LOGIN", "334");

                String base64User = Base64.getEncoder().encodeToString(user.getBytes(StandardCharsets.UTF_8));
                log("CLIENT: " + base64User + "  (Base64 của '" + user + "')");
                out.print(base64User + "\r\n");
                out.flush();
                readResponse(in, "334");

                String base64Pass = Base64.getEncoder().encodeToString(pass.getBytes(StandardCharsets.UTF_8));
                log("CLIENT: " + base64Pass + "  (Base64 của mật khẩu)");
                out.print(base64Pass + "\r\n");
                out.flush();
                readResponse(in, "235"); // 235 = Đăng nhập thành công!

                // B4: Gửi MAIL FROM & RCPT TO
                sendCommand(out, in, "MAIL FROM:<" + user + ">", "250");
                sendCommand(out, in, "RCPT TO:<" + to + ">", "250");

                // B5: Gửi lệnh DATA và nội dung thư
                sendCommand(out, in, "DATA", "354");

                out.print("From: " + user + "\r\n");
                out.print("To: " + to + "\r\n");
                out.print("Subject: " + subject + "\r\n");
                out.print("\r\n");
                out.print(body + "\r\n");
                out.print(".\r\n");
                out.flush();
                log("CLIENT: [Đã gửi Header + Body + dấu chấm kết thúc '.']");
                readResponse(in, "250");

                // B6: Đóng kết nối
                sendCommand(out, in, "QUIT", "221");
                log("\n===> ĐĂNG NHẬP & GỬI EMAIL THÀNH CÔNG!");

            } catch (Exception ex) {
                log("\n[LỖI XÁC THỰC / GỬI THƯ]: " + ex.getMessage());
            } finally {
                SwingUtilities.invokeLater(() -> btnSendMail.setEnabled(true));
            }
        }).start();
    }

    private void sendCommand(PrintWriter out, BufferedReader in, String cmd, String expectedCode) throws IOException {
        log("CLIENT: " + cmd);
        out.print(cmd + "\r\n");
        out.flush();
        readResponse(in, expectedCode);
    }

    private void readResponse(BufferedReader in, String expectedCode) throws IOException {
        String response = in.readLine();
        log("SERVER: " + response);
        if (response == null || !response.startsWith(expectedCode)) {
            throw new IOException("Server từ chối (Mong đợi mã " + expectedCode + "): " + response);
        }
    }

    private void log(String message) {
        SwingUtilities.invokeLater(() -> txtProtocolLog.append(message + "\n"));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new SMTPClient().setVisible(true));
    }
}