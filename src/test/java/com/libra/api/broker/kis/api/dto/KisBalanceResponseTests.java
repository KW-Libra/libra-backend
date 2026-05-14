package com.libra.api.broker.kis.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KisBalanceResponseTests {

    @Test
    void mapsKisBalanceFieldsToStableResponse() {
        KisBalanceResponse response = KisBalanceResponse.from(
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

        assertThat(response.environment()).isEqualTo("paper");
        assertThat(response.holdings()).hasSize(1);
        assertThat(response.holdings().getFirst().symbol()).isEqualTo("005930");
        assertThat(response.holdings().getFirst().quantity()).isEqualByComparingTo(new BigDecimal("10"));
        assertThat(response.holdings().getFirst().profitLossRate()).isEqualByComparingTo(new BigDecimal("2.86"));
        assertThat(response.summary().depositAmount()).isEqualByComparingTo(new BigDecimal("1000000"));
        assertThat(response.summary().profitLossAmount()).isEqualByComparingTo(new BigDecimal("20000"));
    }
}
