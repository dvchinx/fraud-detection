package com.florez.backend.fraud;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class MlServiceConfig {

    @Bean
    RestClient mlServiceRestClient(
            @Value("${ml.service.url}") String baseUrl,
            @Value("${ml.service.connect-timeout-ms}") long connectTimeoutMs,
            @Value("${ml.service.read-timeout-ms}") long readTimeoutMs
    ) {
        // uvicorn no soporta el upgrade a HTTP/2 en texto plano (h2c) que el
        // HttpClient intenta por defecto; forzamos HTTP/1.1 explicitamente.
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .configureMessageConverters(converters -> converters.withJsonConverter(new JacksonJsonHttpMessageConverter()))
                .build();
    }
}
