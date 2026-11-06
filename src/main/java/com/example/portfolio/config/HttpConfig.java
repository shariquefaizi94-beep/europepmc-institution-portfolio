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

    /**
     * One RestClient shared by both Europe PMC clients. {@link SimpleClientHttpRequestFactory}
     * (HttpURLConnection) is used because it honours the standard JVM proxy properties and gives
     * simple, explicit connect/read timeouts.
     */
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

    /**
     * Fixed-size pool for publication lookups. Because it is a singleton, the bound applies to
     * the whole service: two concurrent portfolio requests share the same N slots, so Europe PMC
     * never sees more than N simultaneous Articles API calls from this instance.
     */
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
