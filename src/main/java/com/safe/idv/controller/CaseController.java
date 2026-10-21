package com.safe.idv.controller;

import com.safe.idv.config.ApiKeyService;
import com.safe.idv.model.CaseCreateRequest;
import com.safe.idv.model.CaseResponse;
import com.safe.idv.model.LiveAnalysisResult;
import com.safe.idv.model.VisionProviderResult;
import com.safe.idv.service.CaseStore;
import com.safe.idv.service.GeolocationService;
import com.safe.idv.service.LiveAnalysisOrchestrator;
import com.safe.idv.service.MockAnalysisService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@ConditionalOnProperty(
   name = {"safe.demo-endpoints.enabled"},
   havingValue = "true"
)
@RestController
@RequestMapping({"/api/v1/cases"})
public class CaseController {
   private final CaseStore caseStore;
   private final ApiKeyService apiKeyService;
   private final LiveAnalysisOrchestrator liveAnalysisOrchestrator;
   private final MockAnalysisService mockAnalysisService;
   private final GeolocationService geolocationService;

   public CaseController(CaseStore caseStore, ApiKeyService apiKeyService, LiveAnalysisOrchestrator liveAnalysisOrchestrator, MockAnalysisService mockAnalysisService, GeolocationService geolocationService) {
      this.caseStore = caseStore;
      this.apiKeyService = apiKeyService;
      this.liveAnalysisOrchestrator = liveAnalysisOrchestrator;
      this.mockAnalysisService = mockAnalysisService;
      this.geolocationService = geolocationService;
   }

   @GetMapping
   public List listCases() {
      return List.copyOf(this.caseStore.all());
   }

   @GetMapping({"/{id}"})
   public ResponseEntity getCase(@PathVariable String id) {
      CaseResponse c = this.caseStore.get(id);
      return c == null ? ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Case not found: " + id)) : ResponseEntity.ok(c);
   }

   @PostMapping({"/{id}/analyze"})
   public ResponseEntity analyze(@PathVariable String id) {
      CaseResponse c = this.caseStore.get(id);
      return c == null ? ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Case not found: " + id)) : ResponseEntity.ok(c);
   }

   @PostMapping
   public ResponseEntity createCase(@RequestBody CaseCreateRequest request, HttpServletRequest httpRequest) {
      Map<String, Object> applicant = request.applicant != null ? request.applicant : Map.of();
      Map<String, Object> images = request.images != null ? request.images : Map.of();
      String docType = request.docType;
      int id = this.caseStore.allocateId();
      String caseNumber = this.caseStore.caseNumberFor(id);
      ApiKeyService.Resolved resolved = this.apiKeyService.resolveKeysForRequest(request.liveKeys);
      CaseResponse full = new CaseResponse();
      full.caseId = id;
      full.caseNumber = caseNumber;
      full.createdAt = LocalDateTime.now().toString();
      full.applicantData = applicant;
      full.error = null;
      full.captureSignals = request.captureSignals;
      if (resolved.liveMode) {
         try {
            LiveAnalysisResult live = this.liveAnalysisOrchestrator.runLiveAnalysis(applicant, images, resolved.openaiKey.isBlank() ? null : resolved.openaiKey, resolved.anthropicKey.isBlank() ? null : resolved.anthropicKey, docType);
            List<String> providersUsed = live.visionProviders.entrySet().stream().filter((ex) -> ex.getValue().available).map(Map.Entry::getKey).collect(Collectors.toList());
            String keySource = resolved.usedRequestKeys ? "keys entered in the demo UI for this request" : "server-configured keys";
            full.status = "NEEDS_REVIEW";
            full.analysisMessage = "Analysis complete. (LIVE MODE via " + keySource + " -- real vision-model analysis via: " + (providersUsed.isEmpty() ? "none available" : String.join(", ", providersUsed)) + ")";
            full.ocrData = live.ocrData;
            full.barcodeData = live.barcodeData;
            full.comparison = live.comparison;
            full.faceMatch = live.faceMatch;
            full.documentFlags = live.documentFlags;
            full.confidenceScores = live.confidenceScores;
            full.visionProviders = live.visionProviders;
            full.mockMode = null;
         } catch (Exception e) {
            MockAnalysisService.Result r = this.mockAnalysisService.build();
            full.status = r.status;
            full.analysisMessage = "LIVE MODE analysis failed (" + e.getMessage() + ") -- no real analysis was completed for this case. The REVIEW verdict reflects that failure, not an actual assessment.";
            full.ocrData = r.ocrData;
            full.barcodeData = r.barcodeData;
            full.comparison = r.comparison;
            full.faceMatch = r.faceMatch;
            full.documentFlags = r.documentFlags;
            full.confidenceScores = r.confidenceScores;
            full.mockMode = true;
         }
      } else {
         MockAnalysisService.Result r = this.mockAnalysisService.build();
         full.status = r.status;
         full.analysisMessage = "No live analysis was performed -- no OpenAI/Anthropic key was available for this request (none entered in the demo UI, and none configured on the server via OPENAI_API_KEY / ANTHROPIC_API_KEY / api_keys.json). Enter a key in the dev harness panel above, or set one before launch (see README), to run real OCR and face-match analysis. This result is always REVIEW and must not be read as a pass or a fail.";
         full.ocrData = r.ocrData;
         full.barcodeData = r.barcodeData;
         full.comparison = r.comparison;
         full.faceMatch = r.faceMatch;
         full.documentFlags = r.documentFlags;
         full.confidenceScores = r.confidenceScores;
         full.mockMode = true;
      }

      full.ipGeolocation = this.geolocationService.geolocate(this.geolocationService.clientIpFor(httpRequest), resolved.liveMode);
      this.caseStore.put(String.valueOf(id), full);
      return ResponseEntity.status(HttpStatus.CREATED).body(full);
   }
}
