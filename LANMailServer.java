import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;

public class LANMailServer extends JFrame {

    public static final int SMTP_PORT = Integer.getInteger("lab3.smtp.port", 2525);
    public static final int POP3_PORT = Integer.getInteger("lab3.pop3.port", 1110);
    private final MailStore store;
    interface OutboundRelay {
        String prepare(String raw, String sender) throws Exception;
        void send(String raw, String recipient) throws Exception;
    }
    private volatile OutboundRelay gmailRelay;

    private JTextArea txtServerLog;
    private JLabel lblLanIp;
    private String serverLanIp;

    public LANMailServer(MailStore store) {
        this(store, null);
    }

    LANMailServer(MailStore store, OutboundRelay relayService) {
        this.store = store;
        this.gmailRelay = relayService;
        setTitle("LAN Mail Server - SMTP (" + SMTP_PORT + ") & POP3 (" + POP3_PORT + ")");
        setSize(900, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(8, 8));

        serverLanIp = getRealLanIPv4();

        // Panel hiển thị IP LAN ở trên cùng
        JPanel topPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        topPanel.setBackground(new Color(230, 245, 255));

        lblLanIp = new JLabel("ĐỊA CHỈ IP LAN CỦA SERVER: " + serverLanIp, SwingConstants.CENTER);
        lblLanIp.setFont(new Font("SansSerif", Font.BOLD, 18));
        lblLanIp.setForeground(new Color(0, 102, 204));

        JLabel lblGuide = new JLabel(
                "-> Các máy client trong mạng LAN nhập Host là [" + serverLanIp + "] (SMTP: " + SMTP_PORT + " | POP3: " + POP3_PORT + ")",
                SwingConstants.CENTER
        );
        lblGuide.setFont(new Font("SansSerif", Font.PLAIN, 13));

        topPanel.add(lblLanIp);
        topPanel.add(lblGuide);
        add(topPanel, BorderLayout.NORTH);

        // Khu vực nhật ký kết nối
        txtServerLog = new JTextArea();
        txtServerLog.setEditable(false);
        txtServerLog.setFont(new Font("Consolas", Font.PLAIN, 13));
        JScrollPane scrollLog = new JScrollPane(txtServerLog);
        scrollLog.setBorder(BorderFactory.createTitledBorder("Nhật ký kết nối trực tiếp từ các máy Client trong mạng LAN"));
        add(scrollLog, BorderLayout.CENTER);
        JPanel actions = new JPanel();
        JButton relay = new JButton("Cấu hình Gmail chuyển thư...");
        JButton sent = new JButton("Thư đã gửi / File đính kèm");
        actions.add(relay);
        actions.add(sent);
        add(actions, BorderLayout.SOUTH);
        relay.addActionListener(e -> configureRelay(relay));
        sent.addActionListener(e -> showSent(sent));

        // Khởi động 2 luồng Server lắng nghe trên 0.0.0.0
        new Thread(this::startSmtpServer).start();
        new Thread(this::startPop3Server).start();
    }

