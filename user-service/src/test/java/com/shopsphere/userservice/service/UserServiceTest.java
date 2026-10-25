package com.shopsphere.userservice.service;

import com.shopsphere.userservice.dto.RegisterUserRequest;
import com.shopsphere.userservice.dto.UserResponse;
import com.shopsphere.userservice.entity.Role;
import com.shopsphere.userservice.entity.User;
import com.shopsphere.userservice.exception.EmailAlreadyRegisteredException;
import com.shopsphere.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private RegisterUserRequest request;

    @BeforeEach
    void setUp() {
        request = new RegisterUserRequest(
                "Test User",
                "TEST@example.com",
                "Password123"
        );
    }

    @Test
    void registerUser_shouldSaveUserAndReturnResponse() {
        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.empty());
        when(passwordEncoder.encode("Password123"))
                .thenReturn("hashed-password");

        User savedUser = User.builder()
                .id(101L)
                .name("Test User")
                .email("test@example.com")
                .password("hashed-password")
                .role(Role.USER)
                .build();

        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        UserResponse response = userService.registerUser(request);

        assertNotNull(response);
        assertEquals(101L, response.getId());
        assertEquals("Test User", response.getName());
        assertEquals("test@example.com", response.getEmail());

        verify(userRepository).findByEmail("test@example.com");
        verify(passwordEncoder).encode("Password123");
        verify(userRepository).save(argThat(user ->
                "test@example.com".equals(user.getEmail())
                        && "hashed-password".equals(user.getPassword())
                        && user.getRole() == Role.USER
        ));
    }

    @Test
    void registerUser_shouldRejectDuplicateEmail() {
        User existingUser = User.builder()
                .id(10L)
                .name("Existing User")
                .email("test@example.com")
                .password("existing-hash")
                .role(Role.USER)
                .build();

        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(existingUser));

        assertThrows(
                EmailAlreadyRegisteredException.class,
                () -> userService.registerUser(request)
        );

        verify(userRepository, never()).save(any(User.class));
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void registerUser_shouldNormalizeEmail() {
        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.empty());
        when(passwordEncoder.encode("Password123"))
                .thenReturn("hashed-password");

        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(102L);
            return user;
        });

        userService.registerUser(request);

        verify(userRepository).findByEmail("test@example.com");
        verify(userRepository).save(argThat(user ->
                "test@example.com".equals(user.getEmail())
        ));
    }
}
