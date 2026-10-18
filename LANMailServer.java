import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.List;

public class LANMailServer extends JFrame {

    public static final int SMTP_PORT = 2525;
    public static final int POP3_PORT = 1110;
    public static final File DATA_DIR = new File("mail_data");
    public static final File ACCOUNTS_FILE = new File(DATA_DIR, "accounts.txt");

    private JTextArea txtServerLog;
    private JLabel lblLanIp;
    private String serverLanIp;

    public LANMailServer() {
        setTitle("LAN Mail Server (SMTP Port 2525 & POP3 Port 1110) - Chạy đa máy qua mạng LAN");
        setSize(850, 550);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(8, 8));

        initStorage();
        serverLanIp = getRealLanIPv4();

        // Panel hiển thị IP LAN nổi bật ở trên cùng để các máy khác nhập vào
        JPanel topPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        topPanel.setBackground(new Color(230, 245, 255));

        lblLanIp = new JLabel("ĐỊA CHỈ IP LAN CỦA SERVER NÀY: " + serverLanIp, SwingConstants.CENTER);
        lblLanIp.setFont(new Font("SansSerif", Font.BOLD, 18));
        lblLanIp.setForeground(new Color(0, 102, 204));

        JLabel lblGuide = new JLabel(
                "-> Trên các máy tính khác cùng mạng Wi-Fi/LAN, hãy nhập Host là [" + serverLanIp + "] (SMTP Port: 2525 | POP3 Port: 1110)",
                SwingConstants.CENTER
        );
        lblGuide.setFont(new Font("SansSerif", Font.PLAIN, 13));

        topPanel.add(lblLanIp);
        topPanel.add(lblGuide);
        add(topPanel, BorderLayout.NORTH);

        // Khu vực nhật ký kết nối từ các máy khác trong mạng LAN
        txtServerLog = new JTextArea();
        txtServerLog.setEditable(false);
        txtServerLog.setFont(new Font("Monospaced", Font.PLAIN, 13));
        JScrollPane scrollLog = new JScrollPane(txtServerLog);
        scrollLog.setBorder(BorderFactory.createTitledBorder("Nhật ký kết nối trực tiếp từ các máy trong mạng LAN"));
        add(scrollLog, BorderLayout.CENTER);

