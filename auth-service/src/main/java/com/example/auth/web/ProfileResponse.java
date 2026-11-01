package com.example.auth.web;

import java.time.Instant;

public record ProfileResponse(String username, String role, Instant createdAt) {
}
