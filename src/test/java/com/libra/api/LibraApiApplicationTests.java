package com.libra.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class LibraApiApplicationTests {

    @Test
    void contextLoads() {
        // 부트 검증: 모든 bean 이 생성 가능한지.
        // 실제 통합 테스트는 별도 클래스에서 Testcontainers + Postgres 로.
    }
}
