package com.daaradji.budget.transaction;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.daaradji.budget.category.CategoryType;
import com.daaradji.budget.common.BusinessException;
import com.daaradji.budget.transaction.TransactionDtos.TransactionFilter;
import com.daaradji.budget.transaction.TransactionDtos.TransactionResponse;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

/** Relevé PDF des dépenses d'une semaine, d'un mois ou d'une année. */
@Service
public class ExpenseReportService {

    public enum ReportPeriod { WEEK, MONTH, YEAR }

    public record Report(String fileName, byte[] content) {
    }

    private record Range(LocalDate from, LocalDate to, String label, String slug) {
    }

    private static final Locale FR = Locale.FRENCH;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter LONG_DAY = DateTimeFormatter.ofPattern("d MMMM yyyy", FR);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", FR);
    private static final Color ACCENT = new Color(0x0d, 0x6e, 0xfd);
    private static final Color HEADER_BG = new Color(0xe9, 0xf1, 0xff);
    private static final Color ZEBRA = new Color(0xf8, 0xf9, 0xfa);

    private final TransactionService transactions;

    public ExpenseReportService(TransactionService transactions) {
        this.transactions = transactions;
    }

    @Transactional(readOnly = true)
    public Report expenses(Long userId, ReportPeriod period, LocalDate date) {
        Range range = range(period, date == null ? LocalDate.now() : date);
        List<TransactionResponse> rows = transactions.export(userId,
                new TransactionFilter(range.from(), range.to(), CategoryType.EXPENSE, null, null, null));
        return new Report("depenses-" + range.slug() + ".pdf", render(range, rows));
    }

    private static Range range(ReportPeriod period, LocalDate d) {
        return switch (period) {
            case WEEK -> {
                LocalDate from = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                LocalDate to = from.plusDays(6);
                yield new Range(from, to, "Semaine du " + from.format(LONG_DAY) + " au " + to.format(LONG_DAY),
                        "semaine-" + from);
            }
            case MONTH -> {
                LocalDate from = d.withDayOfMonth(1);
                yield new Range(from, d.with(TemporalAdjusters.lastDayOfMonth()), capitalize(from.format(MONTH)),
                        from.toString().substring(0, 7));
            }
            case YEAR -> new Range(d.withDayOfYear(1), d.with(TemporalAdjusters.lastDayOfYear()),
                    "Année " + d.getYear(), String.valueOf(d.getYear()));
        };
    }

