package com.safe.idv.barcode;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;

@Service
public class BarcodeDecodeService {
   public Map decode(String backDataUri, String docType) {
      if ("other".equals(docType)) {
         Map<String, Object> out = new LinkedHashMap();
         out.put("decodeStatus", "NOT_ATTEMPTED");
         out.put("decodeNotes", "No structured barcode format is defined for this document type.");
         return out;
      } else {
         boolean isCard = "passport_card".equals(docType);
         BarcodeFormat format = isCard ? BarcodeFormat.CODE_39 : BarcodeFormat.PDF_417;
         String formatLabel = isCard ? "Code 39" : "PDF417";
         if (backDataUri != null && !backDataUri.isBlank()) {
            try {
               String mime = "image/jpeg";
               String b64 = backDataUri;
               if (backDataUri.startsWith("data:")) {
                  int comma = backDataUri.indexOf(44);
                  String header = backDataUri.substring(5, comma);
                  mime = header.split(";")[0];
                  b64 = backDataUri.substring(comma + 1);
               }

               byte[] raw = Base64.getDecoder().decode(b64);
               double receivedKb = (double)raw.length / (double)1024.0F;
               BufferedImage img = ImageIO.read(new ByteArrayInputStream(raw));
               if (img == null) {
                  return this.failure("Could not decode image bytes (unsupported format or corrupt data). mime=" + mime + ", size_on_wire=" + Math.round(receivedKb) + "KB.");
               } else {
                  int width = img.getWidth();
                  int height = img.getHeight();
                  BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(img)));
                  Map<DecodeHintType, Object> hints = new EnumMap(DecodeHintType.class);
                  hints.put(DecodeHintType.POSSIBLE_FORMATS, List.of(format));
                  hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);

                  Result result;
                  try {
                     result = (new MultiFormatReader()).decode(bitmap, hints);
                  } catch (NotFoundException var23) {
                     return this.failure("No " + formatLabel + " barcode detected. Received: mime=" + mime + ", size_on_wire=" + Math.round(receivedKb) + "KB, dimensions=" + width + "x" + height + ". This usually means the photo needs to be closer/sharper/better-lit, or the barcode area is cropped out of frame.");
                  }

                  String rawText = result.getText();
                  Map<String, Object> parsed = isCard ? Code39Parser.parse(rawText) : AamvaParser.parse(rawText);
                  boolean foundAnyFields = !parsed.isEmpty();
                  Map<String, Object> out = new LinkedHashMap(parsed);
                  out.put("decodeStatus", foundAnyFields ? "SUCCESS" : "PARTIAL");
                  if (foundAnyFields) {
                     out.put("decodeNotes", (Object)null);
                  } else {
                     String snippet = rawText.length() > 200 ? rawText.substring(0, 200) : rawText;
                     snippet = snippet.replace("\n", "\\n");
                     String fieldLabel = isCard ? "Code 39" : "AAMVA";
                     out.put("decodeNotes", "Barcode detected but no recognized " + fieldLabel + " fields parsed (best-effort parser -- not spec-complete). Raw barcode text (first 200 chars): " + snippet);
                  }

                  return out;
               }
            } catch (Exception e) {
               return this.failure("Barcode decode error: " + String.valueOf(e));
            }
         } else {
            return this.failure("No back image provided.");
         }
      }
   }

   private Map failure(String notes) {
      Map<String, Object> m = new LinkedHashMap();
      m.put("decodeStatus", "FAILED");
      m.put("decodeNotes", notes);
      return m;
   }
}
