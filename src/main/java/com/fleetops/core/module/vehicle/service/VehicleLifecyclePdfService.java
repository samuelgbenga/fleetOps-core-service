package com.fleetops.core.module.vehicle.service;

import com.fleetops.core.module.activity.model.VehicleActivityLog;
import com.fleetops.core.module.activity.repository.VehicleActivityLogRepository;
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
import com.fleetops.core.shared.context.TenantContext;
import com.fleetops.core.shared.exception.ResourceNotFoundException;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VehicleLifecyclePdfService {

    private final VehicleRepository vehicleRepository;
    private final TripRequestRepository tripRequestRepository;
    private final MileageLogRepository mileageLogRepository;
    private final MaintenanceFlagRepository maintenanceFlagRepository;
    private final BreakdownRepository breakdownRepository;
    private final VehicleActivityLogRepository activityLogRepository;

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");
    private static final DateTimeFormatter D_FMT  = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private static final Color C_NAVY    = new Color(15,  52,  96);
    private static final Color C_BLUE    = new Color(25,  118, 210);
    private static final Color C_SECTION = new Color(227, 242, 253);
    private static final Color C_ALT     = new Color(245, 245, 245);
    private static final Color C_SUCCESS = new Color(200, 230, 201);
    private static final Color C_WARNING = new Color(255, 243, 205);
    private static final Color C_DANGER  = new Color(255, 205, 210);
    private static final Color C_INFO    = new Color(187, 222, 251);
    private static final Color C_ORANGE  = new Color(255, 224, 178);

    private static final Font F_TITLE    = new Font(Font.HELVETICA, 22, Font.BOLD,   Color.WHITE);
    private static final Font F_SUBTITLE = new Font(Font.HELVETICA, 11, Font.NORMAL, new Color(180, 210, 255));
    private static final Font F_PLATE    = new Font(Font.HELVETICA, 26, Font.BOLD,   Color.WHITE);
    private static final Font F_CAR      = new Font(Font.HELVETICA, 13, Font.BOLD,   new Color(180, 210, 255));
    private static final Font F_SECTION  = new Font(Font.HELVETICA, 10, Font.BOLD,   C_NAVY);
    private static final Font F_COL_HDR  = new Font(Font.HELVETICA, 8,  Font.BOLD,   Color.WHITE);
    private static final Font F_BODY     = new Font(Font.HELVETICA, 8,  Font.NORMAL, Color.BLACK);
    private static final Font F_BODY_B   = new Font(Font.HELVETICA, 8,  Font.BOLD,   Color.BLACK);
    private static final Font F_LABEL    = new Font(Font.HELVETICA, 8,  Font.BOLD,   new Color(60, 60, 60));
    private static final Font F_VALUE    = new Font(Font.HELVETICA, 8,  Font.NORMAL, Color.BLACK);
    private static final Font F_EMPTY    = new Font(Font.HELVETICA, 8,  Font.ITALIC, Color.GRAY);

    @Transactional(readOnly = true)
    public byte[] generate(Long vehicleId) {
        Long companyId = TenantContext.getCompanyId();
        Vehicle vehicle = vehicleRepository.findByIdAndCompanyId(vehicleId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + vehicleId));

        List<TripRequest>        trips       = tripRequestRepository.findByVehicleIdAndCompanyId(vehicleId, companyId);
        List<MileageLog>         mileage     = mileageLogRepository.findByVehicleIdAndCompanyId(vehicleId, companyId);
        List<MaintenanceFlag>    maintenance = maintenanceFlagRepository.findByVehicleIdAndCompanyId(vehicleId, companyId);
        List<BreakdownReport>    breakdowns  = breakdownRepository.findByVehicleIdAndCompanyId(vehicleId, companyId);
        List<VehicleActivityLog> activities  = activityLogRepository
                .findByVehicleIdAndCompanyIdOrderByOccurredAtAsc(vehicleId, companyId);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 36, 36, 54, 54);
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(new PageFooterEvent());
            doc.open();

            addCoverHeader(doc, vehicle);
            addVehicleDetails(doc, vehicle);
            addTripsSection(doc, trips);
            addMileageSection(doc, mileage);
            addMaintenanceSection(doc, maintenance);
            addBreakdownSection(doc, breakdowns);
            addActivitySection(doc, activities);

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("PDF generation failed", e);
        }
    }

    // ─── Cover ────────────────────────────────────────────────────────────────

    private void addCoverHeader(Document doc, Vehicle vehicle) throws DocumentException {
        PdfPTable banner = new PdfPTable(1);
        banner.setWidthPercentage(100);
        banner.setSpacingAfter(14);

        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(C_NAVY);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPaddingTop(20);
        cell.setPaddingBottom(20);
        cell.setPaddingLeft(14);
        cell.setPaddingRight(14);

        Paragraph title = new Paragraph("FLEETOPS", F_TITLE);
        title.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(title);

        Paragraph sub = new Paragraph("Vehicle Lifecycle Report", F_SUBTITLE);
        sub.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(sub);

        cell.addElement(new Paragraph(" "));

        Paragraph plate = new Paragraph(vehicle.getPlateNumber(), F_PLATE);
        plate.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(plate);

        Paragraph car = new Paragraph(vehicle.getMake() + "  " + vehicle.getModel(), F_CAR);
        car.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(car);

        cell.addElement(new Paragraph(" "));

        Paragraph ts = new Paragraph("Generated: " + LocalDateTime.now().format(DT_FMT), F_SUBTITLE);
        ts.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(ts);

        banner.addCell(cell);
        doc.add(banner);
    }

    // ─── Vehicle details ──────────────────────────────────────────────────────

    private void addVehicleDetails(Document doc, Vehicle vehicle) throws DocumentException {
        addSectionHeader(doc, "VEHICLE DETAILS");

        PdfPTable t = new PdfPTable(4);
        t.setWidthPercentage(100);
        t.setWidths(new float[]{1.4f, 2f, 1.4f, 2f});
        t.setSpacingAfter(10);

        infoRow(t, "Make",               vehicle.getMake(),
                   "Model",              vehicle.getModel());
        infoRow(t, "Status",             vehicle.getStatus().name(),
                   "Plate Number",       vehicle.getPlateNumber());
        infoRow(t, "Current Mileage",    vehicle.getCurrentMileage() + " km",
                   "Milestone Interval", vehicle.getMilestoneInterval() + " km");
        infoRow(t, "Health Score",       safe(vehicle.getHealthScore()),
                   "Health Grade",       safe(vehicle.getHealthGrade()));
        infoRow(t, "Lifecycle %",        safe(vehicle.getLifecyclePercentage()) + "%",
                   "Breakdown Count",    String.valueOf(vehicle.getBreakdownCount()));
        infoRow(t, "Max Mileage",        vehicle.getMaxMileage() + " km",
                   "Max Trips",          String.valueOf(vehicle.getMaxTrips()));

        doc.add(t);
    }

    // ─── Trips ────────────────────────────────────────────────────────────────

    private void addTripsSection(Document doc, List<TripRequest> trips) throws DocumentException {
        addSectionHeader(doc, "TRIP HISTORY  (" + trips.size() + " trip" + (trips.size() != 1 ? "s" : "") + ")");

        if (trips.isEmpty()) { emptyNote(doc); return; }

        PdfPTable t = new PdfPTable(6);
        t.setWidthPercentage(100);
        t.setWidths(new float[]{0.4f, 2.2f, 1.4f, 1.4f, 1.4f, 2f});
        t.setSpacingAfter(10);

        colHeaders(t, "#", "Destination", "Start", "End", "Status", "Requested By");

        int row = 1;
        for (TripRequest trip : trips) {
            Color bg = tripColor(trip.getStatus().name());
            dataCell(t, String.valueOf(row++),                 bg, F_BODY,   Element.ALIGN_CENTER);
            dataCell(t, trip.getDestination(),                 bg, F_BODY,   Element.ALIGN_LEFT);
            dataCell(t, trip.getStartDate().format(D_FMT),     bg, F_BODY,   Element.ALIGN_CENTER);
            dataCell(t, trip.getEndDate().format(D_FMT),       bg, F_BODY,   Element.ALIGN_CENTER);
            dataCell(t, trip.getStatus().name(),               bg, F_BODY_B, Element.ALIGN_CENTER);
            dataCell(t, trip.getRequestedBy().getName(),       bg, F_BODY,   Element.ALIGN_LEFT);
        }
        doc.add(t);
    }

    // ─── Mileage ──────────────────────────────────────────────────────────────

    private void addMileageSection(Document doc, List<MileageLog> logs) throws DocumentException {
        addSectionHeader(doc, "MILEAGE LOG  (" + logs.size() + " entr" + (logs.size() != 1 ? "ies" : "y") + ")");

        if (logs.isEmpty()) { emptyNote(doc); return; }

        PdfPTable t = new PdfPTable(4);
        t.setWidthPercentage(100);
        t.setWidths(new float[]{0.4f, 2.2f, 2f, 2f});
        t.setSpacingAfter(10);

        colHeaders(t, "#", "Date Logged", "Reported Mileage (km)", "Submitted By");

        int row = 1;
        for (MileageLog log : logs) {
            Color bg = (row % 2 == 0) ? C_ALT : Color.WHITE;
            dataCell(t, String.valueOf(row++),                  bg, F_BODY,   Element.ALIGN_CENTER);
            dataCell(t, log.getLoggedAt().format(DT_FMT),       bg, F_BODY,   Element.ALIGN_LEFT);
            dataCell(t, log.getReportedMileage() + " km",       bg, F_BODY_B, Element.ALIGN_RIGHT);
            dataCell(t, log.getSubmittedBy().getName(),          bg, F_BODY,   Element.ALIGN_LEFT);
        }
        doc.add(t);
    }

    // ─── Maintenance ──────────────────────────────────────────────────────────

    private void addMaintenanceSection(Document doc, List<MaintenanceFlag> flags) throws DocumentException {
        addSectionHeader(doc, "MAINTENANCE FLAGS  (" + flags.size() + " flag" + (flags.size() != 1 ? "s" : "") + ")");

        if (flags.isEmpty()) { emptyNote(doc); return; }

        PdfPTable t = new PdfPTable(5);
        t.setWidthPercentage(100);
        t.setWidths(new float[]{0.4f, 1.4f, 1.8f, 1.8f, 3.2f});
        t.setSpacingAfter(10);

        colHeaders(t, "#", "Trigger", "Status", "Opened", "Description");

        int row = 1;
        for (MaintenanceFlag flag : flags) {
            Color bg = maintenanceColor(flag.getStatus().name());
            dataCell(t, String.valueOf(row++),              bg, F_BODY,   Element.ALIGN_CENTER);
            dataCell(t, flag.getTriggerType().name(),       bg, F_BODY,   Element.ALIGN_LEFT);
            dataCell(t, flag.getStatus().name(),            bg, F_BODY_B, Element.ALIGN_LEFT);
            dataCell(t, flag.getOpenedAt().format(D_FMT),   bg, F_BODY,   Element.ALIGN_LEFT);
            dataCell(t, nvl(flag.getDescription(), "—"),    bg, F_BODY,   Element.ALIGN_LEFT);
        }
        doc.add(t);
    }

    // ─── Breakdowns ───────────────────────────────────────────────────────────

    private void addBreakdownSection(Document doc, List<BreakdownReport> breakdowns) throws DocumentException {
        addSectionHeader(doc, "BREAKDOWN REPORTS  (" + breakdowns.size() + ")");

        if (breakdowns.isEmpty()) { emptyNote(doc); return; }

        PdfPTable t = new PdfPTable(5);
        t.setWidthPercentage(100);
        t.setWidths(new float[]{0.4f, 1.5f, 1.8f, 1.8f, 3.1f});
        t.setSpacingAfter(10);

        colHeaders(t, "#", "Status", "Reported At", "Resolved At", "Location");

        int row = 1;
        for (BreakdownReport b : breakdowns) {
            Color bg = "RESOLVED".equals(b.getStatus().name()) ? C_SUCCESS : C_DANGER;
            dataCell(t, String.valueOf(row++),                                              bg, F_BODY,   Element.ALIGN_CENTER);
            dataCell(t, b.getStatus().name(),                                               bg, F_BODY_B, Element.ALIGN_LEFT);
            dataCell(t, b.getReportedAt().format(D_FMT),                                    bg, F_BODY,   Element.ALIGN_LEFT);
            dataCell(t, b.getResolvedAt() != null ? b.getResolvedAt().format(D_FMT) : "—", bg, F_BODY,   Element.ALIGN_LEFT);
            dataCell(t, nvl(b.getLocationDescription(), "—"),                               bg, F_BODY,   Element.ALIGN_LEFT);
        }
        doc.add(t);
    }

    // ─── Activity timeline ────────────────────────────────────────────────────

    private void addActivitySection(Document doc, List<VehicleActivityLog> logs) throws DocumentException {
        addSectionHeader(doc, "ACTIVITY TIMELINE  (" + logs.size() + " event" + (logs.size() != 1 ? "s" : "") + ")");

        if (logs.isEmpty()) { emptyNote(doc); return; }

        PdfPTable t = new PdfPTable(5);
        t.setWidthPercentage(100);
        t.setWidths(new float[]{0.4f, 2f, 1.8f, 1.6f, 3f});
        t.setSpacingAfter(10);

        colHeaders(t, "#", "Date/Time", "Event Type", "Actor", "Description");

        int row = 1;
        for (VehicleActivityLog log : logs) {
            Color bg = activityColor(log.getEventType());
            String actor = nvl(log.getActorName(), "—") + " (" + nvl(log.getActorRole(), "—") + ")";
            dataCell(t, String.valueOf(row++),               bg, F_BODY,   Element.ALIGN_CENTER);
            dataCell(t, log.getOccurredAt().format(DT_FMT),  bg, F_BODY,   Element.ALIGN_LEFT);
            dataCell(t, log.getEventType(),                  bg, F_BODY_B, Element.ALIGN_LEFT);
            dataCell(t, actor,                               bg, F_BODY,   Element.ALIGN_LEFT);
            dataCell(t, nvl(log.getDescription(), "—"),      bg, F_BODY,   Element.ALIGN_LEFT);
        }
        doc.add(t);
    }

    // ─── Shared table helpers ─────────────────────────────────────────────────

    private void addSectionHeader(Document doc, String label) throws DocumentException {
        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(100);
        t.setSpacingBefore(10);
        t.setSpacingAfter(4);

        PdfPCell cell = new PdfPCell(new Phrase(label, F_SECTION));
        cell.setBackgroundColor(C_SECTION);
        cell.setBorderColor(C_BLUE);
        cell.setBorderWidth(0.5f);
        cell.setPadding(6);
        t.addCell(cell);
        doc.add(t);
    }

    private void emptyNote(Document doc) throws DocumentException {
        Paragraph p = new Paragraph("  No records found.", F_EMPTY);
        p.setSpacingAfter(8);
        doc.add(p);
    }

    private void colHeaders(PdfPTable t, String... headers) {
        for (String h : headers) {
            PdfPCell c = new PdfPCell(new Phrase(h, F_COL_HDR));
            c.setBackgroundColor(C_BLUE);
            c.setBorder(Rectangle.NO_BORDER);
            c.setPadding(5);
            c.setHorizontalAlignment(Element.ALIGN_CENTER);
            t.addCell(c);
        }
    }

    private void dataCell(PdfPTable t, String text, Color bg, Font font, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text != null ? text : "—", font));
        c.setBackgroundColor(bg);
        c.setBorderColor(new Color(220, 220, 220));
        c.setBorderWidth(0.3f);
        c.setPadding(4);
        c.setHorizontalAlignment(align);
        t.addCell(c);
    }

    private void infoRow(PdfPTable t, String lbl1, String val1, String lbl2, String val2) {
        PdfPCell l1 = new PdfPCell(new Phrase(lbl1, F_LABEL));
        l1.setBackgroundColor(C_ALT);
        l1.setBorderColor(new Color(220, 220, 220));
        l1.setBorderWidth(0.3f);
        l1.setPadding(5);
        t.addCell(l1);

        PdfPCell v1 = new PdfPCell(new Phrase(val1 != null ? val1 : "—", F_VALUE));
        v1.setBorderColor(new Color(220, 220, 220));
        v1.setBorderWidth(0.3f);
        v1.setPadding(5);
        t.addCell(v1);

        PdfPCell l2 = new PdfPCell(new Phrase(lbl2, F_LABEL));
        l2.setBackgroundColor(C_ALT);
        l2.setBorderColor(new Color(220, 220, 220));
        l2.setBorderWidth(0.3f);
        l2.setPadding(5);
        t.addCell(l2);

        PdfPCell v2 = new PdfPCell(new Phrase(val2 != null ? val2 : "—", F_VALUE));
        v2.setBorderColor(new Color(220, 220, 220));
        v2.setBorderWidth(0.3f);
        v2.setPadding(5);
        t.addCell(v2);
    }

    // ─── Status colours ───────────────────────────────────────────────────────

    private Color tripColor(String status) {
        return switch (status) {
            case "APPROVED"  -> C_SUCCESS;
            case "COMPLETED" -> C_INFO;
            case "REJECTED"  -> C_DANGER;
            default          -> C_WARNING;
        };
    }

    private Color maintenanceColor(String status) {
        return switch (status) {
            case "RESOLVED"                       -> C_SUCCESS;
            case "IN_PROGRESS", "QUOTE_APPROVED"  -> C_INFO;
            case "OPEN"                           -> C_DANGER;
            default                               -> C_ORANGE;
        };
    }

    private Color activityColor(String type) {
        if (type == null) return Color.WHITE;
        if (type.contains("COMPLETED"))  return C_SUCCESS;
        if (type.contains("APPROVED"))   return C_INFO;
        if (type.contains("REJECTED"))   return C_DANGER;
        if (type.contains("MAINTENANCE") || type.contains("QUOTATION")) return C_ORANGE;
        if (type.contains("BREAKDOWN"))  return C_DANGER;
        return Color.WHITE;
    }

    // ─── Utilities ────────────────────────────────────────────────────────────

    private String safe(Object val) {
        return val == null ? "N/A" : String.valueOf(val);
    }

    private String nvl(String val, String fallback) {
        return (val == null || val.isBlank()) ? fallback : val;
    }

    // ─── Page footer ──────────────────────────────────────────────────────────

    static class PageFooterEvent extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            Font f = new Font(Font.HELVETICA, 7, Font.NORMAL, Color.GRAY);
            Phrase footer = new Phrase(
                    "Page " + writer.getPageNumber() +
                    "   |   FleetOps Vehicle Lifecycle Report   |   Confidential", f);
            ColumnText.showTextAligned(cb, Element.ALIGN_CENTER, footer,
                    (document.left() + document.right()) / 2f,
                    document.bottom() - 12, 0);
        }
    }
}
