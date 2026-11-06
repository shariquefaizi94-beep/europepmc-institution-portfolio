package com.example.portfolio.europepmc;

import java.net.SocketTimeoutException;
import java.net.URI;

import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

// Every failure mode ends up as UpstreamException so callers only handle one type.
@Component
public class EuropePmcHttp {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public EuropePmcHttp(RestClient europePmcRestClient, ObjectMapper objectMapper) {
        this.restClient = europePmcRestClient;
        this.objectMapper = objectMapper;
    }

    JsonNode getJson(URI uri, String upstream) {
        String body;
        try {
            // parse ourselves rather than trusting the upstream Content-Type
            body = restClient.get().uri(uri).retrieve().body(String.class);
        } catch (RestClientResponseException e) {
            throw new UpstreamException(upstream,
                    upstream + " returned HTTP " + e.getStatusCode().value(), false, e);
        } catch (ResourceAccessException e) {
            boolean timeout = hasCause(e, SocketTimeoutException.class);
            throw new UpstreamException(upstream,
                    upstream + (timeout ? " timed out" : " could not be reached"), timeout, e);
        } catch (RestClientException e) {
            throw new UpstreamException(upstream, upstream + " call failed", false, e);
        }
        if (body == null || body.isBlank()) {
            throw new UpstreamException(upstream, upstream + " returned an empty body", false, null);
        }
        try {
            return objectMapper.readTree(body);
        } catch (JsonProcessingException e) {
            throw new UpstreamException(upstream, upstream + " returned a response that is not valid JSON", false, e);
        }
    }

    private static boolean hasCause(Throwable t, Class<? extends Throwable> type) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (type.isInstance(c)) {
                return true;
            }
        }
        return false;
    }
}
