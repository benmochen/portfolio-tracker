package com.benmochen.portfolio.instrument;

import com.benmochen.portfolio.common.NotFoundException;
import com.benmochen.portfolio.instrument.InstrumentDtos.CreateInstrumentRequest;
import com.benmochen.portfolio.instrument.InstrumentDtos.InstrumentResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class InstrumentService {

    private final InstrumentRepository instrumentRepository;

    public InstrumentService(InstrumentRepository instrumentRepository) {
        this.instrumentRepository = instrumentRepository;
    }

    @Transactional(readOnly = true)
    public List<InstrumentResponse> findAll() {
        return instrumentRepository.findAll().stream()
                .map(InstrumentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public InstrumentResponse findById(Long id) {
        return instrumentRepository.findById(id)
                .map(InstrumentResponse::from)
                .orElseThrow(() -> NotFoundException.of("Instrument", id));
    }

    @Transactional
    public InstrumentResponse create(CreateInstrumentRequest request) {
        Instrument instrument = new Instrument(
                request.symbol(),
                request.exchange(),
                request.currency(),
                request.instrumentType(),
                request.name());
        return InstrumentResponse.from(instrumentRepository.save(instrument));
    }

}
