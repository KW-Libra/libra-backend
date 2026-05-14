package com.libra.api.broker.kis.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record KisBalanceResponse(
    String environment,
    List<KisHolding> holdings,
    AccountSummary summary,
    Map<String, Object> rawSummary,
    boolean hasNextPage,
    String nextContextFk,
    String nextContextNk
) {

    public static KisBalanceResponse from(
        String environment,
        List<Map<String, Object>> holdings,
        Map<String, Object> summary,
        boolean hasNextPage,
        String nextContextFk,
        String nextContextNk
    ) {
        return new KisBalanceResponse(
            environment,
            holdings.stream()
                .map(KisHolding::from)
                .toList(),
            AccountSummary.from(summary),
            Map.copyOf(summary),
            hasNextPage,
            blankToEmpty(nextContextFk),
            blankToEmpty(nextContextNk)
        );
    }

    public record KisHolding(
        String symbol,
        String name,
        BigDecimal quantity,
        BigDecimal orderableQuantity,
        BigDecimal averagePrice,
        BigDecimal currentPrice,
        BigDecimal purchaseAmount,
        BigDecimal valuationAmount,
        BigDecimal profitLossAmount,
        BigDecimal profitLossRate,
        Map<String, Object> raw
    ) {

        static KisHolding from(Map<String, Object> output) {
            return new KisHolding(
                stringValue(output, "pdno"),
                stringValue(output, "prdt_name"),
                decimalValue(output, "hldg_qty"),
                decimalValue(output, "ord_psbl_qty"),
                decimalValue(output, "pchs_avg_pric"),
                decimalValue(output, "prpr"),
                decimalValue(output, "pchs_amt"),
                decimalValue(output, "evlu_amt"),
                decimalValue(output, "evlu_pfls_amt"),
                decimalValue(output, "evlu_pfls_rt"),
                Map.copyOf(output)
            );
        }
    }

    public record AccountSummary(
        BigDecimal depositAmount,
        BigDecimal stockValuationAmount,
        BigDecimal totalValuationAmount,
        BigDecimal netAssetAmount,
        BigDecimal purchaseAmount,
        BigDecimal valuationAmount,
        BigDecimal profitLossAmount,
        BigDecimal profitLossRate
    ) {

        static AccountSummary from(Map<String, Object> output) {
            return new AccountSummary(
                decimalValue(output, "dnca_tot_amt"),
                decimalValue(output, "scts_evlu_amt"),
                decimalValue(output, "tot_evlu_amt"),
                decimalValue(output, "nass_amt"),
                decimalValue(output, "pchs_amt_smtl_amt"),
                decimalValue(output, "evlu_amt_smtl_amt"),
                decimalValue(output, "evlu_pfls_smtl_amt"),
                decimalValue(output, "asst_icdc_erng_rt")
            );
        }
    }

    private static String stringValue(Map<String, Object> output, String key) {
        Object value = output.get(key);
        return value == null ? "" : value.toString();
    }

    private static BigDecimal decimalValue(Map<String, Object> output, String key) {
        String value = stringValue(output, key).replace(",", "").trim();
        if (value.isBlank()) {
            return null;
        }
        return new BigDecimal(value);
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value;
    }
}
