package com.shopsphere.userservice.service;

import com.shopsphere.userservice.entity.RefreshToken;
import com.shopsphere.userservice.entity.User;
import com.shopsphere.userservice.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class RefreshTokenService {
    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${app.refresh-token.expiration}")
    private long refreshTokenExpiration;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    public RefreshToken createRefreshToken(User user) {

        RefreshToken refreshToken = RefreshToken.builder()
                .token(UUID.randomUUID().toString())
                .expiryDate(Instant.now().plusMillis(refreshTokenExpiration))
                .revoked(false)
                .user(user)
                .build();
        return refreshTokenRepository.save(refreshToken);
    }

    public RefreshToken findByToken(String token) {
        return refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Refresh token not found"));
    }

    public boolean isValid(RefreshToken refreshToken) {
        if (refreshToken.isRevoked()) {
            return false;
        }
        return refreshToken.getExpiryDate().isAfter(Instant.now());
    }

    @Transactional
    public void refreshToken(String token) {
        RefreshToken refreshToken = findByToken(token);
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);
    }

    @Transactional
    public RefreshToken rotateToken(RefreshToken oldToken) {

        // Revoke the old refresh token
        oldToken.setRevoked(true);
        refreshTokenRepository.save(oldToken);

        // Create a new refresh token for the same user
        return createRefreshToken(oldToken.getUser());
    }

    @Transactional
    public void revokeToken(String token) {

        RefreshToken refreshToken = findByToken(token);

        refreshToken.setRevoked(true);

        refreshTokenRepository.save(refreshToken);
    }
}
