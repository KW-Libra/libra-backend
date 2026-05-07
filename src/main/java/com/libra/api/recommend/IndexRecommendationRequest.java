package com.libra.api.recommend;

import java.util.List;

public record IndexRecommendationRequest(
        List<String> universes,
        String risk,
        Double incomePreference,
        List<String> esgExclusions,
        List<String> sectorsExclude
) {
}
