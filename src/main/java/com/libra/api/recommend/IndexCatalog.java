package com.libra.api.recommend;

import java.util.List;

public final class IndexCatalog {

    private IndexCatalog() {
    }

    public static final List<IndexMeta> ALL = List.of(
            new IndexMeta("KOSPI200", "코스피200", List.of("KR"), "blend", "large", "kis_master", "한국 시가총액 상위 200개 대형주 대표 지수입니다."),
            new IndexMeta("KRX300", "KRX 300", List.of("KR"), "blend", "large+mid", "kis_master", "KOSPI와 KOSDAQ을 함께 보는 통합 대표 지수입니다."),
            new IndexMeta("KOSDAQ150", "코스닥150", List.of("KR"), "growth", "mid", "kis_master", "코스닥 성장주 중심의 직접 인덱싱 후보입니다."),
            new IndexMeta("KRX_HIDIV", "KRX 고배당50", List.of("KR"), "value/income", "large", "fdr", "국내 고배당 성향 투자자를 위한 인컴형 지수입니다."),
            new IndexMeta("SPX", "S&P 500", List.of("US"), "blend", "large", "fdr", "미국 대형주 500개에 분산하는 대표 벤치마크입니다."),
            new IndexMeta("NDX", "NASDAQ 100", List.of("US"), "growth", "large", "fdr", "미국 기술 성장주 중심의 나스닥 대표 지수입니다."),
            new IndexMeta("DJIA", "Dow Jones 30", List.of("US"), "value", "large", "static", "미국 우량 가치주 30개 중심의 보수적 후보입니다."),
            new IndexMeta("VIG", "Dividend Achievers", List.of("US"), "income", "large", "static", "장기 배당 증가 기업 중심의 인컴형 후보입니다."),
            new IndexMeta("ACWI", "MSCI ACWI", List.of("GLOBAL"), "blend", "large", "static", "전 세계 선진국과 신흥국에 분산하는 글로벌 후보입니다.")
    );

    public static IndexMeta find(String code) {
        return ALL.stream()
                .filter(item -> item.code().equalsIgnoreCase(code))
                .findFirst()
                .orElse(null);
    }
}
