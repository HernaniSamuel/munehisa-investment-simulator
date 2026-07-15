package com.munehisa.backend.infra.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import tools.jackson.databind.json.JsonMapper;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RestAuthenticationEntryPointTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final RestAuthenticationEntryPoint entryPoint = new RestAuthenticationEntryPoint(jsonMapper);

    @Test
    void commence_writesUnauthorizedJsonBody() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));

        entryPoint.commence(request, response, new BadCredentialsException("bad creds"));

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(response).setContentType(MediaType.APPLICATION_JSON_VALUE);
        assertTrue(body.toString().contains("Missing or invalid authentication token"));
    }
}
