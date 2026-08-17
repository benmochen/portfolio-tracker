package com.benmochen.portfolio.pricing;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/fx")
public class FxController {

    private final FxRateService fxRateService;

    public FxController(FxRateService fxRateService) {
        this.fxRateService = fxRateService;
    }

    /**
     * Fetches USD/CAD rates. With no start date it resumes from the last
     * stored rate, or 2020 on a first run. No quota applies, so this is safe
     * to call as often as you like.
     */
    @PostMapping("/refresh")
    public FxRateService.FxRefreshResult refresh(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from) {
        return fxRateService.refresh(from);
    }
}
