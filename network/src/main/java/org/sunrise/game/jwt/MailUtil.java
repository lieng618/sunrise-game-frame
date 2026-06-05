package org.sunrise.game.jwt;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeUtility;
import lombok.Data;
import org.sunrise.game.config.ConfigReader;
import org.sunrise.game.log.LogCore;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class MailUtil {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    private static Session session;

    @Data
    public static class EmailCode {
        private String email;
        private String code;
        private long expireTime;
    }

    private static final Map<String, EmailCode> emailCodes = new ConcurrentHashMap<>();

    //初始化smtp会话，单例只初始化一次
    private static Session getSession() {
        if (session != null) return session;
        Properties prop = ConfigReader.getProp();
        prop.put("mail.smtp.host", "smtp.qq.com");
        prop.put("mail.smtp.port", "587");
        prop.put("mail.smtp.auth", "true");
        prop.put("mail.smtp.starttls.enable", "true");
        prop.put("mail.smtp.starttls.required", "true");
        //超时防卡死
        prop.put("mail.smtp.connectiontimeout", "5000");
        prop.put("mail.smtp.timeout", "5000");
        prop.put("mail.smtp.writetimeout", "5000");

        session = Session.getInstance(prop, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(prop.getProperty("mail.smtp.username"), prop.getProperty("mail.smtp.password"));
            }
        });
        return session;
    }

    /**
     * 发送验证码邮件
     *
     * @param toMail 收件邮箱
     */
    public static String sendVerifyCode(String toMail) {
        try {
            if (!isValidEmail(toMail)) {
                return "";
            }
            EmailCode existEntry = emailCodes.get(toMail);
            if (existEntry != null) {
                long lastSendTime = existEntry.getExpireTime() - 300000;
                // 发送频繁，1分钟内只发送一次验证码
                if (System.currentTimeMillis() - lastSendTime < 60 * 1000) {
                    return "";
                }
            }

            Properties prop = ConfigReader.getProp();
            Session session = getSession();
            MimeMessage msg = new MimeMessage(session);
            //发件人带昵称
            msg.setFrom(new InternetAddress(prop.getProperty("mail.smtp.username"), "sunrise-game-frame code", "UTF-8"));

            msg.setRecipient(Message.RecipientType.TO, new InternetAddress(toMail));
            //邮件标题
            msg.setSubject(MimeUtility.encodeText("【系统验证码】", "UTF-8", "B"));
            //模板
            String code = generateNumericCode();
            String html = "<div style='padding:20px'>" +
                    "<h3>您好！您的验证码：</h3>" +
                    "<p style='font-size:24px;color:#0066ff;font-weight:bold'>" + code + "</p>" +
                    "<p>验证码5分钟内有效，请勿泄露。如非本人操作请忽略。</p>" +
                    "</div>";
            msg.setContent(html, "text/html;charset=UTF-8");
            Transport.send(msg);

            EmailCode entry = new EmailCode();
            entry.setEmail(toMail);
            entry.setCode(code);
            entry.setExpireTime(System.currentTimeMillis() + 300000);
            emailCodes.put(toMail, entry);
            return code;
        } catch (Exception e) {
            LogCore.BaseServer.error("sendVerifyCode failed ", e);
        }
        return "";
    }

    public static boolean verifyCode(String email, String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        EmailCode entry = emailCodes.get(email);
        if (entry == null || !code.equals(entry.getCode())) {
            return false;
        }
        return System.currentTimeMillis() <= entry.getExpireTime();
    }

    public static void removeCode(String email) {
        emailCodes.remove(email);
    }

    private static String generateNumericCode() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            sb.append((int) (Math.random() * 10));
        }
        return sb.toString();
    }

    private static boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }
}
