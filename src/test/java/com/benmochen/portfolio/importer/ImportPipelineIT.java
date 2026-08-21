package com.benmochen.portfolio.importer;

import com.benmochen.portfolio.TestcontainersConfiguration;
import com.benmochen.portfolio.account.AccountRepository;
import com.benmochen.portfolio.position.PositionDtos;
import com.benmochen.portfolio.position.PositionService;
import com.benmochen.portfolio.user.AppUser;
import com.benmochen.portfolio.user.AppUserRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The whole import pipeline against a real Postgres: spreadsheet bytes in,
 * correct positions out.
 *
 * The workbook is built in code rather than checked in as a fixture, so the
 * exact quirks being tested are visible right here: text cells for every
 * value, 12-hour timestamps, newest row first, three spellings of one symbol,
 * and a same-day buy and sell.
 *
 * Every unit test in this project covers one component. This is the only test
 * that would catch a break in how they fit together.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
@WithMockUser(username = "importtester")
class ImportPipelineIT {

    private static final String[] HEADERS = {
            "Transaction Date", "Settlement Date", "Action", "Symbol", "Description",
            "Quantity", "Price", "Gross Amount", "Commission", "Net Amount",
            "Currency", "Account #", "Activity Type", "Account Type"
    };

    @Autowired
    private ImportService importService;

    @Autowired
    private PositionService positionService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @BeforeEach
    void seedUser() {
        if (appUserRepository.findByUsername("importtester").isEmpty()) {
            appUserRepository.save(new AppUser("importtester", "not-a-real-hash"));
        }
    }

    @Test
    void importsAWorkbookAndDerivesCorrectPositions() throws IOException {
        var result = importService.importWorkbook("test.xlsx", workbook());

        assertThat(result.rowsInFile()).isEqualTo(6);
        assertThat(result.inserted()).isEqualTo(6);
        assertThat(result.warnings()).isEmpty();

        Long accountId = accountRepository
                .findByExternalIdAndUserId("99999999",
                        appUserRepository.findByUsername("importtester").orElseThrow().getId())
                .orElseThrow()
                .getId();

        PositionDtos.PositionsResponse positions =
                positionService.forAccount(accountId, null);

        var acme = positions.open().stream()
                .filter(p -> p.symbol().equals("ACME"))
                .findFirst()
                .orElseThrow();

        // 100 bought at 10.00 plus 50 at 12.00, minus 30 sold the same day as
        // the second buy. Pooled cost before the sale is 1000 + 600 = 1600
        // over 150 units, so 10.6667 each. Selling 30 releases 320.
        // Remaining: 120 units at a cost of 1280.
        assertThat(acme.quantity()).isEqualByComparingTo("120");
        assertThat(acme.costBasis()).isEqualByComparingTo("1280.00");

        // The dividend arrived under a different spelling of the same symbol.
        // If resolution failed it would sit on a separate instrument and this
        // would be zero.
        assertThat(acme.dividendsReceived()).isEqualByComparingTo("15.00");
    }

    @Test
    void reimportingTheSameFileChangesNothing() throws IOException {
        // Built once and reused deliberately. An .xlsx is a ZIP archive with
        // timestamps inside it, so generating the same content twice produces
        // different bytes and a different file hash. Calling workbook() twice
        // would test nothing: the second import would legitimately be a new
        // file as far as the hash is concerned.
        byte[] bytes = workbook();

        importService.importWorkbook("test.xlsx", bytes);
        var second = importService.importWorkbook("test.xlsx", bytes);

        assertThat(second.alreadyImported()).isTrue();
        assertThat(second.inserted()).isZero();
    }

    /**
     * A miniature Questrade export, newest row first, with every cell written
     * as text exactly as the real file does.
     */
    private byte[] workbook() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = wb.createSheet("Activities");
            writeRow(sheet, 0, HEADERS);

            // Newest first, matching the real export's ordering.
            writeRow(sheet, 1, row("2026-03-01", "", ".ACME", "ACME CORP CASH DIV ON 120 SHS",
                    "0", "0", "15.00", "0", "15.00", "Dividends"));
            // Same-day sell listed BEFORE its buy: the ordering trap.
            writeRow(sheet, 2, row("2026-02-01", "Sell", "ACME.TO", "ACME CORP WE ACTED AS AGENT",
                    "-30", "12.00", "360.00", "0", "360.00", "Trades"));
            writeRow(sheet, 3, row("2026-02-01", "Buy", "ACME.TO", "ACME CORP WE ACTED AS AGENT",
                    "50", "12.00", "600.00", "0", "-600.00", "Trades"));
            writeRow(sheet, 4, row("2026-01-15", "Buy", "ACME.TO", "ACME CORP WE ACTED AS AGENT",
                    "100", "10.00", "1000.00", "0", "-1000.00", "Trades"));
            writeRow(sheet, 5, row("2026-01-10", "CON", "", "CONTRIBUTION",
                    "0", "0", "0", "0", "5000.00", "Deposits"));
            // An opaque internal code for the same issuer, as the real file
            // uses on some rows.
            writeRow(sheet, 6, row("2026-01-05", "", "A012345", "ACME CORP DIST ON 0 SHS",
                    "0", "0", "0", "0", "0.00", "Dividends"));

            wb.write(out);
            return out.toByteArray();
        }
    }

    private String[] row(String date, String action, String symbol, String description,
                         String quantity, String price, String gross, String commission,
                         String net, String activityType) {
        return new String[]{
                date + " 12:00:00 AM", date + " 12:00:00 AM", action, symbol, description,
                quantity, price, gross, commission, net,
                "CAD", "99999999", activityType, "Individual TFSA"
        };
    }

    private void writeRow(Sheet sheet, int index, String[] values) {
        Row row = sheet.createRow(index);
        for (int i = 0; i < values.length; i++) {
            // Written as strings deliberately: the real export stores every
            // value as text, including dates and numbers.
            row.createCell(i).setCellValue(values[i]);
        }
    }
}
