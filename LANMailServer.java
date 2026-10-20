import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

public class LANMailServer extends JFrame {

    public static final int SMTP_PORT = 2525;
    public static final int POP3_PORT = 1110;
    public static final File DATA_DIR = new File("mail_data");
    public static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private JTextArea txtServerLog;
    private JLabel lblLanIp;
    private String serverLanIp;

    public LANMailServer() {
        setTitle("LAN Mail Server - SMTP (Port 2525) & POP3 (Port 1110)");
        setSize(900, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(8, 8));

        initStorage();
        serverLanIp = getRealLanIPv4();

        // Panel hiển thị IP LAN ở trên cùng
        JPanel topPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        topPanel.setBackground(new Color(230, 245, 255));

        lblLanIp = new JLabel("ĐỊA CHỈ IP LAN CỦA SERVER: " + serverLanIp, SwingConstants.CENTER);
        lblLanIp.setFont(new Font("SansSerif", Font.BOLD, 18));
        lblLanIp.setForeground(new Color(0, 102, 204));

        JLabel lblGuide = new JLabel(
                "-> Các máy client trong mạng LAN nhập Host là [" + serverLanIp + "] (SMTP: 2525 | POP3: 1110)",
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

        // Khởi động 2 luồng Server lắng nghe trên 0.0.0.0
        new Thread(this::startSmtpServer).start();
        new Thread(this::startPop3Server).start();
    }

    public static String getRealLanIPv4() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp() || iface.isVirtual()) continue;
                String name = iface.getDisplayName().toLowerCase();
                if (name.contains("vmware") || name.contains("virtual") || name.contains("wsl") || name.contains("docker")) continue;

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    // Khởi tạo thư mục gốc và 2 tài khoản mẫu
    public static void initStorage() {
        try {
            if (!DATA_DIR.exists()) DATA_DIR.mkdirs();
            registerUser("sv1@vku.udn.vn", "123456");
            registerUser("sv2@vku.udn.vn", "123456");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Tạo thư mục riêng cho từng tài khoản và lưu file account_info.txt bên trong
    public static synchronized boolean registerUser(String email, String password) throws IOException {
        email = email.toLowerCase().trim();
        File userMailbox = new File(DATA_DIR, email);
        File accountInfoFile = new File(userMailbox, "account_info.txt");

        // Nếu đã có file account_info.txt thì tài khoản đã tồn tại
        if (accountInfoFile.exists()) return false;

        if (!userMailbox.exists()) userMailbox.mkdirs();

        String createdTime = LocalDateTime.now().format(TIME_FMT);
        String infoContent = "Tên tài khoản: " + email + "\r\n"
                           + "Thời gian tạo: " + createdTime + "\r\n"
                           + "Mật khẩu: " + password + "\r\n";

        Files.write(accountInfoFile.toPath(), infoContent.getBytes(StandardCharsets.UTF_8));
        return true;
    }

    // Đọc danh sách tài khoản từ file account_info.txt trong từng thư mục con
    public static synchronized Map<String, String> loadAccounts() throws IOException {
        Map<String, String> map = new HashMap<>();
        if (!DATA_DIR.exists()) return map;

        File[] userFolders = DATA_DIR.listFiles(File::isDirectory);
        if (userFolders == null) return map;

        for (File folder : userFolders) {
            File infoFile = new File(folder, "account_info.txt");
            if (infoFile.exists()) {
                String email = null;
                String pass = null;
                for (String line : Files.readAllLines(infoFile.toPath(), StandardCharsets.UTF_8)) {
                    if (line.startsWith("Tên tài khoản:")) {
                        email = line.substring("Tên tài khoản:".length()).trim().toLowerCase();
                    } else if (line.startsWith("Mật khẩu:")) {
                        pass = line.substring("Mật khẩu:".length()).trim();
                    }
                }
                if (email != null && pass != null) {
                    map.put(email, pass);
                }
            }
        }
        return map;
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
        try (BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8), true)) {

            out.print("220 " + serverLanIp + " LAN ESMTP Server Ready\r\n");
            out.flush();

            boolean authenticated = false;
            String sender = null;
            List<String> recipients = new ArrayList<>();

            String line;
            while ((line = in.readLine()) != null) {
                log("[SMTP <- " + clientIp + "]: " + line);
                String upper = line.toUpperCase().trim();

                if (upper.startsWith("REGISTER ")) {
                    String[] parts = line.split(" ", 3);
                    if (parts.length == 3 && registerUser(parts[1], parts[2])) {
                        log("   + Đăng ký thành công tài khoản: " + parts[1] + " (Đã tạo account_info.txt)");
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

                    Map<String, String> accounts = loadAccounts();
                    if (accounts.containsKey(user) && accounts.get(user).equals(pass)) {
                        authenticated = true;
                        log("   + Xác thực AUTH LOGIN thành công cho user: " + user);
                        out.print("235 2.7.0 Authentication successful\r\n");
                    } else {
                        out.print("535 5.7.8 Authentication failed\r\n");
                    }
                } else if (upper.startsWith("MAIL FROM:")) {
                    if (!authenticated) {
                        out.print("530 Authentication required before sending mail\r\n");
                    } else {
                        sender = line.substring(10).replace("<", "").replace(">", "").trim().toLowerCase();
                        recipients.clear();
                        out.print("250 2.1.0 Sender OK\r\n");
                    }
                } else if (upper.startsWith("RCPT TO:")) {
                    String rcpt = line.substring(8).replace("<", "").replace(">", "").trim().toLowerCase();
                    Map<String, String> accounts = loadAccounts();
                    if (!accounts.containsKey(rcpt)) {
                        out.print("550 5.1.1 User unknown: " + rcpt + "\r\n");
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

                        String subject = "(Không có tiêu đề)";
                        StringBuilder bodyBuilder = new StringBuilder();
                        boolean inBody = false;

                        String dataLine;
                        while ((dataLine = in.readLine()) != null) {
                            if (dataLine.equals(".")) break;

                            if (!inBody) {
                                if (dataLine.isEmpty()) {
                                    inBody = true; // Bắt đầu vào phần Nội dung sau dòng trống
                                } else if (dataLine.toLowerCase().startsWith("subject:")) {
                                    subject = dataLine.substring(8).trim();
                                }
                            } else {
                                bodyBuilder.append(dataLine).append("\r\n");
                            }
                        }

                        String sendTime = LocalDateTime.now().format(TIME_FMT);
                        String bodyContent = bodyBuilder.toString().trim();

                        for (String rcpt : recipients) {
                            File userBox = new File(DATA_DIR, rcpt);
                            if (!userBox.exists()) userBox.mkdirs();

                            String formattedMail = "Thời gian gửi: " + sendTime + "\r\n"
                                                 + "Người gửi: " + sender + "\r\n"
                                                 + "Người nhận: " + rcpt + "\r\n"
                                                 + "Tiêu đề: " + subject + "\r\n"
                                                 + "Nội dung:\r\n" + bodyContent + "\r\n";

                            File mailFile = new File(userBox, "mail_" + System.currentTimeMillis() + ".txt");
                            Files.write(mailFile.toPath(), formattedMail.getBytes(StandardCharsets.UTF_8));
                        }
                        log("   + Đã lưu thư từ [" + sender + "] gửi tới: " + recipients);
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
        try (BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8), true)) {

            out.print("+OK LAN POP3 Server Ready\r\n");
            out.flush();

            String currentUser = null;
            boolean authenticated = false;
            List<File> mailFiles = new ArrayList<>();

            String line;
            while ((line = in.readLine()) != null) {
                log("[POP3 <- " + clientIp + "]: " + line);
                String upper = line.toUpperCase().trim();

                if (upper.startsWith("USER ")) {
                    currentUser = line.substring(5).trim().toLowerCase();
                    Map<String, String> accounts = loadAccounts();
                    if (accounts.containsKey(currentUser)) {
                        out.print("+OK User accepted\r\n");
                    } else {
                        out.print("-ERR User does not exist\r\n");
                    }
                } else if (upper.startsWith("PASS ")) {
                    String pass = line.substring(5).trim();
                    Map<String, String> accounts = loadAccounts();
                    if (currentUser != null && accounts.containsKey(currentUser) && accounts.get(currentUser).equals(pass)) {
                        authenticated = true;
                        File userDir = new File(DATA_DIR, currentUser);
                        // Chỉ lọc các file tin nhắn bắt đầu bằng "mail_" để không lấy nhầm file "account_info.txt"
                        File[] files = userDir.listFiles((dir, name) -> name.startsWith("mail_") && name.endsWith(".txt"));
                        mailFiles = (files != null) ? new ArrayList<>(Arrays.asList(files)) : new ArrayList<>();
                        mailFiles.sort(Comparator.comparing(File::getName));
                        log("   + Đăng nhập POP3 thành công cho hộp thư: " + currentUser);
                        out.print("+OK Mailbox locked and ready (" + mailFiles.size() + " messages)\r\n");
                    } else {
                        out.print("-ERR Invalid password\r\n");
                    }
                } else if (!authenticated) {
                    out.print("-ERR Please login with USER and PASS first\r\n");
                } else if (upper.equals("STAT")) {
                    long totalBytes = mailFiles.stream().mapToLong(File::length).sum();
                    out.print("+OK " + mailFiles.size() + " " + totalBytes + "\r\n");
                } else if (upper.equals("LIST")) {
                    out.print("+OK " + mailFiles.size() + " messages\r\n");
                    for (int i = 0; i < mailFiles.size(); i++) {
                        out.print((i + 1) + " " + mailFiles.get(i).length() + "\r\n");
                    }
                    out.print(".\r\n");
                } else if (upper.startsWith("RETR ")) {
                    int idx = Integer.parseInt(line.substring(5).trim()) - 1;
                    if (idx >= 0 && idx < mailFiles.size()) {
                        File f = mailFiles.get(idx);
                        String content = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                        out.print("+OK " + f.length() + " octets\r\n");
                        out.print(content);
                        if (!content.endsWith("\r\n")) out.print("\r\n");
                        out.print(".\r\n");
                    } else {
                        out.print("-ERR No such message\r\n");
                    }
                } else if (upper.startsWith("DELE ")) {
                    int idx = Integer.parseInt(line.substring(5).trim()) - 1;
                    if (idx >= 0 && idx < mailFiles.size()) {
                        mailFiles.get(idx).delete();
                        log("   + Đã xóa bức thư số #" + (idx + 1) + " trong hộp thư của " + currentUser);
                        out.print("+OK Message deleted\r\n");
                    } else {
                        out.print("-ERR Message does not exist\r\n");
                    }
                } else if (upper.equals("QUIT")) {
                    out.print("+OK POP3 server signing off\r\n");
                    out.flush();
                    break;
                } else {
                    out.print("-ERR Unknown POP3 command\r\n");
                }
                out.flush();
            }
        } catch (Exception e) {
            log("[POP3 Ngắt kết nối]: " + e.getMessage());
        }
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> txtServerLog.append(msg + "\n"));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LANMailServer().setVisible(true));
    }
}