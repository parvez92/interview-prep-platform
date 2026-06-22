package com.interview.prep.platform.backend_core.user;

import com.interview.prep.platform.backend_core.common.error.ApiException;
import com.interview.prep.platform.backend_core.common.error.ErrorCode;
import com.interview.prep.platform.backend_core.common.security.JwtService;
import com.interview.prep.platform.backend_core.user.dto.TokenResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AppUserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private JwtService jwtService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(
                "test-jwt-secret-that-is-long-enough-for-hs256",
                900_000L,
                604_800_000L
        );
        authService = new AuthService(userRepository, passwordEncoder, jwtService);
    }

    @Test
    void login_validCredentials_returnsTokens() {
        AppUser user = userWithId(1L, "user@example.com", "$2a$10$hash");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "$2a$10$hash")).thenReturn(true);

        TokenResponse result = authService.login("user@example.com", "secret");

        assertThat(result.getAccessToken()).isNotBlank();
        assertThat(result.getRefreshToken()).isNotBlank();
        assertThat(result.getExpiresIn()).isEqualTo(900L);
    }

    @Test
    void login_wrongPassword_throwsBadCredentials() {
        AppUser user = userWithId(1L, "user@example.com", "$2a$10$hash");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "$2a$10$hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login("user@example.com", "wrong"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.BAD_CREDENTIALS);
    }

    @Test
    void login_unknownEmail_throwsBadCredentials() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("unknown@example.com", "pass"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.BAD_CREDENTIALS);
    }

    @Test
    void refresh_validRefreshToken_returnsNewAccessToken() {
        String refreshToken = jwtService.mintRefreshToken(42L);

        TokenResponse result = authService.refresh(refreshToken);

        assertThat(result.getAccessToken()).isNotBlank();
        assertThat(result.getRefreshToken()).isEqualTo(refreshToken);
    }

    @Test
    void refresh_accessTokenUsedAsRefresh_throwsTokenInvalid() {
        String accessToken = jwtService.mintAccessToken(42L);

        assertThatThrownBy(() -> authService.refresh(accessToken))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.TOKEN_INVALID);
    }

    @Test
    void refresh_expiredToken_throwsTokenExpired() {
        JwtService shortLived = new JwtService(
                "test-jwt-secret-that-is-long-enough-for-hs256", 900_000L, 1L);
        String expired = shortLived.mintRefreshToken(42L);

        assertThatThrownBy(() -> authService.refresh(expired))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.TOKEN_EXPIRED);
    }

    private AppUser userWithId(Long id, String email, String hash) {
        AppUser u = new AppUser();
        u.setId(id);
        u.setEmail(email);
        u.setPasswordHash(hash);
        return u;
    }
}
