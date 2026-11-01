package com.example.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auth.domain.User;
import com.example.auth.domain.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String PASSWORD = "correct-horse-battery";

    @Mock
    private UserRepository users;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(users, encoder);
    }

    @Test
    void registerStoresNormalizedUsernameWithHashedPassword() {
        when(users.existsByUsername("alice")).thenReturn(false);
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.register("  Alice ", PASSWORD);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(users).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("alice");
        assertThat(saved.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(encoder.matches(PASSWORD, saved.getPasswordHash())).isTrue();
        assertThat(saved.getRole()).isEqualTo("USER");
    }

    @Test
    void registerRejectsDuplicateUsername() {
        when(users.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> service.register("alice", PASSWORD))
                .isInstanceOf(UsernameAlreadyExistsException.class);
        verify(users, never()).save(any());
    }

    @Test
    void authenticateReturnsUserForValidCredentials() {
        User user = new User("alice", encoder.encode(PASSWORD), "USER");
        when(users.findByUsername("alice")).thenReturn(Optional.of(user));

        assertThat(service.authenticate("ALICE", PASSWORD)).isSameAs(user);
    }

    @Test
    void authenticateRejectsWrongPassword() {
        User user = new User("alice", encoder.encode(PASSWORD), "USER");
        when(users.findByUsername("alice")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.authenticate("alice", "wrong-password-value"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void authenticateRejectsUnknownUserWithSameMessageAsWrongPassword() {
        when(users.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.authenticate("ghost", PASSWORD))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid username or password");
    }
}
