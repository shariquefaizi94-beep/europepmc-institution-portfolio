package com.example.portfolio.europepmc;

/** A call to a Europe PMC API failed: timeout, connection error, HTTP error or unreadable body. */
public class UpstreamException extends RuntimeException {

    private final String upstream;
    private final boolean timeout;

    public UpstreamException(String upstream, String message, boolean timeout, Throwable cause) {
        super(message, cause);
        this.upstream = upstream;
        this.timeout = timeout;
    }

    public String upstream() {
        return upstream;
    }

    public boolean isTimeout() {
        return timeout;
    }
}
