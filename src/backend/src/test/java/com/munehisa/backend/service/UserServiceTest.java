package com.munehisa.backend.service;

import com.munehisa.backend.domain.user.User;
import com.munehisa.backend.dto.DeleteAccountRequestDTO;
import com.munehisa.backend.dto.UpdateNameRequestDTO;
import com.munehisa.backend.dto.UpdateNameResponseDTO;
import com.munehisa.backend.exceptions.InvalidCredentialsException;
import com.munehisa.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.UUID;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Mock
    private PasswordEncoder passwordEncoder;

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

    @Test
    void deleteUserAccount_correctPassword_deletesUser() {
        User user = new User();
        user.setPassword("hashed-password");
        DeleteAccountRequestDTO body = new DeleteAccountRequestDTO("correct-password");
        when(passwordEncoder.matches("correct-password", "hashed-password")).thenReturn(true);

        userService.deleteUserAccount(body, user);

        verify(userRepository).delete(user);
    }

    @Test
    void deleteUserAccount_wrongPassword_throwsInvalidCredentialsAndDoesNotDelete() {
        User user = new User();
        user.setPassword("hashed-password");
        DeleteAccountRequestDTO body = new DeleteAccountRequestDTO("wrong-password");
        when(passwordEncoder.matches("wrong-password", "hashed-password")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> userService.deleteUserAccount(body, user));
        verify(userRepository, never()).delete(any());
    }
}
