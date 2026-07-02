package com.munehisa.backend.repository;

import com.munehisa.backend.domain.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @DataJpaTest is a "test slice": it only wires up JPA-related beans
 * (entity manager, Spring Data repositories, the DataSource), not the whole
 * application - that's what keeps it fast compared to @SpringBootTest.
 * By default it tries to replace the DataSource with an embedded in-memory
 * database, but this project has no H2/Derby/HSQL on the classpath (it's
 * Postgres-only), so replace = NONE tells it to keep the real DataSource,
 * which here comes from the Testcontainers Postgres started below.
 * Each test method runs inside a transaction that's rolled back afterwards,
 * so no manual cleanup between tests is needed.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class UserRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private UserRepository userRepository;

    private User persistUser(String email, String verificationToken) {
        User user = new User();
        user.setName("Ada Lovelace");
        user.setEmail(email);
        user.setPassword("hashed-password");
        user.setVerified(false);
        user.setVerificationToken(verificationToken);
        user.setVerificationTokenExpiry(Instant.now().plusSeconds(60));
        return userRepository.save(user);
    }

    @Test
    void findByEmail() {
        persistUser("ada@example.com", "some-token");

        Optional<User> found = userRepository.findByEmail("ada@example.com");

        assertTrue(found.isPresent());
        assertEquals("ada@example.com", found.get().getEmail());
        assertTrue(userRepository.findByEmail("unknown@example.com").isEmpty());
    }

    @Test
    void findByVerificationToken() {
        persistUser("ada@example.com", "some-token");

        Optional<User> found = userRepository.findByVerificationToken("some-token");

        assertTrue(found.isPresent());
        assertEquals("ada@example.com", found.get().getEmail());
        assertTrue(userRepository.findByVerificationToken("unknown-token").isEmpty());
    }
}
