package com.libra.api.recommend;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Service
public class IndexRecommenderService {

    public List<IndexRecommendation> recommend(IndexRecommendationRequest request) {
        List<String> universes = CollectionUtils.isEmpty(request.universes()) ? List.of("KR", "US") : request.universes();
        String risk = request.risk() == null ? "balanced" : request.risk();
        double incomePreference = request.incomePreference() == null ? 0.5d : request.incomePreference();
        boolean strongCustomFilter = size(request.esgExclusions()) >= 2 || size(request.sectorsExclude()) >= 2;
        Set<String> allowedSizes = allowedSizes(risk);

        return IndexCatalog.ALL.stream()
                .filter(index -> index.universe().stream().anyMatch(universe -> universes.contains(universe) || universes.contains("GLOBAL")))
                .filter(index -> allowedSizes.contains(index.size()))
                .map(index -> score(index, universes, risk, incomePreference, strongCustomFilter))
                .sorted(Comparator.comparingDouble(IndexRecommendation::score).reversed())
                .limit(3)
                .toList();
    }

    public List<HoldingSample> sampleHoldings(String indexCode, double capitalKrw) {
        IndexMeta index = IndexCatalog.find(indexCode);
        if (index == null) {
            return List.of();
        }
        if (capitalKrw < 50_000_000d) {
            return List.of(new HoldingSample(etfCodeFor(indexCode), index.name() + " ETF", 1.0d, "직접 인덱싱 최소 자본 전에는 ETF 폴백을 권장합니다."));
        }
        return List.of(
                new HoldingSample(etfCodeFor(indexCode), index.name() + " Core", 0.7d, "MVP 단계에서는 대표 ETF로 핵심 노출을 구성합니다."),
                new HoldingSample("005930", "삼성전자", 0.15d, "국내 대형주 직접 편입 예시입니다."),
                new HoldingSample("000660", "SK하이닉스", 0.15d, "반도체 비중 보강 예시입니다.")
        );
    }

    private IndexRecommendation score(
            IndexMeta index,
            List<String> universes,
            String risk,
            double incomePreference,
            boolean strongCustomFilter
    ) {
        double score = 0.5d;
        StringBuilder rationale = new StringBuilder();
        if (index.universe().stream().allMatch(universes::contains)) {
            score += 0.15d;
            rationale.append("관심 시장과 정합. ");
        }
        if ("conservative".equals(risk) && "large".equals(index.size())) {
            score += 0.10d;
            rationale.append("보수적 성향에 맞는 대형주 중심. ");
        }
        if ("aggressive".equals(risk) && "growth".equals(index.style())) {
            score += 0.15d;
            rationale.append("공격적 성향과 성장 스타일 매칭. ");
        }
        boolean incomeStyle = "income".equals(index.style()) || "value/income".equals(index.style());
        if (incomePreference >= 0.7d && incomeStyle) {
            score += 0.25d;
            rationale.append("배당 선호를 반영. ");
        } else if (incomePreference <= 0.3d && incomeStyle) {
            score -= 0.10d;
            rationale.append("낮은 배당 선호로 감점. ");
        }
        String finalRationale = rationale.isEmpty() ? "기본 성향 기준으로 적합한 후보입니다." : rationale.toString().trim();
        return new IndexRecommendation(
                index.code(),
                index.name(),
                index.style(),
                index.size(),
                Math.round(Math.max(0.0d, Math.min(1.0d, score)) * 100.0d) / 100.0d,
                finalRationale,
                index.description(),
                strongCustomFilter
        );
    }

    private Set<String> allowedSizes(String risk) {
        return switch (risk) {
            case "conservative" -> Set.of("large");
            case "aggressive" -> Set.of("large", "large+mid", "mid");
            default -> Set.of("large", "large+mid");
        };
    }

    private int size(List<String> values) {
        return values == null ? 0 : values.size();
    }

    private String etfCodeFor(String indexCode) {
        return switch (indexCode) {
            case "KOSPI200" -> "069500";
            case "KRX300" -> "214980";
            case "KOSDAQ150" -> "229200";
            case "KRX_HIDIV" -> "279530";
            case "SPX" -> "360750";
            case "NDX" -> "133690";
            case "DJIA" -> "245340";
            case "VIG" -> "458760";
            case "ACWI" -> "251350";
            default -> indexCode;
        };
    }
}
