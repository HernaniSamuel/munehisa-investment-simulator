package com.munehisa.backend.service;

import com.munehisa.backend.domain.user.User;
import com.munehisa.backend.dto.UpdateNameRequestDTO;
import com.munehisa.backend.dto.UpdateNameResponseDTO;
import com.munehisa.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private User buildUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setName("Ada Lovelace");
        user.setEmail("ada@example.com");
        user.setPassword("hashed-password");
        user.setVerified(true);
        return user;
    }

    @Test
    void updateName_success_updatesUserNameAndReturnsIt() {
        User user = buildUser();
        UpdateNameRequestDTO body = new UpdateNameRequestDTO("New name");

        UpdateNameResponseDTO response = userService.updateName(body, user);

        assertEquals("New name", user.getName());
        assertEquals("New name", response.name());
    }
}
