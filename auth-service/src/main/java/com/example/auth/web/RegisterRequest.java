package com.example.auth.web;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;

public record RegisterRequest(
        @NotBlank
        @Size(min = 3, max = 50, message = "must be between 3 and 50 characters")
        @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "may contain only letters, digits, '.', '_' and '-'")
        String username,

        @NotBlank
        @Size(min = 12, max = 72, message = "must be between 12 and 72 characters")
        String password) {

    /** BCrypt silently truncates input beyond 72 bytes, so reject longer passwords instead. */
    @AssertTrue(message = "must be at most 72 bytes when UTF-8 encoded")
    public boolean isPasswordWithinBcryptLimit() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
