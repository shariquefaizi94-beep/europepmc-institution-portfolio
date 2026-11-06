package com.example.portfolio.web;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.portfolio.europepmc.UpstreamException;
import com.example.portfolio.model.PortfolioResponse;
import com.example.portfolio.model.PortfolioResponse.GrantSearch;
import com.example.portfolio.model.PortfolioResponse.RequestEcho;
import com.example.portfolio.model.PortfolioResponse.Summary;
import com.example.portfolio.service.PortfolioService;

@WebMvcTest(PortfolioController.class)
class PortfolioControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    PortfolioService service;

    @Test
    void appliesDefaultsAndNormalisesWhitespace() throws Exception {
        when(service.build(anyString(), anyInt(), anyInt())).thenReturn(new PortfolioResponse(
                new RequestEcho("University of Cambridge", 5, 3, "aff:\"University of Cambridge\""),
                new GrantSearch(0, 0, false), List.of(),
                new Summary("s", 0, Map.of(), Map.of(), 0, 0), List.of()));

        mvc.perform(get("/institution-portfolio").param("institution", "  University   of Cambridge "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.request.limit").value(5));

        verify(service).build(eq("University of Cambridge"), eq(5), eq(3));
    }

    @Test
    void rejectsMissingOrBlankInstitution() throws Exception {
        mvc.perform(get("/institution-portfolio"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("institution: is required"));
        mvc.perform(get("/institution-portfolio").param("institution", "   "))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void rejectsOutOfRangeAndNonNumericLimits() throws Exception {
        mvc.perform(get("/institution-portfolio").param("institution", "Cambridge").param("limit", "11"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("limit: must be at most 10"));
        mvc.perform(get("/institution-portfolio").param("institution", "Cambridge").param("publicationsPerGrant", "0"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/institution-portfolio").param("institution", "Cambridge").param("limit", "five"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("limit: must be a whole number"));
        verifyNoInteractions(service);
    }

    @Test
    void grantSearchFailureIs502AndTimeoutIs504() throws Exception {
        when(service.build(eq("Fail"), anyInt(), anyInt())).thenThrow(
                new UpstreamException("Europe PMC Grants API", "Europe PMC Grants API returned HTTP 500", false, null));
        when(service.build(eq("Slow"), anyInt(), anyInt())).thenThrow(
                new UpstreamException("Europe PMC Grants API", "Europe PMC Grants API timed out", true, null));

        mvc.perform(get("/institution-portfolio").param("institution", "Fail"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.upstream").value("Europe PMC Grants API"));
        mvc.perform(get("/institution-portfolio").param("institution", "Slow"))
                .andExpect(status().isGatewayTimeout());
    }
}
