package com.libra.api.config;

import com.libra.api.auth.domain.User;
import com.libra.api.auth.domain.UserRepository;
import com.libra.api.auth.service.PasswordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 시연용 demo user 시드. local/dev/demo profile 에서만 활성.
 * V2 SQL 로 박지 않는 이유: bcrypt 해시가 매번 다른데 SQL 에 박으면 비번 변경 불가.
 */
@Configuration
public class DemoDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final String DEMO_EMAIL = "demo@libra.local";
    private static final String DEMO_PASSWORD = "demo1234";

    @Bean
    @Profile({"local", "dev", "demo"})
    public ApplicationRunner seedDemoUser(UserRepository users, PasswordService passwords) {
        return args -> {
            if (users.existsByEmail(DEMO_EMAIL)) {
                log.info("demo user '{}' already exists — skip seed", DEMO_EMAIL);
                return;
            }
            User user = new User(DEMO_EMAIL, passwords.hash(DEMO_PASSWORD), "Demo User");
            users.save(user);
            log.info("seeded demo user: {} / {}", DEMO_EMAIL, DEMO_PASSWORD);
        };
    }
}
