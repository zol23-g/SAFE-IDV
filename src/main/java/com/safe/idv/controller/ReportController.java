package com.safe.idv.controller;

import com.safe.idv.service.ReportStoreService;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@ConditionalOnProperty(
   name = {"safe.demo-endpoints.enabled"},
   havingValue = "true"
)
@RestController
public class ReportController {
   private final ReportStoreService reportStore;

   public ReportController(ReportStoreService reportStore) {
      this.reportStore = reportStore;
   }

   @PostMapping({"/api/v1/reports/{sessionId}"})
   public ResponseEntity saveReport(@PathVariable String sessionId, @RequestBody String html) {
      try {
         ReportStoreService.ReportMeta meta = this.reportStore.save(sessionId, html);
         return ResponseEntity.status(HttpStatus.CREATED).body(meta);
      } catch (IllegalArgumentException e) {
         return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
      }
   }

   @GetMapping({"/api/v1/reports"})
   public List listReports() {
      return this.reportStore.list();
   }

   @DeleteMapping({"/api/v1/reports/{sessionId}"})
   public ResponseEntity deleteReport(@PathVariable String sessionId) {
      try {
         boolean deleted = this.reportStore.delete(sessionId);
         return !deleted ? ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "No saved report for session: " + sessionId)) : ResponseEntity.noContent().build();
      } catch (IllegalArgumentException e) {
         return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
      } catch (UncheckedIOException e) {
         return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Could not delete report " + sessionId + ": " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage())));
      }
   }

   @GetMapping(
      value = {"/reports/index.html"},
      produces = {"text/html"}
   )
   public String reportsIndex() {
      return this.buildIndexHtml(this.reportStore.list());
   }

   private String buildIndexHtml(List<ReportStoreService.ReportMeta> reports) {
      StringBuilder rows = new StringBuilder();
      if (reports.isEmpty()) {
         rows.append("<tr><td colspan=\"3\" class=\"empty\">No reports saved yet. Complete a session to see it here.</td></tr>");
      } else {
         for(ReportStoreService.ReportMeta r : reports) {
            rows.append("<tr>").append("<td><a href=\"").append(this.escape(r.url())).append("\">").append(this.escape(r.sessionId())).append("</a></td>").append("<td>").append(this.escape(r.savedAt())).append("</td>").append("<td>").append(String.format("%.1f KB", (double)r.sizeBytes() / (double)1024.0F)).append("</td>").append("</tr>");
         }
      }

      int var10000 = reports.size();
      return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><title>SAFE IDV — Saved Reports</title><style>body{font-family:-apple-system,Segoe UI,Helvetica,Arial,sans-serif;max-width:720px;margin:40px auto;padding:0 20px;color:#1c2430;}h1{font-size:20px;margin-bottom:4px;}.sub{color:#5a6472;font-size:13px;margin-bottom:24px;}table{border-collapse:collapse;width:100%;font-size:14px;}th,td{border:1px solid #d8dce2;padding:8px 12px;text-align:left;}th{background:#f6f7f9;font-size:11px;text-transform:uppercase;color:#5a6472;}td.empty{color:#5a6472;text-align:center;padding:24px;}a{color:#534AB7;text-decoration:none;font-family:monospace;}a:hover{text-decoration:underline;}</style></head><body><h1>Saved IDV Session Reports</h1><div class=\"sub\">" + var10000 + " report" + (reports.size() == 1 ? "" : "s") + " saved on this server. Reload this page after a new session completes.</div><table><tr><th>Session</th><th>Saved</th><th>Size</th></tr>" + String.valueOf(rows) + "</table></body></html>";
   }

   private String escape(String s) {
      return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
   }
}
