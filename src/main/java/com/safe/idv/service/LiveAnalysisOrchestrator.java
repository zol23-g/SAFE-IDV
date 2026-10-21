package com.safe.idv.service;

import com.safe.idv.barcode.BarcodeDecodeService;
import com.safe.idv.config.ServiceProviderConfig;
import com.safe.idv.config.ServiceProviderOverride;
import com.safe.idv.model.LiveAnalysisResult;
import com.safe.idv.model.VisionProviderResult;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.springframework.stereotype.Service;

@Service
public class LiveAnalysisOrchestrator {
   private final BarcodeDecodeService barcodeDecodeService;
   private final VisionProviderService visionProviderService;
   private final DocumentAnalysisSupport documentAnalysisSupport;
   private final ServiceProviderConfig serviceProviderConfig;
   private final Executor executor = Executors.newFixedThreadPool(2);

   public LiveAnalysisOrchestrator(BarcodeDecodeService barcodeDecodeService, VisionProviderService visionProviderService, DocumentAnalysisSupport documentAnalysisSupport, ServiceProviderConfig serviceProviderConfig) {
      this.barcodeDecodeService = barcodeDecodeService;
      this.visionProviderService = visionProviderService;
      this.documentAnalysisSupport = documentAnalysisSupport;
      this.serviceProviderConfig = serviceProviderConfig;
   }

   private ImageParts dataUriToParts(String dataUri) {
      if (dataUri == null) {
         return new ImageParts((String)null, (String)null);
      } else if (dataUri.startsWith("data:")) {
         int comma = dataUri.indexOf(44);
         String header = dataUri.substring(5, comma);
         String mime = header.split(";")[0];
         return new ImageParts(mime.isBlank() ? "image/jpeg" : mime, dataUri.substring(comma + 1));
      } else {
         return new ImageParts("image/jpeg", dataUri);
      }
   }

   private static ServiceProviderOverride matchingOverride(ServiceProviderOverride override, String provider) {
      return override != null && override.appliesTo(provider) ? override : null;
   }

   private static boolean hasEffectiveKey(ServiceProviderOverride override, String sharedKey) {
      if (override != null && override.apiKey() != null && !override.apiKey().isBlank()) {
         return true;
      } else {
         return sharedKey != null && !sharedKey.isBlank();
      }
   }

   private VisionProviderResult runProvider(String provider, Map applicant, String frontMime, String frontB64, String selfieMime, String selfieB64, String apiKey, Map barcodeResult) {
      VisionProviderResult result = new VisionProviderResult();
      result.available = true;
      ServiceProviderOverride ocrOverride = matchingOverride(this.serviceProviderConfig.ocrOverride(), provider);
      ServiceProviderOverride faceMatchOverride = matchingOverride(this.serviceProviderConfig.faceMatchOverride(), provider);
      if (!hasEffectiveKey(ocrOverride, apiKey)) {
         result.available = false;
         result.error = provider + " OCR skipped: no API key configured for this service.";
         return result;
      } else {
         try {
            Map<String, Object> ocr = "openai".equals(provider) ? this.visionProviderService.openaiOcr(apiKey, frontMime, frontB64, ocrOverride) : this.visionProviderService.claudeOcr(apiKey, frontMime, frontB64, ocrOverride);
            result.ocrData = ocr;
         } catch (Exception e) {
            result.available = false;
            result.error = provider + " OCR call failed: " + e.getMessage();
            return result;
         }

         if (!hasEffectiveKey(faceMatchOverride, apiKey)) {
            Map<String, Object> fm = new HashMap();

            for(String k : VisionProviderService.FACEMATCH_SCHEMA_KEYS) {
               fm.put(k, (Object)null);
            }

            fm.put("matchStatus", "UNABLE_TO_COMPARE");
            fm.put("aiNotes", provider + " face match skipped: no API key configured for this service.");
            result.faceMatch = fm;
         } else {
            try {
               Map<String, Object> fm = "openai".equals(provider) ? this.visionProviderService.openaiFaceMatch(apiKey, frontMime, frontB64, selfieMime, selfieB64, faceMatchOverride) : this.visionProviderService.claudeFaceMatch(apiKey, frontMime, frontB64, selfieMime, selfieB64, faceMatchOverride);
               result.faceMatch = fm;
            } catch (Exception e) {
               Map<String, Object> fm = new HashMap();

               for(String k : VisionProviderService.FACEMATCH_SCHEMA_KEYS) {
                  fm.put(k, (Object)null);
               }

               fm.put("matchStatus", "UNABLE_TO_COMPARE");
               fm.put("aiNotes", provider + " face-match call failed: " + e.getMessage());
               result.faceMatch = fm;
            }
         }

         result.comparison = ComparisonService.compareThreeWay(applicant, result.ocrData, barcodeResult);
         Map<String, Object> flags = this.documentAnalysisSupport.documentFlagsFromOcr(result.ocrData, barcodeResult);
         flags.put("isPdf417Success", "SUCCESS".equals(barcodeResult.get("decodeStatus")));
         result.documentFlags = flags;
         result.confidenceScores = this.documentAnalysisSupport.confidenceScoresFromOcr(result.ocrData);
         return result;
      }
   }

