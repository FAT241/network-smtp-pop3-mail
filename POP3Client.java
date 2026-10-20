import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class POP3Client extends JFrame {

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel mainContainer = new JPanel(cardLayout);

    // Màn hình Đăng nhập POP3
    private JTextField txtAuthServer, txtAuthPort, txtAuthEmail;
    private JPasswordField txtAuthPassword;
    private JLabel lblAuthStatus;

    // Thông tin phiên đăng nhập
    private String loggedInServer;
    private int loggedInPort;
    private String loggedInEmail;
    private String loggedInPassword;

    // Màn hình Hộp thư đến (Sau khi đăng nhập)
    private JLabel lblCurrentUser;
    private JTextArea txtMailOutput;
    private JTextField txtDeleteIndex;
    private JButton btnRefreshMail, btnDeleteMail;

    public POP3Client() {
        setTitle("POP3 Client - Hệ thống Nhận Thư (Đăng nhập USER/PASS qua LAN)");
        setSize(860, 640);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        mainContainer.add(createLoginPanel(), "LOGIN");
        mainContainer.add(createInboxPanel(), "INBOX");

        add(mainContainer);
        cardLayout.show(mainContainer, "LOGIN");
    }

    // ==================== MÀN HÌNH 1: ĐĂNG NHẬP HỘP THƯ POP3 ====================
    private JPanel createLoginPanel() {
        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.setBackground(new Color(240, 244, 248));

        JPanel card = new JPanel(new BorderLayout(10, 15));
        card.setPreferredSize(new Dimension(500, 350));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 210, 220), 1),
                BorderFactory.createEmptyBorder(20, 25, 20, 25)
        ));

        JLabel title = new JLabel("ĐĂNG NHẬP HỘP THƯ ĐẾN (POP3)", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 18));
        title.setForeground(new Color(15, 23, 42));
        card.add(title, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridLayout(4, 2, 10, 15));
        form.setBackground(Color.WHITE);

        txtAuthServer = new JTextField("localhost");
        txtAuthPort = new JTextField("1110");
        txtAuthEmail = new JTextField("sv2@vku.udn.vn");
        txtAuthPassword = new JPasswordField("123456");

        form.add(new JLabel("IP Máy chủ POP3 (LAN Host):"));
        form.add(txtAuthServer);
        form.add(new JLabel("Cổng POP3 (Port):"));
        form.add(txtAuthPort);
        form.add(new JLabel("Tài khoản nhận thư (USER):"));
        form.add(txtAuthEmail);
        form.add(new JLabel("Mật khẩu (PASS):"));
        form.add(txtAuthPassword);

        card.add(form, BorderLayout.CENTER);

        JPanel south = new JPanel(new GridLayout(2, 1, 5, 8));
        south.setBackground(Color.WHITE);

        lblAuthStatus = new JLabel("Đăng nhập bằng tài khoản đã tạo để xem hộp thư đến.", SwingConstants.CENTER);
        lblAuthStatus.setForeground(new Color(100, 116, 139));

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        btns.setBackground(Color.WHITE);
        JButton btnLogin = new JButton("Đăng nhập & Mở Hộp thư");
        JButton btnHelp = new JButton("? Hướng dẫn");

        btnLogin.setBackground(new Color(37, 99, 235));
        btnLogin.setForeground(Color.WHITE);
        btns.add(btnLogin);
        btns.add(btnHelp);

        south.add(lblAuthStatus);
        south.add(btns);
        card.add(south, BorderLayout.SOUTH);

        wrapper.add(card);

        btnLogin.addActionListener(e -> performPop3Login());
        btnHelp.addActionListener(e -> showHelpDialog());

        return wrapper;
    }

    // ==================== MÀN HÌNH 2: HỘP THƯ ĐẾN (SAU KHI ĐĂNG NHẬP) ====================
    private JPanel createInboxPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel userBar = new JPanel(new BorderLayout());
        userBar.setBackground(new Color(220, 252, 231));
        userBar.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        lblCurrentUser = new JLabel("Hộp thư của: ");
        lblCurrentUser.setFont(new Font("SansSerif", Font.BOLD, 13));
        lblCurrentUser.setForeground(new Color(21, 128, 61));

        JButton btnLogout = new JButton("Đăng xuất / Đổi tài khoản");
        userBar.add(lblCurrentUser, BorderLayout.WEST);
        userBar.add(btnLogout, BorderLayout.EAST);
        panel.add(userBar, BorderLayout.NORTH);

        txtMailOutput = new JTextArea();
        txtMailOutput.setEditable(false);
        txtMailOutput.setFont(new Font("Consolas", Font.PLAIN, 13));
        JScrollPane scrollPane = new JScrollPane(txtMailOutput);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Nhật ký lệnh POP3 (STAT / LIST / RETR) & Nội dung Email"));
        panel.add(scrollPane, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 5));
        btnRefreshMail = new JButton("Tải lại Hộp thư (RETR)");
        btnRefreshMail.setBackground(new Color(37, 99, 235));
        btnRefreshMail.setForeground(Color.WHITE);

        txtDeleteIndex = new JTextField("1", 4);
        btnDeleteMail = new JButton("Xóa thư số # (Lệnh DELE)");

        bottomPanel.add(btnRefreshMail);
        bottomPanel.add(new JLabel(" |  Nhập STT thư cần xóa:"));
        bottomPanel.add(txtDeleteIndex);
        bottomPanel.add(btnDeleteMail);

        panel.add(bottomPanel, BorderLayout.SOUTH);

        btnLogout.addActionListener(e -> cardLayout.show(mainContainer, "LOGIN"));
        btnRefreshMail.addActionListener(e -> fetchAllEmails());
        btnDeleteMail.addActionListener(e -> deleteSelectedEmail());

        return panel;
    }

    private void performPop3Login() {
        String host = txtAuthServer.getText().trim();
        int port = Integer.parseInt(txtAuthPort.getText().trim());
        String user = txtAuthEmail.getText().trim().toLowerCase();
        String pass = new String(txtAuthPassword.getPassword());

        lblAuthStatus.setText("Đang kiểm tra USER / PASS với POP3 Server...");
        new Thread(() -> {
            try (Socket socket = new Socket(host, port);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                 PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {

                String greeting = in.readLine();
                out.print("USER " + user + "\r\n");
                out.flush();
                String userRes = in.readLine();

                out.print("PASS " + pass + "\r\n");
                out.flush();
                String passRes = in.readLine();

                out.print("QUIT\r\n");
                out.flush();

                SwingUtilities.invokeLater(() -> {
                    if (userRes != null && userRes.startsWith("+OK") && passRes != null && passRes.startsWith("+OK")) {
                        loggedInServer = host;
                        loggedInPort = port;
                        loggedInEmail = user;
                        loggedInPassword = pass;

                        lblCurrentUser.setText("Hộp thư của: " + loggedInEmail + "  |  POP3 Server: " + loggedInServer + ":" + loggedInPort);
                        cardLayout.show(mainContainer, "INBOX");
                        fetchAllEmails(); // Tự động tải thư ngay khi vào hộp thư
                    } else {
                        lblAuthStatus.setText("Đăng nhập thất bại: Sai tài khoản hoặc mật khẩu!");
                        JOptionPane.showMessageDialog(this, "POP3 Server từ chối đăng nhập:\n" + passRes, "Lỗi đăng nhập", JOptionPane.ERROR_MESSAGE);
                    }
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    lblAuthStatus.setText("Lỗi kết nối: " + ex.getMessage());
                    JOptionPane.showMessageDialog(this, "Không thể kết nối POP3 Server: " + ex.getMessage());
                });
            }
        }).start();
    }

    private void fetchAllEmails() {
        txtMailOutput.setText("");
        btnRefreshMail.setEnabled(false);

        new Thread(() -> {
            try (Socket socket = new Socket(loggedInServer, loggedInPort);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                 PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {

                readSingleLine(in);
                sendCmd(out, in, "USER " + loggedInEmail);
                sendCmd(out, in, "PASS " + loggedInPassword);

                String statRes = sendCmd(out, in, "STAT");
                String[] parts = statRes.split(" ");
                int mailCount = (parts.length >= 2) ? Integer.parseInt(parts[1]) : 0;
                log("\n===> HỘP THƯ CỦA [" + loggedInEmail + "] HIỆN CÓ: " + mailCount + " EMAIL.\n");

                log("CLIENT: LIST");
                out.print("LIST\r\n");
                out.flush();
                readMultiLine(in);

                for (int i = 1; i <= mailCount; i++) {
                    log("\n================ ĐỌC EMAIL SỐ #" + i + " ================");
                    log("CLIENT: RETR " + i);
                    out.print("RETR " + i + "\r\n");
                    out.flush();
                    readMultiLine(in);
                }

                sendCmd(out, in, "QUIT");

            } catch (Exception ex) {
                log("\n[LỖI POP3]: " + ex.getMessage());
            } finally {
                SwingUtilities.invokeLater(() -> btnRefreshMail.setEnabled(true));
            }
        }).start();
    }

    private void deleteSelectedEmail() {
        String idxStr = txtDeleteIndex.getText().trim();
        new Thread(() -> {
            try (Socket socket = new Socket(loggedInServer, loggedInPort);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                 PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {

                readSingleLine(in);
                sendCmd(out, in, "USER " + loggedInEmail);
                sendCmd(out, in, "PASS " + loggedInPassword);
                sendCmd(out, in, "DELE " + idxStr);
                sendCmd(out, in, "QUIT");

                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, "Đã xóa thư số #" + idxStr + " thành công!");
                    fetchAllEmails();
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Lỗi khi xóa thư: " + ex.getMessage()));
            }
        }).start();
    }

    private void showHelpDialog() {
        String msg = """
                HƯỚNG DẪN NHẬN THƯ VỚI POP3 CLIENT
                =====================================================
                1. Nhập đúng IP của máy đang chạy LANMailServer (Port: 1110).
                2. Đăng nhập bằng tài khoản người nhận (VD: sv2@vku.udn.vn / Pass: 123456).
                3. Chương trình sẽ tự động gửi bộ lệnh chuẩn RFC 1939:
                   - USER & PASS : Xác thực người dùng.
                   - STAT & LIST : Kiểm tra số lượng và kích thước thư.
                   - RETR <i>    : Tải nội dung từng bức thư về hiển thị.
                   - DELE <i>    : Xóa thư trên Server theo số thứ tự.
                """;
        JOptionPane.showMessageDialog(this, msg, "Hướng dẫn POP3 Client", JOptionPane.INFORMATION_MESSAGE);
    }

    private String sendCmd(PrintWriter out, BufferedReader in, String cmd) throws IOException {
        log("CLIENT: " + cmd);
        out.print(cmd + "\r\n");
        out.flush();
        return readSingleLine(in);
    }

    private String readSingleLine(BufferedReader in) throws IOException {
        String res = in.readLine();
        log("SERVER: " + res);
        if (res == null || !res.startsWith("+OK")) {
            throw new IOException("POP3 lỗi: " + res);
        }
        return res;
    }

    private void readMultiLine(BufferedReader in) throws IOException {
        readSingleLine(in);
        String line;
        while ((line = in.readLine()) != null) {
            if (line.equals(".")) {
                log("SERVER: . (Kết thúc dữ liệu)");
                break;
            }
            log("  " + line);
        }
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> txtMailOutput.append(msg + "\n"));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new POP3Client().setVisible(true));
    }
}