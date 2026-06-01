package com.devtoolcopilot.docgen.wenduoduo;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

@Configuration
@EnableConfigurationProperties(WenDuoDuoProperties.class)
public class WenDuoDuoConfig {
    @Bean("wenduoduoRestTemplate")
    public RestTemplate wenduoduoRestTemplate(WenDuoDuoProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int timeout = properties.getTimeoutMs() > 0 ? properties.getTimeoutMs() : 120000;
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return new RestTemplate(factory);
    }
}
