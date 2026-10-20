import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class SMTPClient extends JFrame {

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel mainContainer = new JPanel(cardLayout);

    // Các ô nhập ở màn hình Đăng nhập / Đăng ký
    private JTextField txtAuthServer, txtAuthPort, txtAuthEmail;
    private JPasswordField txtAuthPassword;
    private JLabel lblAuthStatus;

    // Các ô ở màn hình Gửi thư chính (sau khi đã đăng nhập)
    private String loggedInServer;
    private int loggedInPort;
    private String loggedInEmail;
    private String loggedInPassword;

    private JLabel lblCurrentUser;
    private JTextField txtTo, txtSubject;
    private JTextArea txtMessage, txtProtocolLog;
    private JButton btnSendMail;

    public SMTPClient() {
        setTitle("SMTP Client - Hệ thống Gửi Thư (Hỗ trợ Đăng ký / Đăng nhập qua LAN)");
        setSize(860, 650);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Tạo 2 màn hình: "AUTH" (Đăng nhập/Đăng ký) và "MAIL" (Soạn & Gửi thư)
        mainContainer.add(createAuthPanel(), "AUTH");
        mainContainer.add(createMailPanel(), "MAIL");

        add(mainContainer);
        cardLayout.show(mainContainer, "AUTH");
    }

    // ==================== MÀN HÌNH 1: ĐĂNG NHẬP & ĐĂNG KÝ ====================
    private JPanel createAuthPanel() {
        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.setBackground(new Color(240, 244, 248));

        JPanel card = new JPanel(new BorderLayout(10, 15));
        card.setPreferredSize(new Dimension(520, 380));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 210, 220), 1),
                BorderFactory.createEmptyBorder(20, 25, 20, 25)
        ));

        JLabel title = new JLabel("ĐĂNG NHẬP / ĐĂNG KÝ SMTP MAIL", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 18));
        title.setForeground(new Color(15, 23, 42));
        card.add(title, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridLayout(4, 2, 10, 15));
        form.setBackground(Color.WHITE);

        txtAuthServer = new JTextField("localhost");
        txtAuthPort = new JTextField("2525");
        txtAuthEmail = new JTextField("sv1@vku.udn.vn");
        txtAuthPassword = new JPasswordField("123456");

        form.add(new JLabel("IP Máy chủ LAN (Server Host):"));
        form.add(txtAuthServer);
        form.add(new JLabel("Cổng SMTP (Port):"));
        form.add(txtAuthPort);
        form.add(new JLabel("Tài khoản Email:"));
        form.add(txtAuthEmail);
        form.add(new JLabel("Mật khẩu:"));
        form.add(txtAuthPassword);

        card.add(form, BorderLayout.CENTER);

        JPanel southPanel = new JPanel(new GridLayout(3, 1, 5, 8));
        southPanel.setBackground(Color.WHITE);

        lblAuthStatus = new JLabel("Nhập IP của máy chạy LANMailServer để Đăng nhập hoặc Đăng ký.", SwingConstants.CENTER);
        lblAuthStatus.setForeground(new Color(100, 116, 139));

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        btnPanel.setBackground(Color.WHITE);
        JButton btnLogin = new JButton("Đăng nhập ngay");
        JButton btnRegister = new JButton("Đăng ký tài khoản mới");
        JButton btnHelp = new JButton("? Hướng dẫn sử dụng");

        btnLogin.setBackground(new Color(37, 99, 235));
        btnLogin.setForeground(Color.WHITE);
        btnLogin.setFocusPainted(false);

        btnPanel.add(btnLogin);
        btnPanel.add(btnRegister);

        JPanel helpPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        helpPanel.setBackground(Color.WHITE);
        helpPanel.add(btnHelp);

        southPanel.add(lblAuthStatus);
        southPanel.add(btnPanel);
        southPanel.add(helpPanel);

        card.add(southPanel, BorderLayout.SOUTH);
        wrapper.add(card);

        // Sự kiện nút bấm
        btnLogin.addActionListener(e -> performLogin());
        btnRegister.addActionListener(e -> performRegister());
        btnHelp.addActionListener(e -> showHelpDialog());

        return wrapper;
    }

    // ==================== MÀN HÌNH 2: SOẠN & GỬI THƯ (SAU KHI ĐĂNG NHẬP) ====================
    private JPanel createMailPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Thanh trạng thái tài khoản phía trên cùng
        JPanel userBar = new JPanel(new BorderLayout());
        userBar.setBackground(new Color(224, 242, 254));
        userBar.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        lblCurrentUser = new JLabel("Đã đăng nhập: ");
        lblCurrentUser.setFont(new Font("SansSerif", Font.BOLD, 13));
        lblCurrentUser.setForeground(new Color(3, 105, 161));

        JPanel rightBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightBtns.setOpaque(false);
        JButton btnHelp = new JButton("? Hướng dẫn");
        JButton btnLogout = new JButton("Đăng xuất / Đổi tài khoản");
        rightBtns.add(btnHelp);
        rightBtns.add(btnLogout);

        userBar.add(lblCurrentUser, BorderLayout.WEST);
        userBar.add(rightBtns, BorderLayout.EAST);

        // Form nhập Người nhận & Tiêu đề
        JPanel mailHeaderPanel = new JPanel(new GridLayout(2, 2, 8, 8));
        mailHeaderPanel.setBorder(BorderFactory.createTitledBorder("Thông tin người nhận"));
        txtTo = new JTextField("sv2@vku.udn.vn");
        txtSubject = new JTextField("Bao cao Bai Lab Giao thuc SMTP & POP3");

        mailHeaderPanel.add(new JLabel("Gửi tới Email (RCPT TO):"));
        mailHeaderPanel.add(txtTo);
        mailHeaderPanel.add(new JLabel("Tiêu đề thư (Subject):"));
        mailHeaderPanel.add(txtSubject);

        JPanel northContainer = new JPanel(new BorderLayout(5, 8));
        northContainer.add(userBar, BorderLayout.NORTH);
        northContainer.add(mailHeaderPanel, BorderLayout.CENTER);
        panel.add(northContainer, BorderLayout.NORTH);

        // Khung soạn nội dung và Nhật ký lệnh SMTP
        txtMessage = new JTextArea("Xin chao,\nDay la thu duoc gui tu SMTP Client sau khi xac thuc AUTH LOGIN thanh cong!");
        JScrollPane scrollMsg = new JScrollPane(txtMessage);
        scrollMsg.setBorder(BorderFactory.createTitledBorder("Nội dung bức thư (Body)"));

        txtProtocolLog = new JTextArea();
        txtProtocolLog.setEditable(false);
        txtProtocolLog.setFont(new Font("Consolas", Font.PLAIN, 13));
        JScrollPane scrollLog = new JScrollPane(txtProtocolLog);
        scrollLog.setBorder(BorderFactory.createTitledBorder("Nhật ký giao thức SMTP (TCP Socket Log)"));

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, scrollMsg, scrollLog);
        splitPane.setDividerLocation(180);
        panel.add(splitPane, BorderLayout.CENTER);

        btnSendMail = new JButton("Gửi Email qua Giao thức SMTP");
        btnSendMail.setFont(new Font("SansSerif", Font.BOLD, 14));
        btnSendMail.setBackground(new Color(22, 163, 74));
        btnSendMail.setForeground(Color.WHITE);
        panel.add(btnSendMail, BorderLayout.SOUTH);

        btnLogout.addActionListener(e -> cardLayout.show(mainContainer, "AUTH"));
        btnHelp.addActionListener(e -> showHelpDialog());
        btnSendMail.addActionListener(e -> sendEmailProcess());

        return panel;
    }

    // Xử lý Đăng ký tài khoản mới lên LANMailServer
    private void performRegister() {
        String host = txtAuthServer.getText().trim();
        int port = Integer.parseInt(txtAuthPort.getText().trim());
        String email = txtAuthEmail.getText().trim().toLowerCase();
        String pass = new String(txtAuthPassword.getPassword());

        if (email.isEmpty() || pass.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập đầy đủ Email và Mật khẩu!");
            return;
        }

        lblAuthStatus.setText("Đang kết nối tới Server để đăng ký tài khoản...");
        new Thread(() -> {
            try (Socket socket = new Socket(host, port);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                 PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {

                String greeting = in.readLine();
                out.print("REGISTER " + email + " " + pass + "\r\n");
                out.flush();
                String res = in.readLine();

                out.print("QUIT\r\n");
                out.flush();

                SwingUtilities.invokeLater(() -> {
                    if (res != null && res.startsWith("250")) {
                        JOptionPane.showMessageDialog(this,
                                "Đăng ký tài khoản [" + email + "] thành công!\nHệ thống sẽ tự động đăng nhập cho bạn.",
                                "Đăng ký thành công", JOptionPane.INFORMATION_MESSAGE);
                        performLogin(); // Tự động đăng nhập luôn sau khi đăng ký
                    } else {
                        lblAuthStatus.setText("Đăng ký thất bại: " + res);
                        JOptionPane.showMessageDialog(this, "Không thể đăng ký: " + res, "Lỗi", JOptionPane.ERROR_MESSAGE);
                    }
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    lblAuthStatus.setText("Lỗi kết nối Server: " + ex.getMessage());
                    JOptionPane.showMessageDialog(this, "Không kết nối được tới Server (" + host + ":" + port + ")\nChi tiết: " + ex.getMessage());
                });
            }
        }).start();
    }

    // Xử lý kiểm tra Đăng nhập bằng lệnh AUTH LOGIN (RFC 4954)
    private void performLogin() {
        String host = txtAuthServer.getText().trim();
        int port = Integer.parseInt(txtAuthPort.getText().trim());
        String email = txtAuthEmail.getText().trim().toLowerCase();
        String pass = new String(txtAuthPassword.getPassword());

        lblAuthStatus.setText("Đang xác thực tài khoản với SMTP Server...");
        new Thread(() -> {
            try (Socket socket = new Socket(host, port);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                 PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {

                readExpected(in, "220");
                out.print("EHLO vku.udn.vn\r\n");
                out.flush();
                readExpected(in, "250");

                out.print("AUTH LOGIN\r\n");
                out.flush();
                readExpected(in, "334");

                out.print(Base64.getEncoder().encodeToString(email.getBytes(StandardCharsets.UTF_8)) + "\r\n");
                out.flush();
                readExpected(in, "334");

                out.print(Base64.getEncoder().encodeToString(pass.getBytes(StandardCharsets.UTF_8)) + "\r\n");
                out.flush();
                String authRes = in.readLine();

                out.print("QUIT\r\n");
                out.flush();

                SwingUtilities.invokeLater(() -> {
                    if (authRes != null && authRes.startsWith("235")) {
                        loggedInServer = host;
                        loggedInPort = port;
                        loggedInEmail = email;
                        loggedInPassword = pass;

                        lblCurrentUser.setText("Đang đăng nhập: " + loggedInEmail + "  |  Máy chủ: " + loggedInServer + ":" + loggedInPort);
                        txtProtocolLog.setText("=== ĐÃ XÁC THỰC AUTH LOGIN THÀNH CÔNG VỚI TÀI KHOẢN: " + loggedInEmail + " ===\n");
                        cardLayout.show(mainContainer, "MAIL");
                    } else {
                        lblAuthStatus.setText("Sai tài khoản hoặc mật khẩu!");
                        JOptionPane.showMessageDialog(this, "Đăng nhập thất bại: Sai tài khoản hoặc mật khẩu!\n(Nếu chưa có tài khoản, hãy bấm 'Đăng ký tài khoản mới')", "Lỗi xác thực", JOptionPane.ERROR_MESSAGE);
                    }
                });

            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    lblAuthStatus.setText("Lỗi kết nối: " + ex.getMessage());
                    JOptionPane.showMessageDialog(this, "Lỗi kết nối tới Server: " + ex.getMessage());
                });
            }
        }).start();
    }

    // Quá trình gửi thư sau khi đã ở trong màn hình chính
    private void sendEmailProcess() {
        String to = txtTo.getText().trim();
        String subject = txtSubject.getText().trim();
        String body = txtMessage.getText();

        txtProtocolLog.setText("");
        btnSendMail.setEnabled(false);

        new Thread(() -> {
            try (Socket socket = new Socket(loggedInServer, loggedInPort);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                 PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {

                readAndLog(in, "220");
                sendAndLog(out, in, "EHLO vku.udn.vn", "250");
                sendAndLog(out, in, "AUTH LOGIN", "334");

                String b64User = Base64.getEncoder().encodeToString(loggedInEmail.getBytes(StandardCharsets.UTF_8));
                log("CLIENT: " + b64User + " (Base64 User)");
                out.print(b64User + "\r\n");
                out.flush();
                readAndLog(in, "334");

                String b64Pass = Base64.getEncoder().encodeToString(loggedInPassword.getBytes(StandardCharsets.UTF_8));
                log("CLIENT: " + b64Pass + " (Base64 Pass)");
                out.print(b64Pass + "\r\n");
                out.flush();
                readAndLog(in, "235");

                sendAndLog(out, in, "MAIL FROM:<" + loggedInEmail + ">", "250");
                sendAndLog(out, in, "RCPT TO:<" + to + ">", "250");
                sendAndLog(out, in, "DATA", "354");

                out.print("From: " + loggedInEmail + "\r\n");
                out.print("To: " + to + "\r\n");
                out.print("Subject: " + subject + "\r\n");
                out.print("\r\n");
                out.print(body + "\r\n");
                out.print(".\r\n");
                out.flush();
                log("CLIENT: [Đã gửi Header + Body + dấu chấm '.' kết thúc DATA]");
                readAndLog(in, "250");

                sendAndLog(out, in, "QUIT", "221");
                log("\n===> GỬI THƯ THÀNH CÔNG TỚI [" + to + "]!");

            } catch (Exception ex) {
                log("\n[LỖI KHI GỬI THƯ]: " + ex.getMessage());
            } finally {
                SwingUtilities.invokeLater(() -> btnSendMail.setEnabled(true));
            }
        }).start();
    }

    private void showHelpDialog() {
        String guide = """
                HƯỚNG DẪN SỬ DỤNG HỆ THỐNG MAIL (NHÓM 2)
                =========================================================
                1. KHỞI ĐỘNG SERVER (Trên 1 máy làm máy chủ):
                   - Chạy file LANMailServer.java trước tiên.
                   - Xem địa chỉ IP LAN hiện trên chữ màu xanh của Server (VD: 192.168.43.10).
                
                2. ĐĂNG KÝ / ĐĂNG NHẬP TÀI KHOẢN:
                   - Nhập IP của máy Server vào ô 'IP Máy chủ LAN' (nếu chạy cùng 1 máy thì để localhost).
                   - Tài khoản có sẵn: sv1@vku.udn.vn (Mật khẩu: 123456) và sv2@vku.udn.vn (Mật khẩu: 123456).
                   - Muốn tạo tài khoản mới: Nhập email bất kỳ (VD: phat@vku.udn.vn) + mật khẩu -> Bấm 'Đăng ký tài khoản mới'.
                
                3. GỬI VÀ NHẬN THƯ:
                   - Lưu ý: Tài khoản người nhận (RCPT TO) phải là tài khoản đã được đăng ký trên Server.
                   - Sau khi gửi thư xong ở SMTPClient, mở POP3Client đăng nhập đúng tài khoản người nhận để tải thư về đọc.
                """;
        JOptionPane.showMessageDialog(this, guide, "Hướng dẫn sử dụng SMTP & POP3", JOptionPane.INFORMATION_MESSAGE);
    }

    private void readExpected(BufferedReader in, String code) throws IOException {
        String res = in.readLine();
        if (res == null || !res.startsWith(code)) throw new IOException(res);
    }

    private void sendAndLog(PrintWriter out, BufferedReader in, String cmd, String code) throws IOException {
        log("CLIENT: " + cmd);
        out.print(cmd + "\r\n");
        out.flush();
        readAndLog(in, code);
    }

    private void readAndLog(BufferedReader in, String code) throws IOException {
        String res = in.readLine();
        log("SERVER: " + res);
        if (res == null || !res.startsWith(code)) {
            throw new IOException("Server từ chối (Cần mã " + code + "): " + res);
        }
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> txtProtocolLog.append(msg + "\n"));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new SMTPClient().setVisible(true));
    }
}