package com.operaciones.backend.auth;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class AuthTokenStore {

    public enum ChallengePurpose {
        CHANGE_PASSWORD,
        SETUP_2FA,
        VERIFY_2FA
    }

    public record Session(
            String token,
            Long userId,
            String kind,
            String displayName,
            Instant expiresAt) {
    }

    public record Challenge(
            String token,
            Long userId,
            String kind,
            String displayName,
            ChallengePurpose purpose,
            Instant expiresAt) {
    }

    public record LoginEmailCode(String code, Instant expiresAt) {
    }

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, Challenge> challenges = new ConcurrentHashMap<>();
    private final Map<String, LoginEmailCode> loginEmailCodes = new ConcurrentHashMap<>();

    public Session createSession(Long userId, String kind, String displayName) {
        purgeExpired();
        String token = newToken();
        Session session = new Session(token, userId, kind, displayName, Instant.now().plusSeconds(60L * 60L * 12L));
        sessions.put(token, session);
        return session;
    }

    public Challenge createChallenge(Long userId, String kind, String displayName, ChallengePurpose purpose) {
        purgeExpired();
        String token = newToken();
        Challenge challenge = new Challenge(
                token,
                userId,
                kind,
                displayName,
                purpose,
                Instant.now().plusSeconds(60L * 10L));
        challenges.put(token, challenge);
        return challenge;
    }

    public Optional<Session> findSession(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        purgeExpired();
        return Optional.ofNullable(sessions.get(token));
    }

    public Optional<Challenge> findChallenge(String token, ChallengePurpose purpose) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        purgeExpired();
        Challenge challenge = challenges.get(token);
        if (challenge == null || challenge.purpose() != purpose) {
            return Optional.empty();
        }
        if (challenge.expiresAt().isBefore(Instant.now())) {
            challenges.remove(token);
            loginEmailCodes.remove(token);
            return Optional.empty();
        }
        return Optional.of(challenge);
    }

    public Optional<Challenge> consumeChallenge(String token, ChallengePurpose purpose) {
        Optional<Challenge> found = findChallenge(token, purpose);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        challenges.remove(token);
        loginEmailCodes.remove(token);
        return found;
    }

    public void putLoginEmailCode(String challengeToken, String code, Instant expiresAt) {
        if (challengeToken == null || code == null) {
            return;
        }
        loginEmailCodes.put(challengeToken, new LoginEmailCode(code, expiresAt));
    }

    public Optional<LoginEmailCode> findLoginEmailCode(String challengeToken) {
        if (challengeToken == null || challengeToken.isBlank()) {
            return Optional.empty();
        }
        LoginEmailCode entry = loginEmailCodes.get(challengeToken);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expiresAt().isBefore(Instant.now())) {
            loginEmailCodes.remove(challengeToken);
            return Optional.empty();
        }
        return Optional.of(entry);
    }

    public void revokeSession(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    public void updateSessionDisplayName(String token, String displayName) {
        if (token == null || displayName == null || displayName.isBlank()) {
            return;
        }
        Session current = sessions.get(token);
        if (current == null) {
            return;
        }
        sessions.put(
                token,
                new Session(current.token(), current.userId(), current.kind(), displayName.trim(), current.expiresAt()));
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        sessions.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
        challenges.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
        loginEmailCodes.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    private static String newToken() {
        return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    }
}
