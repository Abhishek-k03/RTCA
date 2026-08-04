package com.rtca.auth.refresh;

import com.rtca.common.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final RefreshTokenProperties properties;

    public record Rotated(Long userId, String token) {
    }

    /** Starts a new session at login. */
    @Transactional
    public String issue(Long userId) {
        return issue(userId, UUID.randomUUID());
    }

    /**
     * Swaps a refresh token for a new one. A token that was already swapped
     * coming back later means it leaked, so the whole session is revoked.
     */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public Rotated rotate(String rawToken) {
        Instant now = Instant.now();
        RefreshToken current = repository.findForUpdate(hash(rawToken))
                .filter(t -> t.isUsable(now))
                .orElseThrow(RefreshTokenService::invalid);

        Instant replacedAt = current.getReplacedAt();
        if (replacedAt != null && replacedAt.plus(properties.reuseInterval()).isBefore(now)) {
            log.warn("Refresh token reused, revoking session {} of user {}", current.getFamilyId(), current.getUserId());
            repository.revokeFamily(current.getFamilyId(), now);
            throw invalid();
        }
        current.markReplaced(now);
        return new Rotated(current.getUserId(), issue(current.getUserId(), current.getFamilyId()));
    }

    /** Ends this session only. Unknown tokens are ignored. */
    @Transactional
    public void revoke(String rawToken) {
        repository.findByTokenHash(hash(rawToken))
                .ifPresent(t -> repository.revokeFamily(t.getFamilyId(), Instant.now()));
    }

    // replaced and revoked tokens are kept until they expire, for reuse detection
    @Scheduled(cron = "0 17 4 * * *")
    @Transactional
    public void deleteExpired() {
        int deleted = repository.deleteExpired(Instant.now());
        if (deleted > 0) {
            log.info("Deleted {} expired refresh tokens", deleted);
        }
    }

    private String issue(Long userId, UUID familyId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        repository.save(RefreshToken.builder()
                .userId(userId)
                .familyId(familyId)
                .tokenHash(hash(token))
                .expiresAt(Instant.now().plus(properties.ttl()))
                .build());
        return token;
    }

    // only the hash is stored, so a copy of the table can't be used to sign in
    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static UnauthorizedException invalid() {
        return new UnauthorizedException("Session expired, please sign in again");
    }
}
