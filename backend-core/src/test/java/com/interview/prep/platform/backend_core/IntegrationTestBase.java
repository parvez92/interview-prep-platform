package com.interview.prep.platform.backend_core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.prep.platform.backend_core.user.AppUser;
import com.interview.prep.platform.backend_core.user.AppUserRepository;
import com.interview.prep.platform.backend_core.user.UserSettings;
import com.interview.prep.platform.backend_core.user.UserSettingsRepository;
import com.interview.prep.platform.backend_core.user.dto.LoginRequest;
import com.interview.prep.platform.backend_core.user.dto.TokenResponse;
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

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Common setup for all Spring Boot ITs.
 * Provides a live Postgres (Testcontainers) and a seeded test user ready to log in.
 */
@SpringBootTest(properties = "spring.config.import=")
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    protected static final String TEST_EMAIL    = "it-user@preploop.test";
    protected static final String TEST_PASSWORD = "TestPass123!";

    @Autowired protected WebApplicationContext context;
    @Autowired protected ObjectMapper          objectMapper;
    @Autowired protected AppUserRepository     userRepository;
    @Autowired protected UserSettingsRepository settingsRepository;
    @Autowired protected PasswordEncoder       passwordEncoder;

    protected MockMvc mockMvc;
    protected Long    testUserId;

    /** Call in @BeforeEach — recreates mockMvc and a fresh test user. */
    protected void baseSetUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        seedUser();
    }

    private void seedUser() {
        settingsRepository.deleteAll();
        userRepository.deleteAll();

        AppUser user = new AppUser();
        user.setEmail(TEST_EMAIL);
        user.setPasswordHash(passwordEncoder.encode(TEST_PASSWORD));
        user.setDisplayName("IT Test User");
        AppUser saved = userRepository.save(user);
        testUserId = saved.getId();

        UserSettings settings = new UserSettings();
        settings.setUserId(saved.getId());
        settingsRepository.save(settings);
    }

    /** Logs in and returns the access token. */
    protected String login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(TEST_EMAIL, TEST_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), TokenResponse.class)
                .getAccessToken();
    }

    protected String bearer() throws Exception {
        return "Bearer " + login();
    }
}
