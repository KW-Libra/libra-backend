package com.libra.api.integration.kis;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "libra.kis")
public class KisProperties {

    private Credential real = new Credential();
    private Credential demo = new Credential();
    private String credentialSecret = "change-me-libra-kis-credential-secret-32-bytes";

    public Credential getReal() {
        return real;
    }

    public void setReal(Credential real) {
        this.real = real;
    }

    public Credential getDemo() {
        return demo;
    }

    public void setDemo(Credential demo) {
        this.demo = demo;
    }

    public String getCredentialSecret() {
        return credentialSecret;
    }

    public void setCredentialSecret(String credentialSecret) {
        this.credentialSecret = credentialSecret;
    }

    public static class Credential {
        private String baseUrl;
        private String appKey;
        private String appSecret;
        private String accountNo;
        private String productCode;
        private String userAgent;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getAppKey() {
            return appKey;
        }

        public void setAppKey(String appKey) {
            this.appKey = appKey;
        }

        public String getAppSecret() {
            return appSecret;
        }

        public void setAppSecret(String appSecret) {
            this.appSecret = appSecret;
        }

        public String getAccountNo() {
            return accountNo;
        }

        public void setAccountNo(String accountNo) {
            this.accountNo = accountNo;
        }

        public String getProductCode() {
            return productCode;
        }

        public void setProductCode(String productCode) {
            this.productCode = productCode;
        }

        public String getUserAgent() {
            return userAgent;
        }

        public void setUserAgent(String userAgent) {
            this.userAgent = userAgent;
        }
    }
}
