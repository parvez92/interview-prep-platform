package com.interview.prep.platform.backend_core.user;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty("app.seed.email")
@RequiredArgsConstructor
public class UserSeeder implements ApplicationRunner {

    private final AppUserRepository userRepository;
    private final UserSettingsRepository settingsRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.email:}")
    private String email;

    @Value("${app.seed.password:}")
    private String password;

    @Value("${app.seed.display-name:User}")
    private String displayName;

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.findByEmail(email).isPresent()) {
            return;
        }
        AppUser user = new AppUser();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setDisplayName(displayName);
        AppUser saved = userRepository.save(user);

        UserSettings settings = new UserSettings();
        settings.setUserId(saved.getId());
        settingsRepository.save(settings);
    }
}
