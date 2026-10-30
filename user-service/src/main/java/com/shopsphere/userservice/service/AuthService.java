package com.shopsphere.userservice.service;

import com.shopsphere.userservice.dto.LoginRequest;
import com.shopsphere.userservice.dto.LoginResponse;
import com.shopsphere.userservice.entity.RefreshToken;
import com.shopsphere.userservice.entity.User;
import com.shopsphere.userservice.exception.InvalidCredentialsException;
import com.shopsphere.userservice.repository.UserRepository;
import com.shopsphere.userservice.security.JwtService;
import com.shopsphere.userservice.security.GoogleIdentity;
import com.shopsphere.userservice.security.GoogleIdentityTokenVerifier;
import com.shopsphere.userservice.entity.Role;
import com.shopsphere.userservice.dto.AdminSetupRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final GoogleIdentityTokenVerifier googleIdentityTokenVerifier;
    private final String adminSetupCode;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            GoogleIdentityTokenVerifier googleIdentityTokenVerifier,
            @Value("${app.admin-setup-code:}") String adminSetupCode) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.googleIdentityTokenVerifier = googleIdentityTokenVerifier;
        this.adminSetupCode = adminSetupCode == null ? "" : adminSetupCode.trim();
    }

    @Transactional(readOnly = true)
    public boolean isInitialAdminSetupAvailable() {
        return !adminSetupCode.isBlank()
                && userRepository.countByRole(Role.ADMIN) == 0;
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public LoginResponse setupInitialAdmin(AdminSetupRequest request) {
        if (adminSetupCode.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Initial admin setup is disabled");
        }

        byte[] expected = adminSetupCode.getBytes(StandardCharsets.UTF_8);
        byte[] supplied = request.setupCode().getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, supplied)) {
            throw new InvalidCredentialsException("Invalid admin setup code");
        }

        if (userRepository.countByRole(Role.ADMIN) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "An admin account already exists");
        }

        String email = request.email().trim().toLowerCase(java.util.Locale.ROOT);
        User user = userRepository.findByEmail(email).orElseGet(() -> User.builder()
                .name(request.name().trim())
                .email(email)
                .password(passwordEncoder.encode(request.password()))
                .role(Role.ADMIN)
                .build());

        if (user.getId() != null
                && user.getGoogleSubject() == null
                && !passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new InvalidCredentialsException(
                    "Enter the current password for this ShopSphere account");
        }

        user.setRole(Role.ADMIN);
        if (user.getName() == null || user.getName().isBlank()) {
            user.setName(request.name().trim());
        }

        return createSession(userRepository.save(user));
    }

    public LoginResponse login(LoginRequest request) {

        User user = userRepository
                .findByEmail(request.getEmail())
                .orElseThrow(() ->
                        new InvalidCredentialsException(
                                "Invalid email or password"
                        )
                );

        boolean passwordMatches =
                passwordEncoder.matches(
                        request.getPassword(),
                        user.getPassword()
                );

        if (!passwordMatches) {
            throw new InvalidCredentialsException(
                    "Invalid email or password"
            );
        }

        return createSession(user);
    }

    public LoginResponse loginWithGoogle(String credential) {
        GoogleIdentity identity = googleIdentityTokenVerifier.verify(credential);
        String email = identity.email().trim().toLowerCase();
        User user = userRepository.findByGoogleSubject(identity.subject()).orElseGet(() -> {
            User matchingEmail = userRepository.findByEmail(email).orElse(null);
            if (matchingEmail != null) {
                if (matchingEmail.getGoogleSubject() != null) {
                    throw new InvalidCredentialsException("This email is linked to a different Google account");
                }
                matchingEmail.setGoogleSubject(identity.subject());
                return userRepository.save(matchingEmail);
            }
            return userRepository.save(User.builder()
                    .name(identity.name() == null || identity.name().isBlank() ? email : identity.name().trim())
                    .email(email)
                    .password(passwordEncoder.encode(java.util.UUID.randomUUID().toString()))
                    .googleSubject(identity.subject())
                    .role(Role.USER)
                    .build());
        });
        return createSession(user);
    }

    private LoginResponse createSession(User user) {
        String accessToken = jwtService.generateToken(user.getEmail(), user.getId(), user.getRole());
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);
        return new LoginResponse(accessToken, refreshToken.getToken(), "Bearer");
    }

    public LoginResponse refreshAccessToken(String token) {

        RefreshToken oldRefreshToken =
                refreshTokenService.findByToken(token);

        if (!refreshTokenService.isValid(oldRefreshToken)) {
            throw new IllegalArgumentException(
                    "Refresh token is expired or revoked"
            );
        }

        User user = oldRefreshToken.getUser();
        // Generate new access token
        String accessToken = jwtService.generateToken(
                user.getEmail(),
                user.getId(),
                user.getRole()
        );

        // Revoke old refresh token and create a new one
        RefreshToken newRefreshToken =
                refreshTokenService.rotateToken(oldRefreshToken);


        return new LoginResponse(
                accessToken,
                newRefreshToken.getToken(),
                "Bearer"
        );
    }

    public void logout(String refreshToken) {

        refreshTokenService.revokeToken(refreshToken);
    }
}
