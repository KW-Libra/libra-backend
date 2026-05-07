package com.libra.api.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.libra.api.portfolio.PortfolioHolding;
import com.libra.api.portfolio.PortfolioSnapshot;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ContractSerializationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void serializesPortfolioSnapshotInSnakeCase() throws Exception {
        PortfolioSnapshot snapshot = new PortfolioSnapshot(
                OffsetDateTime.parse("2026-04-15T10:00:00+09:00"),
                List.of(new PortfolioHolding(
                        "005930",
                        "삼성전자",
                        0.7d,
                        List.of("005930.KS"),
                        10d,
                        65000d,
                        60000d,
                        650000d,
                        50000d
                )),
                1000000d,
                0.3d,
                List.of("장기 보유")
        );

        String json = objectMapper.writeValueAsString(snapshot);

        assertThat(json).contains("generated_at");
        assertThat(json).contains("company_name");
        assertThat(json).contains("total_value_krw");
        assertThat(json).contains("cash_weight");
        assertThat(json).contains("user_preferences");
        assertThat(json).contains("average_price");
        assertThat(json).contains("market_value_krw");
        assertThat(json).contains("unrealized_pnl_krw");
        assertThat(json).doesNotContain("generatedAt");
        assertThat(json).doesNotContain("companyName");
        assertThat(json).doesNotContain("averagePrice");
        assertThat(json).doesNotContain("marketValueKrw");
    }
}
