package com.safe.idv.model;

import java.util.Map;

public class LiveAnalysisResult {
   public Map<String, Object> ocrData;
   public Map<String, Object> faceMatch;
   public ComparisonResult comparison;
   public Map<String, Object> documentFlags;
   public Map<String, Object> confidenceScores;
   public Map<String, Object> barcodeData;
   public Map<String, VisionProviderResult> visionProviders;
}
