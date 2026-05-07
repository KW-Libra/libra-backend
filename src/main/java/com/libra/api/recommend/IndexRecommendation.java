package com.libra.api.recommend;

public record IndexRecommendation(
        String code,
        String name,
        String style,
        String size,
        double score,
        String rationale,
        String description,
        boolean fallbackToCustomTilt
) {
}
