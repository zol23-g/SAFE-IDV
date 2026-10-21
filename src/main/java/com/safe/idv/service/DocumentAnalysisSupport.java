package com.safe.idv.service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class DocumentAnalysisSupport {
   public Map documentFlagsFromOcr(Map ocrIn, Map barcodeIn) {
      Map<String, Object> ocr = ocrIn != null ? ocrIn : Map.of();
      Map<String, Object> barcode = barcodeIn != null ? barcodeIn : Map.of();
      boolean expired = false;
      Object expObj = ocr.get("expirationDate");
      if (expObj != null) {
         try {
            expired = LocalDate.parse(expObj.toString()).isBefore(LocalDate.now());
         } catch (Exception var8) {
         }
      }

      Map<String, Object> flags = new HashMap();
      flags.put("isDocumentExpired", expired);
      flags.put("isOcrSuccess", !ocr.isEmpty());
      flags.put("isPdf417Success", (Object)null);
      flags.put("isDriversLicenseRealId", truthy(ocr.get("isRealId")) || truthy(barcode.get("isRealId")));
      flags.put("isCommercialDriversLicense", truthy(ocr.get("isCommercialDriversLicense")));
      flags.put("isDriveOnly", false);
      flags.put("isVeteran", truthy(ocr.get("isVeteran")) || truthy(barcode.get("isVeteran")));
      flags.put("isDonor", truthy(ocr.get("isDonor")) || truthy(barcode.get("isDonor")));
      flags.put("isLimitedDurationDocument", truthy(ocr.get("isLimitedDurationDocument")) || truthy(barcode.get("isLimitedDurationDocument")));
      flags.put("isInvalidated", truthy(ocr.get("isInvalidated")));
      flags.put("isNonDomicile", truthy(ocr.get("isNonDomicile")));
      flags.put("isEnhancedCredential", truthy(ocr.get("isEnhancedCredential")));
      flags.put("isPermit", truthy(ocr.get("isPermit")));
      flags.put("isInterlockRestricted", truthy(ocr.get("isInterlockRestricted")));
      flags.put("isSampleDocument", truthy(ocr.get("isSampleDocument")));
      flags.put("documentConfidence", ocr.get("extractionConfidence"));
      return flags;
   }

   public Map confidenceScoresFromOcr(Map ocr) {
      Object conf = ocr != null ? ocr.get("extractionConfidence") : null;
      Map<String, Object> out = new HashMap();

      for(String k : List.of("firstName", "lastName", "dateOfBirth", "documentNumber", "expirationDate")) {
         out.put(k, conf != null ? conf : -1);
      }

      return out;
   }

   private static boolean truthy(Object v) {
      if (v == null) {
         return false;
      } else if (v instanceof Boolean) {
         return (Boolean)v;
      } else if (v instanceof String) {
         return !((String)v).isBlank();
      } else {
         return true;
      }
   }
}
