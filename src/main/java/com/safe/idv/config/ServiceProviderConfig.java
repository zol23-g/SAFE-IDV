package com.safe.idv.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ServiceProviderConfig {
   private final ServiceProviderOverride ocrOverride;
   private final ServiceProviderOverride faceMatchOverride;

   public ServiceProviderConfig(@Value("${safe.ocr.provider:}") String ocrProvider, @Value("${safe.ocr.api-key:}") String ocrApiKey, @Value("${safe.ocr.base-url:}") String ocrBaseUrl, @Value("${safe.ocr.model:}") String ocrModel, @Value("${safe.facematch.provider:}") String fmProvider, @Value("${safe.facematch.api-key:}") String fmApiKey, @Value("${safe.facematch.base-url:}") String fmBaseUrl, @Value("${safe.facematch.model:}") String fmModel) {
      this.ocrOverride = new ServiceProviderOverride(blankToNull(ocrProvider), blankToNull(ocrApiKey), blankToNull(ocrBaseUrl), blankToNull(ocrModel));
      this.faceMatchOverride = new ServiceProviderOverride(blankToNull(fmProvider), blankToNull(fmApiKey), blankToNull(fmBaseUrl), blankToNull(fmModel));
   }

   private static String blankToNull(String s) {
      return s != null && !s.isBlank() ? s.strip() : null;
   }

   public ServiceProviderOverride ocrOverride() {
      return this.ocrOverride;
   }

   public ServiceProviderOverride faceMatchOverride() {
      return this.faceMatchOverride;
   }
}
