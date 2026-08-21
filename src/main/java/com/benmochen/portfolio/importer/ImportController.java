package com.benmochen.portfolio.importer;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/imports")
public class ImportController {

    private final ImportService importService;

    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    /**
     * Upload a Questrade Activities .xlsx export.
     *
     * The whole file is read into memory. That is fine for a personal export
     * of a few thousand rows and would not be for a large one; noted as a
     * known limit rather than solved.
     */
    @PostMapping(consumes = "multipart/form-data")
    public ImportDtos.ImportResult upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new ImportException("The uploaded file is empty.");
        }
        try {
            return importService.importWorkbook(file.getOriginalFilename(), file.getBytes());
        } catch (IOException e) {
            throw new ImportException("Could not read the upload: " + e.getMessage(), e);
        }
    }
}
