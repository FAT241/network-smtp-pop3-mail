import jakarta.mail.*;
import jakarta.mail.internet.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.swing.*;
import java.awt.*;

/** MIME is shared by SMTP composition, server storage and POP3 viewing. */
final class MailMime {
    static final int MAX_BYTES = 20 * 1024 * 1024;
    // Avoid reverse-DNS stalls on Wi-Fi/VPN adapters when generating Message-ID.
    static final Session SESSION = Session.getInstance(mimeProperties());
    private static Properties mimeProperties() {
        Properties properties = new Properties();
        properties.setProperty("mail.from", "postmaster@lab.invalid");
        return properties;
    }

    static InternetAddress address(String value) throws MessagingException {
        if (value == null || !value.matches("[^\\s@<>;,]+@[^\\s@<>;,]+"))
            throw new MessagingException("Địa chỉ email không hợp lệ");
        InternetAddress result = new InternetAddress(value, true);
        result.validate();
        return result;
    }

    static MimeMessage parse(String raw) throws MessagingException {
        return new MimeMessage(SESSION, new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));
    }

    static String raw(MimeMessage message) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        message.writeTo(out);
        if (out.size() > MAX_BYTES) throw new IOException("Thư và file đính kèm vượt quá 20 MiB sau mã hóa.");
        return out.toString(StandardCharsets.UTF_8);
    }

    static String compose(String from, String to, String subject, String body, File file) throws Exception {
        MimeMessage message = new MimeMessage(SESSION);
        message.setFrom(address(from));
        message.setRecipient(Message.RecipientType.TO, address(to));
        message.setSubject(subject, "UTF-8");
        message.setSentDate(new Date());
        if (file == null) {
            message.setText(body, "UTF-8");
        } else {
            if (!file.isFile() || file.length() > 14 * 1024 * 1024)
                throw new IOException("Chọn một file tối đa 14 MiB.");
            MimeMultipart parts = new MimeMultipart();
            MimeBodyPart text = new MimeBodyPart();
            text.setText(body, "UTF-8");
            parts.addBodyPart(text);
            MimeBodyPart attachment = new MimeBodyPart();
            attachment.attachFile(file, "application/octet-stream", "base64");
            parts.addBodyPart(attachment);
            message.setContent(parts);
        }
        message.saveChanges();
        return raw(message);
    }

    static String canonical(String raw, String sender, java.util.List<String> recipients) throws Exception {
        MimeMessage message = parse(raw);
        message.setFrom(address(sender));
        message.setRecipients(Message.RecipientType.TO,
                recipients.stream().map(s -> {
                    try { return address(s); } catch (MessagingException e) { throw new IllegalArgumentException(e); }
                }).toArray(InternetAddress[]::new));
        for (String name : new String[]{"Bcc", "Cc", "Sender", "Reply-To", "Return-Path", "X-Lab-Sender", "X-Lab-Status"})
            message.removeHeader(name);
        if (message.getSentDate() == null) message.setSentDate(new Date());
        message.saveChanges();
        return raw(message);
    }

    static String forRelay(String raw, String sender, String gmail) throws Exception {
        MimeMessage message = parse(raw);
        message.setFrom(new InternetAddress(gmail, sender + " (LAN Mail)", "UTF-8"));
        message.setReplyTo(new Address[]{address(gmail)});
        message.setHeader("X-Lab-Sender", sender);
        message.saveChanges();
        return raw(message);
    }

    record Attachment(String name, byte[] bytes) {}
    record Preview(String text, java.util.List<Attachment> attachments) {}

    static Preview preview(String raw) throws Exception {
        MimeMessage message = parse(raw);
        StringBuilder text = new StringBuilder();
        text.append("Từ: ").append(Arrays.toString(message.getFrom()))
            .append("\nĐến: ").append(Arrays.toString(message.getAllRecipients()))
            .append("\nTiêu đề: ").append(message.getSubject()).append('\n');
        String status = message.getHeader("X-Lab-Status", null);
        if (status != null) text.append("Trạng thái: ").append(status).append('\n');
        text.append('\n');
        java.util.List<Attachment> attachments = new ArrayList<>();
        readPart(message, text, attachments, 0);
        return new Preview(text.toString(), attachments);
    }

    private static void readPart(Part part, StringBuilder text, java.util.List<Attachment> files, int depth) throws Exception {
        if (depth > 20) throw new IOException("MIME có quá nhiều lớp lồng nhau.");
        if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition()) || part.getFileName() != null) {
            String name = part.getFileName();
            if (name == null) name = "attachment.bin";
            name = name.replace('\\', '/');
            name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[<>:\"|?*\\p{Cntrl}]", "_");
            if (name.isBlank() || name.equals(".") || name.equals("..")) name = "attachment.bin";
            try (InputStream in = part.getInputStream()) {
                byte[] bytes = in.readNBytes(MAX_BYTES + 1);
                if (bytes.length > MAX_BYTES) throw new IOException("File quá lớn.");
                files.add(new Attachment(name, bytes));
            }
            text.append("\nFile: ").append(name).append('\n');
        } else if (part.isMimeType("multipart/*")) {
            Multipart parts = (Multipart) part.getContent();
            for (int i = 0; i < parts.getCount(); i++) readPart(parts.getBodyPart(i), text, files, depth + 1);
        } else if (part.isMimeType("text/*")) {
            text.append(part.getContent()).append('\n');
        }
    }

    static void show(Component parent, String raw) {
        try {
            Preview preview = preview(raw);
            JTextArea body = new JTextArea(preview.text(), 20, 70);
            body.setEditable(false);
            body.setLineWrap(true);
            body.setWrapStyleWord(true);
            JPanel panel = new JPanel(new BorderLayout(8, 8));
            panel.add(new JScrollPane(body));
            JComboBox<String> files = new JComboBox<>(preview.attachments().stream().map(Attachment::name).toArray(String[]::new));
            JButton save = new JButton("Lưu file đính kèm...");
            save.setEnabled(!preview.attachments().isEmpty());
            JPanel bottom = new JPanel();
            bottom.add(files);
            bottom.add(save);
            panel.add(bottom, BorderLayout.SOUTH);
            save.addActionListener(e -> {
                Attachment attachment = preview.attachments().get(files.getSelectedIndex());
                JFileChooser chooser = new JFileChooser();
                chooser.setSelectedFile(new File(attachment.name()));
                if (chooser.showSaveDialog(panel) != JFileChooser.APPROVE_OPTION) return;
                Path path = chooser.getSelectedFile().toPath();
                if (Files.exists(path) && JOptionPane.showConfirmDialog(panel, "Ghi đè file đã có?",
                        "Lưu file", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
                try {
                    Files.write(path, attachment.bytes());
                    JOptionPane.showMessageDialog(panel, "Đã lưu: " + path);
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(panel, "Không lưu được: " + ex.getMessage());
                }
            });
            JOptionPane.showMessageDialog(parent, panel, "Xem thư và file đính kèm", JOptionPane.PLAIN_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(parent, "Không đọc được thư: " + e.getMessage());
        }
    }
}
