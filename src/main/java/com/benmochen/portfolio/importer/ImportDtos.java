package com.benmochen.portfolio.importer;

import java.util.List;

public final class ImportDtos {

    private ImportDtos() {
    }

    /**
     * @param alreadyImported true when the exact same file was uploaded before
     *                        and nothing was done this time
     */
    public record ImportResult(
            Long batchId,
            int rowsInFile,
            int inserted,
            int skippedAsDuplicate,
            boolean alreadyImported,
            List<String> warnings
    ) {
    }
}
