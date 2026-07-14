package com.munehisa.backend.service;

import com.munehisa.backend.domain.user.User;
import com.munehisa.backend.exceptions.AccountLockedException;
import com.munehisa.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;

@RequiredArgsConstructor
@Service
public class AccountLockoutService {
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;

    @Value("${login.lockout.max-attempts}")
    private long loginMaxFailedAttempts;

    @Value("${login.lockout.lock-time}")
    private long accountLockTime;

    public boolean checkPassword(User user, String password) {
        // First, check if the account is locked and if it should be unlocked
        if (user.getLockedUntil() != null) {
            if (user.getLockedUntil().isAfter(Instant.now())) {
                throw new AccountLockedException(user.getLockedUntil());
            }
            if (user.getLockedUntil().isBefore(Instant.now())) {
                user.setLockedUntil(null);
                user.setFailedLoginAttempts(0);
                userRepository.save(user);
            }
        }

        // Just need to check password once per call, it takes too much time to be made more than once
        boolean checkPasswordResult = passwordEncoder.matches(password, user.getPassword());

        // if password is wrong, increment FailedLoginAttempts and check if should lock account
        if (!checkPasswordResult) {
            user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
            userRepository.save(user);

            if (user.getFailedLoginAttempts() >= loginMaxFailedAttempts) {
                user.setLockedUntil(Instant.now().plusMillis(accountLockTime));
                userRepository.save(user);
                throw new AccountLockedException(user.getLockedUntil());
            }

            return false;
        }

        // At this point of the code, the password is already verified and correct
        if (user.getLockedUntil() == null) {
            user.setFailedLoginAttempts(0);
            userRepository.save(user);
            return true;
        }

        // return false by default for safety reasons
        return false;
    }
}
