import jakarta.mail.*;
import java.util.Properties;

final class GmailRelay implements LANMailServer.OutboundRelay {
    private final String email;
    private final String password;

    GmailRelay(String email, String password) throws MessagingException {
        MailMime.address(email);
        if (password.isBlank()) throw new MessagingException("Nhập Mật khẩu ứng dụng Google.");
        this.email = email;
        this.password = password.replace(" ", "");
    }

    public String prepare(String raw, String sender) throws Exception {
        return MailMime.forRelay(raw, sender, email);
    }

    private Transport connect() throws MessagingException {
        Properties props = new Properties();
        props.setProperty("mail.smtp.ssl.enable", "true");
        props.setProperty("mail.smtp.ssl.checkserveridentity", "true");
        props.setProperty("mail.smtp.auth", "true");
        props.setProperty("mail.smtp.connectiontimeout", "10000");
        props.setProperty("mail.smtp.timeout", "30000");
        props.setProperty("mail.smtp.writetimeout", "30000");
        props.setProperty("mail.smtp.from", email);
        props.setProperty("mail.smtp.localhost", "localhost");
        Transport transport = Session.getInstance(props).getTransport("smtp");
        try {
            transport.connect("smtp.gmail.com", 465, email, password);
            return transport;
        } catch (MessagingException e) {
            try { transport.close(); } catch (MessagingException ignored) { }
            throw e;
        }
    }

    void verify() throws MessagingException {
        Transport transport = connect();
        try { transport.close(); } catch (MessagingException ignored) { }
    }

    public void send(String raw, String recipient) throws Exception {
        Transport transport = connect();
        try {
            transport.sendMessage(MailMime.parse(raw), new Address[]{MailMime.address(recipient)});
        } finally {
            // DATA acceptance is the success boundary; a failed QUIT must not trigger a duplicate send.
            try { transport.close(); } catch (MessagingException ignored) { }
        }
    }
}
