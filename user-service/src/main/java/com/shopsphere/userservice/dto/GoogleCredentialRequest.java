package com.shopsphere.userservice.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleCredentialRequest(@NotBlank String credential) {
}
