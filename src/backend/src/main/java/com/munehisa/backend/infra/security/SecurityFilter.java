package com.munehisa.backend.infra.security;

import com.munehisa.backend.domain.user.User;
import com.munehisa.backend.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

@Component
@AllArgsConstructor
public class SecurityFilter extends OncePerRequestFilter {
    private final TokenService tokenService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws ServletException, IOException {
        var token = this.recoverToken(request);
        var login = tokenService.validateToken(token);

        if (login.isPresent()) {
            Optional<User> userOpt = userRepository.findByEmail(login.get());
            if (userOpt.isEmpty()) {
                filterChain.doFilter(request, response);
                return;
            }
            User user = userOpt.get();
            var authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));
            var authentication = new UsernamePasswordAuthenticationToken(user, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        filterChain.doFilter(request, response);
    }

    private String recoverToken(HttpServletRequest request) {
        var authHeader = request.getHeader("Authorization");
        if (authHeader == null) return null;
        if (!authHeader.startsWith("Bearer ")) return null;
        return authHeader.substring(7);
    }
}