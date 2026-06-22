package com.interview.prep.platform.backend_core.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.prep.platform.backend_core.TestcontainersConfiguration;
import com.interview.prep.platform.backend_core.user.dto.LoginRequest;
import com.interview.prep.platform.backend_core.user.dto.RefreshRequest;
import com.interview.prep.platform.backend_core.user.dto.TokenResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.config.import=")
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class AuthControllerIT {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private UserSettingsRepository settingsRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        settingsRepository.deleteAll();
        userRepository.deleteAll();

        AppUser user = new AppUser();
        user.setEmail("it@example.com");
        user.setPasswordHash(passwordEncoder.encode("pass1234"));
        user.setDisplayName("IT User");
        AppUser saved = userRepository.save(user);

        UserSettings settings = new UserSettings();
        settings.setUserId(saved.getId());
        settingsRepository.save(settings);
    }

    @Test
    void login_validCredentials_returns200WithTokens() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("it@example.com", "pass1234"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    void login_badPassword_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("it@example.com", "wrong"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_unknownEmail_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("nobody@example.com", "pass1234"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMe_withValidToken_returns200WithEmail() throws Exception {
        String token = login();

        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("it@example.com"))
                .andExpect(jsonPath("$.onboarded").value(false));
    }

    @Test
    void getMe_withNoToken_returns401() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_validRefreshToken_returnsNewAccessToken() throws Exception {
        TokenResponse tokens = loginFull();

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(tokens.getRefreshToken()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").value(tokens.getRefreshToken()));
    }

    @Test
    void refresh_accessTokenAsRefresh_returns401() throws Exception {
        String accessToken = login();

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(accessToken))))
                .andExpect(status().isUnauthorized());
    }

    private String login() throws Exception {
        return loginFull().getAccessToken();
    }

    private TokenResponse loginFull() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("it@example.com", "pass1234"))))
                .andExpect(status().isOk())
                .andReturn();
        TokenResponse tr = objectMapper.readValue(result.getResponse().getContentAsString(), TokenResponse.class);
        assertThat(tr).isNotNull();
        return tr;
    }
}
