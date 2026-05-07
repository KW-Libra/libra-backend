package com.libra.api.integration.kis;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class KisStockMasterService {

    private static final String KOSPI_MASTER_URL = "https://new.real.download.dws.co.kr/common/master/kospi_code.mst.zip";
    private static final String KOSDAQ_MASTER_URL = "https://new.real.download.dws.co.kr/common/master/kosdaq_code.mst.zip";
    private static final Charset CP949 = Charset.forName("CP949");
    private static final Duration CACHE_TTL = Duration.ofHours(24);

    private final RestClient restClient = RestClient.create();
    private volatile CachedMaster cachedMaster;

    public List<KisStockListing> search(String query, Set<String> markets, int limit) {
        List<KisStockListing> listings = loadListings();
        String normalizedQuery = normalize(query);
        Set<String> normalizedMarkets = normalizeMarkets(markets);
        int cappedLimit = Math.max(1, Math.min(limit <= 0 ? 30 : limit, 100));

        return listings.stream()
                .filter(item -> normalizedMarkets.isEmpty() || normalizedMarkets.contains(item.market()))
                .filter(item -> matches(item, normalizedQuery))
                .sorted(Comparator
                        .comparingInt((KisStockListing item) -> score(item, normalizedQuery))
                        .thenComparing(KisStockListing::market)
                        .thenComparing(KisStockListing::companyName)
                        .thenComparing(KisStockListing::ticker))
                .limit(cappedLimit)
                .toList();
    }

    public List<KisStockListing> loadListings() {
        CachedMaster snapshot = cachedMaster;
        Instant now = Instant.now();
        if (snapshot != null && snapshot.loadedAt().plus(CACHE_TTL).isAfter(now)) {
            return snapshot.listings();
        }
        synchronized (this) {
            snapshot = cachedMaster;
            if (snapshot != null && snapshot.loadedAt().plus(CACHE_TTL).isAfter(now)) {
                return snapshot.listings();
            }
            List<KisStockListing> listings = new ArrayList<>();
            listings.addAll(downloadAndParse(KOSPI_MASTER_URL, "KOSPI", 228, now));
            listings.addAll(downloadAndParse(KOSDAQ_MASTER_URL, "KOSDAQ", 222, now));
            List<KisStockListing> immutableListings = List.copyOf(listings);
            cachedMaster = new CachedMaster(now, immutableListings);
            return immutableListings;
        }
    }

    private List<KisStockListing> downloadAndParse(String url, String market, int tailLength, Instant loadedAt) {
        byte[] zipBytes;
        try {
            zipBytes = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(byte[].class);
        } catch (RestClientException exception) {
            throw new KisPortfolioSyncException("Failed to download KIS stock master: " + market, exception);
        }
        if (zipBytes == null || zipBytes.length == 0) {
            throw new KisPortfolioSyncException("KIS stock master was empty: " + market);
        }

        List<KisStockListing> listings = new ArrayList<>();
        try (
                ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(zipBytes));
                InputStreamReader inputStreamReader = openFirstEntry(zipInputStream);
                BufferedReader reader = new BufferedReader(inputStreamReader)
        ) {
            String line;
            while ((line = reader.readLine()) != null) {
                KisStockListing listing = parseMasterLine(line, market, tailLength, loadedAt);
                if (listing != null) {
                    listings.add(listing);
                }
            }
        } catch (IOException exception) {
            throw new KisPortfolioSyncException("Failed to parse KIS stock master: " + market, exception);
        }
        return listings;
    }

    private InputStreamReader openFirstEntry(ZipInputStream zipInputStream) throws IOException {
        ZipEntry entry = zipInputStream.getNextEntry();
        if (entry == null) {
            throw new IOException("zip file has no entries");
        }
        return new InputStreamReader(zipInputStream, CP949);
    }

    KisStockListing parseMasterLine(String line, String market, int tailLength, Instant loadedAt) {
        if (!StringUtils.hasText(line) || line.length() <= tailLength + 21) {
            return null;
        }
        String head = line.substring(0, line.length() - tailLength);
        if (head.length() < 22) {
            return null;
        }
        String ticker = head.substring(0, 9).trim();
        if (!ticker.matches("\\d{6}")) {
            return null;
        }
        String standardCode = head.substring(9, 21).trim();
        String companyName = head.substring(21).trim();
        if (!StringUtils.hasText(companyName)) {
            return null;
        }
        return new KisStockListing(
                ticker,
                companyName,
                market,
                standardCode,
                "KIS_STOCK_MASTER",
                loadedAt
        );
    }

    private boolean matches(KisStockListing item, String normalizedQuery) {
        if (!StringUtils.hasText(normalizedQuery)) {
            return true;
        }
        return normalize(item.ticker()).contains(normalizedQuery)
                || normalize(item.companyName()).contains(normalizedQuery)
                || normalize(item.standardCode()).contains(normalizedQuery);
    }

    private int score(KisStockListing item, String normalizedQuery) {
        if (!StringUtils.hasText(normalizedQuery)) {
            return 10;
        }
        if (normalize(item.ticker()).equals(normalizedQuery)) {
            return 0;
        }
        if (normalize(item.ticker()).startsWith(normalizedQuery)) {
            return 1;
        }
        if (normalize(item.companyName()).equals(normalizedQuery)) {
            return 2;
        }
        if (normalize(item.companyName()).startsWith(normalizedQuery)) {
            return 3;
        }
        if (normalize(item.standardCode()).startsWith(normalizedQuery)) {
            return 4;
        }
        return 5;
    }

    private Set<String> normalizeMarkets(Set<String> markets) {
        if (markets == null || markets.isEmpty()) {
            return Set.of();
        }
        return markets.stream()
                .map(market -> market == null ? "" : market.trim().toUpperCase(Locale.ROOT))
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private String normalize(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        return raw.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private record CachedMaster(Instant loadedAt, List<KisStockListing> listings) {
    }
}
