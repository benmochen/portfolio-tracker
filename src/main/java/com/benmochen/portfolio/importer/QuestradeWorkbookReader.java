package com.benmochen.portfolio.importer;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads a Questrade "Activities" .xlsx export into ActivityRow records.
 *
 * Two facts about the real file drive this class:
 *   1. Every cell is stored as TEXT, including dates and numbers. Nothing can
 *      be read with getNumericCellValue().
 *   2. Dates look like "2023-01-05 12:00:00 AM": a 12-hour clock with a
 *      meridiem marker. Parsing that with a 24-hour pattern fails silently on
 *      some rows and loudly on others, so the pattern is explicit and the
 *      Locale is pinned to ENGLISH (a French-locale JVM would expect a
 *      different AM/PM token).
 */
@Component
public class QuestradeWorkbookReader {

    private static final String SHEET_NAME = "Activities";

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm:ss a", Locale.ENGLISH);

    /** Fallback for exports that omit the time portion. */
    private static final DateTimeFormatter DATE_ONLY =
            DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH);

    private final DataFormatter formatter = new DataFormatter(Locale.ENGLISH);

    public List<ActivityRow> read(InputStream in) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(in)) {
            Sheet sheet = workbook.getSheet(SHEET_NAME);
            if (sheet == null) {
                sheet = workbook.getSheetAt(0);
            }

            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                throw new ImportException("The spreadsheet has no header row.");
            }
            Map<String, Integer> columns = indexHeaders(headerRow);

            List<ActivityRow> rows = new ArrayList<>();
            for (int i = headerRow.getRowNum() + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isBlank(row, columns)) {
                    continue;
                }
                rows.add(toActivityRow(row, columns, i + 1));
            }
            return rows;
        }
    }

    private Map<String, Integer> indexHeaders(Row headerRow) {
        Map<String, Integer> columns = new HashMap<>();
        for (Cell cell : headerRow) {
            String name = formatter.formatCellValue(cell).trim();
            if (!name.isEmpty()) {
                columns.put(name.toLowerCase(Locale.ROOT), cell.getColumnIndex());
            }
        }
        for (String required : List.of("transaction date", "action", "net amount",
                "currency", "account #", "activity type")) {
            if (!columns.containsKey(required)) {
                throw new ImportException(
                        "Missing expected column: '" + required + "'. "
                        + "Found: " + columns.keySet());
            }
        }
        return columns;
    }

    private boolean isBlank(Row row, Map<String, Integer> columns) {
        for (Integer index : columns.values()) {
            if (!text(row, index).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private ActivityRow toActivityRow(Row row, Map<String, Integer> c, int rowNumber) {
        return new ActivityRow(
                parseDate(get(row, c, "transaction date"), rowNumber, "Transaction Date"),
                parseNullableDate(get(row, c, "settlement date")),
                get(row, c, "action"),
                emptyToNull(get(row, c, "symbol")),
                emptyToNull(get(row, c, "description")),
                parseDecimal(get(row, c, "quantity"), rowNumber, "Quantity"),
                parseDecimal(get(row, c, "price"), rowNumber, "Price"),
                parseDecimal(get(row, c, "gross amount"), rowNumber, "Gross Amount"),
                parseDecimal(get(row, c, "commission"), rowNumber, "Commission"),
                parseDecimal(get(row, c, "net amount"), rowNumber, "Net Amount"),
                get(row, c, "currency"),
                get(row, c, "account #"),
                get(row, c, "activity type"),
                get(row, c, "account type"));
    }

    private String get(Row row, Map<String, Integer> columns, String header) {
        Integer index = columns.get(header);
        return index == null ? "" : text(row, index);
    }

    private String text(Row row, int index) {
        Cell cell = row.getCell(index);
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private LocalDate parseDate(String raw, int rowNumber, String field) {
        LocalDate parsed = parseNullableDate(raw);
        if (parsed == null) {
            throw new ImportException(
                    "Row " + rowNumber + ": could not read " + field + " from '" + raw + "'");
        }
        return parsed;
    }

    private LocalDate parseNullableDate(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw, TIMESTAMP).toLocalDate();
        } catch (DateTimeParseException ignored) {
            // fall through to the date-only form
        }
        try {
            return LocalDate.parse(raw, DATE_ONLY);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    /**
     * Parses money and quantities. Strips currency symbols, thousands
     * separators and parenthesised negatives, all of which appear in broker
     * exports depending on export settings.
     *
     * Returns BigDecimal, never double: binary floating point cannot represent
     * 0.1 exactly, and this data feeds a returns calculation.
     */
    private BigDecimal parseDecimal(String raw, int rowNumber, String field) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        String cleaned = raw.replace("$", "")
                .replace(",", "")
                .replace("\u00A0", "")
                .trim();
        boolean parenthesisedNegative = cleaned.startsWith("(") && cleaned.endsWith(")");
        if (parenthesisedNegative) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        if (cleaned.isEmpty()) {
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(cleaned);
            return parenthesisedNegative ? value.negate() : value;
        } catch (NumberFormatException e) {
            throw new ImportException(
                    "Row " + rowNumber + ": could not read " + field + " from '" + raw + "'");
        }
    }
}
