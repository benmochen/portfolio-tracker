package com.benmochen.portfolio.returns;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/accounts/{accountId}/summary")
public class SummaryController {

    private final PortfolioSummaryService summaryService;

    public SummaryController(PortfolioSummaryService summaryService) {
        this.summaryService = summaryService;
    }

    @GetMapping
    public SummaryDtos.AccountSummary get(
            @PathVariable Long accountId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return summaryService.summarise(accountId, asOf);
    }
}
