package com.munehisa.backend.infra.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.munehisa.backend.domain.user.User;
import com.auth0.jwt.algorithms.Algorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class TokenService {
    @Value("${api.security.token.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long tokenExpirationMs;

    public String generateToken(User user) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);

            String token = JWT.create()
                    .withIssuer("munehisa-backend")
                    .withSubject(user.getEmail())
                    .withExpiresAt(this.generateExpirationDate())
                    .sign(algorithm);

            return token;

        } catch(JWTCreationException excption) {
            throw new RuntimeException("AuthError");
        }
    }

    public String validateToken(String token) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            return JWT.require(algorithm)
                    .withIssuer("munehisa-backend")
                    .build()
                    .verify(token)
                    .getSubject();

        } catch(JWTVerificationException exception) {
            return null;
        }
    }

    private Instant generateExpirationDate() {
        return Instant.now().plusMillis(tokenExpirationMs);
    }
}
