package com.munehisa.backend.infra;

import com.munehisa.backend.controllers.IntegrationTestBase;
import com.munehisa.backend.domain.user.User;
import com.munehisa.backend.dto.CreateSimulationRequestDTO;
import com.munehisa.backend.dto.LoginRequestDTO;
import com.munehisa.backend.infra.security.TokenService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.YearMonth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage of issue #150's acceptance criteria: RestErrorMessage.message (business-
 * logic exceptions) and the individual @Valid constraint messages (built-in and custom message=)
 * must be resolved in the request's Accept-Language locale, defaulting to English when the
 * header is missing or names an unsupported locale.
 */
@Tag("integration")
class LocaleResolutionIntegrationTest extends IntegrationTestBase {

    @Autowired
    private TokenService tokenService;

    // --- business-logic exception (invalid credentials -> error.invalidCredentials) ---------

    @Test
    void wrongPasswordLogin_withAcceptLanguagePtBr_returnsPortugueseMessage() throws Exception {
        createUser(user -> user.setVerified(true));
        LoginRequestDTO body = new LoginRequestDTO("ada@example.com", "wrong-password");

        mockMvc.perform(post("/auth/login")
                        .header("Accept-Language", "pt-BR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Credenciais inválidas"));
    }

    @Test
    void wrongPasswordLogin_withAcceptLanguageEn_returnsEnglishMessage() throws Exception {
        createUser(user -> user.setVerified(true));
        LoginRequestDTO body = new LoginRequestDTO("ada@example.com", "wrong-password");

        mockMvc.perform(post("/auth/login")
                        .header("Accept-Language", "en")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid Credentials"));
    }

    @Test
    void wrongPasswordLogin_withNoAcceptLanguageHeader_returnsEnglishMessage() throws Exception {
        createUser(user -> user.setVerified(true));
        LoginRequestDTO body = new LoginRequestDTO("ada@example.com", "wrong-password");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid Credentials"));
    }

    @Test
    void wrongPasswordLogin_withUnsupportedAcceptLanguage_returnsEnglishMessage() throws Exception {
        createUser(user -> user.setVerified(true));
        LoginRequestDTO body = new LoginRequestDTO("ada@example.com", "wrong-password");

        mockMvc.perform(post("/auth/login")
                        .header("Accept-Language", "fr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid Credentials"));
    }

    // --- built-in constraint (@NotBlank on CreateSimulationRequestDTO.name) -----------------

    @Test
    void createSimulation_blankName_withAcceptLanguagePtBr_returnsLocalizedNotBlankMessage() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        CreateSimulationRequestDTO body = new CreateSimulationRequestDTO(null, "BRL", YearMonth.now());

        mockMvc.perform(post("/simulations")
                        .header("Authorization", "Bearer " + token)
                        .header("Accept-Language", "pt-BR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("name: não pode estar em branco"));
    }

    @Test
    void createSimulation_blankName_withAcceptLanguageEn_returnsEnglishNotBlankMessage() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        CreateSimulationRequestDTO body = new CreateSimulationRequestDTO(null, "BRL", YearMonth.now());

        mockMvc.perform(post("/simulations")
                        .header("Authorization", "Bearer " + token)
                        .header("Accept-Language", "en")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("name: must not be blank"));
    }

    // --- custom message= constraint (@Pattern on CreateSimulationRequestDTO.baseCurrency) ---

    @Test
    void createSimulation_invalidBaseCurrency_withAcceptLanguagePtBr_returnsLocalizedCustomMessage() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        CreateSimulationRequestDTO body = new CreateSimulationRequestDTO("Retirement plan", "EUR", YearMonth.now());

        mockMvc.perform(post("/simulations")
                        .header("Authorization", "Bearer " + token)
                        .header("Accept-Language", "pt-BR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("baseCurrency: deve ser BRL ou USD"));
    }

    @Test
    void createSimulation_invalidBaseCurrency_withAcceptLanguageEn_returnsEnglishCustomMessage() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        CreateSimulationRequestDTO body = new CreateSimulationRequestDTO("Retirement plan", "EUR", YearMonth.now());

        mockMvc.perform(post("/simulations")
                        .header("Authorization", "Bearer " + token)
                        .header("Accept-Language", "en")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("baseCurrency: must be BRL or USD"));
    }
}
