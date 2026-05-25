package com.libra.api.backtest.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.libra.api.backtest.api.dto.BacktestMetricResponse;
import com.libra.api.backtest.api.dto.BacktestTradeAlphaResponse;
import com.libra.api.backtest.api.dto.BacktestTradeAlphaSummaryResponse;
import com.libra.api.backtest.api.dto.BacktestValidationResponse;
import com.libra.api.backtest.config.BacktestProperties;
import com.libra.api.common.error.ApiException;
import com.libra.api.common.error.ErrorCode;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class BacktestValidationService {

    public static final String FINAL_JUDGE_EXPERIMENT_ID = "kr-objective-2020-2023-opendart-googlenews";
    private static final String CONFIRMATION_JSON = "confirmation-v3-results.article-supergemma-v2-finaljudge-full.json";
    private static final String TRADE_ALPHA_CSV = "libra-v1-v3-trade-alpha-comparison.csv";
    private static final String MAIN_CANDIDATE = "LIBRA-v3 T+2 Confirmation Gate";
    private static final String LIBRA_V1 = "LIBRA";

    private final BacktestProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public BacktestValidationService(BacktestProperties properties) {
        this.properties = properties;
    }

    public BacktestValidationResponse getValidation(String experimentId) {
        if (!FINAL_JUDGE_EXPERIMENT_ID.equals(experimentId)) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "지원하지 않는 백테스트 실험입니다: " + experimentId);
        }

        Path experimentDir = properties.outputRoot().resolve(experimentId).normalize();
        Path confirmationJson = experimentDir.resolve(CONFIRMATION_JSON);
        Path tradeAlphaCsv = experimentDir.resolve(TRADE_ALPHA_CSV);

        if (!Files.isRegularFile(confirmationJson)) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "백테스트 결과 파일을 찾을 수 없습니다: " + confirmationJson);
        }
        if (!Files.isRegularFile(tradeAlphaCsv)) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "거래별 분석 파일을 찾을 수 없습니다: " + tradeAlphaCsv);
        }

        try {
            JsonNode payload = objectMapper.readTree(confirmationJson.toFile());
            List<BacktestMetricResponse> results = readResults(payload);
            BacktestMetricResponse mainCandidate = findStrategy(results, MAIN_CANDIDATE);
            BacktestMetricResponse libraV1 = findStrategy(results, LIBRA_V1);
            List<BacktestTradeAlphaResponse> tradeAlpha = readTradeAlpha(tradeAlphaCsv);

            return new BacktestValidationResponse(
                experimentId,
                "멀티 에이전틱 AI 기반 개인 투자자 포트폴리오 자동 리밸런싱 검증",
                "2020-01-02 -> 2023-12-28, 987 trading days",
                "KIS price fixture + OpenDART disclosures + Google News RSS article-body ingest",
                "Final Judge 판단은 유지하고 Execution Confirmation Agent가 T+2 residual drift를 확인한 뒤 체결합니다.",
                mainCandidate,
                libraV1,
                results,
                summarizeTradeAlpha(tradeAlpha),
                tradeAlpha,
                List.of(confirmationJson.toString(), tradeAlphaCsv.toString()),
                List.of(
                    "Mock 데이터가 아니라 백테스트 산출물 파일을 backend가 직접 읽어 응답합니다.",
                    "T+3 Delayed Execution은 timing-only 결과이며 confirmation으로 해석하지 않습니다.",
                    "MDD는 개선이 아니라 악화되지 않은 것으로 표현합니다.",
                    "Risk Parity가 Return/Sharpe는 더 높지만 거래 횟수와 비용이 큽니다."
                )
            );
        } catch (IOException e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "백테스트 산출물을 읽을 수 없습니다", e);
        }
    }

    private static List<BacktestMetricResponse> readResults(JsonNode payload) {
        JsonNode ranked = payload.path("ranked_by_ending_value");
        if (!ranked.isArray()) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "백테스트 결과 JSON에 ranked_by_ending_value가 없습니다");
        }
        List<BacktestMetricResponse> rows = new ArrayList<>();
        for (JsonNode row : ranked) {
            rows.add(new BacktestMetricResponse(
                text(row, "strategy"),
                text(row, "group"),
                decimal(row, "ending_value_krw"),
                decimal(row, "total_return_pct"),
                decimal(row, "annualized_volatility_pct"),
                decimal(row, "sharpe_ratio"),
                decimal(row, "max_drawdown_pct"),
                integer(row, "trades"),
                decimal(row, "turnover_krw"),
                decimal(row, "transaction_cost_krw"),
                decimal(row, "return_gap_vs_libra_pct_points")
            ));
        }
        return rows;
    }

    private static BacktestMetricResponse findStrategy(List<BacktestMetricResponse> rows, String strategy) {
        return rows.stream()
            .filter(row -> strategy.equals(row.strategy()))
            .findFirst()
            .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR, "백테스트 결과에 전략이 없습니다: " + strategy));
    }

    private static List<BacktestTradeAlphaResponse> readTradeAlpha(Path csv) throws IOException {
        List<String> lines = Files.readAllLines(csv, StandardCharsets.UTF_8).stream()
            .filter(line -> !line.isBlank())
            .toList();
        if (lines.isEmpty()) {
            return List.of();
        }
        String[] headers = stripBom(lines.get(0)).split(",", -1);
        List<BacktestTradeAlphaResponse> rows = new ArrayList<>();
        for (int lineIndex = 1; lineIndex < lines.size(); lineIndex++) {
            if (lines.get(lineIndex).isBlank()) {
                continue;
            }
            String[] values = lines.get(lineIndex).split(",", -1);
            rows.add(new BacktestTradeAlphaResponse(
                value(headers, values, "signal_date"),
                value(headers, values, "v3_policy"),
                value(headers, values, "v1_execute_date"),
                value(headers, values, "v3_confirmation_date"),
                value(headers, values, "v3_execute_date"),
                Boolean.parseBoolean(value(headers, values, "v3_was_skipped")),
                decimal(value(headers, values, "v1_trade_alpha_20d_pct")),
                decimal(value(headers, values, "v3_trade_alpha_20d_pct")),
                decimal(value(headers, values, "improvement_20d_pct")),
                decimal(value(headers, values, "v1_trade_alpha_60d_pct")),
                decimal(value(headers, values, "v3_trade_alpha_60d_pct")),
                decimal(value(headers, values, "improvement_60d_pct"))
            ));
        }
        return rows;
    }

    private static BacktestTradeAlphaSummaryResponse summarizeTradeAlpha(List<BacktestTradeAlphaResponse> rows) {
        int executed = 0;
        int skipped = 0;
        int v1Negative20d = 0;
        int v1Negative60d = 0;
        int v3Negative20d = 0;
        int v3Negative60d = 0;

        for (BacktestTradeAlphaResponse row : rows) {
            if (row.v3WasSkipped()) {
                skipped++;
            } else {
                executed++;
            }
            if (isNegative(row.v1TradeAlpha20dPct())) {
                v1Negative20d++;
            }
            if (isNegative(row.v1TradeAlpha60dPct())) {
                v1Negative60d++;
            }
            if (isNegative(row.v3TradeAlpha20dPct())) {
                v3Negative20d++;
            }
            if (isNegative(row.v3TradeAlpha60dPct())) {
                v3Negative60d++;
            }
        }

        return new BacktestTradeAlphaSummaryResponse(
            rows.size(),
            executed,
            skipped,
            v1Negative20d,
            v1Negative60d,
            v3Negative20d,
            v3Negative60d
        );
    }

    private static boolean isNegative(BigDecimal value) {
        return value != null && value.signum() < 0;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private static Integer integer(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asInt();
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        return value.decimalValue();
    }

    private static BigDecimal decimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return new BigDecimal(value);
    }

    private static String value(String[] headers, String[] values, String key) {
        for (int index = 0; index < headers.length; index++) {
            if (key.equals(headers[index])) {
                return index < values.length ? values[index] : "";
            }
        }
        return "";
    }

    private static String stripBom(String value) {
        if (value != null && !value.isEmpty() && value.charAt(0) == '\uFEFF') {
            return value.substring(1);
        }
        return value;
    }
}
