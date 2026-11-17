import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import javax.swing.*;

/** Uses an isolated PostgreSQL schema and a fake outbound relay; never sends Internet mail. */
public class MailIntegrationTest {
    static int checks;
    static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
        checks++;
    }
    static int port() throws IOException { try (ServerSocket s = new ServerSocket(0)) { return s.getLocalPort(); } }
    static class Wire implements AutoCloseable {
        Socket socket;
        BufferedReader in;
        PrintWriter out;
        Wire(int port) throws Exception {
            socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(60000);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            in.readLine();
        }
        String cmd(String command) throws IOException {
            out.print(command + "\r\n"); out.flush(); return in.readLine();
        }
        void smtpLogin(String user) throws Exception {
            check(cmd("EHLO test").startsWith("250"), "EHLO");
            check(cmd("AUTH LOGIN").startsWith("334"), "AUTH");
            cmd(Base64.getEncoder().encodeToString(user.getBytes(StandardCharsets.UTF_8)));
            check(cmd(Base64.getEncoder().encodeToString("test-pass".getBytes())).startsWith("235"), "SMTP login");
        }
        String submit(String user, String recipient, String raw) throws Exception {
            smtpLogin(user);
            check(cmd("MAIL FROM:<" + user + ">").startsWith("250"), "sender");
            check(cmd("RCPT TO:<" + recipient + ">").startsWith("250"), "recipient");
            check(cmd("DATA").startsWith("354"), "DATA");
            out.print(raw.replaceAll("(?m)^\\.", ".."));
            if (!raw.endsWith("\r\n")) out.print("\r\n");
            out.print(".\r\n"); out.flush();
            return in.readLine();
        }
        void popLogin(String user) throws Exception {
            check(cmd("USER " + user).startsWith("+OK"), "USER");
            check(cmd("PASS test-pass").startsWith("+OK"), "PASS");
        }
        String retrieve() throws Exception {
            check(cmd("RETR 1").startsWith("+OK"), "RETR");
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null && !line.equals("."))
                result.append(line.startsWith("..") ? line.substring(1) : line).append("\r\n");
            return result.toString();
        }
        public void close() throws IOException { socket.close(); }
    }
    static class FakeRelay implements LANMailServer.OutboundRelay {
        String received;
        boolean fail;
        public String prepare(String raw, String sender) throws Exception {
            return MailMime.forRelay(raw, sender, "relay@example.org");
        }
        public void send(String raw, String recipient) throws Exception {
            check(recipient.equals("recipient@example.net"), "external envelope");
            if (fail) throw new IOException("simulated disconnect");
            received = raw;
        }
    }
    public static void main(String[] args) throws Exception {
        String url = System.getenv().getOrDefault("LAB3_DB_URL", MailStore.DEFAULT_URL);
        String user = System.getenv("LAB3_DB_USER"), password = System.getenv("LAB3_DB_PASSWORD");
        String schema = "mail_test_" + UUID.randomUUID().toString().replace("-", "");
        int result = 1;
        Path file = Files.createTempFile("mail-test-", "-tệp.bin");
        try (Connection db = DriverManager.getConnection(url, user, password); Statement sql = db.createStatement()) {
            sql.execute("CREATE SCHEMA " + schema);
            try {
                MailStore store = new MailStore(url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema, user, password);
                store.initialize();
                store.register("sender@lab.test", "test-pass");
                store.register("receiver@lab.test", "test-pass");
                check(!store.register("bad\r\naddress", "pass"), "reject injected account");
                int smtp = port(), pop = port();
                System.setProperty("lab3.smtp.port", "" + smtp);
                System.setProperty("lab3.pop3.port", "" + pop);
                FakeRelay relay = new FakeRelay();
                SwingUtilities.invokeAndWait(() -> new LANMailServer(store, relay));
                byte[] bytes = new byte[8192];
                new Random(1).nextBytes(bytes);
                Files.write(file, bytes);
                String raw = MailMime.compose("sender@lab.test", "receiver@lab.test", "Tiêu đề tiếng Việt", "Xin chào\r\n.dòng chấm", file.toFile());
                try (Wire wire = new Wire(smtp)) {
                    check(wire.cmd("MAIL FROM:<sender@lab.test>").startsWith("530"), "authentication required");
                }
                try (Wire wire = new Wire(smtp)) {
                    check(wire.submit("sender@lab.test", "receiver@lab.test", raw).startsWith("250"), "local accepted");
                }
                try (Wire wire = new Wire(pop)) {
                    wire.popLogin("receiver@lab.test");
                    String received = wire.retrieve();
                    var preview = MailMime.preview(received);
                    check(preview.text().contains("Tiêu đề tiếng Việt"), "unicode subject");
                    check(preview.text().contains(".dòng chấm"), "unicode body/dot stuffing");
                    check(Arrays.equals(bytes, preview.attachments().get(0).bytes()), "local attachment exact bytes");
                    check(wire.cmd("XBOX SENT").startsWith("+OK"), "sent folder selection");
                    check(wire.cmd("STAT").startsWith("+OK 0 "), "receiver cannot see sender history");
                }
                try (Wire wire = new Wire(smtp)) {
                    check(wire.submit("sender@lab.test", "recipient@example.net", raw).startsWith("250"), "relay accepted");
                }
                check(Arrays.equals(bytes, MailMime.preview(relay.received).attachments().get(0).bytes()), "relay attachment exact bytes");
                check(MailMime.parse(relay.received).getFrom()[0].toString().contains("relay@example.org"), "real relay From");
                check(MailMime.parse(relay.received).getHeader("X-Lab-Sender", null).equals("sender@lab.test"), "internal sender preserved");
                check(store.sent("sender@lab.test").get(1).content().contains("RELAY_ACCEPTED"), "persist acceptance");
                relay.fail = true;
                try (Wire wire = new Wire(smtp)) {
                    check(wire.submit("sender@lab.test", "recipient@example.net", raw).startsWith("451"), "relay failure not success");
                }
                check(store.sent("sender@lab.test").get(2).content().contains("UNKNOWN_CHECK_GMAIL"), "uncertain delivery retained");
                try (Wire wire = new Wire(pop)) {
                    wire.popLogin("sender@lab.test");
                    wire.cmd("XBOX SENT");
                    check(wire.cmd("STAT").startsWith("+OK 3 "), "all outgoing records visible");
                    check(wire.cmd("DELE 1").startsWith("-ERR"), "sent history read-only");
                    check(Arrays.equals(bytes, MailMime.preview(wire.retrieve()).attachments().get(0).bytes()), "sender downloads file via POP3");
                }
                MailStore reopened = new MailStore(url + "?currentSchema=" + schema, user, password);
                check(reopened.sent("sender@lab.test").size() == 3, "history survives new DB connection");
                System.out.println("PASS: " + checks + " checks; no real Internet email sent.");
                result = 0;
            } catch (Throwable failure) {
                failure.printStackTrace();
                for (java.awt.Window window : java.awt.Window.getWindows()) {
                    if (window instanceof LANMailServer) {
                        var field = LANMailServer.class.getDeclaredField("txtServerLog");
                        field.setAccessible(true);
                        System.err.println(((JTextArea) field.get(window)).getText());
                    }
                }
            } finally { sql.execute("DROP SCHEMA " + schema + " CASCADE"); }
        } finally {
            Files.deleteIfExists(file);
        }
        System.exit(result);
    }
}
