import java.util.StringJoiner;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class SMTPClient extends JFrame {

    private static final String GMAIL_HOST = "smtp.gmail.com";
    private static final int GMAIL_PORT = 465;

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel mainContainer = new JPanel(cardLayout);

    // Các ô nhập ở màn hình Đăng nhập / Đăng ký
    private JTextField txtAuthServer, txtAuthPort, txtAuthEmail;
    private JPasswordField txtAuthPassword;
    private JLabel lblAuthStatus;
    private JButton btnScan;

    // Các ô ở màn hình Gửi thư chính (sau khi đã đăng nhập)
    private String loggedInServer;
    private int loggedInPort;
    private String loggedInEmail;
    private String loggedInPassword;

    private JLabel lblCurrentUser;
    private JTextField txtTo, txtSubject;
    private JTextArea txtMessage, txtProtocolLog;
    private JButton btnSendMail;
    private File attachedFile;

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
        btnScan = new JButton("Tự động quét Server LAN");
        helpPanel.add(btnScan);
        helpPanel.add(btnHelp);
        JButton btnGmail = new JButton("Dùng Gmail");
        helpPanel.add(btnGmail);
        btnGmail.addActionListener(e -> {
            boolean gmail = txtAuthServer.isEditable();
            txtAuthServer.setText(gmail ? GMAIL_HOST : "localhost");
            txtAuthPort.setText(gmail ? Integer.toString(GMAIL_PORT) : "2525");
            txtAuthServer.setEditable(!gmail);
            txtAuthPort.setEditable(!gmail);
            btnRegister.setEnabled(!gmail);
            btnScan.setEnabled(!gmail);
            btnGmail.setText(gmail ? "Dùng máy chủ LAN" : "Dùng Gmail");
            txtAuthEmail.setText(gmail ? "" : "sv1@vku.udn.vn");
            txtAuthPassword.setText("");
            lblAuthStatus.setText(gmail
                    ? "Nhập địa chỉ Gmail và Mật khẩu ứng dụng rồi bấm Đăng nhập."
                    : "Nhập địa chỉ máy chủ LAN để đăng nhập hoặc đăng ký.");
            txtAuthEmail.requestFocusInWindow();
        });

        southPanel.add(lblAuthStatus);
        southPanel.add(btnPanel);
        southPanel.add(helpPanel);

        card.add(southPanel, BorderLayout.SOUTH);
        wrapper.add(card);

        // Sự kiện nút bấm
        btnLogin.addActionListener(e -> performLogin());
        btnRegister.addActionListener(e -> performRegister());
        btnScan.addActionListener(e -> autoScanLanServer());
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
        JPanel bottom = new JPanel(new BorderLayout(8, 8));
        JPanel files = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton attach = new JButton("Đính kèm file...");
        JButton remove = new JButton("Bỏ file");
        JLabel selectedFile = new JLabel("Chưa chọn file (tối đa 14 MiB)");
        files.add(attach);
        files.add(remove);
        files.add(selectedFile);
        attach.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File candidate = chooser.getSelectedFile();
                if (!candidate.isFile() || candidate.length() > 14 * 1024 * 1024) {
                    JOptionPane.showMessageDialog(this, "Chọn một file tối đa 14 MiB.");
                    return;
                }
                attachedFile = candidate;
                selectedFile.setText(candidate.getName());
            }
        });
        remove.addActionListener(e -> { attachedFile = null; selectedFile.setText("Chưa chọn file"); });
        bottom.add(files, BorderLayout.NORTH);
        bottom.add(btnSendMail, BorderLayout.SOUTH);
        panel.add(bottom, BorderLayout.SOUTH);

        btnLogout.addActionListener(e -> {
            loggedInPassword = null;
            if (isGmail(loggedInServer)) txtAuthPassword.setText("");
            cardLayout.show(mainContainer, "AUTH");
        });
        btnHelp.addActionListener(e -> showHelpDialog());
        btnSendMail.addActionListener(e -> sendEmailProcess());

        return panel;
    }

    // Xử lý Đăng ký tài khoản mới lên LANMailServer
    private void performRegister() {
        String host = txtAuthServer.getText().trim();
        if (isGmail(host)) {
            lblAuthStatus.setText("Dùng tài khoản Google có sẵn để đăng nhập Gmail.");
            return;
        }
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
        int port;
        try {
            port = Integer.parseInt(txtAuthPort.getText().trim());
            if (port < 1 || port > 65535 || isGmail(host) && port != GMAIL_PORT) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            lblAuthStatus.setText(isGmail(host) ? "Gmail dùng cổng TLS 465." : "Cổng SMTP không hợp lệ.");
            return;
        }
        String email = txtAuthEmail.getText().trim().toLowerCase();
        if (!email.matches("[^\\s@<>]+@[^\\s@<>]+")) {
            lblAuthStatus.setText("Địa chỉ email không hợp lệ.");
            return;
        }
        String inputPassword = new String(txtAuthPassword.getPassword());
        String pass = isGmail(host) ? inputPassword.replace(" ", "") : inputPassword;

        lblAuthStatus.setText("Đang xác thực tài khoản với SMTP Server...");
        new Thread(() -> {
            try (Socket socket = connectSmtp(host, port);
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
                String authRes = readSmtpReply(in);

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
                        JOptionPane.showMessageDialog(this, isGmail(host)
                                ? "Gmail từ chối xác thực. Kiểm tra địa chỉ và Mật khẩu ứng dụng.\n" + authRes
                                : "Đăng nhập thất bại: Sai tài khoản hoặc mật khẩu!",
                                "Lỗi xác thực", JOptionPane.ERROR_MESSAGE);
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
        File file = attachedFile;

        if (!to.matches("[^\\s@<>]+@[^\\s@<>]+") || subject.contains("\r") || subject.contains("\n")) {
            JOptionPane.showMessageDialog(this, "Địa chỉ người nhận hoặc tiêu đề không hợp lệ.");
            return;
        }
        String host = loggedInServer;
        int port = loggedInPort;
        String email = loggedInEmail;
        String pass = loggedInPassword;
        txtProtocolLog.setText("");
        btnSendMail.setEnabled(false);

        new Thread(() -> {
            try (Socket socket = connectSmtp(host, port);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                 PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {

                readAndLog(in, "220");
                sendAndLog(out, in, "EHLO vku.udn.vn", "250");
                sendAndLog(out, in, "AUTH LOGIN", "334");

                String b64User = Base64.getEncoder().encodeToString(email.getBytes(StandardCharsets.UTF_8));
                log("CLIENT: [tài khoản]");
                out.print(b64User + "\r\n");
                out.flush();
                readAndLog(in, "334");

                String b64Pass = Base64.getEncoder().encodeToString(pass.getBytes(StandardCharsets.UTF_8));
                log("CLIENT: [mật khẩu ẩn]");
                out.print(b64Pass + "\r\n");
                out.flush();
                readAndLog(in, "235");

                sendAndLog(out, in, "MAIL FROM:<" + email + ">", "250");
                sendAndLog(out, in, "RCPT TO:<" + to + ">", "250");
                String mime = MailMime.compose(email, to, subject, body, file);
                sendAndLog(out, in, "DATA", "354");
                out.print(mime.replaceAll("(?m)^\\.", ".."));
                if (!mime.endsWith("\r\n")) out.print("\r\n");
                out.print(".\r\n");
                out.flush();
                log("CLIENT: [Đã gửi Header + Body + dấu chấm '.' kết thúc DATA]");
                readAndLog(in, "250");

                // Acceptance is already confirmed; a failed QUIT must not be reported as send failure.
                try { sendAndLog(out, in, "QUIT", "221"); } catch (IOException ignored) { }
                log("\n===> SERVER ĐÃ CHẤP NHẬN THƯ TỚI [" + to + "]!");

            } catch (Exception ex) {
                log("\n[LỖI KHI GỬI THƯ]: " + ex.getMessage());
            } finally {
                SwingUtilities.invokeLater(() -> btnSendMail.setEnabled(true));
            }
        }).start();
    }

    // ==================== TỰ ĐỘNG DÒ TÌM SERVER TRONG MẠNG LAN ====================
    private void autoScanLanServer() {
        final int port;
        try {
            port = Integer.parseInt(txtAuthPort.getText().trim());
        } catch (NumberFormatException ex) {
            lblAuthStatus.setText("Cổng không hợp lệ, vui lòng nhập số!");
            return;
        }

        btnScan.setEnabled(false);
        lblAuthStatus.setText("Đang quét tìm Server trong mạng LAN (cổng " + port + ")...");

        new Thread(() -> {
            ExecutorService pool = Executors.newFixedThreadPool(64);
            List<String> found = Collections.synchronizedList(new ArrayList<>());
            try {
                // 1. Xác định dải mạng (subnet) từ IP LAN của máy hiện tại, VD: "192.168.43."
                String localIp = getLocalIPv4();
                String subnet = localIp.substring(0, localIp.lastIndexOf('.') + 1);

                // 2. Quét .1 -> .254 song song bằng thread pool, mỗi máy chỉ chờ tối đa vài trăm ms
                List<Callable<Void>> tasks = new ArrayList<>();
                for (int i = 1; i < 255; i++) {
                    final String targetIp = subnet + i;
                    tasks.add(() -> {
                        if (isMailServer(targetIp, port)) found.add(targetIp);
                        return null;
                    });
                }
                pool.invokeAll(tasks);
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    lblAuthStatus.setText("Lỗi quét mạng LAN: " + e.getMessage());
                    btnScan.setEnabled(txtAuthServer.isEditable());
                });
                return;
            } finally {
                pool.shutdownNow();
            }

            // 3. Hiển thị kết quả (chỉ 1 hộp thoại duy nhất, không bị spam)
            List<String> result = new ArrayList<>(found);
            Collections.sort(result);
            SwingUtilities.invokeLater(() -> {
                btnScan.setEnabled(txtAuthServer.isEditable());
                if (result.isEmpty()) {
                    lblAuthStatus.setText("Không tìm thấy Server nào ở cổng " + port + " trong mạng LAN.");
                    JOptionPane.showMessageDialog(this,
                            "Không tìm thấy Mail Server nào trong mạng LAN.\n"
                                    + "- Kiểm tra LANMailServer đã chạy chưa.\n"
                                    + "- Kiểm tra cả hai máy cùng mạng Wi-Fi/LAN và Firewall đã cho phép cổng " + port + ".",
                            "Quét LAN", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                String chosen = result.get(0);
                if (result.size() > 1) {
                    Object sel = JOptionPane.showInputDialog(this, "Tìm thấy " + result.size() + " Server, chọn 1 máy:",
                            "Quét LAN", JOptionPane.QUESTION_MESSAGE, null, result.toArray(), chosen);
                    if (sel == null) {
                        lblAuthStatus.setText("Đã hủy chọn Server.");
                        return;
                    }
                    chosen = sel.toString();
                }
                if (!txtAuthServer.isEditable()) return;
                txtAuthServer.setText(chosen);
                lblAuthStatus.setText("Đã tìm thấy Server tại: " + chosen);
                JOptionPane.showMessageDialog(this, "Đã tự động tìm thấy Mail Server tại IP: " + chosen);
            });
        }).start();
    }

    // Kiểm tra ip:port có thật sự là Mail Server không (đọc lời chào của server, không chỉ mở cổng)
    private boolean isMailServer(String ip, int port) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(ip, port), 250);
            s.setSoTimeout(600);
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            String greeting = in.readLine();
            try {
                OutputStream os = s.getOutputStream();
                os.write("QUIT\r\n".getBytes(StandardCharsets.UTF_8));
                os.flush();
            } catch (IOException ignored) { }
            return greeting != null && greeting.startsWith("220");
        } catch (Exception e) {
            return false;
        }
    }

    // Lấy IPv4 LAN thật của máy hiện tại (bỏ qua card ảo VMware/Docker/WSL...)
    private static String getLocalIPv4() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp() || iface.isVirtual()) continue;
                String name = iface.getDisplayName().toLowerCase();
                if (name.contains("vmware") || name.contains("virtual") || name.contains("wsl") || name.contains("docker")) continue;
                Enumeration<InetAddress> addrs = iface.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) return a.getHostAddress();
                }
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
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
                   - Lưu ý: Người nhận nội bộ phải đăng ký. Người nhận bên ngoài cần cấu hình Gmail trên Server.
                   - Chọn Đính kèm file để gửi file tối đa 14 MiB.
                   - POP3Client: Hộp thư đến hoặc Thư đã gửi, xem và lưu file.
                   - Gửi ra Gmail: đăng nhập tài khoản NỘI BỘ trên localhost/IP server.
                   - Nút Dùng Gmail gửi trực tiếp, không lưu vào server nội bộ.
                """;
        JOptionPane.showMessageDialog(this, guide, "Hướng dẫn sử dụng SMTP & POP3", JOptionPane.INFORMATION_MESSAGE);
    }
//
    private static boolean isGmail(String host) {
        return GMAIL_HOST.equalsIgnoreCase(host);
    }

    static Socket connectSmtp(String host, int port) throws IOException {
        Socket socket = isGmail(host) ? SSLSocketFactory.getDefault().createSocket() : new Socket();
        try {
            socket.connect(new InetSocketAddress(host, port), 5_000);
            socket.setSoTimeout(120_000);
            if (socket instanceof SSLSocket tls) {
                var params = tls.getSSLParameters();
                params.setEndpointIdentificationAlgorithm("HTTPS");
                tls.setSSLParameters(params);
                tls.startHandshake();
            }
            return socket;
        } catch (IOException e) {
            socket.close();
            throw e;
        }
    }

    private static String encodeSubject(String subject) {
        StringJoiner words = new StringJoiner("\r\n ");
        ByteArrayOutputStream chunk = new ByteArrayOutputStream();
        subject.codePoints().forEach(codePoint -> {
            byte[] bytes = Character.toString(codePoint).getBytes(StandardCharsets.UTF_8);
            if (chunk.size() + bytes.length > 42) {
                words.add("=?UTF-8?B?" + Base64.getEncoder().encodeToString(chunk.toByteArray()) + "?=");
                chunk.reset();
            }
            chunk.writeBytes(bytes);
        });
        if (chunk.size() > 0) {
            words.add("=?UTF-8?B?" + Base64.getEncoder().encodeToString(chunk.toByteArray()) + "?=");
        }
        return words.toString();
    }

    static String readSmtpReply(BufferedReader in) throws IOException {
        String line = in.readLine();
        if (line == null || line.length() < 3) throw new IOException("Phản hồi SMTP không hợp lệ.");
        String code = line.substring(0, 3);
        StringBuilder reply = new StringBuilder(line);
        while (line.startsWith(code + "-")) {
            line = in.readLine();
            if (line == null || !(line.equals(code) || line.startsWith(code + " ") || line.startsWith(code + "-"))) {
                throw new IOException("Phản hồi SMTP nhiều dòng bị ngắt hoặc không hợp lệ.");
            }
            reply.append('\n').append(line);
        }
        return reply.toString();
    }

    private void readExpected(BufferedReader in, String code) throws IOException {
        String res = readSmtpReply(in);
        if (res == null || !res.startsWith(code)) throw new IOException(res);
    }
//
    private void sendAndLog(PrintWriter out, BufferedReader in, String cmd, String code) throws IOException {
        log("CLIENT: " + cmd);
        out.print(cmd + "\r\n");
        out.flush();
        readAndLog(in, code);
    }
//
    private void readAndLog(BufferedReader in, String code) throws IOException {
        String res = readSmtpReply(in);
        log("SERVER: " + res);
        if (res == null || !res.startsWith(code)) {
            throw new IOException("Server từ chối (Cần mã " + code + "): " + res);
        }
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> txtProtocolLog.append(msg + "\n"));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            SMTPClient client = new SMTPClient();
            if (args.length > 0) client.txtAuthServer.setText(args[0]);
            client.setVisible(true);
        });
    }
}