package com.fleetops.core.module.vehicle.service.impl;

import com.fleetops.core.module.breakdown.model.BreakdownReport;
import com.fleetops.core.module.breakdown.repository.BreakdownRepository;
import com.fleetops.core.module.maintenance.model.MaintenanceFlag;
import com.fleetops.core.module.maintenance.repository.MaintenanceFlagRepository;
import com.fleetops.core.module.mileage.model.MileageLog;
import com.fleetops.core.module.mileage.repository.MileageLogRepository;
import com.fleetops.core.module.triprequest.model.TripRequest;
import com.fleetops.core.module.triprequest.repository.TripRequestRepository;
import com.fleetops.core.module.vehicle.model.Vehicle;
import com.fleetops.core.module.vehicle.repository.VehicleRepository;
import com.fleetops.core.module.vehicle.service.VehicleReportService;
import com.fleetops.core.shared.context.TenantContext;
import com.fleetops.core.shared.exception.ResourceNotFoundException;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VehicleReportServiceImpl implements VehicleReportService {

    private final VehicleRepository vehicleRepository;
    private final TripRequestRepository tripRequestRepository;
    private final MaintenanceFlagRepository maintenanceFlagRepository;
    private final MileageLogRepository mileageLogRepository;
    private final BreakdownRepository breakdownRepository;

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private static final Color HEADER_BG  = new Color(30, 58, 138);   // deep blue
    private static final Color SECTION_BG = new Color(239, 246, 255); // light blue
    private static final Color ALT_ROW    = new Color(248, 250, 252); // off-white

    private static final Font TITLE_FONT   = new Font(Font.HELVETICA, 20, Font.BOLD, Color.WHITE);
    private static final Font SUBTITLE_FONT = new Font(Font.HELVETICA, 11, Font.NORMAL, Color.WHITE);
    private static final Font SECTION_FONT = new Font(Font.HELVETICA, 12, Font.BOLD, new Color(30, 58, 138));
    private static final Font LABEL_FONT   = new Font(Font.HELVETICA, 9, Font.BOLD, Color.DARK_GRAY);
    private static final Font VALUE_FONT   = new Font(Font.HELVETICA, 9, Font.NORMAL, Color.BLACK);
    private static final Font TH_FONT      = new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE);
    private static final Font TD_FONT      = new Font(Font.HELVETICA, 9, Font.NORMAL, Color.BLACK);

    @Override
    @Transactional(readOnly = true)
    public byte[] generateVehiclePdf(Long vehicleId) {
        Long companyId = TenantContext.getCompanyId();

        Vehicle vehicle = vehicleRepository.findByIdAndCompanyId(vehicleId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found"));

        List<TripRequest>     trips       = tripRequestRepository.findByVehicleIdAndCompanyId(vehicleId, companyId);
        List<MaintenanceFlag> flags       = maintenanceFlagRepository.findByVehicleIdAndCompanyId(vehicleId, companyId);
        List<MileageLog>      mileageLogs = mileageLogRepository.findByVehicleIdAndCompanyId(vehicleId, companyId);
        List<BreakdownReport> breakdowns  = breakdownRepository.findByVehicleIdAndCompanyId(vehicleId, companyId);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 40, 40, 40, 40);
            PdfWriter.getInstance(doc, out);
            doc.open();

            addCoverHeader(doc, vehicle);
            addVehicleDetails(doc, vehicle);
            addTripSection(doc, trips);
            addMaintenanceSection(doc, flags);
            addMileageSection(doc, mileageLogs);
            addBreakdownSection(doc, breakdowns);

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate vehicle PDF report", e);
        }
    }

    // ── Cover / header ────────────────────────────────────────────────────────

    private void addCoverHeader(Document doc, Vehicle v) throws DocumentException {
        PdfPTable banner = new PdfPTable(1);
        banner.setWidthPercentage(100);

        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(HEADER_BG);
        cell.setPadding(20);
        cell.setBorder(Rectangle.NO_BORDER);

        cell.addElement(new Paragraph("Vehicle Report", TITLE_FONT));
        cell.addElement(new Paragraph(v.getMake() + " " + v.getModel() + "  ·  " + v.getPlateNumber(), SUBTITLE_FONT));
        cell.addElement(new Paragraph("Generated: " + java.time.LocalDateTime.now().format(DT), SUBTITLE_FONT));

        banner.addCell(cell);
        doc.add(banner);
        doc.add(Chunk.NEWLINE);
    }

    // ── Vehicle details ───────────────────────────────────────────────────────

    private void addVehicleDetails(Document doc, Vehicle v) throws DocumentException {
        doc.add(sectionTitle("Vehicle Details"));

        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1.8f, 2.2f, 1.8f, 2.2f});
        table.setSpacingAfter(12);

        addDetailRow(table, "Make", v.getMake(), "Model", v.getModel());
        addDetailRow(table, "Plate Number", v.getPlateNumber(), "Status", v.getStatus().name());
        addDetailRow(table, "Health Score",
                v.getHealthScore() != null ? String.format("%.1f / 100", v.getHealthScore()) : "—",
                "Health Grade", v.getHealthGrade() != null ? v.getHealthGrade() : "—");
        addDetailRow(table, "Current Mileage",
                String.format("%.0f km", v.getCurrentMileage()),
                "Max Mileage", String.format("%.0f km", v.getMaxMileage()));
        addDetailRow(table, "Purchase Date",
                v.getPurchaseDate() != null ? v.getPurchaseDate().format(DATE) : "—",
                "Purchase Price",
                v.getPurchasePrice() != null ? "₦" + v.getPurchasePrice().toPlainString() : "—");
        addDetailRow(table, "Total Maintenance Spend",
                "₦" + v.getTotalMaintenanceSpend().toPlainString(),
                "Breakdown Count", String.valueOf(v.getBreakdownCount()));
        addDetailRow(table, "Lifecycle %",
                String.format("%.1f%%", v.getLifecyclePercentage()),
                "Marked for Sale", Boolean.TRUE.equals(v.getMarkedForSale()) ? "Yes" : "No");
        if (v.getSellRecommendation() != null) {
            addDetailRow(table, "Sell Recommendation", v.getSellRecommendation(), "", "");
        }

        doc.add(table);
    }

    // ── Trip requests ─────────────────────────────────────────────────────────

    private void addTripSection(Document doc, List<TripRequest> trips) throws DocumentException {
        doc.add(sectionTitle("Trip History  (" + trips.size() + " trips)"));

        if (trips.isEmpty()) {
            doc.add(emptyNote("No trip records found for this vehicle."));
            return;
        }

        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1f, 2.5f, 1.5f, 1.5f, 1.5f});
        table.setSpacingAfter(12);

        addTableHeader(table, "#", "Destination", "Status", "Start Date", "End Date");

        int i = 1;
        for (TripRequest t : trips) {
            Color bg = (i % 2 == 0) ? ALT_ROW : Color.WHITE;
            addTableRow(table, bg,
                    String.valueOf(i++),
                    t.getDestination(),
                    t.getStatus().name(),
                    t.getStartDate() != null ? t.getStartDate().format(DATE) : "—",
                    t.getEndDate()   != null ? t.getEndDate().format(DATE)   : "—");
        }
        doc.add(table);
    }

    // ── Maintenance flags ─────────────────────────────────────────────────────

    private void addMaintenanceSection(Document doc, List<MaintenanceFlag> flags) throws DocumentException {
        doc.add(sectionTitle("Maintenance History  (" + flags.size() + " records)"));

        if (flags.isEmpty()) {
            doc.add(emptyNote("No maintenance records found for this vehicle."));
            return;
        }

        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1f, 2f, 1.5f, 2.5f});
        table.setSpacingAfter(12);

        addTableHeader(table, "#", "Trigger Type", "Status", "Description");

        int i = 1;
        for (MaintenanceFlag f : flags) {
            Color bg = (i % 2 == 0) ? ALT_ROW : Color.WHITE;
            addTableRow(table, bg,
                    String.valueOf(i++),
                    f.getTriggerType() != null ? f.getTriggerType().name() : "—",
                    f.getStatus().name(),
                    f.getDescription() != null ? f.getDescription() : "—");
        }
        doc.add(table);
    }

    // ── Mileage logs ──────────────────────────────────────────────────────────

    private void addMileageSection(Document doc, List<MileageLog> logs) throws DocumentException {
        doc.add(sectionTitle("Mileage Logs  (" + logs.size() + " entries)"));

        if (logs.isEmpty()) {
            doc.add(emptyNote("No mileage logs found for this vehicle."));
            return;
        }

        PdfPTable table = new PdfPTable(3);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1f, 3f, 3f});
        table.setSpacingAfter(12);

        addTableHeader(table, "#", "Reported Mileage (km)", "Logged At");

        int i = 1;
        for (MileageLog m : logs) {
            Color bg = (i % 2 == 0) ? ALT_ROW : Color.WHITE;
            addTableRow(table, bg,
                    String.valueOf(i++),
                    String.format("%.0f", m.getReportedMileage()),
                    m.getLoggedAt() != null ? m.getLoggedAt().format(DT) : "—");
        }
        doc.add(table);
    }

    // ── Breakdowns ────────────────────────────────────────────────────────────

    private void addBreakdownSection(Document doc, List<BreakdownReport> breakdowns) throws DocumentException {
        doc.add(sectionTitle("Breakdown Reports  (" + breakdowns.size() + " incidents)"));

        if (breakdowns.isEmpty()) {
            doc.add(emptyNote("No breakdown reports for this vehicle."));
            return;
        }

        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1f, 1.5f, 2f, 3f});
        table.setSpacingAfter(12);

        addTableHeader(table, "#", "Status", "Reported At", "Description");

        int i = 1;
        for (BreakdownReport b : breakdowns) {
            Color bg = (i % 2 == 0) ? ALT_ROW : Color.WHITE;
            addTableRow(table, bg,
                    String.valueOf(i++),
                    b.getStatus() != null ? b.getStatus().name() : "—",
                    b.getReportedAt() != null ? b.getReportedAt().format(DT) : "—",
                    b.getDescription() != null ? b.getDescription() : "—");
        }
        doc.add(table);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Paragraph sectionTitle(String text) {
        Paragraph p = new Paragraph(text, SECTION_FONT);
        p.setSpacingBefore(14);
        p.setSpacingAfter(6);
        return p;
    }

    private Paragraph emptyNote(String text) {
        Font italic = new Font(Font.HELVETICA, 9, Font.ITALIC, Color.GRAY);
        Paragraph p = new Paragraph(text, italic);
        p.setSpacingAfter(10);
        return p;
    }

    private void addDetailRow(PdfPTable t, String l1, String v1, String l2, String v2) {
        addLabelCell(t, l1);
        addValueCell(t, v1);
        addLabelCell(t, l2);
        addValueCell(t, v2);
    }

    private void addLabelCell(PdfPTable t, String text) {
        PdfPCell c = new PdfPCell(new Phrase(text, LABEL_FONT));
        c.setBackgroundColor(SECTION_BG);
        c.setPadding(5);
        c.setBorderColor(Color.LIGHT_GRAY);
        t.addCell(c);
    }

    private void addValueCell(PdfPTable t, String text) {
        PdfPCell c = new PdfPCell(new Phrase(text, VALUE_FONT));
        c.setPadding(5);
        c.setBorderColor(Color.LIGHT_GRAY);
        t.addCell(c);
    }

    private void addTableHeader(PdfPTable table, String... headers) {
        for (String h : headers) {
            PdfPCell c = new PdfPCell(new Phrase(h, TH_FONT));
            c.setBackgroundColor(HEADER_BG);
            c.setPadding(6);
            c.setHorizontalAlignment(Element.ALIGN_LEFT);
            c.setBorder(Rectangle.NO_BORDER);
            table.addCell(c);
        }
    }

    private void addTableRow(PdfPTable table, Color bg, String... values) {
        for (String v : values) {
            PdfPCell c = new PdfPCell(new Phrase(v, TD_FONT));
            c.setBackgroundColor(bg);
            c.setPadding(5);
            c.setBorderColor(Color.LIGHT_GRAY);
            table.addCell(c);
        }
    }
}
