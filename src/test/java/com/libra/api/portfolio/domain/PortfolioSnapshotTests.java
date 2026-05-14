package com.libra.api.portfolio.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.libra.api.broker.kis.api.dto.KisBalanceResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PortfolioSnapshotTests {

    @Test
    void kisBalanceSnapshotCapturesStableSummaryFields() {
        KisBalanceResponse balance = KisBalanceResponse.from(
            "paper",
            List.of(Map.of(
                "pdno", "005930",
                "prdt_name", "Samsung Electronics",
                "hldg_qty", "10",
                "ord_psbl_qty", "8",
                "pchs_avg_pric", "70000.00",
                "prpr", "72000",
                "pchs_amt", "700000",
                "evlu_amt", "720000",
                "evlu_pfls_amt", "20000",
                "evlu_pfls_rt", "2.86"
            )),
            Map.of(
                "dnca_tot_amt", "1000000",
                "scts_evlu_amt", "720000",
                "tot_evlu_amt", "1720000",
                "nass_amt", "1720000",
                "pchs_amt_smtl_amt", "700000",
                "evlu_amt_smtl_amt", "720000",
                "evlu_pfls_smtl_amt", "20000",
                "asst_icdc_erng_rt", "1.18"
            ),
            false,
            "",
            ""
        );

        PortfolioSnapshot snapshot = PortfolioSnapshot.fromKisBalance(
            UUID.randomUUID(),
            balance,
            "{\"environment\":\"paper\"}",
            "trace-1"
        );

        assertThat(snapshot.getProvider()).isEqualTo("KIS");
        assertThat(snapshot.getSource()).isEqualTo("kis_balance");
        assertThat(snapshot.getHoldingsCount()).isEqualTo(1);
        assertThat(snapshot.getTotalValuationAmount()).isEqualByComparingTo(new BigDecimal("1720000"));
        assertThat(snapshot.getProfitLossRate()).isEqualByComparingTo(new BigDecimal("1.18"));
        assertThat(snapshot.getTraceId()).isEqualTo("trace-1");
    }
}
