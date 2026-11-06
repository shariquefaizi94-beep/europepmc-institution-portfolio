package com.example.portfolio.web;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.portfolio.model.PortfolioResponse;
import com.example.portfolio.service.PortfolioService;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@RestController
@Validated
public class PortfolioController {

    static final int DEFAULT_LIMIT = 5;
    static final int MAX_LIMIT = 10;
    static final int DEFAULT_PUBLICATIONS_PER_GRANT = 3;
    static final int MAX_PUBLICATIONS_PER_GRANT = 5;

    private final PortfolioService service;

    public PortfolioController(PortfolioService service) {
        this.service = service;
    }

    @GetMapping(value = "/institution-portfolio", produces = "application/json")
    public PortfolioResponse portfolio(
            @RequestParam
            @NotBlank(message = "must not be blank")
            @Size(min = 2, max = 200, message = "must be between 2 and 200 characters")
            @Pattern(regexp = "[^\\p{Cntrl}]*", message = "must not contain control characters")
            String institution,

            @RequestParam(defaultValue = "" + DEFAULT_LIMIT)
            @Min(value = 1, message = "must be at least 1")
            @Max(value = MAX_LIMIT, message = "must be at most " + MAX_LIMIT)
            int limit,

            @RequestParam(defaultValue = "" + DEFAULT_PUBLICATIONS_PER_GRANT)
            @Min(value = 1, message = "must be at least 1")
            @Max(value = MAX_PUBLICATIONS_PER_GRANT, message = "must be at most " + MAX_PUBLICATIONS_PER_GRANT)
            int publicationsPerGrant) {

        String cleaned = institution.strip().replaceAll("\\s+", " ");
        return service.build(cleaned, limit, publicationsPerGrant);
    }
}
