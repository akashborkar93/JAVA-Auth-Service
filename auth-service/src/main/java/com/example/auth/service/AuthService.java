package com.example.auth.service;

import com.example.auth.domain.User;
import com.example.auth.domain.UserRepository;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    static final String ROLE_USER = "USER";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        // Used to keep login timing similar whether or not the username exists.
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public User register(String username, String rawPassword) {
        String normalized = normalize(username);
        if (users.existsByUsername(normalized)) {
            throw new UsernameAlreadyExistsException();
        }
        try {
            return users.save(new User(normalized, passwordEncoder.encode(rawPassword), ROLE_USER));
        } catch (DataIntegrityViolationException ex) {
            // Lost a race with a concurrent registration of the same username.
            throw new UsernameAlreadyExistsException();
        }
    }

    /** Verifies credentials and returns the user, or throws {@link InvalidCredentialsException}. */
    @Transactional(readOnly = true)
    public User authenticate(String username, String rawPassword) {
        User user = users.findByUsername(normalize(username)).orElse(null);
        String hashToCheck = user != null ? user.getPasswordHash() : dummyHash;
        boolean passwordMatches = passwordEncoder.matches(rawPassword, hashToCheck);

        if (user == null || !passwordMatches) {
            throw new InvalidCredentialsException();
        }
        return user;
    }

    @Transactional(readOnly = true)
    public User getByUsername(String username) {
        return users.findByUsername(normalize(username)).orElseThrow(InvalidCredentialsException::new);
    }

    private static String normalize(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
