package com.quju.platform.service.impl;

import com.quju.platform.config.QujuProperties;
import com.quju.platform.exception.BusinessException;
import com.quju.platform.service.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final QujuProperties qujuProperties;

    @Override
    public void sendActivationEmail(String to, String activationToken) {
        QujuProperties.Mail mail = qujuProperties.getMail();
        String activationLink = buildActivationLink(mail.getActivationBaseUrl(), activationToken);
        if (!mail.isEnabled()) {
            log.info("Mail sending disabled. Activation email target={}, link={}", to, activationLink);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(mail.getFrom(), mail.getFromName(), StandardCharsets.UTF_8.name()));
            helper.setTo(to);
            helper.setSubject("趣聚账号激活");
            helper.setText("""
                    欢迎注册趣聚！

                    请点击下面的链接激活账号：
                    %s

                    该链接 24 小时内有效。如果不是你本人操作，请忽略这封邮件。
                    """.formatted(activationLink), false);
            mailSender.send(message);
        } catch (MailException | MessagingException | UnsupportedEncodingException ex) {
            log.warn("Failed to send activation email to {}", to, ex);
            throw new BusinessException(50002, "激活邮件发送失败，请稍后重试");
        }
    }

    private String buildActivationLink(String baseUrl, String activationToken) {
        String normalizedBaseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        String encodedToken = URLEncoder.encode(activationToken, StandardCharsets.UTF_8);
        return normalizedBaseUrl + "/auth/activate?token=" + encodedToken;
    }
}
