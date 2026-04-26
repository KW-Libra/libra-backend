package com.libra.api;

import com.libra.api.integration.kis.KisProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(KisProperties.class)
public class LibraApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(LibraApiApplication.class, args);
    }
}
