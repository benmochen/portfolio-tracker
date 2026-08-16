package com.benmochen.portfolio.instrument;

import com.benmochen.portfolio.instrument.InstrumentDtos.CreateInstrumentRequest;
import com.benmochen.portfolio.instrument.InstrumentDtos.InstrumentResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/instruments")
public class InstrumentController {

    private final InstrumentService instrumentService;

    public InstrumentController(InstrumentService instrumentService) {
        this.instrumentService = instrumentService;
    }

    @GetMapping
    public List<InstrumentResponse> list() {
        return instrumentService.findAll();
    }

    @GetMapping("/{id}")
    public InstrumentResponse get(@PathVariable Long id) {
        return instrumentService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InstrumentResponse create(@Valid @RequestBody CreateInstrumentRequest request) {
        return instrumentService.create(request);
    }
}
