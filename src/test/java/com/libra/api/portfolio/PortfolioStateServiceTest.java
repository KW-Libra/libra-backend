package com.libra.api.portfolio;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;
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

    @Test
    void persistsAndRestoresCurrentPortfolioSnapshot() {
        PortfolioSnapshot first = snapshot("2026-04-15T09:00:00+09:00", "005930", "삼성전자", 0.5d);
        PortfolioSnapshot second = snapshot("2026-04-16T09:00:00+09:00", "000660", "SK하이닉스", 0.4d);

        portfolioStateService.save(first, "MANUAL");
        portfolioStateService.save(second, "KIS_DOMESTIC_BALANCE");

        assertThat(portfolioStateService.getCurrent())
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
        assertThat(portfolioSnapshotRepository.findTopByOrderByCreatedAtDesc())
                .isPresent()
                .get()
                .satisfies(entity -> assertThat(entity.getSource()).isEqualTo("KIS_DOMESTIC_BALANCE"));
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