    public static String getRealLanIPv4() {
        try {
            String lanAddress = null;
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp() || iface.isVirtual()) continue;
                String name = iface.getDisplayName().toLowerCase();
                if (name.contains("vmware") || name.contains("virtual") || name.contains("wsl")
                        || name.contains("docker") || name.contains("vpn") || name.contains("radmin")
                        || name.contains("wi-fi direct")) continue;

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        if (name.contains("wi-fi") || name.contains("wireless") || name.contains("wlan")) {
                            return addr.getHostAddress();
                        }
                        if (lanAddress == null) lanAddress = addr.getHostAddress();
                    }
                }
            }
            return lanAddress == null ? InetAddress.getLocalHost().getHostAddress() : lanAddress;
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    // ==================== SMTP SERVER (PORT 2525) ====================
    private void startSmtpServer() {
        try (ServerSocket serverSocket = new ServerSocket(SMTP_PORT, 50, InetAddress.getByName("0.0.0.0"))) {
            log("[SMTP Server] Đang mở trên cổng " + SMTP_PORT + " (Chấp nhận kết nối LAN)");
            while (true) {
                Socket client = serverSocket.accept();
                String clientIp = client.getInetAddress().getHostAddress();
                log("\n---> [SMTP] Kết nối từ máy IP: " + clientIp);
                new Thread(() -> handleSmtpClient(client, clientIp)).start();
            }
        } catch (IOException e) {
            log("[LỖI SMTP Server]: " + e.getMessage());
        }
    }

    private void handleSmtpClient(Socket client, String clientIp) {
        try (client; BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8), true)) {

            out.print("220 " + serverLanIp + " LAN ESMTP Server Ready\r\n");
            out.flush();

            client.setSoTimeout(120_000);
            String authenticatedUser = null;
            String sender = null;
            List<String> recipients = new ArrayList<>();

            String line;
            while ((line = in.readLine()) != null) {
                log("[SMTP <- " + clientIp + "]: " + (line.regionMatches(true, 0, "REGISTER ", 0, 9) ? "REGISTER [ẩn]" : line));
                String upper = line.toUpperCase().trim();

                if (upper.startsWith("REGISTER ")) {
                    String[] parts = line.split(" ", 3);
                    if (parts.length == 3 && store.register(parts[1].toLowerCase(Locale.ROOT), parts[2])) {
                        log("   + Đăng ký thành công tài khoản: " + parts[1] + " (PostgreSQL)");
                        out.print("250 Dang ky thanh cong\r\n");
                    } else {
                        out.print("550 Tai khoan da ton tai hoac sai cu phap\r\n");
                    }
                } else if (upper.startsWith("HELO") || upper.startsWith("EHLO")) {
                    out.print("250 Hello " + clientIp + ", AUTH LOGIN supported\r\n");
                } else if (upper.equals("AUTH LOGIN")) {
                    out.print("334 VXNlcm5hbWU6\r\n"); // "Username:"
                    out.flush();
                    String user = new String(Base64.getDecoder().decode(in.readLine().trim()), StandardCharsets.UTF_8).toLowerCase();

                    out.print("334 UGFzc3dvcmQ6\r\n"); // "Password:"
                    out.flush();
                    String pass = new String(Base64.getDecoder().decode(in.readLine().trim()), StandardCharsets.UTF_8);

                    authenticatedUser = null;
                    sender = null;
                    recipients.clear();
                    if (store.authenticate(user, pass)) {
                        authenticatedUser = user;
                        log("   + Xác thực AUTH LOGIN thành công cho user: " + user);
                        out.print("235 2.7.0 Authentication successful\r\n");
                    } else {
                        out.print("535 5.7.8 Authentication failed\r\n");
                    }
                } else if (upper.startsWith("MAIL FROM:")) {
                    sender = null;
                    recipients.clear();
                    String from = line.substring(10).replace("<", "").replace(">", "").trim().toLowerCase(Locale.ROOT);
                    if (authenticatedUser == null) {
                        out.print("530 Authentication required before sending mail\r\n");
                    } else if (!authenticatedUser.equals(from)) {
                        out.print("553 Sender must match the authenticated account\r\n");
                    } else {
                        sender = from;
                        out.print("250 2.1.0 Sender OK\r\n");
                    }
                } else if (upper.startsWith("RCPT TO:")) {
                    String rcpt = line.substring(8).replace("<", "").replace(">", "").trim().toLowerCase();
                    if (sender == null) {
                        out.print("503 Send MAIL FROM first\r\n");
                    } else if (!rcpt.matches("[^\\s@<>;,]+@[^\\s@<>;,]+")) {
                        out.print("553 Invalid recipient address\r\n");
                    } else if (!recipients.isEmpty()) {
                        out.print("452 This lab accepts one recipient per message\r\n");
                    } else if (!store.exists(rcpt) && gmailRelay == null) {
                        out.print("550 External recipient: configure Gmail relay on LANMailServer first\r\n");
                    } else {
                        recipients.add(rcpt);
                        out.print("250 2.1.5 Recipient OK\r\n");
                    }
                } else if (upper.equals("DATA")) {
                    if (sender == null || recipients.isEmpty()) {
                        out.print("503 Bad sequence of commands\r\n");
                    } else {
                        out.print("354 Start mail input; end with <CR><LF>.<CR><LF>\r\n");
                        out.flush();

                        StringBuilder raw = new StringBuilder();
                        long bytes = 0;
                        String dataLine;
                        while ((dataLine = in.readLine()) != null) {
                            if (dataLine.equals(".")) break;
                            if (dataLine.startsWith("..")) dataLine = dataLine.substring(1);
                            bytes += dataLine.getBytes(StandardCharsets.UTF_8).length + 2L;
                            if (bytes > MailMime.MAX_BYTES) {
                                out.print("552 Message exceeds 20 MiB\r\n");
                                out.flush();
                                return;
                            }
                            raw.append(dataLine).append("\r\n");
                        }
                        if (dataLine == null) return;
                        try {
                            String content = MailMime.canonical(raw.toString(), sender, recipients);
                            String recipient = recipients.get(0);
                            if (store.exists(recipient)) {
                                store.deliverRaw(sender, recipients, content);
                            } else {
                                OutboundRelay relay = gmailRelay;
                                if (relay == null) throw new IOException("Chưa cấu hình Gmail chuyển thư.");
                                content = relay.prepare(content, sender);
                                long id = store.recordOutgoing(sender, recipient, content);
                                try {
                                    relay.send(content, recipient);
                                } catch (Exception ex) {
                                    // A dropped connection after DATA can mean Gmail accepted it. Never auto-retry.
                                    String status = ex instanceof jakarta.mail.AuthenticationFailedException
                                            ? "AUTH_FAILED" : "UNKNOWN_CHECK_GMAIL";
                                    store.outgoingStatus(id, status);
                                    throw new IOException("Gmail: " + ex.getMessage()
                                            + ". Kiem tra Thu da gui/Gmail truoc khi gui lai.", ex);
                                }
                                try {
                                    store.outgoingStatus(id, "RELAY_ACCEPTED");
                                } catch (java.sql.SQLException ex) {
                                    log("[CẢNH BÁO] Gmail đã nhận thư #" + id
                                            + ", DB chưa cập nhật trạng thái. Không gửi lại: " + ex.getMessage());
                                }
                                log("[Gmail] Đã nhận để chuyển tiếp tới " + recipient);
                            }
                        } catch (Exception e) {
                            log("[Gửi thư] " + e.getMessage());
                            out.print("451 " + String.valueOf(e.getMessage()).replaceAll("[\\r\\n]+", " ") + "\r\n");
                            out.flush();
                            sender = null;
                            recipients.clear();
                            continue;
                        }
                        sender = null;
                        log("   + Đã lưu thư vào PostgreSQL cho: " + recipients);
                        recipients.clear();
                        out.print("250 2.0.0 Message accepted for delivery\r\n");
                    }
                } else if (upper.equals("QUIT")) {
                    out.print("221 2.0.0 Service closing transmission channel\r\n");
                    out.flush();
                    break;
                } else {
                    out.print("500 Command not recognized\r\n");
                }
                out.flush();
            }
        } catch (Exception e) {
            log("[SMTP Ngắt kết nối từ " + clientIp + "]: " + e.getMessage());
        }
    }

    // ==================== POP3 SERVER (PORT 1110) ====================
    private void startPop3Server() {
        try (ServerSocket serverSocket = new ServerSocket(POP3_PORT, 50, InetAddress.getByName("0.0.0.0"))) {
            log("[POP3 Server] Đang mở trên cổng " + POP3_PORT + " (Chấp nhận kết nối LAN)");
            while (true) {
                Socket client = serverSocket.accept();
                String clientIp = client.getInetAddress().getHostAddress();
                log("\n---> [POP3] Kết nối từ máy IP: " + clientIp);
                new Thread(() -> handlePop3Client(client, clientIp)).start();
            }
        } catch (IOException e) {
            log("[LỖI POP3 Server]: " + e.getMessage());
        }
    }

    private void handlePop3Client(Socket client, String clientIp) {
        try (client; BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8), true)) {

            out.print("+OK LAN POP3 Server Ready\r\n");
            out.flush();

            String currentUser = null;
            boolean authenticated = false;
            boolean sentFolder = false;
            List<MailStore.Message> messages = new ArrayList<>();
            Set<Integer> deleted = new HashSet<>();

            String line;
            while ((line = in.readLine()) != null) {
                log("[POP3 <- " + clientIp + "]: " + (line.regionMatches(true, 0, "PASS ", 0, 5) ? "PASS [ẩn]" : line));
                String upper = line.toUpperCase().trim();

                if (!authenticated && upper.startsWith("USER ")) {
                    currentUser = line.substring(5).trim().toLowerCase();
                    if (store.exists(currentUser)) {
                        out.print("+OK User accepted\r\n");
                    } else {
                        out.print("-ERR User does not exist\r\n");
                    }
                } else if (!authenticated && upper.startsWith("PASS ")) {
                    String pass = line.substring(5).trim();
                    if (currentUser != null && store.authenticate(currentUser, pass)) {
                        messages = store.inbox(currentUser);
                        authenticated = true;
                        log("   + Đăng nhập POP3 thành công cho hộp thư: " + currentUser);
                        out.print("+OK Mailbox ready (" + messages.size() + " messages)\r\n");
                    } else {
                        out.print("-ERR Invalid password\r\n");
                    }
                } else if (upper.equals("QUIT")) {
                    List<Long> ids = new ArrayList<>();
                    for (int index : deleted) ids.add(messages.get(index).id());
                    try {
                        if (authenticated) store.delete(currentUser, ids);
                        out.print("+OK POP3 server signing off\r\n");
                    } catch (java.sql.SQLException e) {
                        log("[PostgreSQL] " + e.getMessage());
                        out.print("-ERR Cannot save mailbox changes\r\n");
                    }
                    out.flush();
                    break;
                } else if (!authenticated) {
                    out.print("-ERR Please login with USER and PASS first\r\n");
                } else if (upper.equals("XBOX SENT") || upper.equals("XBOX INBOX")) {
                    if (!deleted.isEmpty()) {
                        out.print("-ERR Finish deletions with QUIT before changing folder\r\n");
                    } else {
                        sentFolder = upper.equals("XBOX SENT");
                        messages = sentFolder ? store.sent(currentUser) : store.inbox(currentUser);
                        out.print("+OK Folder selected\r\n");
                    }
                } else if (upper.equals("STAT")) {
                    long totalBytes = 0;
                    for (int i = 0; i < messages.size(); i++) {
                        if (!deleted.contains(i)) totalBytes += messages.get(i).bytes();
                    }
                    out.print("+OK " + (messages.size() - deleted.size()) + " " + totalBytes + "\r\n");
                } else if (upper.equals("LIST")) {
                    out.print("+OK " + (messages.size() - deleted.size()) + " messages\r\n");
                    for (int i = 0; i < messages.size(); i++) {
                        if (!deleted.contains(i)) out.print((i + 1) + " " + messages.get(i).bytes() + "\r\n");
                    }
                    out.print(".\r\n");
                } else if (upper.startsWith("RETR ")) {
                    int idx = Integer.parseInt(line.substring(5).trim()) - 1;
                    if (idx >= 0 && idx < messages.size() && !deleted.contains(idx)) {
                        MailStore.Message message = messages.get(idx);
                        out.print("+OK " + message.bytes() + " octets\r\n");
                        String content = message.content().replaceAll("(?m)^\\.", "..");
                        out.print(content);
                        if (!content.endsWith("\r\n")) out.print("\r\n");
                        out.print(".\r\n");
                    } else {
                        out.print("-ERR No such message\r\n");
                    }
                } else if (upper.startsWith("DELE ") && sentFolder) {
                    out.print("-ERR Sent folder is read-only\r\n");
                } else if (upper.startsWith("DELE ")) {
                    int idx = Integer.parseInt(line.substring(5).trim()) - 1;
                    if (idx >= 0 && idx < messages.size() && !deleted.contains(idx)) {
                        deleted.add(idx);
                        log("   + Đã xóa bức thư số #" + (idx + 1) + " trong hộp thư của " + currentUser);
                        out.print("+OK Message deleted\r\n");
                    } else {
                        out.print("-ERR Message does not exist\r\n");
                    }
                } else {
                    out.print("-ERR Unknown POP3 command\r\n");
                }
                out.flush();
            }
        } catch (Exception e) {
            log("[POP3 Ngắt kết nối]: " + e.getMessage());
        }
    }

    private void configureRelay(JButton button) {
        JTextField email = new JTextField(30);
        JPasswordField password = new JPasswordField();
        JPanel form = new JPanel(new GridLayout(0, 1, 5, 5));
        form.add(new JLabel("Gmail trung gian (thư thật sẽ được gửi bằng địa chỉ này)"));
        form.add(email);
        form.add(new JLabel("Mật khẩu ứng dụng Google — chỉ giữ trong phiên chạy server"));
        form.add(password);
        form.add(new JLabel("Tài khoản nội bộ được ghi trong tên người gửi. Reply trả về Gmail trung gian."));
        if (JOptionPane.showConfirmDialog(this, form, "Gmail chuyển thư — TLS 465",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        String address = email.getText().trim();
        String secret = new String(password.getPassword());
        password.setText("");
        button.setEnabled(false);
        button.setText("Đang kiểm tra Gmail...");
        new Thread(() -> {
            try {
                GmailRelay relay = new GmailRelay(address, secret);
                relay.verify();
                gmailRelay = relay;
                log("[Gmail] Đã cấu hình chuyển thư qua " + address);
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                        "Gmail đã kết nối. Client nội bộ có thể gửi tới địa chỉ mail thật."));
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                        "Không cấu hình được Gmail: " + ex.getMessage()));
            } finally {
                SwingUtilities.invokeLater(() -> {
                    button.setEnabled(true);
                    button.setText("Cấu hình Gmail chuyển thư...");
                });
            }
        }, "gmail-config").start();
    }

    private void showSent(JButton button) {
        button.setEnabled(false);
        new Thread(() -> {
            try {
                List<MailStore.SentRow> rows = store.recentSent();
                SwingUtilities.invokeLater(() -> {
                    var model = new javax.swing.table.DefaultTableModel(
                            new String[]{"Người gửi nội bộ", "Người nhận", "Trạng thái", "Thời gian"}, 0) {
                        @Override public boolean isCellEditable(int r, int c) { return false; }
                    };
                    for (var row : rows) model.addRow(new Object[]{row.sender(), row.recipient(), row.status(), row.date()});
                    JTable table = new JTable(model);
                    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
                    JButton view = new JButton("Xem thư / Lưu file đính kèm");
                    view.addActionListener(e -> {
                        int selected = table.getSelectedRow();
                        if (selected >= 0) MailMime.show(this, rows.get(selected).content());
                    });
                    JPanel panel = new JPanel(new BorderLayout(8, 8));
                    panel.add(new JScrollPane(table));
                    panel.add(view, BorderLayout.SOUTH);
                    panel.setPreferredSize(new Dimension(850, 360));
                    JOptionPane.showMessageDialog(this, panel,
                            "100 thư gần nhất — RELAY_ACCEPTED: Gmail nhận chuyển tiếp, chưa xác nhận vào Inbox",
                            JOptionPane.PLAIN_MESSAGE);
                });
            } catch (Exception ex) {
                log("[Thư đã gửi] " + ex.getMessage());
            } finally {
                SwingUtilities.invokeLater(() -> button.setEnabled(true));
            }
        }, "sent-history").start();
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> txtServerLog.append(msg + "\n"));
    }

    private static MailStore configureDatabase() {
        String url = System.getenv().getOrDefault("LAB3_DB_URL", MailStore.DEFAULT_URL);
        String user = System.getenv().getOrDefault("LAB3_DB_USER", "postgres");
        String password = System.getenv("LAB3_DB_PASSWORD");
        if (password != null || GraphicsEnvironment.isHeadless()) {
            return new MailStore(url, user, password == null ? "" : password);
        }

        var dbUrl = new JTextField(url, 36);
        var dbUser = new JTextField(user);
        var dbPassword = new JPasswordField();
        var form = new JPanel(new GridLayout(0, 1, 0, 5));
        form.add(new JLabel("Địa chỉ JDBC PostgreSQL"));
        form.add(dbUrl);
        form.add(new JLabel("User PostgreSQL"));
        form.add(dbUser);
        form.add(new JLabel("Mật khẩu PostgreSQL"));
        form.add(dbPassword);
        if (JOptionPane.showConfirmDialog(null, form, "Kết nối PostgreSQL — LAN Mail Server",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return null;
        }
        char[] secret = dbPassword.getPassword();
        try {
            return new MailStore(dbUrl.getText().strip(), dbUser.getText().strip(), new String(secret));
        } finally {
            Arrays.fill(secret, '\0');
            dbPassword.setText("");
        }
    }

    public static void main(String[] args) {
        MailStore store = configureDatabase();
        if (store == null) return;
        try {
            store.initialize();
        } catch (Exception e) {
            String error = "Không khởi tạo được PostgreSQL: " + e.getMessage();
            System.err.println(error);
            if (!GraphicsEnvironment.isHeadless()) {
                JOptionPane.showMessageDialog(null, error, "LAN Mail Server", JOptionPane.ERROR_MESSAGE);
            }
            System.exit(1);
        }
        SwingUtilities.invokeLater(() -> new LANMailServer(store).setVisible(true));
    }
}
