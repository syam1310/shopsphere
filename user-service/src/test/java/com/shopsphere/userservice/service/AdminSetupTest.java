package com.shopsphere.userservice.service;

import com.shopsphere.userservice.dto.AdminSetupRequest;
import com.shopsphere.userservice.entity.Role;
import com.shopsphere.userservice.entity.User;
import com.shopsphere.userservice.exception.InvalidCredentialsException;
import com.shopsphere.userservice.repository.UserRepository;
import com.shopsphere.userservice.security.GoogleIdentityTokenVerifier;
import com.shopsphere.userservice.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminSetupTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private RefreshTokenService refreshTokenService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtService = mock(JwtService.class);
        refreshTokenService = mock(RefreshTokenService.class);
        GoogleIdentityTokenVerifier googleVerifier = mock(GoogleIdentityTokenVerifier.class);
        authService = new AuthService(
                userRepository,
                passwordEncoder,
                jwtService,
                refreshTokenService,
                googleVerifier,
                "a".repeat(64));
    }

    @Test
    void setupAvailableOnlyWhenCodeIsConfiguredAndNoAdminExists() {
        when(userRepository.countByRole(Role.ADMIN)).thenReturn(0L);
        assertTrue(authService.isInitialAdminSetupAvailable());

        when(userRepository.countByRole(Role.ADMIN)).thenReturn(1L);
        assertFalse(authService.isInitialAdminSetupAvailable());
    }

    @Test
    void validSetupCreatesAdminAndReturnsSession() {
        when(userRepository.countByRole(Role.ADMIN)).thenReturn(0L);
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("LongPassword123"))
                .thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(7L);
            return user;
        });
        when(jwtService.generateToken("admin@example.com", 7L, Role.ADMIN))
                .thenReturn("access-token");
        when(refreshTokenService.createRefreshToken(any(User.class)))
                .thenReturn(com.shopsphere.userservice.entity.RefreshToken.builder()
                        .token("refresh-token").build());

        var response = authService.setupInitialAdmin(new AdminSetupRequest(
                "a".repeat(64), "Store Admin", "ADMIN@example.com", "LongPassword123"));

        assertEquals("access-token", response.getAccessToken());
        assertEquals("refresh-token", response.getRefreshToken());
        verify(userRepository).save(argThat(user ->
                user.getRole() == Role.ADMIN
                        && "admin@example.com".equals(user.getEmail())));
    }

    @Test
    void invalidCodeDoesNotCreateAnAdmin() {
        when(userRepository.countByRole(Role.ADMIN)).thenReturn(0L);

        assertThrows(InvalidCredentialsException.class,
                () -> authService.setupInitialAdmin(new AdminSetupRequest(
                        "b".repeat(64), "Store Admin", "admin@example.com", "LongPassword123")));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void secondAdminBootstrapIsRejected() {
        when(userRepository.countByRole(Role.ADMIN)).thenReturn(1L);

        assertThrows(ResponseStatusException.class,
                () -> authService.setupInitialAdmin(new AdminSetupRequest(
                        "a".repeat(64), "Store Admin", "admin@example.com", "LongPassword123")));

        verify(userRepository, never()).save(any(User.class));
    }
}
