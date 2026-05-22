package com.fyp.cbc.config;

import java.time.Duration;
import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;

import javax.net.ssl.SSLException;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

/**
 * Configuration for WebClient instances used to communicate with Apache Fineract.
 */
@Configuration
@RequiredArgsConstructor
public class WebClientConfig {
    
    private static final Logger log = LoggerFactory.getLogger(WebClientConfig.class);
    
    private final FineractProperties fineractProperties;
    
    /**
     * Creates a WebClient configured for Fineract API calls.
     */
    @Bean(name = "fineractWebClient")
    public WebClient fineractWebClient() {
        HttpClient httpClient = createHttpClient(
            fineractProperties.getConnectionTimeout(),
            fineractProperties.getReadTimeout(),
            fineractProperties.isSkipSslVerification(),
            fineractProperties.isMtlsEnabled()
        );
        
        return WebClient.builder()
            .baseUrl(fineractProperties.getBaseUrl())
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .defaultHeader("Fineract-Platform-TenantId", fineractProperties.getTenantId())
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .filter(logRequest())
            .filter(logResponse())
            .build();
    }
    
    private HttpClient createHttpClient(int connectionTimeout, int readTimeout, boolean skipSsl, boolean mtlsEnabled) {
        HttpClient client = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectionTimeout)
            .responseTimeout(Duration.ofMillis(readTimeout))
            .doOnConnected(conn -> conn
                .addHandlerLast(new ReadTimeoutHandler(readTimeout / 1000))
                .addHandlerLast(new WriteTimeoutHandler(readTimeout / 1000)));
        
        if (mtlsEnabled) {
            try {
                String keyStorePath = fineractProperties.getClientKeyStorePath();
                String keyStorePassword = fineractProperties.getClientKeyStorePassword();
                String trustStorePath = fineractProperties.getTrustStorePath();

                if (keyStorePath == null || keyStorePath.isBlank() || keyStorePassword == null || keyStorePassword.isBlank()) {
                    throw new IllegalStateException("mTLS is enabled but client keystore path/password are not configured");
                }

                KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                KeyStore keyStore = KeyStore.getInstance("PKCS12");
                try (FileInputStream fis = new FileInputStream(keyStorePath)) {
                    keyStore.load(fis, keyStorePassword.toCharArray());
                }
                kmf.init(keyStore, keyStorePassword.toCharArray());

                SslContextBuilder builder = SslContextBuilder.forClient()
                    .keyManager(kmf);

                if (trustStorePath != null && !trustStorePath.isBlank()) {
                    TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                    KeyStore trustStore = KeyStore.getInstance("PKCS12");
                    char[] trustPassword = fineractProperties.getTrustStorePassword() != null
                        ? fineractProperties.getTrustStorePassword().toCharArray()
                        : null;
                    try (FileInputStream fis = new FileInputStream(new File(trustStorePath))) {
                        trustStore.load(fis, trustPassword);
                    }
                    tmf.init(trustStore);
                    builder = builder.trustManager(tmf);
                }

                SslContext sslContext = builder.build();
                client = client.secure(spec -> spec.sslContext(sslContext));
                log.info("Configured outbound mTLS for Fineract WebClient");
            } catch (Exception e) {
                throw new IllegalStateException("Failed to configure outbound mTLS for Fineract client", e);
            }
        } else if (skipSsl) {
            try {
                SslContext sslContext = SslContextBuilder.forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .build();
                client = client.secure(spec -> spec.sslContext(sslContext));
                log.warn("SSL verification is disabled - DO NOT USE IN PRODUCTION!");
            } catch (SSLException e) {
                log.error("Failed to configure SSL context", e);
            }
        }
        
        return client;
    }
    
    private ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(request -> {
            log.debug("==> {} {}", request.method(), request.url());
            request.headers().forEach((name, values) -> {
                if (!name.equalsIgnoreCase(HttpHeaders.AUTHORIZATION)) {
                    values.forEach(value -> log.debug("    {}: {}", name, value));
                }
            });
            return Mono.just(request);
        });
    }
    
    private ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(response -> {
            log.debug("<== {} {}", response.statusCode().value(), response.statusCode());
            return Mono.just(response);
        });
    }
}
