package com.libra.api.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.libra.api.auth.UserEntity;
import com.libra.api.auth.UserRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class PortfolioDefinitionServiceTest {

    @Autowired
    private PortfolioDefinitionService portfolioDefinitionService;

    @Autowired
    private PortfolioDefinitionRepository portfolioDefinitionRepository;

    @Autowired
    private UserRepository userRepository;

    private String userId;

    @BeforeEach
    void setUp() {
        UserEntity user = userRepository.save(UserEntity.newLocalUser(
                "portfolio-definition-test@example.com",
                "$2a$10$dummy.hash.for.test.purposes.only0000000000000000000",
                "Definition Test"
        ));
        userId = user.id();
    }

    @Test
    void persistsAndRestoresLatestPortfolioDefinition() {
        PortfolioDefinition first = definition("반도체 집중", 0.6d, 0.4d);
        PortfolioDefinition second = definition("대형주 분산", 0.5d, 0.5d);

        portfolioDefinitionService.save(userId, first);
        portfolioDefinitionService.save(userId, second);

        assertThat(portfolioDefinitionService.getCurrent(userId))
                .isPresent()
                .get()
                .satisfies(current -> {
                    assertThat(current.name()).isEqualTo("대형주 분산");
                    assertThat(current.targetWeights()).hasSize(2);
                });
        assertThat(portfolioDefinitionRepository.findAll()).hasSize(2);
    }

    @Test
    void rejectsWeightsThatDoNotSumToOne() {
        PortfolioDefinition badDefinition = definition("bad", 0.7d, 0.2d);

        assertThatThrownBy(() -> portfolioDefinitionService.save(userId, badDefinition))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("target_weights");
    }

    private PortfolioDefinition definition(String name, double samsung, double hynix) {
        return new PortfolioDefinition(
                name,
                "사용자 정의 인덱스",
                List.of(
                        new TargetWeight("005930", "삼성전자", samsung, "KR"),
                        new TargetWeight("000660", "SK하이닉스", hynix, "KR")
                ),
                "위험중립형",
                0.05d,
                "임계치 도달 시",
                false,
                OffsetDateTime.parse("2026-05-07T00:00:00+09:00")
        );
    }
}
