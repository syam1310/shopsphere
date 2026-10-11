package com.shopsphere.userservice.service;

import com.shopsphere.userservice.dto.LoginRequest;
import com.shopsphere.userservice.dto.LoginResponse;
import com.shopsphere.userservice.entity.RefreshToken;
import com.shopsphere.userservice.entity.User;
import com.shopsphere.userservice.exception.InvalidCredentialsException;
import com.shopsphere.userservice.repository.UserRepository;
import com.shopsphere.userservice.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokenService) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
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

        String accessToken = jwtService.generateToken(
                user.getEmail(),
                user.getId(),
                user.getRole()
        );

        RefreshToken refreshToken =
                refreshTokenService.createRefreshToken(user);

        return new LoginResponse(
                accessToken,
                refreshToken.getToken(),
                "Bearer"
        );
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