    private static byte[] render(Range range, List<TransactionResponse> rows) {
        Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, ACCENT);
        Font subtitle = FontFactory.getFont(FontFactory.HELVETICA, 11, Color.DARK_GRAY);
        Font section = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.BLACK);
        Font head = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.BLACK);
        Font cell = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);
        Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.BLACK);
        Font small = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);

        BigDecimal total = rows.stream().map(TransactionResponse::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, BigDecimal> byCategory = new LinkedHashMap<>();
        Map<String, BigDecimal> byOperator = new LinkedHashMap<>();
        for (TransactionResponse t : rows) {
            byCategory.merge(t.category().name(), t.amount(), BigDecimal::add);
            if (t.operator() != null) {
                byOperator.merge(t.operator().label(), t.amount(), BigDecimal::add);
            }
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 40, 40);
        try {
            PdfWriter.getInstance(doc, out);
            doc.addTitle("Dépenses - " + range.label());
            doc.open();

            doc.add(new Paragraph("Relevé des dépenses", title));
            doc.add(new Paragraph(range.label() + "  (" + range.from().format(DAY) + " - " + range.to().format(DAY) + ")",
                    subtitle));
            Paragraph summary = new Paragraph("Total : " + money(total) + "   ·   " + rows.size() + " opération"
                    + (rows.size() > 1 ? "s" : ""), bold);
            summary.setSpacingBefore(10);
            summary.setSpacingAfter(12);
            doc.add(summary);

            if (rows.isEmpty()) {
                doc.add(new Paragraph("Aucune dépense sur cette période.", subtitle));
            } else {
                doc.add(heading("Répartition par catégorie", section));
                doc.add(breakdown(sortDesc(byCategory), total, head, cell, bold));
                if (!byOperator.isEmpty()) {
                    doc.add(heading("Crédit par opérateur", section));
                    doc.add(breakdown(sortDesc(byOperator), byOperator.values().stream()
                            .reduce(BigDecimal.ZERO, BigDecimal::add), head, cell, bold));
                }
                doc.add(heading("Détail des opérations", section));
                doc.add(detail(rows, total, head, cell, bold));
            }

            Paragraph footer = new Paragraph("Généré le " + LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm")) + " · Kit budgétaire", small);
            footer.setSpacingBefore(14);
            doc.add(footer);
        } catch (Exception e) {
            throw new BusinessException("Impossible de générer le PDF : " + e.getMessage());
        } finally {
            if (doc.isOpen()) {
                doc.close();
            }
        }
        return out.toByteArray();
    }

    private static Paragraph heading(String text, Font font) {
        Paragraph p = new Paragraph(text, font);
        p.setSpacingBefore(8);
        p.setSpacingAfter(6);
        return p;
    }

    private static PdfPTable breakdown(Map<String, BigDecimal> totals, BigDecimal total, Font head, Font cell,
            Font bold) {
        PdfPTable table = new PdfPTable(new float[] { 5, 3, 2 });
        table.setWidthPercentage(70);
        table.setHorizontalAlignment(Element.ALIGN_LEFT);
        table.setHeaderRows(1);
        addHead(table, head, "Libellé", "Montant", "Part");
        int i = 0;
        for (Map.Entry<String, BigDecimal> e : totals.entrySet()) {
            Color bg = i++ % 2 == 1 ? ZEBRA : null;
            table.addCell(cell(e.getKey(), cell, Element.ALIGN_LEFT, bg));
            table.addCell(cell(money(e.getValue()), cell, Element.ALIGN_RIGHT, bg));
            table.addCell(cell(percent(e.getValue(), total), cell, Element.ALIGN_RIGHT, bg));
        }
        table.addCell(cell("Total", bold, Element.ALIGN_LEFT, HEADER_BG));
        table.addCell(cell(money(total), bold, Element.ALIGN_RIGHT, HEADER_BG));
        table.addCell(cell("100 %", bold, Element.ALIGN_RIGHT, HEADER_BG));
        return table;
    }

    private static PdfPTable detail(List<TransactionResponse> rows, BigDecimal total, Font head, Font cell,
            Font bold) {
        PdfPTable table = new PdfPTable(new float[] { 2.2f, 3.2f, 2.2f, 2.6f, 4.8f, 2.8f });
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        addHead(table, head, "Date", "Catégorie", "Opérateur", "Compte", "Description", "Montant");
        int i = 0;
        for (TransactionResponse t : rows) {
            Color bg = i++ % 2 == 1 ? ZEBRA : null;
            table.addCell(cell(t.date().format(DAY), cell, Element.ALIGN_LEFT, bg));
            table.addCell(cell(t.category().name(), cell, Element.ALIGN_LEFT, bg));
            table.addCell(cell(t.operator() == null ? "" : t.operator().label(), cell, Element.ALIGN_LEFT, bg));
            table.addCell(cell(t.account().name(), cell, Element.ALIGN_LEFT, bg));
            table.addCell(cell(t.description() == null ? "" : t.description(), cell, Element.ALIGN_LEFT, bg));
            table.addCell(cell(money(t.amount()), cell, Element.ALIGN_RIGHT, bg));
        }
        PdfPCell label = cell("Total", bold, Element.ALIGN_LEFT, HEADER_BG);
        label.setColspan(5);
        table.addCell(label);
        table.addCell(cell(money(total), bold, Element.ALIGN_RIGHT, HEADER_BG));
        return table;
    }

    private static void addHead(PdfPTable table, Font font, String... labels) {
        for (int k = 0; k < labels.length; k++) {
            table.addCell(cell(labels[k], font, k == labels.length - 1 || labels[k].equals("Montant")
                    ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT, HEADER_BG));
        }
    }

    private static PdfPCell cell(String text, Font font, int align, Color background) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setHorizontalAlignment(align);
        c.setPadding(4);
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(new Color(0xde, 0xe2, 0xe6));
        if (background != null) {
            c.setBackgroundColor(background);
        }
        return c;
    }

    private static Map<String, BigDecimal> sortDesc(Map<String, BigDecimal> map) {
        Map<String, BigDecimal> sorted = new LinkedHashMap<>();
        map.entrySet().stream().sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .forEach(e -> sorted.put(e.getKey(), e.getValue()));
        return sorted;
    }

    /** 12500 -> « 12 500 FCFA » (espace simple : les polices PDF standard n'ont pas l'espace fine). */
    static String money(BigDecimal amount) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(FR);
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator(',');
        return new DecimalFormat("#,##0.##", symbols).format(amount) + " FCFA";
    }

    private static String percent(BigDecimal part, BigDecimal total) {
        if (total.signum() == 0) {
            return "0 %";
        }
        return part.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP).stripTrailingZeros()
                .toPlainString().replace('.', ',') + " %";
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
