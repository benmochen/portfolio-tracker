package com.benmochen.portfolio.importer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;

/**
 * One row per uploaded file. Exists so an import is auditable: you can see
 * what was loaded, when, and how much of it was new.
 */
@Entity
@Table(name = "import_batch")
public class ImportBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id")
    private Long accountId;

    @Column(length = 255)
    private String filename;

    /** SHA-256 of the whole file. Makes re-uploading the same bytes a no-op. */
    @Column(name = "file_hash", nullable = false)
    private byte[] fileHash;

    @Column(name = "row_count", nullable = false)
    private int rowCount;

    @Column(name = "inserted_count", nullable = false)
    private int insertedCount;

    @Column(name = "skipped_count", nullable = false)
    private int skippedCount;

    @Generated(event = EventType.INSERT)
    @Column(name = "imported_at", nullable = false, insertable = false, updatable = false)
    private Instant importedAt;

    protected ImportBatch() {
    }

    public ImportBatch(String filename, byte[] fileHash, int rowCount) {
        this.filename = filename;
        this.fileHash = fileHash;
        this.rowCount = rowCount;
    }

    public Long getId() {
        return id;
    }

    public String getFilename() {
        return filename;
    }

    public int getRowCount() {
        return rowCount;
    }

    public int getInsertedCount() {
        return insertedCount;
    }

    public void setInsertedCount(int insertedCount) {
        this.insertedCount = insertedCount;
    }

    public int getSkippedCount() {
        return skippedCount;
    }

    public void setSkippedCount(int skippedCount) {
        this.skippedCount = skippedCount;
    }

    public Instant getImportedAt() {
        return importedAt;
    }
}
