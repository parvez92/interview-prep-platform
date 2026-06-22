package com.interview.prep.platform.backend_core.user;

import com.interview.prep.platform.backend_core.common.error.ApiException;
import com.interview.prep.platform.backend_core.common.error.ErrorCode;
import com.interview.prep.platform.backend_core.common.security.JwtService;
import com.interview.prep.platform.backend_core.user.dto.TokenResponse;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public TokenResponse login(String email, String password) {
        AppUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException(ErrorCode.BAD_CREDENTIALS, HttpStatus.UNAUTHORIZED));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ApiException(ErrorCode.BAD_CREDENTIALS, HttpStatus.UNAUTHORIZED);
        }
        return buildTokens(user.getId());
    }

    public TokenResponse refresh(String refreshToken) {
        try {
            Claims claims = jwtService.parseClaims(refreshToken);
            if (!jwtService.isRefreshToken(claims)) {
                throw new ApiException(ErrorCode.TOKEN_INVALID, HttpStatus.UNAUTHORIZED, "Not a refresh token");
            }
            Long userId = jwtService.extractUserId(claims);
            return TokenResponse.builder()
                    .accessToken(jwtService.mintAccessToken(userId))
                    .refreshToken(refreshToken)
                    .expiresIn(900L)
                    .build();
        } catch (ApiException e) {
            throw e;
        } catch (JwtException e) {
            throw new ApiException(ErrorCode.TOKEN_EXPIRED, HttpStatus.UNAUTHORIZED, "Token expired or invalid");
        }
    }

    private TokenResponse buildTokens(Long userId) {
        return TokenResponse.builder()
                .accessToken(jwtService.mintAccessToken(userId))
                .refreshToken(jwtService.mintRefreshToken(userId))
                .expiresIn(900L)
                .build();
    }
}
