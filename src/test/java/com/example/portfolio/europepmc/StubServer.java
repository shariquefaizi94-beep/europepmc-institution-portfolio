package com.example.portfolio.europepmc;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.example.portfolio.config.PortfolioProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

/**
 * Tiny local HTTP server standing in for Europe PMC, so client tests exercise real HTTP, real
 * URL encoding and real timeouts without touching the live services.
 */
final class StubServer implements AutoCloseable {

    private final HttpServer server;
    final List<String> rawRequestUris = new CopyOnWriteArrayList<>();
    volatile int status = 200;
    volatile String body = "{}";
    volatile long delayMillis = 0;

    StubServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/", exchange -> {
            rawRequestUris.add(exchange.getRequestURI().getRawPath()
                    + (exchange.getRequestURI().getRawQuery() == null ? "" : "?" + exchange.getRequestURI().getRawQuery()));
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            try {
                exchange.sendResponseHeaders(status, bytes.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            } catch (IOException ignored) {
                // client gave up (timeout test)
            }
        });
        server.start();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    StubServer respondWithFixture(String name) {
        try {
            body = Files.readString(Path.of("src/test/resources/fixtures", name));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return this;
    }

    PortfolioProperties props(Duration readTimeout) {
        return new PortfolioProperties(baseUrl(), baseUrl(), Duration.ofSeconds(2), readTimeout, 4,
                Duration.ofSeconds(10));
    }

    EuropePmcHttp http(Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(readTimeout);
        return new EuropePmcHttp(RestClient.builder().requestFactory(factory).build(), new ObjectMapper());
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
