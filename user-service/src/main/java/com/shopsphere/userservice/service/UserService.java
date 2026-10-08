package com.shopsphere.userservice.service;

import com.shopsphere.userservice.dto.RegisterUserRequest;
import com.shopsphere.userservice.dto.UserResponse;
import com.shopsphere.userservice.entity.Role;
import com.shopsphere.userservice.entity.User;
import com.shopsphere.userservice.exception.EmailAlreadyRegisteredException;
import com.shopsphere.userservice.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private static final Logger log =
            LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public UserResponse registerUser(RegisterUserRequest request) {

        String normalizedEmail = request.getEmail().trim().toLowerCase();
        Optional<User> existingUser = userRepository.findByEmail(normalizedEmail);

        if (existingUser.isPresent()) {
            log.warn("Registration attempt with already registered email: {}",
                    normalizedEmail);
            throw new EmailAlreadyRegisteredException(
                    "Email is already registered"
            );
        }


        User user = User.builder()
                .name(request.getName())
                .email(normalizedEmail)
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.USER).build();

        User savedUser = userRepository.save(user);
        log.info("User registered successfully with id: {} and email: {}",
                savedUser.getId(),
                savedUser.getEmail());

        return new UserResponse(savedUser.getId(), savedUser.getName(), savedUser.getEmail());
    }

    public UserResponse getCurrentUser(String email) {

        log.info("Fetching current user with email: {}", email);
        User user = userRepository
                .findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("User not found with email: {}", email);

                    return new IllegalArgumentException(
                            "User not found"
                    );
                });

        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail()
        );
    }

    public List<UserResponse> getAllUsers() {

        log.info("Fetching all users");
        return userRepository.findAll()
                .stream()
                .map(user -> new UserResponse(
                        user.getId(),
                        user.getName(),
                        user.getEmail()
                ))
                .toList();
    }


}