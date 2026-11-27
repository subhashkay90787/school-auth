package com.school.auth.otp;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Issues and verifies one-time passwords stored in Redis, delivered over WhatsApp (phone) or email.
 *
 * Keys per identifier (normalized phone or lower-cased email):
 *   otp:code:{id}      SHA-256 of the code, expires after app.otp.ttl
 *   otp:attempts:{id}  failed verification counter, same TTL
 *   otp:cooldown:{id}  blocks re-sending until app.otp.resend-cooldown elapses
 */
@Service
public class OtpService {

    /** Outcome of a verification, so callers can log WRONG_OTP / EXPIRED_OTP / LOCKED separately. */
    public enum Result { OK, WRONG, EXPIRED, LOCKED }

    private static final String CODE_KEY = "otp:code:";
    private static final String ATTEMPTS_KEY = "otp:attempts:";
    private static final String COOLDOWN_KEY = "otp:cooldown:";

    private final StringRedisTemplate redis;
    private final OtpProperties properties;
    private final Map<OtpChannel, OtpSender> senders;
    private final SecureRandom random = new SecureRandom();

    public OtpService(StringRedisTemplate redis, OtpProperties properties, List<OtpSender> senders) {
        this.redis = redis;
        this.properties = properties;
        this.senders = senders.stream().collect(Collectors.toMap(OtpSender::channel, Function.identity()));
    }

    public static String normalizePhone(String phone) {
        return phone == null ? null : phone.replaceAll("[\\s\\-()]", "");
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public static String normalize(OtpChannel channel, String identifier) {
        return channel == OtpChannel.EMAIL ? normalizeEmail(identifier) : normalizePhone(identifier);
    }

    /** SHA-256 hex of the normalized identifier, for login_attempt.identifier_hash. */
    public static String identifierHash(String normalizedIdentifier) {
        return hash(normalizedIdentifier);
    }

    public void sendOtp(OtpChannel channel, String rawIdentifier) {
        String id = normalize(channel, rawIdentifier);

        Boolean allowed = redis.opsForValue().setIfAbsent(COOLDOWN_KEY + id, "1", properties.resendCooldown());
        if (!Boolean.TRUE.equals(allowed)) {
            throw new OtpRateLimitedException("An OTP was sent recently. Please wait before requesting another.");
        }

        String code = generateCode();
        redis.opsForValue().set(CODE_KEY + id, hash(code), properties.ttl());
        redis.delete(ATTEMPTS_KEY + id);

        OtpSender sender = senders.get(channel);
        if (sender == null) {
            throw new IllegalStateException("No OTP sender configured for " + channel);
        }
        sender.send(id, code, properties.ttl().toMinutes());
    }

    /** Phone OTPs (onboarding, profile phone changes) are sent over WhatsApp. */
    public void sendOtp(String rawPhone) {
        sendOtp(OtpChannel.WHATSAPP, rawPhone);
    }

    public boolean verifyOtp(String rawPhone, String code) {
        return verify(OtpChannel.WHATSAPP, rawPhone, code) == Result.OK;
    }

    /**
     * Succeeds exactly once for a correct, unexpired code: the code is consumed on success and invalidated
     * after app.otp.max-attempts failed tries.
     */
    public Result verify(OtpChannel channel, String rawIdentifier, String code) {
        String id = normalize(channel, rawIdentifier);
        String codeKey = CODE_KEY + id;
        String attemptsKey = ATTEMPTS_KEY + id;

        Long attempts = redis.opsForValue().increment(attemptsKey);
        if (attempts != null && attempts == 1) {
            redis.expire(attemptsKey, properties.ttl());
        }
        if (attempts == null || attempts > properties.maxAttempts()) {
            redis.delete(codeKey);
            return Result.LOCKED;
        }

        String storedHash = redis.opsForValue().get(codeKey);
        if (storedHash == null) {
            return Result.EXPIRED;
        }
        if (code == null) {
            return Result.WRONG;
        }

        boolean matches = MessageDigest.isEqual(
                storedHash.getBytes(StandardCharsets.UTF_8),
                hash(code).getBytes(StandardCharsets.UTF_8));

        // Deleting the key is the "consume" step; only the caller that actually deletes it wins
        if (matches && Boolean.TRUE.equals(redis.delete(codeKey))) {
            redis.delete(attemptsKey);
            return Result.OK;
        }
        return Result.WRONG;
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
