package com.shopsphere.userservice.repository;

import com.shopsphere.userservice.entity.User;
import com.shopsphere.userservice.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByGoogleSubject(String googleSubject);
    long countByRole(Role role);
}