   private static String str(Map images, String key) {
      Object v = images.get(key);
      return v == null ? null : String.valueOf(v);
   }

   public LiveAnalysisResult runLiveAnalysis(Map applicant, Map images, String openaiKey, String anthropicKey, String docType) {
      ImageParts front = this.dataUriToParts(str(images, "frontImage"));
      ImageParts selfie = this.dataUriToParts(str(images, "selfieImage"));
      String backUri = str(images, "backImage");
      String backBarcodeUri = str(images, "backBarcodeImage");
      String barcodeUri = backBarcodeUri != null && !backBarcodeUri.isBlank() ? backBarcodeUri : backUri;
      Map<String, Object> barcodeResult = this.barcodeDecodeService.decode(barcodeUri, docType);
      boolean barcodeUriDiffersFromBack = !Objects.equals(barcodeUri, backUri);
      if ("FAILED".equals(barcodeResult.get("decodeStatus")) && barcodeUriDiffersFromBack && backUri != null && !backUri.isBlank()) {
         Map<String, Object> fallback = this.barcodeDecodeService.decode(backUri, docType);
         if (!"FAILED".equals(fallback.get("decodeStatus"))) {
            barcodeResult = fallback;
         }
      }

      Map<String, Object> finalBarcodeResult = barcodeResult;
      boolean openaiOverrideActive = matchingOverride(this.serviceProviderConfig.ocrOverride(), "openai") != null || matchingOverride(this.serviceProviderConfig.faceMatchOverride(), "openai") != null;
      boolean claudeOverrideActive = matchingOverride(this.serviceProviderConfig.ocrOverride(), "claude") != null || matchingOverride(this.serviceProviderConfig.faceMatchOverride(), "claude") != null;
      Map<String, CompletableFuture<VisionProviderResult>> jobs = new HashMap();
      boolean haveImages = front.b64() != null && selfie.b64() != null;
      if ((openaiKey != null && !openaiKey.isBlank() || openaiOverrideActive) && haveImages) {
         jobs.put("openai", CompletableFuture.supplyAsync(() -> this.runProvider("openai", applicant, front.mime(), front.b64(), selfie.mime(), selfie.b64(), openaiKey, finalBarcodeResult), this.executor));
      }

      if ((anthropicKey != null && !anthropicKey.isBlank() || claudeOverrideActive) && haveImages) {
         jobs.put("claude", CompletableFuture.supplyAsync(() -> this.runProvider("claude", applicant, front.mime(), front.b64(), selfie.mime(), selfie.b64(), anthropicKey, finalBarcodeResult), this.executor));
      }

      CompletableFuture.allOf((CompletableFuture[])jobs.values().toArray(new CompletableFuture[0])).join();
      Map<String, VisionProviderResult> visionProviders = new HashMap();
      jobs.forEach((namex, fut) -> visionProviders.put(namex, (VisionProviderResult)fut.join()));

      for(String name : List.of("openai", "claude")) {
         if (!visionProviders.containsKey(name)) {
            VisionProviderResult unavailable = new VisionProviderResult();
            unavailable.available = false;
            unavailable.error = "No API key configured for this provider.";
            visionProviders.put(name, unavailable);
         }
      }

      VisionProviderResult primary = (VisionProviderResult)visionProviders.get("openai");
      if (primary == null || !primary.available) {
         primary = (VisionProviderResult)visionProviders.values().stream().filter((v) -> v.available).findFirst().orElse(null);
      }

      if (primary == null) {
         primary = new VisionProviderResult();
         primary.ocrData = Map.of();
         Map<String, Object> fm = new HashMap();

         for(String k : VisionProviderService.FACEMATCH_SCHEMA_KEYS) {
            fm.put(k, (Object)null);
         }

         fm.put("matchStatus", "UNABLE_TO_COMPARE");
         fm.put("aiNotes", "No vision provider was available for this case.");
         primary.faceMatch = fm;
         primary.comparison = ComparisonService.compareThreeWay(applicant, Map.of(), barcodeResult);
         primary.documentFlags = this.documentAnalysisSupport.documentFlagsFromOcr(Map.of(), barcodeResult);
         primary.confidenceScores = this.documentAnalysisSupport.confidenceScoresFromOcr(Map.of());
      }

      LiveAnalysisResult out = new LiveAnalysisResult();
      out.ocrData = primary.ocrData;
      out.faceMatch = primary.faceMatch;
      out.comparison = primary.comparison;
      out.documentFlags = primary.documentFlags;
      out.confidenceScores = primary.confidenceScores;
      out.barcodeData = barcodeResult;
      out.visionProviders = visionProviders;
      return out;
   }

   private static final class ImageParts {
      private final String mime;
      private final String b64;

      ImageParts(String mime, String b64) {
         this.mime = mime;
         this.b64 = b64;
      }

      String mime() {
         return this.mime;
      }

      String b64() {
         return this.b64;
      }
   }
}
