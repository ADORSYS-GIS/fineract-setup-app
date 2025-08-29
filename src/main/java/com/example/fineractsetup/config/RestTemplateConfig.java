package com.example.fineractsetup.config;

import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.TrustStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import java.security.cert.X509Certificate;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        try {
            // Trust all certificates
            TrustStrategy acceptingTrustStrategy = (X509Certificate[] chain, String authType) -> true;

            // Build SSL context using the above trust strategy
            SSLContext sslContext = SSLContextBuilder
                    .create()
                    .loadTrustMaterial(null, acceptingTrustStrategy)
                    .build();

            // Create SSL socket factory with disabled hostname verification
            SSLConnectionSocketFactory csf = new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);

            // Create HttpClient using the above socket factory
            CloseableHttpClient httpClient = HttpClients.custom()
                    .setSSLSocketFactory(csf)
                    .build();

            // Use the custom HttpClient in the request factory
            HttpComponentsClientHttpRequestFactory requestFactory =
                    new HttpComponentsClientHttpRequestFactory();

            requestFactory.setHttpClient(httpClient);

            // Return RestTemplate with the custom request factory
            return new RestTemplate(requestFactory);

        } catch (Exception e) {
            throw new RuntimeException("Failed to create RestTemplate that accepts all SSL certificates", e);
        }
    }
}
