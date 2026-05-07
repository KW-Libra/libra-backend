package com.libra.api.portfolio;

import static org.assertj.core.api.Assertions.assertThat;

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
class PortfolioStateServiceTest {

    @Autowired
    private PortfolioStateService portfolioStateService;

    @Autowired
    private PortfolioSnapshotRepository portfolioSnapshotRepository;

    @Autowired
    private UserRepository userRepository;

    private String userId;

    @BeforeEach
    void setUp() {
        UserEntity user = userRepository.save(UserEntity.newLocalUser(
                "portfolio-test@example.com",
                "$2a$10$dummy.hash.for.test.purposes.only0000000000000000000",
                "Portfolio Test"
        ));
        userId = user.id();
    }

    @Test
    void persistsAndRestoresCurrentPortfolioSnapshot() {
        PortfolioSnapshot first = snapshot("2026-04-15T09:00:00+09:00", "005930", "삼성전자", 0.5d);
        PortfolioSnapshot second = snapshot("2026-04-16T09:00:00+09:00", "000660", "SK하이닉스", 0.4d);

        portfolioStateService.save(userId, first, "MANUAL");
        portfolioStateService.save(userId, second, "KIS_DOMESTIC_BALANCE");

        assertThat(portfolioStateService.getCurrent(userId))
                .isPresent()
                .get()
                .satisfies(current -> {
                    assertThat(current.generatedAt()).isEqualTo(second.generatedAt());
                    assertThat(current.holdings()).singleElement().satisfies(holding -> {
                        assertThat(holding.ticker()).isEqualTo("000660");
                        assertThat(holding.companyName()).isEqualTo("SK하이닉스");
                    });
                });
        assertThat(portfolioSnapshotRepository.findAll()).hasSize(2);
        assertThat(portfolioSnapshotRepository.findFirstByUserIdOrderByCreatedAtDesc(userId))
                .isPresent()
                .get()
                .satisfies(entity -> {
                    assertThat(entity.getSource()).isEqualTo("KIS_DOMESTIC_BALANCE");
                    assertThat(entity.getUserId()).isEqualTo(userId);
                });
    }

    @Test
    void portfoliosAreScopedPerUser() {
        UserEntity other = userRepository.save(UserEntity.newLocalUser(
                "other@example.com",
                "$2a$10$dummy.hash.for.test.purposes.only0000000000000000000",
                "Other User"
        ));

        portfolioStateService.save(userId, snapshot("2026-04-15T09:00:00+09:00", "005930", "삼성전자", 0.5d), "MANUAL");
        portfolioStateService.save(other.id(), snapshot("2026-04-16T09:00:00+09:00", "000660", "SK하이닉스", 0.4d), "MANUAL");

        assertThat(portfolioStateService.getCurrent(userId))
                .get()
                .satisfies(p -> assertThat(p.holdings().get(0).ticker()).isEqualTo("005930"));
        assertThat(portfolioStateService.getCurrent(other.id()))
                .get()
                .satisfies(p -> assertThat(p.holdings().get(0).ticker()).isEqualTo("000660"));
    }

    private PortfolioSnapshot snapshot(String generatedAt, String ticker, String companyName, double weight) {
        return new PortfolioSnapshot(
                OffsetDateTime.parse(generatedAt),
                List.of(new PortfolioHolding(
                        ticker,
                        companyName,
                        weight,
                        List.of(ticker + ".KS", "KRX:" + ticker),
                        10d,
                        65000d
                )),
                1000000d,
                1d - weight,
                List.of("국내 대형주 중심")
        );
    }
}
