package com.benmochen.portfolio.pricing;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/prices")
public class PriceController {

    private final PriceRefreshService refreshService;
    private final PriceProvider provider;

    public PriceController(PriceRefreshService refreshService, PriceProvider provider) {
        this.refreshService = refreshService;
        this.provider = provider;
    }

    /**
     * @param limit how many instruments to update in this call. Default 5,
     *              which is one minute of the provider's rate limit and keeps
     *              a single run from spending the whole daily quota.
     */
    @PostMapping("/refresh")
    public PriceRefreshService.RefreshResult refresh(
            @RequestParam(defaultValue = "5") int limit,
            @RequestParam(defaultValue = "true") boolean heldOnly) {
        return refreshService.refresh(Math.max(1, Math.min(limit, 25)), heldOnly);
    }

    /**
     * Find how the provider spells a ticker. Costs one request from the daily
     * quota, and is the way to fix a wrong provider_symbol.
     */
    @GetMapping("/search")
    public List<String> search(@RequestParam String q) {
        return provider.searchSymbols(q);
    }
}
