package com.school.auth.otp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Development-only senders that write the OTP to the application log. Replace each with a real provider
 * (WhatsApp Business Cloud API template message; SMTP / SES for email) by declaring a bean of the same type.
 */
@Slf4j
@Configuration
public class LoggingOtpSenders {

    public interface WhatsAppOtpSender extends OtpSender {
        @Override
        default OtpChannel channel() {
            return OtpChannel.WHATSAPP;
        }
    }

    public interface EmailOtpSender extends OtpSender {
        @Override
        default OtpChannel channel() {
            return OtpChannel.EMAIL;
        }
    }

    @Bean
    @ConditionalOnMissingBean(WhatsAppOtpSender.class)
    WhatsAppOtpSender loggingWhatsAppOtpSender() {
        return (destination, code, ttl) ->
                log.warn("[DEV WHATSAPP] to={} code={} (expires in {} min)", destination, code, ttl);
    }

    @Bean
    @ConditionalOnMissingBean(EmailOtpSender.class)
    EmailOtpSender loggingEmailOtpSender() {
        return (destination, code, ttl) ->
                log.warn("[DEV EMAIL] to={} code={} (expires in {} min)", destination, code, ttl);
    }
}
