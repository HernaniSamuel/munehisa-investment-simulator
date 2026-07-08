package com.munehisa.backend.service;

import com.munehisa.backend.domain.user.User;
import com.munehisa.backend.dto.DeleteAccountRequestDTO;
import com.munehisa.backend.dto.UpdateNameRequestDTO;
import com.munehisa.backend.dto.UpdateNameResponseDTO;
import com.munehisa.backend.exceptions.InvalidCredentialsException;
import com.munehisa.backend.infra.security.TokenService;
import com.munehisa.backend.repository.UserRepository;
import lombok.AllArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;


@AllArgsConstructor
@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UpdateNameResponseDTO updateName(UpdateNameRequestDTO updateNameRequest, User user) {
        user.setName(updateNameRequest.name());
        userRepository.save(user);
        return new UpdateNameResponseDTO(user.getName());
    }

    public void deleteUserAccount(DeleteAccountRequestDTO deleteAccountRequest, User user) {
        if (passwordEncoder.matches(deleteAccountRequest.password(), user.getPassword())) {
            // TODO: cascade-delete Simulation/Holding records once that domain exists (tracked in a follow-up issue)
            userRepository.delete(user);
        } else {
            throw new InvalidCredentialsException();
        }
    }
}
