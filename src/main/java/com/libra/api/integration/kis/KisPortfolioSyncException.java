package com.libra.api.integration.kis;

public class KisPortfolioSyncException extends RuntimeException {

    public KisPortfolioSyncException(String message) {
        super(message);
    }

    public KisPortfolioSyncException(String message, Throwable cause) {
        super(message, cause);
    }
}
