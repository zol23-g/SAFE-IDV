package com.safe.idv.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safe.idv.config.ServiceProviderOverride;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class VisionProviderService {
   private static final String OPENAI_MODEL = (String)System.getenv().getOrDefault("SAFE_DEMO_OPENAI_MODEL", "gpt-4o");
   private static final String CLAUDE_MODEL = (String)System.getenv().getOrDefault("SAFE_DEMO_CLAUDE_MODEL", "claude-sonnet-5");
   private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(45L);
   private static final int OCR_MAX_TOKENS = 2200;
   private static final int FACEMATCH_MAX_TOKENS = 900;
   public static final List<String> OCR_SCHEMA_KEYS = List.of("isDocumentDetected", "firstName", "middleName", "lastName", "fullName", "suffix", "namePrefix", "maidenName", "dateOfBirth", "sex", "height", "weight", "eyeColor", "hairColor", "addressLine1", "city", "stateProvince", "postalCode", "country", "documentNumber", "documentType", "issuingJurisdiction", "issueDate", "expirationDate", "documentClass", "restrictionCode", "endorsementsCode", "isRealId", "isCommercialDriversLicense", "isVeteran", "isDonor", "isLimitedDurationDocument", "isInvalidated", "isNonDomicile", "isEnhancedCredential", "isPermit", "isInterlockRestricted", "isSampleDocument", "qualityNotes", "tamperNotes", "extractionConfidence");
   public static final List<String> FACEMATCH_SCHEMA_KEYS = List.of("matchStatus", "matchConfidence", "falseAcceptanceRate", "assessedAgeFromSelfie", "assessedAgeFromDocument", "isAgeCheckSuccess", "isGenderCheckSuccess", "isSuspiciousPhoto", "antiSpoofingConfidence", "isDataFieldsTampered", "isPhotoFromDocumentTampered", "aiNotes");
   private static final String OCR_PROMPT_TEMPLATE = "You are examining an image submitted as the FRONT of a government-issued ID document (e.g. a driver's license) for an identity verification system. Do NOT assume the image actually shows one -- in testing, submissions are sometimes a selfie/portrait, a blank or plain background, an unrelated object or document, or something else entirely. Extract every visible field into a single JSON object with EXACTLY these keys -- use null for anything not visible or not applicable, and do not omit any key:\n\n%s\n\n- isDocumentDetected is a boolean and must be your genuine visual assessment, made FIRST, before extracting anything else: true ONLY if this image actually shows a government-issued identity document (front or back of a driver's license, state/national ID card, passport card, etc.) of any jurisdiction -- even if illegible, damaged, expired, or of poor quality. false if it shows anything else at all, including a selfie/portrait photo of a person with no document visible, a blank/plain background, an unrelated object or document, or nothing identifiable as an ID.\n- If isDocumentDetected is false: every other field below must be null, extractionConfidence must be 0, and qualityNotes must briefly describe what the image actually appears to show instead (e.g. \"appears to be a selfie/headshot photo, not an ID document\"). Do not guess or fabricate ID field values from a non-ID image under any circumstance.\n- dateOfBirth, issueDate, expirationDate must be YYYY-MM-DD format.\n- isRealId, isCommercialDriversLicense, isVeteran, isDonor, isLimitedDurationDocument, isInvalidated, isNonDomicile, isEnhancedCredential, isPermit, isInterlockRestricted, isSampleDocument must be true/false booleans.\n- extractionConfidence is an integer 0-100: your confidence in the overall extraction given image quality/legibility.\n- qualityNotes and tamperNotes are short strings (or null) noting any image-quality issues or visible signs of alteration.\n\nRespond with ONLY the JSON object. No markdown, no commentary, no code fences.";
   private static final String FACEMATCH_PROMPT_TEMPLATE = "You are comparing a live selfie photo against the photo on a government-issued ID document, for an identity verification system. The first image is the ID document (its photo is one part of it); the second image is the live selfie. Assess whether they show the same person, and assess spoofing/tampering risk. Respond with ONLY a single JSON object with EXACTLY these keys -- use null where genuinely not assessable:\n\n%s\n\n- matchStatus must be one of: MATCH, LIKELY_MATCH, UNCERTAIN, NO_MATCH, UNABLE_TO_COMPARE.\n- matchConfidence is an integer 0-100 or null if UNABLE_TO_COMPARE.\n- falseAcceptanceRate is a short string estimate, e.g. \"<0.01%%\" for a strong match, \"~1%%\" for uncertain.\n- assessedAgeFromSelfie / assessedAgeFromDocument are integer age estimates.\n- isAgeCheckSuccess / isGenderCheckSuccess are booleans: are the two images roughly consistent on that dimension.\n- isSuspiciousPhoto: true if the selfie looks like a photo of a photo/screen (a spoofing attempt).\n- antiSpoofingConfidence is an integer 0-100.\n- isDataFieldsTampered: always false (not assessable from images alone in this context).\n- isPhotoFromDocumentTampered: true if the ID's photo itself looks digitally altered or swapped.\n- aiNotes: a short 1-2 sentence explanation of your assessment.\n\nRespond with ONLY the JSON object. No markdown, no commentary, no code fences.";
   private final HttpClient httpClient;
   private final ObjectMapper mapper;

   public VisionProviderService() {
      this.httpClient = HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build();
      this.mapper = new ObjectMapper();
   }

   private String ocrPrompt() {
      return String.format("You are examining an image submitted as the FRONT of a government-issued ID document (e.g. a driver's license) for an identity verification system. Do NOT assume the image actually shows one -- in testing, submissions are sometimes a selfie/portrait, a blank or plain background, an unrelated object or document, or something else entirely. Extract every visible field into a single JSON object with EXACTLY these keys -- use null for anything not visible or not applicable, and do not omit any key:\n\n%s\n\n- isDocumentDetected is a boolean and must be your genuine visual assessment, made FIRST, before extracting anything else: true ONLY if this image actually shows a government-issued identity document (front or back of a driver's license, state/national ID card, passport card, etc.) of any jurisdiction -- even if illegible, damaged, expired, or of poor quality. false if it shows anything else at all, including a selfie/portrait photo of a person with no document visible, a blank/plain background, an unrelated object or document, or nothing identifiable as an ID.\n- If isDocumentDetected is false: every other field below must be null, extractionConfidence must be 0, and qualityNotes must briefly describe what the image actually appears to show instead (e.g. \"appears to be a selfie/headshot photo, not an ID document\"). Do not guess or fabricate ID field values from a non-ID image under any circumstance.\n- dateOfBirth, issueDate, expirationDate must be YYYY-MM-DD format.\n- isRealId, isCommercialDriversLicense, isVeteran, isDonor, isLimitedDurationDocument, isInvalidated, isNonDomicile, isEnhancedCredential, isPermit, isInterlockRestricted, isSampleDocument must be true/false booleans.\n- extractionConfidence is an integer 0-100: your confidence in the overall extraction given image quality/legibility.\n- qualityNotes and tamperNotes are short strings (or null) noting any image-quality issues or visible signs of alteration.\n\nRespond with ONLY the JSON object. No markdown, no commentary, no code fences.", String.join(", ", OCR_SCHEMA_KEYS));
   }

   private String faceMatchPrompt() {
      return String.format("You are comparing a live selfie photo against the photo on a government-issued ID document, for an identity verification system. The first image is the ID document (its photo is one part of it); the second image is the live selfie. Assess whether they show the same person, and assess spoofing/tampering risk. Respond with ONLY a single JSON object with EXACTLY these keys -- use null where genuinely not assessable:\n\n%s\n\n- matchStatus must be one of: MATCH, LIKELY_MATCH, UNCERTAIN, NO_MATCH, UNABLE_TO_COMPARE.\n- matchConfidence is an integer 0-100 or null if UNABLE_TO_COMPARE.\n- falseAcceptanceRate is a short string estimate, e.g. \"<0.01%%\" for a strong match, \"~1%%\" for uncertain.\n- assessedAgeFromSelfie / assessedAgeFromDocument are integer age estimates.\n- isAgeCheckSuccess / isGenderCheckSuccess are booleans: are the two images roughly consistent on that dimension.\n- isSuspiciousPhoto: true if the selfie looks like a photo of a photo/screen (a spoofing attempt).\n- antiSpoofingConfidence is an integer 0-100.\n- isDataFieldsTampered: always false (not assessable from images alone in this context).\n- isPhotoFromDocumentTampered: true if the ID's photo itself looks digitally altered or swapped.\n- aiNotes: a short 1-2 sentence explanation of your assessment.\n\nRespond with ONLY the JSON object. No markdown, no commentary, no code fences.", String.join(", ", FACEMATCH_SCHEMA_KEYS));
   }

   private Map parseJsonLoose(String text) throws Exception {
      if (text != null && !text.isBlank()) {
         String t = text.strip();

         try {
            return (Map)this.mapper.readValue(t, Map.class);
         } catch (Exception var13) {
            Matcher fence = Pattern.compile("```(?:json)?\\s*(.*?)\\s*```", 32).matcher(t);
            if (fence.find()) {
               try {
                  return (Map)this.mapper.readValue(fence.group(1), Map.class);
               } catch (Exception var12) {
               }
            }

            int start = t.indexOf(123);
            int end = t.lastIndexOf(125);
            if (start != -1 && end != -1 && end > start) {
               try {
                  return (Map)this.mapper.readValue(t.substring(start, end + 1), Map.class);
               } catch (Exception var11) {
               }
            }

            if (start != -1) {
               String tail = t.substring(start);
               long opens = tail.chars().filter((c) -> c == 123).count();
               long closes = tail.chars().filter((c) -> c == 125).count();
               if (opens > closes) {
                  int var10002 = t.length();
                  throw new RuntimeException("model response was cut off before the JSON object finished (likely hit the token limit) -- response was " + var10002 + " chars, ending: ..." + t.substring(Math.max(0, t.length() - 200)));
               }
            }

            throw new RuntimeException("could not extract JSON from model response: " + t.substring(0, Math.min(200, t.length())));
         }
      } else {
         throw new IllegalArgumentException("empty response text");
      }
   }

   private JsonNode httpPostJson(String url, Map<String, String> headers, Map<String, Object> payload) throws Exception {
      String body = this.mapper.writeValueAsString(payload);
      HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(url)).timeout(HTTP_TIMEOUT).POST(BodyPublishers.ofString(body));
      Objects.requireNonNull(builder);
      headers.forEach(builder::header);
      HttpResponse<String> resp = this.httpClient.send(builder.build(), BodyHandlers.ofString());
      if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
         return this.mapper.readTree((String)resp.body());
      } else {
         int var10002 = resp.statusCode();
         throw new RuntimeException("HTTP " + var10002 + ": " + (String)resp.body());
      }
   }

   public Map openaiOcr(String apiKey, String frontMime, String frontB64, ServiceProviderOverride override) throws Exception {
      return this.openaiVisionCall(apiKey, this.ocrPrompt(), Collections.singletonList(new String[]{frontMime, frontB64}), 2200, override);
   }

   public Map openaiFaceMatch(String apiKey, String docMime, String docB64, String selfieMime, String selfieB64, ServiceProviderOverride override) throws Exception {
      return this.openaiVisionCall(apiKey, this.faceMatchPrompt(), List.of(new String[]{docMime, docB64}, new String[]{selfieMime, selfieB64}), 900, override);
   }

   private Map openaiVisionCall(String apiKey, String prompt, List<String[]> images, int maxTokens, ServiceProviderOverride override) throws Exception {
      String effectiveKey = override != null && override.apiKey() != null ? override.apiKey() : apiKey;
      String effectiveModel = override != null && override.model() != null ? override.model() : OPENAI_MODEL;
      String effectiveUrl = override != null && override.baseUrl() != null ? override.baseUrl() : "https://api.openai.com/v1/chat/completions";
      List<Map<String, Object>> content = new ArrayList();
      content.add(Map.of("type", "text", "text", prompt));

      for(String[] img : images) {
         content.add(Map.of("type", "image_url", "image_url", Map.of("url", "data:" + img[0] + ";base64," + img[1])));
      }

      Map<String, Object> payload = new HashMap();
      payload.put("model", effectiveModel);
      payload.put("messages", List.of(Map.of("role", "user", "content", content)));
      payload.put("max_tokens", maxTokens);
      payload.put("response_format", Map.of("type", "json_object"));
      Map<String, String> headers = Map.of("Authorization", "Bearer " + effectiveKey, "Content-Type", "application/json");
      JsonNode resp = this.httpPostJson(effectiveUrl, headers, payload);
      String text = resp.at("/choices/0/message/content").asText();
      return this.parseJsonLoose(text);
   }

   public Map claudeOcr(String apiKey, String frontMime, String frontB64, ServiceProviderOverride override) throws Exception {
      return this.claudeVisionCall(apiKey, this.ocrPrompt(), Collections.singletonList(new String[]{frontMime, frontB64}), 2200, override);
   }

   public Map claudeFaceMatch(String apiKey, String docMime, String docB64, String selfieMime, String selfieB64, ServiceProviderOverride override) throws Exception {
      return this.claudeVisionCall(apiKey, this.faceMatchPrompt(), List.of(new String[]{docMime, docB64}, new String[]{selfieMime, selfieB64}), 900, override);
   }

   private Map claudeVisionCall(String apiKey, String prompt, List<String[]> images, int maxTokens, ServiceProviderOverride override) throws Exception {
      String effectiveKey = override != null && override.apiKey() != null ? override.apiKey() : apiKey;
      String effectiveModel = override != null && override.model() != null ? override.model() : CLAUDE_MODEL;
      String effectiveUrl = override != null && override.baseUrl() != null ? override.baseUrl() : "https://api.anthropic.com/v1/messages";
      List<Map<String, Object>> content = new ArrayList();

      for(String[] img : images) {
         content.add(Map.of("type", "image", "source", Map.of("type", "base64", "media_type", img[0], "data", img[1])));
      }

      content.add(Map.of("type", "text", "text", prompt));
      Map<String, Object> payload = new HashMap();
      payload.put("model", effectiveModel);
      payload.put("max_tokens", maxTokens);
      payload.put("messages", List.of(Map.of("role", "user", "content", content)));
      Map<String, String> headers = Map.of("x-api-key", effectiveKey, "anthropic-version", "2023-06-01", "Content-Type", "application/json");
      JsonNode resp = this.httpPostJson(effectiveUrl, headers, payload);
      StringBuilder text = new StringBuilder();
      if (resp.has("content")) {
         for(JsonNode block : resp.get("content")) {
            if (block.has("text")) {
               text.append(block.get("text").asText());
            }
         }
      }

      return this.parseJsonLoose(text.toString());
   }
}
