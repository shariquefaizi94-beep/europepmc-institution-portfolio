package com.example.portfolio.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class HttpConfig {

    // HttpURLConnection-based factory: simple timeouts and it respects the JVM proxy settings.
    @Bean
    RestClient europePmcRestClient(RestClient.Builder builder, PortfolioProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.connectTimeout());
        factory.setReadTimeout(props.readTimeout());
        return builder
                .requestFactory(factory)
                .defaultHeader("Accept", "application/json")
                .defaultHeader("User-Agent", "institution-portfolio/0.1 (take-home exercise)")
                .build();
    }

    // Singleton pool, so the limit is service-wide: concurrent requests share the same N slots
    // and Europe PMC never sees more than N parallel article lookups from one instance.
    @Bean(destroyMethod = "shutdownNow")
    ExecutorService publicationLookupExecutor(PortfolioProperties props) {
        AtomicInteger counter = new AtomicInteger();
        return Executors.newFixedThreadPool(props.maxConcurrentLookups(), runnable -> {
            Thread t = new Thread(runnable, "pub-lookup-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }
}
