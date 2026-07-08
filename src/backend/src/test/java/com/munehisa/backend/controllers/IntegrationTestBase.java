package com.munehisa.backend.controllers;

import com.munehisa.backend.domain.user.User;
import com.munehisa.backend.repository.UserRepository;
import com.munehisa.backend.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import java.util.function.Consumer;


@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("integration")
public abstract class IntegrationTestBase {

    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    static {
        postgres.start();
    }

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @MockitoBean
    protected EmailService emailService;

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected JsonMapper objectMapper;
    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAll();
    }

    protected User createUser(Consumer<User> customizer) {
        User user = new User();
        user.setName("Ada Lovelace");
        user.setEmail("ada@example.com");
        user.setPassword(passwordEncoder.encode("correct-password"));
        user.setVerified(true);
        customizer.accept(user);
        return userRepository.save(user);
    }
}
