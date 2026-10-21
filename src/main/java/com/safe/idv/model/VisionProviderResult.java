package com.safe.idv.model;

import java.util.Map;

public class VisionProviderResult {
   public boolean available;
   public String error;
   public Map ocrData;
   public Map faceMatch;
   public ComparisonResult comparison;
   public Map documentFlags;
   public Map confidenceScores;
}
