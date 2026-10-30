package com.shopsphere.userservice.security;

public record GoogleIdentity(String subject, String email, String name, boolean emailVerified) {
}
