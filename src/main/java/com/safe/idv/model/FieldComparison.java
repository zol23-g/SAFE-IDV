package com.safe.idv.model;

public class FieldComparison {
   public String fieldName;
   public Object applicationValue;
   public Object ocrValue;
   public Object barcodeValue;
   public String matchStatus;

   public FieldComparison(String fieldName, Object applicationValue, Object ocrValue, Object barcodeValue, String matchStatus) {
      this.fieldName = fieldName;
      this.applicationValue = applicationValue;
      this.ocrValue = ocrValue;
      this.barcodeValue = barcodeValue;
      this.matchStatus = matchStatus;
   }
}
