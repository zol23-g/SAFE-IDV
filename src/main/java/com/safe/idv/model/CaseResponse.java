package com.safe.idv.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.util.Map;

public class CaseResponse {
   public int caseId;
   public String caseNumber;
   public String status;
   public String createdAt;
   public String analysisMessage;
   public Map applicantData;
   public Map ocrData;
   public Map barcodeData;
   public ComparisonResult comparison;
   public Map faceMatch;
   public Map documentFlags;
   public Map confidenceScores;
   @JsonInclude(Include.NON_NULL)
   public Map visionProviders;
   public String error;
   @JsonInclude(Include.NON_NULL)
   public Boolean mockMode;
   @JsonInclude(Include.NON_NULL)
   public Map captureSignals;
   public Map ipGeolocation;
}
