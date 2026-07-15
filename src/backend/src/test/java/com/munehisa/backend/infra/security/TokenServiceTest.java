package com.munehisa.backend.infra.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.munehisa.backend.domain.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenServiceTest {

    private static final String SECRET = "test-secret-key-not-for-production-use-only-for-tests";

    private final TokenService tokenService = new TokenService();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(tokenService, "secret", SECRET);
        ReflectionTestUtils.setField(tokenService, "tokenExpirationMs", 3_600_000L);
    }

    private User buildUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("ada@example.com");
        return user;
    }

    @Test
    void generateToken_thenValidate_returnsSubjectEmail() {
        String token = tokenService.generateToken(buildUser());

        Optional<String> subject = tokenService.validateToken(token);

        assertTrue(subject.isPresent());
        assertEquals("ada@example.com", subject.get());
    }

    @Test
    void validateToken_expiredToken_returnsEmpty() {
        ReflectionTestUtils.setField(tokenService, "tokenExpirationMs", -1_000L);

        String token = tokenService.generateToken(buildUser());

        assertTrue(tokenService.validateToken(token).isEmpty());
    }

    @Test
    void validateToken_wrongSignature_returnsEmpty() {
        String tokenSignedWithDifferentSecret = JWT.create()
                .withIssuer("munehisa-backend")
                .withSubject("ada@example.com")
                .withExpiresAt(Instant.now().plusSeconds(60))
                .sign(Algorithm.HMAC256("a-completely-different-secret"));

        assertTrue(tokenService.validateToken(tokenSignedWithDifferentSecret).isEmpty());
    }

    @Test
    void validateToken_wrongIssuer_returnsEmpty() {
        String tokenWithWrongIssuer = JWT.create()
                .withIssuer("someone-else")
                .withSubject("ada@example.com")
                .withExpiresAt(Instant.now().plusSeconds(60))
                .sign(Algorithm.HMAC256(SECRET));

        assertTrue(tokenService.validateToken(tokenWithWrongIssuer).isEmpty());
    }

    @Test
    void validateToken_malformedToken_returnsEmpty() {
        assertTrue(tokenService.validateToken("not-a-jwt").isEmpty());
    }
}
