package com.libra.api.ingest.service;

import com.libra.api.agent.api.dto.RunStartRequest;
import com.libra.api.auth.domain.User;
import com.libra.api.broker.kis.service.KisConnection;
import com.libra.api.broker.kis.service.KisCredentialService;
import com.libra.api.broker.kis.service.KisMarketClient;
import com.libra.api.common.error.ApiException;
import com.libra.api.common.error.ErrorCode;
import com.libra.api.ingest.config.LiveIngestProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class LiveIngestService {

    private static final int PRICE_HISTORY_LOOKBACK_TRADING_DAYS = 120;
    private static final int PRICE_HISTORY_QUERY_CALENDAR_DAYS = 220;
    private static final long KIS_DAILY_CHART_REQUEST_DELAY_MS = 150L;

    private final LiveIngestProperties props;
    private final ObjectMapper objectMapper;
    private final KisCredentialService credentials;
    private final KisMarketClient marketClient;

    public LiveIngestService(
        LiveIngestProperties props,
        ObjectMapper objectMapper,
        KisCredentialService credentials,
        KisMarketClient marketClient
    ) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.credentials = credentials;
        this.marketClient = marketClient;
    }

    public RunStartRequest prepare(RunStartRequest req, User user, String traceId) {
        if (req.ingest_bundle() != null && !req.ingest_bundle().isEmpty()) {
            return req.withIngestBundle(req.ingest_bundle());
        }
        if (!hasHoldings(req.portfolio())) {
            throw new ApiException(
                ErrorCode.VALIDATION_FAILED,
                "실서비스 run에는 holdings가 포함된 portfolio가 필요합니다. 잔고 동기화 후 다시 실행하세요."
            );
        }
        Map<String, Object> portfolio = enrichPortfolioPriceHistory(req.portfolio(), user);

        Path ingestRoot = props.workspaceRoot().toAbsolutePath().normalize();
        if (!Files.isDirectory(ingestRoot.resolve("src").resolve("libra_ingest"))) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "libra-ingest repo를 찾을 수 없습니다: " + ingestRoot);
        }

        String safeTraceId = sanitize(traceId);
        String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(OffsetDateTime.now());
        Path runDir = props.outputRoot().toAbsolutePath().normalize().resolve(timestamp + "-" + safeTraceId);
        Path portfolioPath = runDir.resolve("portfolio.json");
        Path bundlePath = runDir.resolve("ingest-bundle.json");
        Path logPath = runDir.resolve("backend-live-ingest.log");

        try {
            Files.createDirectories(runDir);
            Files.writeString(portfolioPath, objectMapper.writeValueAsString(portfolio), StandardCharsets.UTF_8);
            runBuilder(runDir, logPath);
            Map<String, Object> bundle = buildBundle(runDir, user, safeTraceId);
            Files.writeString(bundlePath, objectMapper.writeValueAsString(bundle), StandardCharsets.UTF_8);
            return req.withPortfolioAndIngestBundle(portfolio, bundle);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "실서비스 ingest bundle 생성에 실패했습니다", e);
        }
    }

    private Map<String, Object> enrichPortfolioPriceHistory(Map<String, Object> source, User user) {
        Map<String, Object> enriched = new LinkedHashMap<>(source);
        Object rawHoldings = source.get("holdings");
        if (!(rawHoldings instanceof List<?> holdings)) {
            return enriched;
        }

        KisConnection connection = credentials.resolve(user);
        LocalDate endDate = LocalDate.now(ZoneId.of("Asia/Seoul"));
        LocalDate startDate = endDate.minusDays(PRICE_HISTORY_QUERY_CALENDAR_DAYS);
        Double portfolioTotalValue = totalPortfolioValue(source);
        List<Map<String, Object>> enrichedHoldings = new ArrayList<>();

        for (Object item : holdings) {
            if (!(item instanceof Map<?, ?> holdingMap)) {
                continue;
            }
            Map<String, Object> holding = copyStringKeyMap(holdingMap);
            String ticker = firstNonBlank(holding, "ticker", "symbol");
            if (ticker.isBlank()) {
                enrichedHoldings.add(holding);
                continue;
            }
            normalizeHoldingSchema(holding, ticker, portfolioTotalValue);
            List<Map<String, Object>> rowsForLiquidity;
            if (!hasUsableOhlcv(holding)) {
                if (!isDomesticStockSymbol(ticker)) {
                    throw new ApiException(
                        ErrorCode.VALIDATION_FAILED,
                        "KIS 일봉 조회는 6자리 국내 주식 코드만 지원합니다: " + ticker
                    );
                }
                String marketCode = firstNonBlank(holding, "market_code", "marketCode");
                if (marketCode.isBlank()) {
                    marketCode = "J";
                }
                List<Map<String, Object>> ohlcv;
                try {
                    ohlcv = marketClient.dailyOhlcv(ticker, marketCode, startDate, endDate, connection);
                    pauseBetweenDailyChartRequests();
                } catch (ApiException e) {
                    throw new ApiException(
                        e.getCode(),
                        "KIS 일봉 조회 실패: " + ticker + " (" + marketCode + ") - " + e.getMessage(),
                        e
                    );
                }
                if (ohlcv.isEmpty()) {
                    throw new ApiException(
                        ErrorCode.VALIDATION_FAILED,
                        "KIS 가격 히스토리가 비어 있습니다: " + ticker
                    );
                }
                List<Map<String, Object>> trimmed = tailRows(ohlcv, PRICE_HISTORY_LOOKBACK_TRADING_DAYS);
                holding.put("ohlcv", trimmed);
                holding.put("daily_returns", dailyReturns(trimmed));
                rowsForLiquidity = trimmed;
            } else {
                rowsForLiquidity = ohlcvRows(holding.get("ohlcv"));
            }
            putLiquidityMetrics(holding, rowsForLiquidity);
            enrichedHoldings.add(holding);
        }

        enriched.put("holdings", enrichedHoldings);
        return enriched;
    }

    static boolean isDomesticStockSymbol(String ticker) {
        return ticker != null && ticker.trim().matches("\\d{6}");
    }

    private static void normalizeHoldingSchema(Map<String, Object> holding, String ticker, Double portfolioTotalValue) {
        holding.put("ticker", ticker);
        if (!hasNonBlankValue(holding.get("symbol"))) {
            holding.put("symbol", ticker);
        }
        putAliasIfMissing(holding, "company_name", "name", "symbol");
        putAliasIfMissing(holding, "shares", "quantity");
        putAliasIfMissing(holding, "last_price", "current_price", "currentPrice");
        putAliasIfMissing(holding, "average_price", "averagePrice");
        putAliasIfMissing(holding, "market_value_krw", "valuation_amount", "valuationAmount");
        putAliasIfMissing(holding, "unrealized_pnl_krw", "profit_loss_amount", "profitLossAmount");
        if (!holding.containsKey("aliases")) {
            List<String> aliases = new ArrayList<>();
            aliases.add(ticker);
            String name = firstNonBlank(holding, "company_name", "name");
            if (!name.isBlank() && !name.equals(ticker)) {
                aliases.add(name);
            }
            holding.put("aliases", aliases);
        }
        if (!hasNumeric(holding, "weight") && portfolioTotalValue != null && portfolioTotalValue > 0) {
            Double marketValue = toDouble(holding.get("market_value_krw"));
            if (marketValue != null && marketValue > 0) {
                holding.put("weight", Math.max(0.0, Math.min(1.0, marketValue / portfolioTotalValue)));
            }
        }
    }

    private static void putAliasIfMissing(Map<String, Object> holding, String targetKey, String... sourceKeys) {
        if (hasNonBlankValue(holding.get(targetKey))) {
            return;
        }
        for (String sourceKey : sourceKeys) {
            Object value = holding.get(sourceKey);
            if (hasNonBlankValue(value)) {
                holding.put(targetKey, value);
                return;
            }
        }
    }

    private static boolean hasNonBlankValue(Object value) {
        return value != null && !value.toString().isBlank();
    }

    private static Double totalPortfolioValue(Map<String, Object> source) {
        Double direct = firstDouble(source, "total_value_krw", "totalValueKrw", "totalValuationAmount", "netAssetAmount");
        if (direct != null && direct > 0) {
            return direct;
        }
        Object summary = source.get("summary");
        if (summary instanceof Map<?, ?> summaryMap) {
            Double fromSummary = firstDouble(
                copyStringKeyMap(summaryMap),
                "totalValuationAmount",
                "netAssetAmount",
                "valuationAmount",
                "total_value_krw"
            );
            if (fromSummary != null && fromSummary > 0) {
                return fromSummary;
            }
        }
        return null;
    }

    private static Double firstDouble(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Double value = toDouble(source.get(key));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static void pauseBetweenDailyChartRequests() {
        try {
            Thread.sleep(KIS_DAILY_CHART_REQUEST_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(ErrorCode.KIS_UNAVAILABLE, "KIS 일봉 조회 대기 중 인터럽트되었습니다", e);
        }
    }

    private void runBuilder(
        Path runDir,
        Path logPath
    ) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(props.pythonCommand());
        command.add("-m");
        command.add("libra_ingest.ingest_cli");
        command.add("--live-baseline");
        command.add("--out-dir");
        command.add(runDir.toString());
        command.add("--emit-push-candidates");
        command.add("--pretty");
        command.add("--require-article-body");
        command.add("--rss-limit");
        command.add(Integer.toString(props.rssLimit()));
        command.add("--dart-limit");
        command.add(Integer.toString(props.dartLimit()));
        command.add("--report-limit");
        command.add(Integer.toString(props.reportLimit()));
        command.add("--report-pdf-pages");
        command.add(Integer.toString(props.reportPdfPages()));
        command.add("--report-min-body-chars");
        command.add(Integer.toString(props.reportMinBodyChars()));

        ProcessBuilder builder = new ProcessBuilder(command)
            .directory(runDir.toFile())
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.appendTo(logPath.toFile()));
        builder.environment().put("PYTHONPATH", props.workspaceRoot().toAbsolutePath().normalize().resolve("src").toString());
        Process process = builder.start();
        boolean completed = process.waitFor(props.timeout().toMillis(), TimeUnit.MILLISECONDS);
        if (!completed) {
            process.destroyForcibly();
            throw new ApiException(
                ErrorCode.AGENT_TIMEOUT,
                "실서비스 ingest bundle 생성이 제한 시간 안에 끝나지 않았습니다. log=" + logPath
            );
        }
        if (process.exitValue() != 0) {
            throw new ApiException(
                ErrorCode.VALIDATION_FAILED,
                "실서비스 ingest bundle 생성 실패. log tail=" + tail(logPath, 3000)
            );
        }
        if (!Files.isRegularFile(runDir.resolve("events.json"))) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "libra-ingest events.json이 생성되지 않았습니다: " + runDir);
        }
        if (!Files.isRegularFile(runDir.resolve("normalized_documents.json"))) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "libra-ingest normalized_documents.json이 생성되지 않았습니다: " + runDir);
        }
    }

    private Map<String, Object> buildBundle(Path runDir, User user, String traceId) throws IOException {
        @SuppressWarnings("unchecked")
        Map<String, Object> eventsPayload = objectMapper.readValue(
            Files.readString(runDir.resolve("events.json"), StandardCharsets.UTF_8),
            Map.class
        );
        @SuppressWarnings("unchecked")
        Map<String, Object> documentsPayload = objectMapper.readValue(
            Files.readString(runDir.resolve("normalized_documents.json"), StandardCharsets.UTF_8),
            Map.class
        );
        List<?> events = eventsPayload.get("events") instanceof List<?> list ? list : List.of();
        List<?> documents = documentsPayload.get("documents") instanceof List<?> list ? list : List.of();
        if (events.isEmpty() && documents.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "libra-ingest가 사용 가능한 문서/이벤트를 생성하지 못했습니다");
        }
        String asOf = OffsetDateTime.now(ZoneId.of("Asia/Seoul")).toString();
        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("bundle_id", "service-run-" + traceId);
        bundle.put("as_of", asOf);
        bundle.put("portfolio_id", user.getId().toString());
        bundle.put("source_policy", "libra-ingest live baseline: RSS article body required + DART + reports");
        bundle.put("prices_until", LocalDate.now(ZoneId.of("Asia/Seoul")).toString());
        bundle.put("observed_count", events.size());
        bundle.put("portfolio_relevant_count", events.size());
        bundle.put("usable_for_trade_decision", true);
        bundle.put("items", events);
        bundle.put("document_count", documents.size());
        bundle.put("documents", documents);
        return bundle;
    }

    private static boolean hasHoldings(Map<String, Object> portfolio) {
        if (portfolio == null) {
            return false;
        }
        Object holdings = portfolio.get("holdings");
        return holdings instanceof List<?> list && !list.isEmpty();
    }

    private static Map<String, Object> copyStringKeyMap(Map<?, ?> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() != null) {
                copy.put(entry.getKey().toString(), entry.getValue());
            }
        }
        return copy;
    }

    private static boolean hasUsableOhlcv(Map<String, Object> holding) {
        Object rows = holding.get("ohlcv");
        return rows instanceof List<?> list && !list.isEmpty();
    }

    private static String firstNonBlank(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value != null && !value.toString().isBlank()) {
                return value.toString().trim();
            }
        }
        return "";
    }

    private static List<Map<String, Object>> tailRows(List<Map<String, Object>> rows, int maxRows) {
        if (rows.size() <= maxRows) {
            return rows;
        }
        return new ArrayList<>(rows.subList(rows.size() - maxRows, rows.size()));
    }

    private static List<Map<String, Object>> ohlcvRows(Object rawRows) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (!(rawRows instanceof List<?> list)) {
            return rows;
        }
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                rows.add(copyStringKeyMap(map));
            }
        }
        return rows;
    }

    private static void putLiquidityMetrics(Map<String, Object> holding, List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            return;
        }
        Double averageVolume = averageVolume(rows, 20);
        Double averageTurnover = averageTurnoverKrw(rows, 20);
        if (averageVolume != null && !hasNumeric(holding, "avg_daily_volume")) {
            holding.put("avg_daily_volume", averageVolume);
        }
        if (averageTurnover != null) {
            if (!hasNumeric(holding, "avg_daily_turnover_krw")) {
                holding.put("avg_daily_turnover_krw", averageTurnover);
            }
            if (!hasNumeric(holding, "adv_krw")) {
                holding.put("adv_krw", averageTurnover);
            }
        }
    }

    private static Double averageVolume(List<Map<String, Object>> rows, int lookback) {
        double total = 0.0;
        int count = 0;
        int start = Math.max(0, rows.size() - lookback);
        for (int i = start; i < rows.size(); i++) {
            Double volume = toDouble(rows.get(i).get("volume"));
            if (volume != null && volume > 0) {
                total += volume;
                count++;
            }
        }
        return count == 0 ? null : total / count;
    }

    private static Double averageTurnoverKrw(List<Map<String, Object>> rows, int lookback) {
        double total = 0.0;
        int count = 0;
        int start = Math.max(0, rows.size() - lookback);
        for (int i = start; i < rows.size(); i++) {
            Map<String, Object> row = rows.get(i);
            Double close = toDouble(row.get("close"));
            Double volume = toDouble(row.get("volume"));
            if (close != null && close > 0 && volume != null && volume > 0) {
                total += close * volume;
                count++;
            }
        }
        return count == 0 ? null : total / count;
    }

    private static List<Double> dailyReturns(List<Map<String, Object>> rows) {
        List<Double> returns = new ArrayList<>();
        Double previousClose = null;
        for (Map<String, Object> row : rows) {
            Double close = toDouble(row.get("close"));
            if (close == null || close <= 0) {
                continue;
            }
            if (previousClose != null && previousClose > 0) {
                returns.add(close / previousClose - 1.0);
            }
            previousClose = close;
        }
        return returns;
    }

    private static Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Double.parseDouble(value.toString().replace(",", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean hasNumeric(Map<String, Object> source, String key) {
        Double value = toDouble(source.get(key));
        return value != null && value > 0;
    }

    private static String sanitize(String value) {
        String input = value == null || value.isBlank() ? "missing-trace-id" : value;
        String sanitized = input.replaceAll("[^A-Za-z0-9_.-]", "-");
        if (sanitized.length() > 80) {
            return sanitized.substring(0, 80);
        }
        return sanitized;
    }

    private static String tail(Path path, int maxChars) {
        try {
            String text = Files.readString(path, StandardCharsets.UTF_8);
            if (text.length() <= maxChars) {
                return text;
            }
            return text.substring(text.length() - maxChars);
        } catch (IOException e) {
            return "unreadable log: " + path;
        }
    }
}