        // Khởi động cả 2 dịch vụ SMTP và POP3 lắng nghe toàn mạng LAN (0.0.0.0)
        new Thread(this::startSmtpServer).start();
        new Thread(this::startPop3Server).start();
    }

    // Hàm tự động lấy đúng IPv4 của card Wi-Fi / Ethernet (Bỏ qua 127.0.0.1 và card ảo VMware/WSL/Docker)
    public static String getRealLanIPv4() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp() || iface.isVirtual()) continue;

                String name = iface.getDisplayName().toLowerCase();
                if (name.contains("vmware") || name.contains("virtual") || name.contains("wsl") || name.contains("docker") || name.contains("vbox")) {
                    continue;
                }

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

    public static void initStorage() {
        try {
            if (!DATA_DIR.exists()) DATA_DIR.mkdirs();
            if (!ACCOUNTS_FILE.exists()) {
                registerUser("sv1@vku.udn.vn", "123456");
                registerUser("sv2@vku.udn.vn", "123456");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static synchronized boolean registerUser(String email, String password) throws IOException {
        email = email.toLowerCase().trim();
        Map<String, String> accounts = loadAccounts();
        if (accounts.containsKey(email)) return false;

        try (FileWriter fw = new FileWriter(ACCOUNTS_FILE, true)) {
            fw.write(email + ":" + password + "\n");
        }
        File userMailbox = new File(DATA_DIR, email);
        if (!userMailbox.exists()) userMailbox.mkdirs();
        return true;
    }

    public static synchronized Map<String, String> loadAccounts() throws IOException {
        Map<String, String> map = new HashMap<>();
        if (!ACCOUNTS_FILE.exists()) return map;
        for (String line : Files.readAllLines(ACCOUNTS_FILE.toPath(), StandardCharsets.UTF_8)) {
            String[] parts = line.split(":", 2);
            if (parts.length == 2) map.put(parts[0].trim(), parts[1].trim());
        }
        return map;
    }

    // ==================== SMTP SERVER (LẮNG NGHE MỌI MÁY TRONG LAN) ====================
    private void startSmtpServer() {
        try (ServerSocket serverSocket = new ServerSocket(SMTP_PORT, 50, InetAddress.getByName("0.0.0.0"))) {
            log("[SMTP Server] Đang mở trên " + serverLanIp + ":" + SMTP_PORT + " (Chấp nhận mọi máy trong LAN)");
            while (true) {
                Socket client = serverSocket.accept();
                String clientIp = client.getInetAddress().getHostAddress();
                log("\n---> [SMTP] Có kết nối mới từ máy khách IP: " + clientIp);
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
                        log("   + Máy " + clientIp + " vừa tạo tài khoản mới: " + parts[1]);
                        out.print("250 Dang ky tai khoan thanh cong: " + parts[1] + "\r\n");
                    } else {
                        out.print("550 Tai khoan da ton tai hoac sai cu phap\r\n");
                    }
                } else if (upper.startsWith("HELO") || upper.startsWith("EHLO")) {
                    out.print("250 Hello " + clientIp + ", AUTH LOGIN supported\r\n");
                } else if (upper.equals("AUTH LOGIN")) {
                    out.print("334 VXNlcm5hbWU6\r\n");
                    out.flush();
                    String user = new String(Base64.getDecoder().decode(in.readLine().trim()), StandardCharsets.UTF_8).toLowerCase();

                    out.print("334 UGFzc3dvcmQ6\r\n");
                    out.flush();
                    String pass = new String(Base64.getDecoder().decode(in.readLine().trim()), StandardCharsets.UTF_8);

                    Map<String, String> accounts = loadAccounts();
                    if (accounts.containsKey(user) && accounts.get(user).equals(pass)) {
                        authenticated = true;
                        log("   + Máy " + clientIp + " đăng nhập SMTP thành công với user: " + user);
                        out.print("235 Dang nhap thanh cong\r\n");
                    } else {
                        out.print("535 Sai tai khoan hoac mat khau\r\n");
                    }
                } else if (upper.startsWith("MAIL FROM:")) {
                    if (!authenticated) {
                        out.print("530 Vui long dang nhap AUTH LOGIN truoc\r\n");
                    } else {
                        sender = line.substring(10).replace("<", "").replace(">", "").trim().toLowerCase();
                        recipients.clear();
                        out.print("250 Sender OK (" + sender + ")\r\n");
                    }
                } else if (upper.startsWith("RCPT TO:")) {
                    String rcpt = line.substring(8).replace("<", "").replace(">", "").trim().toLowerCase();
                    Map<String, String> accounts = loadAccounts();
                    if (!accounts.containsKey(rcpt)) {
                        out.print("550 Nguoi nhan khong ton tai tren Server: " + rcpt + "\r\n");
                    } else {
                        recipients.add(rcpt);
                        out.print("250 Recipient OK (" + rcpt + ")\r\n");
                    }
                } else if (upper.equals("DATA")) {
                    if (sender == null || recipients.isEmpty()) {
                        out.print("503 Chua nhap MAIL FROM hoac RCPT TO\r\n");
                    } else {
                        out.print("354 Nhap noi dung thu, ket thuc bang dau cham '.' tren 1 dong\r\n");
                        out.flush();
                        StringBuilder content = new StringBuilder();
                        String dataLine;
                        while ((dataLine = in.readLine()) != null) {
                            if (dataLine.equals(".")) break;
                            content.append(dataLine).append("\r\n");
                        }
                        for (String rcpt : recipients) {
                            File userBox = new File(DATA_DIR, rcpt);
                            if (!userBox.exists()) userBox.mkdirs();
                            File mailFile = new File(userBox, "mail_" + System.currentTimeMillis() + ".txt");
                            Files.write(mailFile.toPath(), content.toString().getBytes(StandardCharsets.UTF_8));
                        }
                        log("   + Đã nhận thư từ " + sender + " (" + clientIp + ") gửi tới " + recipients);
                        out.print("250 Da luu thu vao hop thu cua " + recipients + "\r\n");
                    }
                } else if (upper.equals("QUIT")) {
                    out.print("221 Dong ket noi SMTP\r\n");
                    out.flush();
                    break;
                } else {
                    out.print("500 Lenh khong hop le\r\n");
                }
                out.flush();
            }
        } catch (Exception e) {
            log("[SMTP Ngắt kết nối từ " + clientIp + "]: " + e.getMessage());
        }
    }

    // ==================== POP3 SERVER (LẮNG NGHE MỌI MÁY TRONG LAN) ====================
    private void startPop3Server() {
        try (ServerSocket serverSocket = new ServerSocket(POP3_PORT, 50, InetAddress.getByName("0.0.0.0"))) {
            log("[POP3 Server] Đang mở trên " + serverLanIp + ":" + POP3_PORT + " (Chấp nhận mọi máy trong LAN)");
            while (true) {
                Socket client = serverSocket.accept();
                String clientIp = client.getInetAddress().getHostAddress();
                log("\n---> [POP3] Có kết nối mới từ máy khách IP: " + clientIp);
                new Thread(() -> handlePop3Client(client, clientIp)).start();
            }
        } catch (IOException e) {
            log("[LỖI POP3 Server]: " + e.getMessage());
        }
    }

    private void handlePop3Client(Socket client, String clientIp) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8), true)) {

            out.print("+OK " + serverLanIp + " LAN POP3 Server Ready\r\n");
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
                        out.print("+OK Tai khoan hop le, vui long nhap PASS\r\n");
                    } else {
                        out.print("-ERR Tai khoan khong ton tai\r\n");
                    }
                } else if (upper.startsWith("PASS ")) {
                    String pass = line.substring(5).trim();
                    Map<String, String> accounts = loadAccounts();
                    if (currentUser != null && accounts.containsKey(currentUser) && accounts.get(currentUser).equals(pass)) {
                        authenticated = true;
                        File userDir = new File(DATA_DIR, currentUser);
                        File[] files = userDir.listFiles((dir, name) -> name.endsWith(".txt"));
                        mailFiles = (files != null) ? new ArrayList<>(Arrays.asList(files)) : new ArrayList<>();
                        mailFiles.sort(Comparator.comparing(File::getName));
                        log("   + Máy " + clientIp + " đăng nhập POP3 thành công (" + currentUser + ")");
                        out.print("+OK Dang nhap thanh cong, co " + mailFiles.size() + " thu\r\n");
                    } else {
                        out.print("-ERR Sai mat khau\r\n");
                    }
                } else if (!authenticated) {
                    out.print("-ERR Vui long dang nhap USER va PASS truoc\r\n");
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
                        out.print("-ERR Khong tim thay thu\r\n");
                    }
                } else if (upper.equals("QUIT")) {
                    out.print("+OK POP3 Server Tam biet\r\n");
                    out.flush();
                    break;
                } else {
                    out.print("-ERR Lenh POP3 khong hop le\r\n");
                }
                out.flush();
            }
        } catch (Exception e) {
            log("[POP3 Ngắt kết nối từ " + clientIp + "]: " + e.getMessage());
        }
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> txtServerLog.append(msg + "\n"));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LANMailServer().setVisible(true));
    }
}