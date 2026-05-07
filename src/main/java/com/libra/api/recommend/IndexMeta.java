package com.libra.api.recommend;

import java.util.List;

public record IndexMeta(
        String code,
        String name,
        List<String> universe,
        String style,
        String size,
        String dataSource,
        String description
) {
}
