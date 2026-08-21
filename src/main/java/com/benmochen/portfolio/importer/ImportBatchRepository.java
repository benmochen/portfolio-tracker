package com.benmochen.portfolio.importer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, Long> {

    Optional<ImportBatch> findByFileHash(byte[] fileHash);
}
