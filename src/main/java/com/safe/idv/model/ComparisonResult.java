package com.safe.idv.model;

import java.util.List;

public class ComparisonResult {
   public Double overallMatchScore;
   public String overallStatus;
   public Integer mismatchCount;
   public List fields;

   public ComparisonResult() {
   }

   public ComparisonResult(Double overallMatchScore, String overallStatus, Integer mismatchCount, List fields) {
      this.overallMatchScore = overallMatchScore;
      this.overallStatus = overallStatus;
      this.mismatchCount = mismatchCount;
      this.fields = fields;
   }
}
