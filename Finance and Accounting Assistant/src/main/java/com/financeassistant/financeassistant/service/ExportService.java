package com.financeassistant.financeassistant.service;

// PATH: Finance and Accounting Assistant/src/main/java/com/financeassistant/financeassistant/service/ExportService.java

import com.financeassistant.financeassistant.entity.Transaction;
import com.financeassistant.financeassistant.repository.TransactionRepository;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * ExportService
 * Generates PDF and CSV exports of transaction data.
 *
 * PDF uses iText7 (com.itextpdf:kernel, layout, io).
 * CSV uses Apache Commons CSV.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExportService {

    private final TransactionRepository transactionRepository;

    private static final DeviceRgb BRAND_BLUE  = new DeviceRgb(59, 130, 246);
    private static final DeviceRgb DARK_BG     = new DeviceRgb(15, 23, 42);
    private static final DeviceRgb ROW_ALT     = new DeviceRgb(30, 41, 59);
    private static final DeviceRgb HEADER_BG   = new DeviceRgb(30, 64, 175);

    // ── PDF Export ────────────────────────────────────────────────────────────
    public byte[] exportPdf(Long companyId, String companyName, String period) throws IOException {
        List<Transaction> txns = transactionRepository.findByCompanyIdOrderByDateDesc(companyId);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document doc = new Document(pdfDoc);

        // ── Header ────────────────────────────────────────────────────────────
        Paragraph title = new Paragraph("💼 FinanceAI — Transaction Report")
                .setFontSize(20).setBold()
                .setFontColor(BRAND_BLUE)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(4);
        doc.add(title);

        Paragraph subtitle = new Paragraph(companyName + "   |   " +
                "Period: " + period + "   |   Generated: " + LocalDate.now())
                .setFontSize(10)
                .setFontColor(com.itextpdf.kernel.colors.ColorConstants.GRAY)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(20);
        doc.add(subtitle);

        // ── Summary row ───────────────────────────────────────────────────────
        BigDecimal income  = txns.stream().filter(t -> t.getAmount().compareTo(BigDecimal.ZERO) > 0)
                                 .map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expense = txns.stream().filter(t -> t.getAmount().compareTo(BigDecimal.ZERO) < 0)
                                 .map(t -> t.getAmount().abs()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal net = income.subtract(expense);

        Table sumTable = new Table(UnitValue.createPercentArray(new float[]{33, 33, 34}))
                .useAllAvailableWidth().setMarginBottom(20);

        addSummaryCell(sumTable, "Total Income", "₹" + income.toPlainString(), new DeviceRgb(16, 185, 129));
        addSummaryCell(sumTable, "Total Expense", "₹" + expense.toPlainString(), new DeviceRgb(239, 68, 68));
        addSummaryCell(sumTable, "Net Profit", "₹" + net.toPlainString(),
                net.compareTo(BigDecimal.ZERO) >= 0 ? new DeviceRgb(16, 185, 129) : new DeviceRgb(239, 68, 68));
        doc.add(sumTable);

        // ── Transaction table ─────────────────────────────────────────────────
        Table table = new Table(UnitValue.createPercentArray(new float[]{15, 40, 20, 15, 10}))
                .useAllAvailableWidth();

        // Header row
        String[] headers = {"Date", "Description", "Category", "Amount", "Type"};
        for (String h : headers) {
            Cell cell = new Cell().add(new Paragraph(h).setBold().setFontSize(10)
                    .setFontColor(ColorConstants.WHITE))
                    .setBackgroundColor(HEADER_BG).setPadding(6);
            table.addHeaderCell(cell);
        }

        // Data rows
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMM yy");
        boolean alt = false;
        for (Transaction tx : txns) {
            boolean income2 = tx.getAmount().compareTo(BigDecimal.ZERO) > 0;
            DeviceRgb bg = alt ? ROW_ALT : new DeviceRgb(15, 23, 42);
            DeviceRgb amtColor = income2 ? new DeviceRgb(16, 185, 129) : new DeviceRgb(239, 68, 68);

            addCell(table, tx.getDate().format(fmt), bg, ColorConstants.LIGHT_GRAY, 9);
            addCell(table, tx.getDescription(), bg, ColorConstants.LIGHT_GRAY, 9);
            addCell(table, tx.getCategory() != null ? tx.getCategory().getName() : "—", bg, ColorConstants.GRAY, 9);
            Cell amtCell = new Cell().add(new Paragraph(
                    (income2 ? "+" : "−") + "₹" + tx.getAmount().abs().toPlainString())
                    .setFontSize(9).setFontColor(amtColor)).setBackgroundColor(bg).setPadding(4);
            table.addCell(amtCell);
            addCell(table, income2 ? "Income" : "Expense", bg, income2 ? new DeviceRgb(16,185,129) : new DeviceRgb(239,68,68), 9);
            alt = !alt;
        }
        doc.add(table);

        // ── Footer ────────────────────────────────────────────────────────────
        doc.add(new Paragraph("\nGenerated by FinanceAI Assistant — financeai.in")
                .setFontSize(8).setFontColor(ColorConstants.GRAY)
                .setTextAlignment(TextAlignment.CENTER));

        doc.close();
        return baos.toByteArray();
    }

    // ── CSV Export ────────────────────────────────────────────────────────────
    public String exportCsv(Long companyId) throws IOException {
        List<Transaction> txns = transactionRepository.findByCompanyIdOrderByDateDesc(companyId);
        StringWriter sw = new StringWriter();

        try (CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT
                .builder()
                .setHeader("Date", "Description", "Category", "Amount", "Type", "Source")
                .build())) {

            for (Transaction tx : txns) {
                boolean isIncome = tx.getAmount().compareTo(BigDecimal.ZERO) > 0;
                printer.printRecord(
                        tx.getDate().toString(),
                        tx.getDescription(),
                        tx.getCategory() != null ? tx.getCategory().getName() : "",
                        tx.getAmount().toPlainString(),
                        isIncome ? "Income" : "Expense",
                        tx.getSource()
                );
            }
        }
        return sw.toString();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private void addSummaryCell(Table t, String label, String value, DeviceRgb color) {
        Cell c = new Cell()
                .add(new Paragraph(label).setFontSize(9).setFontColor(ColorConstants.GRAY))
                .add(new Paragraph(value).setFontSize(14).setBold().setFontColor(color))
                .setBackgroundColor(ROW_ALT).setPadding(10).setTextAlignment(TextAlignment.CENTER);
        t.addCell(c);
    }

    private void addCell(Table t, String text, DeviceRgb bg, com.itextpdf.kernel.colors.Color fg, float size) {
        t.addCell(new Cell().add(new Paragraph(text).setFontSize(size).setFontColor(fg))
                .setBackgroundColor(bg).setPadding(4));
    }
}
