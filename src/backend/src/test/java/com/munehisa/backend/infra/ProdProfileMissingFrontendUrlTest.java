package com.munehisa.backend.infra;

import com.munehisa.backend.BackendApplication;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Confirms the acceptance criterion that starting the app with
 * SPRING_PROFILES_ACTIVE=prod and FRONTEND_URL unset fails to start, and specifically
 * because of the missing app.frontend-url property (application-prod.properties defines it
 * as ${FRONTEND_URL} with no fallback) rather than some other missing config. Every other
 * property application.properties requires with no default is supplied directly so the
 * failure can't be attributed to anything but the one under test.
 */
@Tag("integration")
class ProdProfileMissingFrontendUrlTest {

    private static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    static {
        postgres.start();
    }

    @Test
    void missingFrontendUrl_failsToStartTheApplication() {
        SpringApplicationBuilder builder = new SpringApplicationBuilder(BackendApplication.class)
                .web(WebApplicationType.NONE);

        // Passed as command-line-style args (highest property precedence, above any
        // application-*.properties file or a developer's local .env) rather than
        // builder.properties(), which only sets low-precedence *default* properties and
        // would silently lose to the real values in application.properties/.env.
        Exception exception = assertThrows(Exception.class, () -> builder.run(
                "--spring.profiles.active=prod",
                "--spring.datasource.url=" + postgres.getJdbcUrl(),
                "--spring.datasource.username=" + postgres.getUsername(),
                "--spring.datasource.password=" + postgres.getPassword(),
                "--api.security.token.secret=test-secret-key-not-for-production-use-only-for-tests",
                "--spring.mail.password=test-email-password",
                "--data-service.api-key=test-data-service-api-key"
        ));

        assertTrue(causeChainMentions(exception, "app.frontend-url"),
                "expected the startup failure to be caused by the missing app.frontend-url property, was: " + exception);
    }

    private static boolean causeChainMentions(Throwable throwable, String text) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains(text)) {
                return true;
            }
        }
        return false;
    }
}
