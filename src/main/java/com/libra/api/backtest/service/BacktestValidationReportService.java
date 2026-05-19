package com.libra.api.backtest.service;

import com.libra.api.backtest.config.BacktestReportProperties;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class BacktestValidationReportService {

    private final BacktestReportProperties properties;
    private final ObjectMapper objectMapper;
    private final S3Client s3Client;
    private final Clock clock;

    private JsonNode cachedReport;
    private Instant cachedAt;

    @Autowired
    public BacktestValidationReportService(
        BacktestReportProperties properties,
        ObjectMapper objectMapper,
        S3Client s3Client
    ) {
        this(properties, objectMapper, s3Client, Clock.systemUTC());
    }

    BacktestValidationReportService(
        BacktestReportProperties properties,
        ObjectMapper objectMapper,
        S3Client s3Client,
        Clock clock
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.s3Client = s3Client;
        this.clock = clock;
    }

    public synchronized JsonNode publicRss3yValidation() {
        if (cachedReport != null && cachedAt != null
            && cachedAt.plus(properties.cacheTtl()).isAfter(clock.instant())) {
            return cachedReport;
        }

        URI uri = properties.validationReportUri();
        if (uri == null) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Backtest validation report URI is not configured"
            );
        }

        try {
            JsonNode report = readJson(uri);
            cachedReport = report;
            cachedAt = clock.instant();
            return report;
        } catch (IOException | RuntimeException ex) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Backtest validation report is unavailable",
                ex
            );
        }
    }

    private JsonNode readJson(URI uri) throws IOException {
        String scheme = uri.getScheme();
        if ("s3".equalsIgnoreCase(scheme)) {
            return readS3(uri);
        }
        if ("file".equalsIgnoreCase(scheme)) {
            return objectMapper.readTree(Path.of(uri).toFile());
        }
        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
            URL url = uri.toURL();
            try (InputStream stream = url.openStream()) {
                return objectMapper.readTree(stream);
            }
        }
        if (scheme == null || scheme.isBlank()) {
            return objectMapper.readTree(Files.newInputStream(Path.of(uri.toString())));
        }
        throw new IOException("Unsupported backtest report URI scheme: " + scheme);
    }

    private JsonNode readS3(URI uri) throws IOException {
        String bucket = uri.getHost();
        String key = uri.getPath();
        if (bucket == null || bucket.isBlank() || key == null || key.length() <= 1) {
            throw new IOException("Invalid S3 URI: " + uri);
        }

        ResponseBytes<GetObjectResponse> bytes = s3Client.getObjectAsBytes(GetObjectRequest.builder()
            .bucket(bucket)
            .key(key.substring(1))
            .build());
        return objectMapper.readTree(bytes.asInputStream());
    }
}
