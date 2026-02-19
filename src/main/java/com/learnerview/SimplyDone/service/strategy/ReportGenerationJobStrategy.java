package com.learnerview.SimplyDone.service.strategy;

import com.learnerview.SimplyDone.model.Job;
import com.learnerview.SimplyDone.model.JobType;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Strategy for executing report generation jobs.
 * Supports: HTML, CSV, JSON, TXT report formats.
 *
 * For PDF/Excel generation, users can extend this class and override
 * generatePdf() or generateExcel() methods by adding appropriate libraries
 * (e.g., iText for PDF, Apache POI for Excel).
 */
@Component
@Slf4j
public class ReportGenerationJobStrategy implements JobExecutionStrategy {

    @Override
    public JobType getSupportedJobType() {
        return JobType.REPORT_GENERATION;
    }

    @Override
    public void execute(Job job) throws Exception {
        log.info("Executing report generation job: {} (ID: {})", job.getMessage(), job.getId());

        validateJob(job);

        Map<String, Object> params = job.getParameters();
        String format = ((String) params.get("format")).toUpperCase();
        String outputPath = (String) params.get("outputPath");
        String reportType = (String) params.getOrDefault("reportType", "GENERIC");

        try {
            switch (format) {
                case "HTML":
                    generateHtmlReport(outputPath, params);
                    break;
                case "CSV":
                    generateCsvReport(outputPath, params);
                    break;
                case "JSON":
                    generateJsonReport(outputPath, params);
                    break;
                case "TXT":
                case "TEXT":
                    generateTextReport(outputPath, params);
                    break;
                case "PDF":
                    generatePdfReport(outputPath, params);
                    break;
                case "EXCEL":
                case "XLSX":
                    generateExcelReport(outputPath, params);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported report format: " + format);
            }

            log.info("Report generated successfully: {} for job: {}", outputPath, job.getId());

        } catch (Exception e) {
            log.error("Report generation failed for job {}: {}", job.getId(), e.getMessage());
            throw new Exception("Report generation failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void validateJob(Job job) throws IllegalArgumentException {
        if (job.getParameters() == null) {
            throw new IllegalArgumentException("Report generation job requires parameters");
        }

        Map<String, Object> params = job.getParameters();
        String format = (String) params.get("format");
        String outputPath = (String) params.get("outputPath");

        if (format == null || format.trim().isEmpty()) {
            throw new IllegalArgumentException("Report 'format' is required");
        }

        if (outputPath == null || outputPath.trim().isEmpty()) {
            throw new IllegalArgumentException("Report 'outputPath' is required");
        }

        // Validate data is provided
        Object data = params.get("data");
        if (data == null) {
            throw new IllegalArgumentException("Report 'data' is required");
        }
    }

    @Override
    public long estimateExecutionTime(Job job) {
        Object data = job.getParameters().get("data");
        String format = (String) job.getParameters().get("format");

        // Estimate based on data size and format complexity
        int dataSize = 0;
        if (data instanceof Collection) {
            dataSize = ((Collection<?>) data).size();
        }

        switch (format.toUpperCase()) {
            case "PDF":
                return Math.max(10, dataSize / 100); // 10s base + 1s per 100 rows
            case "EXCEL":
                return Math.max(8, dataSize / 200);
            case "HTML":
                return Math.max(5, dataSize / 500);
            default:
                return 5;
        }
    }

    private void generateHtmlReport(String outputPath, Map<String, Object> params) throws IOException {
        String title = (String) params.getOrDefault("title", "Report");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) params.get("data");
        @SuppressWarnings("unchecked")
        List<String> columns = (List<String>) params.get("columns");

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"en\">\n");
        html.append("<head>\n");
        html.append("  <meta charset=\"UTF-8\">\n");
        html.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("  <title>").append(escapeHtml(title)).append("</title>\n");
        html.append("  <style>\n");
        html.append(getHtmlStyles());
        html.append("  </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("  <div class=\"container\">\n");
        html.append("    <h1>").append(escapeHtml(title)).append("</h1>\n");
        html.append("    <p class=\"meta\">Generated: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("</p>\n");

        if (data != null && !data.isEmpty()) {
            html.append("    <table>\n");
            html.append("      <thead>\n");
            html.append("        <tr>\n");

            // Auto-detect columns if not provided
            if (columns == null || columns.isEmpty()) {
                columns = new ArrayList<>(data.get(0).keySet());
            }

            for (String column : columns) {
                html.append("          <th>").append(escapeHtml(column)).append("</th>\n");
            }

            html.append("        </tr>\n");
            html.append("      </thead>\n");
            html.append("      <tbody>\n");

            for (Map<String, Object> row : data) {
                html.append("        <tr>\n");
                for (String column : columns) {
                    Object value = row.get(column);
                    html.append("          <td>").append(escapeHtml(String.valueOf(value))).append("</td>\n");
                }
                html.append("        </tr>\n");
            }

            html.append("      </tbody>\n");
            html.append("    </table>\n");
            html.append("    <p class=\"footer\">Total rows: ").append(data.size()).append("</p>\n");
        } else {
            html.append("    <p class=\"no-data\">No data available</p>\n");
        }

        html.append("  </div>\n");
        html.append("</body>\n");
        html.append("</html>\n");

        Files.writeString(Paths.get(outputPath), html.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        log.debug("HTML report generated: {}", outputPath);
    }

    private void generateCsvReport(String outputPath, Map<String, Object> params) throws IOException {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) params.get("data");
        @SuppressWarnings("unchecked")
        List<String> columns = (List<String>) params.get("columns");

        if (data == null || data.isEmpty()) {
            Files.writeString(Paths.get(outputPath), "", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return;
        }

        // Auto-detect columns
        if (columns == null || columns.isEmpty()) {
            columns = new ArrayList<>(data.get(0).keySet());
        }

        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputPath),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {

            // Write header
            writer.write(columns.stream()
                    .map(this::escapeCsv)
                    .collect(Collectors.joining(",")));
            writer.newLine();

            // Write data rows
            for (Map<String, Object> row : data) {
                List<String> values = new ArrayList<>();
                for (String column : columns) {
                    Object value = row.get(column);
                    values.add(escapeCsv(String.valueOf(value)));
                }
                writer.write(String.join(",", values));
                writer.newLine();
            }
        }

        log.debug("CSV report generated: {}", outputPath);
    }

    private void generateJsonReport(String outputPath, Map<String, Object> params) throws IOException {
        Map<String, Object> report = new HashMap<>();
        report.put("title", params.getOrDefault("title", "Report"));
        report.put("generatedAt", LocalDateTime.now().toString());
        report.put("data", params.get("data"));

        // Simple JSON serialization (for production, use Jackson)
        String json = convertToJson(report);

        Files.writeString(Paths.get(outputPath), json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        log.debug("JSON report generated: {}", outputPath);
    }

    private void generateTextReport(String outputPath, Map<String, Object> params) throws IOException {
        String title = (String) params.getOrDefault("title", "Report");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) params.get("data");

        StringBuilder text = new StringBuilder();
        text.append("=".repeat(60)).append("\n");
        text.append(title).append("\n");
        text.append("Generated: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
        text.append("=".repeat(60)).append("\n\n");

        if (data != null && !data.isEmpty()) {
            for (int i = 0; i < data.size(); i++) {
                Map<String, Object> row = data.get(i);
                text.append("Record ").append(i + 1).append(":\n");
                for (Map.Entry<String, Object> entry : row.entrySet()) {
                    text.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                }
                text.append("\n");
            }
            text.append("Total records: ").append(data.size()).append("\n");
        } else {
            text.append("No data available\n");
        }

        Files.writeString(Paths.get(outputPath), text.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        log.debug("Text report generated: {}", outputPath);
    }

    /**
     * PDF generation stub. Users can override this method and add iText or Apache PDFBox dependency.
     * Example with iText:
     * <code>
     * Document document = new Document();
     * PdfWriter.getInstance(document, new FileOutputStream(outputPath));
     * document.open();
     * document.add(new Paragraph(title));
     * // Add table data...
     * document.close();
     * </code>
     */
    protected void generatePdfReport(String outputPath, Map<String, Object> params) throws Exception {
        log.warn("PDF generation not implemented. To enable PDF reports, add iText dependency and override generatePdfReport()");
        log.info("Generating HTML report as fallback for PDF request");

        // Generate HTML as fallback
        String htmlPath = outputPath.replace(".pdf", ".html");
        generateHtmlReport(htmlPath, params);

        throw new UnsupportedOperationException(
            "PDF generation requires additional library. " +
            "Add 'com.itextpdf:itext7-core:7.2.5' to pom.xml and override generatePdfReport(). " +
            "HTML report generated as fallback: " + htmlPath
        );
    }

    /**
     * Excel generation stub. Users can override this method and add Apache POI dependency.
     * Example with Apache POI:
     * <code>
     * Workbook workbook = new XSSFWorkbook();
     * Sheet sheet = workbook.createSheet("Report");
     * // Create rows and cells...
     * workbook.write(new FileOutputStream(outputPath));
     * workbook.close();
     * </code>
     */
    protected void generateExcelReport(String outputPath, Map<String, Object> params) throws Exception {
        log.warn("Excel generation not implemented. To enable Excel reports, add Apache POI dependency and override generateExcelReport()");
        log.info("Generating CSV report as fallback for Excel request");

        // Generate CSV as fallback
        String csvPath = outputPath.replace(".xlsx", ".csv");
        generateCsvReport(csvPath, params);

        throw new UnsupportedOperationException(
            "Excel generation requires additional library. " +
            "Add 'org.apache.poi:poi-ooxml:5.2.3' to pom.xml and override generateExcelReport(). " +
            "CSV report generated as fallback: " + csvPath
        );
    }

    private String getHtmlStyles() {
        return """
            body {
              font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;
              background: #f5f5f5;
              margin: 0;
              padding: 20px;
            }
            .container {
              max-width: 1200px;
              margin: 0 auto;
              background: white;
              padding: 30px;
              border-radius: 8px;
              box-shadow: 0 2px 4px rgba(0,0,0,0.1);
            }
            h1 {
              color: #333;
              border-bottom: 3px solid #007bff;
              padding-bottom: 10px;
            }
            .meta {
              color: #666;
              font-size: 14px;
              margin-bottom: 20px;
            }
            table {
              width: 100%;
              border-collapse: collapse;
              margin: 20px 0;
            }
            th {
              background: #007bff;
              color: white;
              padding: 12px;
              text-align: left;
              font-weight: 600;
            }
            td {
              padding: 10px 12px;
              border-bottom: 1px solid #eee;
            }
            tr:hover {
              background: #f8f9fa;
            }
            .footer, .no-data {
              color: #666;
              font-size: 14px;
              margin-top: 20px;
            }
            .no-data {
              padding: 40px;
              text-align: center;
              background: #f8f9fa;
              border-radius: 4px;
            }
            """;
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String convertToJson(Object obj) {
        // Simple JSON conversion (in production, use ObjectMapper from Jackson)
        if (obj == null) return "null";
        if (obj instanceof String) return "\"" + escapeJson((String) obj) + "\"";
        if (obj instanceof Number || obj instanceof Boolean) return obj.toString();
        if (obj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) obj;
            return map.entrySet().stream()
                    .map(e -> "\"" + escapeJson(e.getKey()) + "\":" + convertToJson(e.getValue()))
                    .collect(Collectors.joining(",", "{", "}"));
        }
        if (obj instanceof Collection) {
            return ((Collection<?>) obj).stream()
                    .map(this::convertToJson)
                    .collect(Collectors.joining(",", "[", "]"));
        }
        return "\"" + escapeJson(obj.toString()) + "\"";
    }

    private String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
}
