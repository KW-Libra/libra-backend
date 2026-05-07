package com.libra.api.integration.kis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class KisStockMasterServiceTest {

    private final KisStockMasterService service = new KisStockMasterService();

    @Test
    void parsesKospiMasterLine() {
        String head = "005930   KR7005930003삼성전자                      ";
        String line = head + " ".repeat(228);

        KisStockListing listing = service.parseMasterLine(line, "KOSPI", 228, Instant.parse("2026-05-07T00:00:00Z"));

        assertThat(listing).isNotNull();
        assertThat(listing.ticker()).isEqualTo("005930");
        assertThat(listing.standardCode()).isEqualTo("KR7005930003");
        assertThat(listing.companyName()).isEqualTo("삼성전자");
        assertThat(listing.market()).isEqualTo("KOSPI");
    }

    @Test
    void parsesKosdaqMasterLine() {
        String head = "035420   KR7035420009NAVER                       ";
        String line = head + " ".repeat(222);

        KisStockListing listing = service.parseMasterLine(line, "KOSDAQ", 222, Instant.parse("2026-05-07T00:00:00Z"));

        assertThat(listing).isNotNull();
        assertThat(listing.ticker()).isEqualTo("035420");
        assertThat(listing.standardCode()).isEqualTo("KR7035420009");
        assertThat(listing.companyName()).isEqualTo("NAVER");
        assertThat(listing.market()).isEqualTo("KOSDAQ");
    }

    @Test
    void skipsNonSixDigitProducts() {
        String head = "F70100026KR5701000261한투글로벌넥스트웨이브1(A)       ";
        String line = head + " ".repeat(228);

        KisStockListing listing = service.parseMasterLine(line, "KOSPI", 228, Instant.parse("2026-05-07T00:00:00Z"));

        assertThat(listing).isNull();
    }
}
