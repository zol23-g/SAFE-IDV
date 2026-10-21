package com.safe.idv.idscan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safe.idv.config.ApiKeyService;
import com.safe.idv.config.ServiceProviderConfig;
import com.safe.idv.config.ServiceProviderOverride;
import com.safe.idv.model.LiveKeys;
import com.safe.idv.service.GeolocationService;
import com.safe.idv.service.VisionProviderService;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.annotation.PreDestroy;
import javax.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class IdScanSubmissionService {
   private static final Logger log = LoggerFactory.getLogger(IdScanSubmissionService.class);
   private final String registerUrl;
   private final String checkStatusUrl;
   private final int timeoutSeconds;
   private final String authHeaderName;
   private final String authHeaderValue;
   private final String signatureSalt;
   private final boolean trustForwardedFor;
   private final boolean ocrEnabled;
   private final IdScanRegisterClient client = new IdScanRegisterClient();
   private final ObjectMapper objectMapper = new ObjectMapper();
   private final IdScanCaseStore store;
   private final ApiKeyService apiKeyService;
   private final VisionProviderService visionProviderService;
   private final ServiceProviderConfig serviceProviderConfig;
   private final GeolocationService geolocationService;
   private final ExecutorService ocrExecutor = Executors.newFixedThreadPool(2);

   public IdScanSubmissionService(@Value("${safe.idscan.register-url}") String registerUrl, @Value("${safe.idscan.check-status-url:${safe.idscan.check_status_url:http://localhost:3002/api/id_verification/check_status}}") String checkStatusUrl, @Value("${safe.idscan.timeout-seconds:60}") int timeoutSeconds, @Value("${safe.idscan.auth-header-name:}") String authHeaderName, @Value("${safe.idscan.auth-header-value:}") String authHeaderValue, @Value("${safe.idscan.device-signature-salt:}") String signatureSalt, @Value("${safe.idscan.trust-forwarded-for:false}") boolean trustForwardedFor, @Value("${safe.idscan.ocr-enabled:true}") boolean ocrEnabled, @Value("${safe.idscan.cases-dir:idscan-cases}") String casesDir, ApiKeyService apiKeyService, VisionProviderService visionProviderService, ServiceProviderConfig serviceProviderConfig, GeolocationService geolocationService) {
      this.registerUrl = registerUrl.strip();
      this.checkStatusUrl = checkStatusUrl != null ? checkStatusUrl.strip() : "http://localhost:3002/api/id_verification/check_status";
      this.timeoutSeconds = timeoutSeconds;
      this.authHeaderName = authHeaderName;
      this.authHeaderValue = authHeaderValue;
      this.signatureSalt = signatureSalt;
      this.trustForwardedFor = trustForwardedFor;
      this.ocrEnabled = ocrEnabled;
      this.store = new IdScanCaseStore(Paths.get(casesDir));
      this.apiKeyService = apiKeyService;
      this.visionProviderService = visionProviderService;
      this.serviceProviderConfig = serviceProviderConfig;
      this.geolocationService = geolocationService;
      if (signatureSalt == null || signatureSalt.isBlank()) {
         log.warn("safe.idscan.device-signature-salt is not set: device_digital_signature will be an UNSALTED hash. Set SAFE_DEVICE_SIG_SALT to a long random secret before real use.");
      }

   }

   @PreDestroy
   void shutdown() {
      this.ocrExecutor.shutdown();
   }

   public Map<String, Object> checkStatus(String token) {
      if (token == null || token.isBlank()) {
         Map<String, Object> err = new LinkedHashMap<>();
         err.put("status", "error");
         err.put("message", "magic_link_token is required");
         return err;
      }
      Map<String, Object> req = new LinkedHashMap<>();
      req.put("magic_link_token", token.strip());
      String json = MiniJson.write(req);
      log.info("Checking ID verification status for token {} at {}", token, this.checkStatusUrl);
      IdScanRegisterClient.RegisterResult r = this.client.post(this.checkStatusUrl, json, this.timeoutSeconds, this.authHeaderName, this.authHeaderValue);
      log.info("check_status raw response from SAFE (HTTP {}, elapsed {}ms): {}", r.httpStatus, r.elapsedMs, r.body);
      if (!r.gotResponse()) {
         log.error("Failed to connect to check_status endpoint: {}", r.transportError);
         Map<String, Object> err = new LinkedHashMap<>();
         err.put("status", "error");
         err.put("message", "Unable to connect to verification server. Please try again.");
         return err;
      }
      try {
         @SuppressWarnings("unchecked")
         Map<String, Object> res = (Map<String, Object>) this.objectMapper.readValue(r.body, Map.class);
         return res;
      } catch (Exception e) {
         log.error("Failed to parse check_status response (HTTP {}): {}", r.httpStatus, r.body, e);
         Map<String, Object> err = new LinkedHashMap<>();
         err.put("status", "error");
         err.put("message", "Invalid response from verification server: " + r.body);
         return err;
      }
   }

   public Map submit(Map body, HttpServletRequest httpRequest) {
      String token = str(body.get("magicLinkToken"));
      if (token == null) {
         throw new IllegalArgumentException("magicLinkToken is required");
      } else {
         Map<String, Object> images = asMap(body.get("images"));
         Object selfieRaw = images.get("selfieImage");
         Object frontRaw = images.get("frontImage");
         Object backRaw = images.get("backImage");
         String selfieB64 = IdScanPayloadBuilder.stripDataUri(selfieRaw);
         String frontB64 = IdScanPayloadBuilder.stripDataUri(frontRaw);
         String backB64 = IdScanPayloadBuilder.stripDataUri(backRaw);
         if (selfieB64 == null) {
            throw new IllegalArgumentException("selfie image is required (liveness cannot be skipped)");
         } else if (frontB64 == null) {
            throw new IllegalArgumentException("front ID image is required");
         } else {
            String docType = str(body.get("docType"));
            String idType = IdScanPayloadBuilder.idTypeFor(docType);
            String ip = this.trustForwardedFor ? this.geolocationService.clientIpFor(httpRequest) : httpRequest.getRemoteAddr();
            Map<String, Object> ipGeo = this.geolocationService.geolocate(ip, true);
            Object ipLat = "resolved".equals(ipGeo.get("status")) ? ipGeo.get("lat") : null;
            Object ipLon = "resolved".equals(ipGeo.get("status")) ? ipGeo.get("lon") : null;
            Map<String, Object> gps = asMap(body.get("gps"));
            Map<String, Object> device = asMap(body.get("device"));
            Map<String, Object> deviceInfo = IdScanPayloadBuilder.parseUserAgent(str(device.get("userAgent")), str(device.get("platform")), asMap(device.get("uaData")));
            String deviceInfoText = (String)deviceInfo.get("summary");
            String signature = IdScanPayloadBuilder.deviceSignature(this.signatureSalt, device);
            Map<String, Object> payload = IdScanPayloadBuilder.buildRegisterPayload(token, idType, gps.get("latitude"), gps.get("longitude"), ipLat, ipLon, ip, signature, deviceInfoText, selfieB64, frontB64, IdScanPayloadBuilder.sendsBackImage(idType) ? backB64 : null);
            String var10000 = UUID.randomUUID().toString();
            String reference = "IDS-" + var10000.substring(0, 8).toUpperCase();
            Map<String, Object> record = new LinkedHashMap();
            record.put("reference", reference);
            record.put("receivedAt", Instant.now().toString());
            record.put("magicLinkToken", token);
            record.put("docType", docType);
            record.put("region", str(body.get("region")));
            record.put("idType", idType);
            record.put("clientIp", ip);
            record.put("ipGeolocation", ipGeo);
            record.put("gps", gps);
            record.put("deviceOsBrowserInfo", deviceInfo);
            record.put("deviceDigitalSignature", signature);
            record.put("imageSizesBase64Chars", sizes(selfieB64, frontB64, backB64));
            record.put("captureSignals", body.get("captureSignals"));
            record.put("registerRequestSentWithoutImages", redactImages(payload));
            Map<String, Object> register = new LinkedHashMap();
            register.put("url", this.registerUrl);
            record.put("register", register);
            if (idType == null) {
               register.put("skipped", true);
               register.put("reason", "docType '" + docType + "' is not a recognized document type (DL, IC, PassportCard, PASSPORT, OTHERS); nothing was forwarded.");
               this.store.writeCase(reference, record);
            } else {
               IdScanRegisterClient.RegisterResult r = this.client.post(this.registerUrl, MiniJson.write(payload), this.timeoutSeconds, this.authHeaderName, this.authHeaderValue);
               register.put("elapsedMs", r.elapsedMs);
               if (r.gotResponse()) {
                  register.put("httpStatus", r.httpStatus);
                  register.put("responseBody", r.body);
               } else {
                  register.put("transportError", r.transportError);
               }

               this.store.writeCase(reference, record);
               if (!r.gotResponse()) {
                  log.warn("IDScan register transport failure for {}: {}", reference, r.transportError);
                  throw new TransportException("register endpoint unreachable");
               }

               log.info("IDScan register {} -> HTTP {} in {} ms", new Object[]{reference, r.httpStatus, r.elapsedMs});
            }

            String frontMime = IdScanPayloadBuilder.mimeOf(frontRaw);
            if (this.ocrEnabled) {
               this.ocrExecutor.submit(() -> this.runOcr(reference, frontMime, frontB64));
            } else {
               this.store.writeOcr(reference, ocrRecord(reference, "skipped", "OCR disabled (safe.idscan.ocr-enabled=false)", (String)null, (Map)null));
            }

            Map<String, Object> out = new LinkedHashMap();
            out.put("accepted", true);
            out.put("reference", reference);
            return out;
         }
      }
   }

   private void runOcr(String reference, String mime, String b64) {
      try {
         ApiKeyService.Resolved keys = this.apiKeyService.resolveKeysForRequest((LiveKeys)null);
         ServiceProviderOverride ov = this.serviceProviderConfig.ocrOverride();
         ServiceProviderOverride applied = null;
         String provider;
         String key;
         if (ov != null && ov.isSet()) {
            provider = ov.normalizedProvider();
            applied = ov;
            key = ov.apiKey() != null ? ov.apiKey() : ("openai".equals(provider) ? keys.openaiKey : keys.anthropicKey);
         } else if (!keys.anthropicKey.isBlank()) {
            provider = "claude";
            key = keys.anthropicKey;
         } else {
            if (keys.openaiKey.isBlank()) {
               this.store.writeOcr(reference, ocrRecord(reference, "skipped", "no OCR API key configured", (String)null, (Map)null));
               return;
            }

            provider = "openai";
            key = keys.openaiKey;
         }

         if (key == null || key.isBlank()) {
            this.store.writeOcr(reference, ocrRecord(reference, "skipped", "no API key for OCR provider '" + provider + "'", provider, (Map)null));
            return;
         }

         Map<String, Object> ocr = "openai".equals(provider) ? this.visionProviderService.openaiOcr(key, mime, b64, applied) : this.visionProviderService.claudeOcr(key, mime, b64, applied);
         this.store.writeOcr(reference, ocrRecord(reference, "ok", (String)null, provider, ocr));
      } catch (Exception var11) {
         Exception e = var11;
         log.warn("OCR failed for {}: {}", reference, var11.toString());

         try {
            this.store.writeOcr(reference, ocrRecord(reference, "error", String.valueOf(e.getMessage()), (String)null, (Map)null));
         } catch (Exception var10) {
         }
      }

   }

   private static Map ocrRecord(String reference, String status, String detail, String provider, Map ocr) {
      Map<String, Object> m = new LinkedHashMap();
      m.put("reference", reference);
      m.put("status", status);
      m.put("completedAt", Instant.now().toString());
      if (provider != null) {
         m.put("provider", provider);
      }

      if (detail != null) {
         m.put("detail", detail);
      }

      if (ocr != null) {
         m.put("ocr", ocr);
      }

      return m;
   }

   private static String str(Object o) {
      if (o == null) {
         return null;
      } else {
         String s = o.toString().strip();
         return s.isEmpty() ? null : s;
      }
   }

   private static Map asMap(Object o) {
      return (Map)(o instanceof Map ? (Map)o : new LinkedHashMap());
   }

   private static Map sizes(String selfie, String front, String back) {
      Map<String, Object> m = new LinkedHashMap();
      m.put("selfie", selfie == null ? 0 : selfie.length());
      m.put("front", front == null ? 0 : front.length());
      m.put("back", back == null ? 0 : back.length());
      return m;
   }

   private static Map redactImages(Map payload) {
      Map<String, Object> copy = new LinkedHashMap(payload);

      for(String k : new String[]{"base64_selfie", "base64_id", "base64_id_back"}) {
         if (copy.containsKey(k)) {
            Object var10002 = copy.get(k);
            copy.put(k, "[" + String.valueOf(var10002).length() + " base64 chars omitted]");
         }
      }

      return copy;
   }

   public static class TransportException extends RuntimeException {
      private static final long serialVersionUID = 1L;

      public TransportException(String message) {
         super(message);
      }
   }
}
