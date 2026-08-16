package com.benmochen.portfolio.instrument;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public final class InstrumentDtos {

    private InstrumentDtos() {
    }

    public record CreateInstrumentRequest(
            @NotBlank String symbol,
            String exchange,
            @NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "must be a 3-letter ISO code")
            String currency,
            @NotNull InstrumentType instrumentType,
            String name
    ) {
    }

    public record InstrumentResponse(
            Long id,
            String symbol,
            String exchange,
            String currency,
            InstrumentType instrumentType,
            String name
    ) {
        public static InstrumentResponse from(Instrument i) {
            return new InstrumentResponse(
                    i.getId(),
                    i.getSymbol(),
                    i.getExchange(),
                    i.getCurrency(),
                    i.getInstrumentType(),
                    i.getName());
        }
    }
}
