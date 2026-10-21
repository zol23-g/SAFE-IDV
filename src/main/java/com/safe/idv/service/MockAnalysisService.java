package com.safe.idv.service;

import com.safe.idv.model.ComparisonResult;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class MockAnalysisService {
   private static final List<String> OCR_NULL_KEYS = List.of("isDocumentDetected", "firstName", "middleName", "lastName", "fullName", "suffix", "namePrefix", "maidenName", "dateOfBirth", "sex", "height", "weight", "eyeColor", "hairColor", "addressLine1", "city", "stateProvince", "postalCode", "country", "documentNumber", "documentType", "issuingJurisdiction", "issueDate", "expirationDate", "documentClass", "restrictionCode", "endorsementsCode", "isRealId", "extractionConfidence", "tamperNotes");

   public Result build() {
      String note = "No live document/face analysis was performed -- this server has no OPENAI_API_KEY or ANTHROPIC_API_KEY configured (see README). This REVIEW reflects the absence of any real verification, not an assessment of the submitted images -- it must not be read as a pass or a fail.";
      Map<String, Object> ocr = new HashMap();

      for(String k : OCR_NULL_KEYS) {
         ocr.put(k, (Object)null);
      }

      ocr.put("qualityNotes", note);
      Map<String, Object> barcode = new HashMap();
      barcode.put("decodeStatus", "FAILED");
      barcode.put("decodeNotes", note);
      ComparisonResult comparison = new ComparisonResult((Double)null, "Not evaluated -- no live analysis configured.", (Integer)null, List.of());
      Map<String, Object> faceMatch = new HashMap();
      faceMatch.put("matchStatus", "UNABLE_TO_COMPARE");
      faceMatch.put("matchConfidence", (Object)null);
      faceMatch.put("falseAcceptanceRate", (Object)null);
      faceMatch.put("assessedAgeFromSelfie", (Object)null);
      faceMatch.put("assessedAgeFromDocument", (Object)null);
      faceMatch.put("isAgeCheckSuccess", (Object)null);
      faceMatch.put("isGenderCheckSuccess", (Object)null);
      faceMatch.put("isSuspiciousPhoto", false);
      faceMatch.put("antiSpoofingConfidence", (Object)null);
      faceMatch.put("isDataFieldsTampered", false);
      faceMatch.put("isPhotoFromDocumentTampered", false);
      faceMatch.put("aiNotes", note);
      Map<String, Object> flags = new HashMap();
      flags.put("isDocumentExpired", false);
      flags.put("isOcrSuccess", false);
      flags.put("isPdf417Success", false);
      flags.put("isDriversLicenseRealId", false);
      flags.put("isCommercialDriversLicense", false);
      flags.put("isDriveOnly", false);
      flags.put("isVeteran", false);
      flags.put("isDonor", false);
      flags.put("isLimitedDurationDocument", false);
      flags.put("isInvalidated", false);
      flags.put("isNonDomicile", false);
      flags.put("isEnhancedCredential", false);
      flags.put("isPermit", false);
      flags.put("isInterlockRestricted", false);
      flags.put("isSampleDocument", false);
      flags.put("documentConfidence", (Object)null);
      Map<String, Object> confidence = new HashMap();

      for(String k : List.of("firstName", "lastName", "dateOfBirth", "documentNumber", "expirationDate")) {
         confidence.put(k, -1);
      }

      return new Result("NEEDS_REVIEW", ocr, barcode, comparison, faceMatch, flags, confidence);
   }

   public static class Result {
      public final String status;
      public final Map ocrData;
      public final Map barcodeData;
      public final ComparisonResult comparison;
      public final Map faceMatch;
      public final Map documentFlags;
      public final Map confidenceScores;

      Result(String status, Map ocrData, Map barcodeData, ComparisonResult comparison, Map faceMatch, Map documentFlags, Map confidenceScores) {
         this.status = status;
         this.ocrData = ocrData;
         this.barcodeData = barcodeData;
         this.comparison = comparison;
         this.faceMatch = faceMatch;
         this.documentFlags = documentFlags;
         this.confidenceScores = confidenceScores;
      }
   }
}
