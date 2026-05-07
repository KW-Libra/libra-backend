package com.libra.api.integration.kis;

import com.libra.api.portfolio.PortfolioHolding;
import com.libra.api.portfolio.PortfolioSnapshot;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class KisPortfolioMapper {

    public PortfolioSnapshot toSnapshot(
            List<KisBalanceHoldingRow> holdingRows,
            List<KisBalanceSummaryRow> summaryRows,
            List<String> userPreferences
    ) {
        List<KisBalanceHoldingRow> safeHoldingRows = holdingRows == null ? List.of() : holdingRows;
        List<KisBalanceSummaryRow> safeSummaryRows = summaryRows == null ? List.of() : summaryRows;
        KisBalanceSummaryRow summary = safeSummaryRows.isEmpty() ? null : safeSummaryRows.get(0);

        double cashValue = parseAmount(summary == null ? null : summary.dncaTotAmt());
        double stockValue = parseAmount(summary == null ? null : summary.evluAmtSmtlAmt());
        if (stockValue <= 0d) {
            stockValue = safeHoldingRows.stream()
                    .mapToDouble(this::resolveMarketValue)
                    .sum();
        }
        double totalValue = parseAmount(summary == null ? null : summary.totEvluAmt());
        if (totalValue <= 0d) {
            totalValue = stockValue + cashValue;
        }
        double resolvedTotalValue = totalValue;

        List<PortfolioHolding> holdings = safeHoldingRows.stream()
                .map(row -> toHolding(row, resolvedTotalValue))
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparingDouble(PortfolioHolding::weight).reversed()
                        .thenComparing(PortfolioHolding::ticker))
                .toList();

        double cashWeight = resolvedTotalValue > 0d ? clamp(cashValue / resolvedTotalValue) : 0d;

        return new PortfolioSnapshot(
                OffsetDateTime.now(),
                holdings,
                resolvedTotalValue > 0d ? round(resolvedTotalValue, 2) : null,
                cashWeight,
                userPreferences == null ? List.of() : List.copyOf(userPreferences)
        );
    }

    private PortfolioHolding toHolding(KisBalanceHoldingRow row, double totalValue) {
        if (row == null || !StringUtils.hasText(row.pdno()) || !StringUtils.hasText(row.prdtName())) {
            return null;
        }

        double shares = parseAmount(row.hldgQty());
        if (shares <= 0d) {
            return null;
        }

        double lastPrice = parseAmount(row.prpr());
        double marketValue = resolveMarketValue(row);
        double averagePrice = parseAmount(row.pchsAvgPric());
        Double unrealizedPnl = parseOptionalAmount(row.evluPflsAmt());
        double weight = totalValue > 0d ? round(clamp(marketValue / totalValue), 6) : 0d;

        return new PortfolioHolding(
                row.pdno().trim(),
                row.prdtName().trim(),
                weight,
                buildAliases(row.pdno().trim(), row.prdtName().trim()),
                shares,
                lastPrice > 0d ? lastPrice : null,
                averagePrice > 0d ? averagePrice : null,
                marketValue > 0d ? round(marketValue, 2) : null,
                unrealizedPnl == null ? null : round(unrealizedPnl, 2)
        );
    }

    private double resolveMarketValue(KisBalanceHoldingRow row) {
        if (row == null) {
            return 0d;
        }
        double marketValue = parseAmount(row.evluAmt());
        if (marketValue > 0d) {
            return marketValue;
        }
        double shares = parseAmount(row.hldgQty());
        double lastPrice = parseAmount(row.prpr());
        return shares > 0d && lastPrice > 0d ? shares * lastPrice : 0d;
    }

    private List<String> buildAliases(String ticker, String companyName) {
        Set<String> aliases = new TreeSet<>();
        aliases.add(ticker + ".KS");
        aliases.add("KRX:" + ticker);

        String collapsed = companyName.replace(" ", "");
        if (!collapsed.equals(companyName)) {
            aliases.add(collapsed);
        }

        return new ArrayList<>(aliases);
    }

    private double parseAmount(String raw) {
        if (!StringUtils.hasText(raw)) {
            return 0d;
        }
        try {
            return Double.parseDouble(raw.replace(",", "").trim());
        } catch (NumberFormatException exception) {
            return 0d;
        }
    }

    private Double parseOptionalAmount(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return Double.parseDouble(raw.replace(",", "").trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private double clamp(double value) {
        return Math.max(0d, Math.min(1d, value));
    }

    private double round(double value, int scale) {
        double factor = Math.pow(10d, scale);
        return Math.round(value * factor) / factor;
    }
}
