package com.munehisa.backend.service;

import com.munehisa.backend.domain.user.User;
import com.munehisa.backend.dto.*;
import com.munehisa.backend.exceptions.*;
import com.munehisa.backend.infra.security.TokenService;
import com.munehisa.backend.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;


@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final EmailService emailService;

    @Value("${verification.token.expiration}")
    private long verificationTokenExpirationMs;

    @Value("${reset.token.expiration}")
    private long resetPasswordTokenExpirationMs;

    public void register(RegisterRequestDTO body) {

        Optional<User> existingUser = repository.findByEmail(body.email());
        if (existingUser.isPresent()) {
            if (existingUser.get().isVerified()) {
                throw new UserAlreadyExistsException();
            } else {
                throw new EmailPendingVerificationException();
            }
        }

        User user = new User();
        user.setName(body.name());
        user.setEmail(body.email());
        user.setVerified(false);
        user.setPassword(passwordEncoder.encode(body.password()));

        String verificationToken = UUID.randomUUID().toString();
        user.setVerificationToken(verificationToken);
        user.setVerificationTokenExpiry(Instant.now().plusMillis(verificationTokenExpirationMs));
        repository.save(user);

        emailService.sendVerificationEmail(user.getEmail(), user.getVerificationToken());
    }

    public Optional<ResendEmailResponseDTO> resendEmail(ResendEmailRequestDTO body) {
        User user = repository.findByEmail(body.email()).orElseThrow(InvalidCredentialsException::new);

        if (user.isVerified()) {
            throw new UserAlreadyExistsException();
        }
        if (user.getVerificationTokenExpiry().isAfter(Instant.now())) {
            ResendEmailResponseDTO email_sent = new ResendEmailResponseDTO(
                    "A verification email has already been sent. Please wait until the current token expires before requesting a new one.",
                    user.getVerificationTokenExpiry());
            return Optional.of(email_sent);
        }

        String verificationToken = UUID.randomUUID().toString();
        user.setVerificationToken(verificationToken);
        user.setVerificationTokenExpiry(Instant.now().plusMillis(verificationTokenExpirationMs));
        repository.save(user);

        emailService.sendVerificationEmail(user.getEmail(), user.getVerificationToken());

        return Optional.empty();
    }

    public LoginResponseDTO verify(String verificationToken) {
        User user = repository.findByVerificationToken(verificationToken)
                .orElseThrow(VerificationTokenNotFoundException::new);

        if (user.getVerificationTokenExpiry().isBefore(Instant.now())) {
            throw new VerificationTokenExpiredException();
        }

        user.setVerified(true);
        user.setVerificationToken(null);
        user.setVerificationTokenExpiry(null);
        repository.save(user);

        String token = tokenService.generateToken(user);
        return new LoginResponseDTO(user.getName(), token);
    }

    public LoginResponseDTO login(LoginRequestDTO body) {
        User user = repository.findByEmail(body.email()).orElseThrow(InvalidCredentialsException::new);
        if (passwordEncoder.matches(body.password(), user.getPassword())) {
            if (!user.isVerified()) {
                throw new EmailPendingVerificationException();
            } else {
                String token = tokenService.generateToken(user);
                return new LoginResponseDTO(user.getName(), token);
            }
        }
        throw new InvalidCredentialsException();
    }

    public ForgotPasswordResponseDTO forgotPassword(ForgotPasswordRequestDTO body) {
        User user = repository.findByEmail(body.email()).orElseThrow(InvalidCredentialsException::new);

        if (user.getResetPasswordTokenExpiry() == null || user.getResetPasswordTokenExpiry().isBefore(Instant.now())) {
            String resetPasswordToken = UUID.randomUUID().toString();
            user.setResetPasswordToken(resetPasswordToken);
            user.setResetPasswordTokenExpiry(Instant.now().plusMillis(resetPasswordTokenExpirationMs));
            repository.save(user);

            emailService.sendPasswordRecoverEmail(user.getEmail(), user.getResetPasswordToken());

            return new ForgotPasswordResponseDTO("Reset password request accepted. Please look your email.", null);
        } else {
            return new ForgotPasswordResponseDTO(
                    "A password-recover email has already been sent. Please wait until the current token expires before requesting a new one.",
                    user.getResetPasswordTokenExpiry());
        }
    }

    public LoginResponseDTO resetPassword(ResetPasswordRequestDTO body) {
        User user = repository.findByResetPasswordToken(body.resetPasswordToken())
                .orElseThrow(ResetPasswordTokenNotFoundException::new);

        if (user.getResetPasswordTokenExpiry().isBefore(Instant.now())) {
            throw new ResetPasswordTokenExpiredException();
        }

        user.setPassword(passwordEncoder.encode(body.newPassword()));
        user.setResetPasswordToken(null);
        user.setResetPasswordTokenExpiry(null);
        repository.save(user);

        String token = tokenService.generateToken(user);
        return new LoginResponseDTO(user.getName(), token);
    }
}
