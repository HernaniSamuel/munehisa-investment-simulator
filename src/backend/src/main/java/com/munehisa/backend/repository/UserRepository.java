package com.munehisa.backend.repository;

import com.munehisa.backend.domain.user.User;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);

    Optional<User> findByVerificationToken(String verificationToken);

    Optional<User> findByResetPasswordToken(String resetPasswordToken);
}
