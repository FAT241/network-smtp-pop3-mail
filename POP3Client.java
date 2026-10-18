
import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class POP3Client extends JFrame {

    private JTextField txtServer, txtPort, txtUser;
    private JPasswordField txtPass;
    private JTextArea txtMailOutput;
    private JButton btnFetchMail;

    public POP3Client() {
        setTitle("POP3 Client - Đăng nhập & Nhận Email (RFC 1939)");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(8, 8));

        JPanel topPanel = new JPanel(new GridLayout(4, 2, 5, 5));
        topPanel.setBorder(BorderFactory.createTitledBorder("Thông tin Đăng nhập hộp thư POP3"));

        txtServer = new JTextField("localhost");
        txtPort = new JTextField("1110");
        txtUser = new JTextField("sv2@vku.udn.vn");
        txtPass = new JPasswordField("123456");

        topPanel.add(new JLabel("POP3 Server Host:"));
        topPanel.add(txtServer);
        topPanel.add(new JLabel("POP3 Port:"));
        topPanel.add(txtPort);
        topPanel.add(new JLabel("Tài khoản (USER):"));
        topPanel.add(txtUser);
        topPanel.add(new JLabel("Mật khẩu (PASS):"));
        topPanel.add(txtPass);

        add(topPanel, BorderLayout.NORTH);

        txtMailOutput = new JTextArea();
        txtMailOutput.setEditable(false);
        txtMailOutput.setFont(new Font("Monospaced", Font.PLAIN, 13));
        JScrollPane scrollPane = new JScrollPane(txtMailOutput);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Nhật ký xác thực POP3 & Nội dung Hộp thư"));
        add(scrollPane, BorderLayout.CENTER);

        btnFetchMail = new JButton("Đăng nhập POP3 & Tải Hộp thư");
        add(btnFetchMail, BorderLayout.SOUTH);

        btnFetchMail.addActionListener(e -> receiveEmails());
    }

    private void receiveEmails() {
        String host = txtServer.getText().trim();
        int port = Integer.parseInt(txtPort.getText().trim());
        String user = txtUser.getText().trim();
        String pass = new String(txtPass.getPassword());

        txtMailOutput.setText("");
        btnFetchMail.setEnabled(false);

        new Thread(() -> {
            try (Socket socket = new Socket(host, port);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                 PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {

                // 1. Đọc lời chào +OK từ POP3 Server
                readSingleLineResponse(in);

                // 2. Đăng nhập bằng lệnh USER và PASS
                sendCommand(out, in, "USER " + user);
                sendCommand(out, in, "PASS " + pass);

                // 3. Kiểm tra số lượng thư trong hộp thư của user này bằng lệnh STAT
                String statRes = sendCommand(out, in, "STAT");
                String[] parts = statRes.split(" ");
                int mailCount = (parts.length >= 2) ? Integer.parseInt(parts[1]) : 0;
                log("\n--> Hộp thư của [" + user + "] hiện có: " + mailCount + " email.\n");

                // 4. Liệt kê danh sách thư bằng lệnh LIST
                log("CLIENT: LIST");
                out.print("LIST\r\n");
                out.flush();
                readMultiLineResponse(in);

                // 5. Tải nội dung từng bức thư bằng lệnh RETR
                for (int i = 1; i <= mailCount; i++) {
                    log("\n================ ĐỌC EMAIL SỐ #" + i + " ================");
                    log("CLIENT: RETR " + i);
                    out.print("RETR " + i + "\r\n");
                    out.flush();
                    readMultiLineResponse(in);
                }

                // 6. Thoát phiên làm việc
                sendCommand(out, in, "QUIT");
                log("\n===> HOÀN TẤT NHẬN EMAIL!");

            } catch (Exception ex) {
                log("\n[LỖI ĐĂNG NHẬP / POP3]: " + ex.getMessage());
            } finally {
                SwingUtilities.invokeLater(() -> btnFetchMail.setEnabled(true));
            }
        }).start();
    }

    private String sendCommand(PrintWriter out, BufferedReader in, String cmd) throws IOException {
        log("CLIENT: " + cmd);
        out.print(cmd + "\r\n");
        out.flush();
        return readSingleLineResponse(in);
    }

    private String readSingleLineResponse(BufferedReader in) throws IOException {
        String response = in.readLine();
        log("SERVER: " + response);
        if (response == null || !response.startsWith("+OK")) {
            throw new IOException("POP3 Server từ chối: " + response);
        }
        return response;
    }

    private void readMultiLineResponse(BufferedReader in) throws IOException {
        readSingleLineResponse(in);
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