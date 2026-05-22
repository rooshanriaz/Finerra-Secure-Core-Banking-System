package com.fyp.fraud.config;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * WebClient for the Python ML fraud service with explicit connect/response timeouts.
 */
@Configuration
public class MlClientConfig {

    @Bean(name = "mlWebClient")
    public WebClient mlWebClient(
            @Value("${ml-service.url:http://127.0.0.1:5000}") String mlServiceUrl,
            @Value("${ml-service.timeout-ms:5000}") int responseTimeoutMs,
            @Value("${ml-service.connect-timeout-ms:3000}") int connectTimeoutMs) {

        HttpClient httpClient = HttpClient.create()
            .responseTimeout(Duration.ofMillis(responseTimeoutMs))
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs);

        return WebClient.builder()
            .baseUrl(mlServiceUrl)
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .build();
    }
}
