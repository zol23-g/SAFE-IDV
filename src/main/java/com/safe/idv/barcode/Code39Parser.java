package com.safe.idv.barcode;

import java.util.LinkedHashMap;
import java.util.Map;

public final class Code39Parser {
   private Code39Parser() {
   }

   public static Map parse(String rawText) {
      String text = rawText == null ? "" : rawText.strip();
      if (text.isEmpty()) {
         return new LinkedHashMap();
      } else {
         Map<String, Object> out = new LinkedHashMap();
         out.put("documentNumber", text);
         out.put("rawBarcodeText", text);
         return out;
      }
   }
}
