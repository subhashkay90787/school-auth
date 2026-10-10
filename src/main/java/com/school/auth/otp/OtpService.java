package com.school.auth.otp;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Issues and verifies one-time passwords stored in Redis.
 *
 * Keys per phone number:
 *   otp:code:{phone}      SHA-256 of the code, expires after app.otp.ttl
 *   otp:attempts:{phone}  failed verification counter, same TTL
 *   otp:cooldown:{phone}  blocks re-sending until app.otp.resend-cooldown elapses
 */
@Service
@RequiredArgsConstructor
public class OtpService {

    private static final String CODE_KEY = "otp:code:";
    private static final String ATTEMPTS_KEY = "otp:attempts:";
    private static final String COOLDOWN_KEY = "otp:cooldown:";

    private final StringRedisTemplate redis;
    private final SmsSender smsSender;
    private final OtpProperties properties;
    private final SecureRandom random = new SecureRandom();

    public static String normalizePhone(String phone) {
        return phone == null ? null : phone.replaceAll("[\\s\\-()]", "");
    }

    public void sendOtp(String rawPhone) {
        String phone = normalizePhone(rawPhone);

        Boolean allowed = redis.opsForValue().setIfAbsent(COOLDOWN_KEY + phone, "1", properties.resendCooldown());
        if (!Boolean.TRUE.equals(allowed)) {
            throw new OtpRateLimitedException("An OTP was sent recently. Please wait before requesting another.");
        }

        String code = generateCode();
        redis.opsForValue().set(CODE_KEY + phone, hash(code), properties.ttl());
        redis.delete(ATTEMPTS_KEY + phone);

        smsSender.send(phone, "Your verification code is " + code
                + ". It expires in " + properties.ttl().toMinutes() + " minutes.");
    }

    /**
     * Returns true exactly once for a correct, unexpired code. The code is consumed on success
     * and invalidated after app.otp.max-attempts failed tries.
     */
    public boolean verifyOtp(String rawPhone, String code) {
        String phone = normalizePhone(rawPhone);
        String codeKey = CODE_KEY + phone;
        String attemptsKey = ATTEMPTS_KEY + phone;

        Long attempts = redis.opsForValue().increment(attemptsKey);
        if (attempts != null && attempts == 1) {
            redis.expire(attemptsKey, properties.ttl());
        }
        if (attempts == null || attempts > properties.maxAttempts()) {
            redis.delete(codeKey);
            return false;
        }

        String storedHash = redis.opsForValue().get(codeKey);
        if (storedHash == null || code == null) {
            return false;
        }

        boolean matches = MessageDigest.isEqual(
                storedHash.getBytes(StandardCharsets.UTF_8),
                hash(code).getBytes(StandardCharsets.UTF_8));

        // Deleting the key is the "consume" step; only the caller that actually deletes it wins
        if (matches && Boolean.TRUE.equals(redis.delete(codeKey))) {
            redis.delete(attemptsKey);
            return true;
        }
        return false;
    }

    private String generateCode() {
        int bound = (int) Math.pow(10, properties.length());
        return String.format("%0" + properties.length() + "d", random.nextInt(bound));
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
