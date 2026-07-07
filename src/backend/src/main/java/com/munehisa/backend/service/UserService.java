package com.munehisa.backend.service;

import com.munehisa.backend.domain.user.User;
import com.munehisa.backend.dto.UpdateNameRequestDTO;
import com.munehisa.backend.dto.UpdateNameResponseDTO;
import com.munehisa.backend.repository.UserRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;


@AllArgsConstructor
@Service
public class UserService {
    private final UserRepository userRepository;

    public UpdateNameResponseDTO updateName(UpdateNameRequestDTO updateNameRequest, User user) {
        user.setName(updateNameRequest.name());
        userRepository.save(user);
        return new UpdateNameResponseDTO(user.getName());
    }
}
