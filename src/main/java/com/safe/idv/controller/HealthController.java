package com.safe.idv.controller;

import com.safe.idv.config.ApiKeyService;
import com.safe.idv.model.HealthResponse;
import java.time.LocalDateTime;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
   private final ApiKeyService apiKeyService;
   private final boolean barcodeDecodeAvailable = true;

   public HealthController(ApiKeyService apiKeyService) {
      this.apiKeyService = apiKeyService;
   }

   @GetMapping({"/api/v1/health"})
   public HealthResponse health() {
      boolean liveMode = this.apiKeyService.serverOpenaiConfigured() || this.apiKeyService.serverAnthropicConfigured();
      HealthResponse r = new HealthResponse();
      r.status = "UP";
      r.service = liveMode ? "SAFE ID Verification API (DEMO -- LIVE MODE)" : "SAFE ID Verification API (DEMO MOCK)";
      r.version = "2.0-demo-springboot";
      r.timestamp = LocalDateTime.now().toString();
      r.mode = liveMode ? "live" : "mock";
      r.modeNote = "Reflects this server's own configured keys (env vars / api_keys.json). Individual requests can still override this per-case by sending their own liveKeys in the POST body -- see the dev harness panel on the capture page.";
      r.openaiConfigured = this.apiKeyService.serverOpenaiConfigured();
      r.anthropicConfigured = this.apiKeyService.serverAnthropicConfigured();
      r.barcodeDecodeAvailable = true;
      return r;
   }
}
