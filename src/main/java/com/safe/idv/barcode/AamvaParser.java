package com.safe.idv.barcode;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AamvaParser {
   private static final Map FIELD_MAP = new LinkedHashMap();
   private static final Pattern FIELD_PATTERN;
   private static final Pattern HEADER_PATTERN;

   private AamvaParser() {
   }

   static String aamvaDate(String raw) {
      if (raw == null) {
         return null;
      } else {
         raw = raw.strip();
         if (raw.length() == 8 && raw.chars().allMatch(Character::isDigit)) {
            String mm = raw.substring(0, 2);
            String dd = raw.substring(2, 4);
            String yyyy = raw.substring(4, 8);

            try {
               return LocalDate.of(Integer.parseInt(yyyy), Integer.parseInt(mm), Integer.parseInt(dd)).toString();
            } catch (NumberFormatException | DateTimeException var5) {
               return null;
            }
         } else {
            return null;
         }
      }
   }

   public static Map parse(String rawTextIn) {
      String rawText = rawTextIn.replace("<LF>", "\n").replace("<CR>", "\n").replace("\r\n", "\n").replace("\r", "\n");
      Map<String, String> fields = new LinkedHashMap();
      Matcher m = FIELD_PATTERN.matcher(rawText);

      while(m.find()) {
         String code = m.group(1);
         String value = m.group(2);
         if (value != null) {
            for(value = value.strip(); value.endsWith("\r"); value = value.substring(0, value.length() - 1)) {
            }
         }

         if (FIELD_MAP.containsKey(code) && value != null && !value.isEmpty()) {
            fields.put((String)FIELD_MAP.get(code), value);
         }
      }

      Map<String, Object> result = new LinkedHashMap(fields);
      if (result.containsKey("dateOfBirth")) {
         String d = aamvaDate((String)result.get("dateOfBirth"));
         if (d != null) {
            result.put("dateOfBirth", d);
         }
      }

      if (result.containsKey("expirationDate")) {
         String d = aamvaDate((String)result.get("expirationDate"));
         if (d != null) {
            result.put("expirationDate", d);
         }
      }

      if (result.containsKey("issueDate")) {
         String d = aamvaDate((String)result.get("issueDate"));
         if (d != null) {
            result.put("issueDate", d);
         }
      }

      if (result.containsKey("sex")) {
         String s = (String)result.get("sex");
         result.put("sex", "1".equals(s) ? "M" : ("2".equals(s) ? "F" : s));
      }

      if (result.containsKey("_complianceType")) {
         String v = (String)result.remove("_complianceType");
         result.put("isRealId", v != null && v.strip().equalsIgnoreCase("F"));
      }

      if (result.containsKey("_organDonorRaw")) {
         String v = (String)result.remove("_organDonorRaw");
         result.put("isDonor", v != null && v.strip().equals("1"));
      }

      if (result.containsKey("_veteranRaw")) {
         String v = (String)result.remove("_veteranRaw");
         result.put("isVeteran", v != null && v.strip().equals("1"));
      }

      if (result.containsKey("_limitedDurationRaw")) {
         String v = (String)result.remove("_limitedDurationRaw");
         result.put("isLimitedDurationDocument", v != null && v.strip().equals("1"));
      }

      Matcher hm = HEADER_PATTERN.matcher(rawText);
      if (hm.find()) {
         result.put("iin", hm.group(1));
         result.put("aamvaVersion", hm.group(2));
         result.put("jurisdictionVersion", hm.group(3));
      }

      return result;
   }

   static {
      FIELD_MAP.put("DAC", "firstName");
      FIELD_MAP.put("DAD", "middleName");
      FIELD_MAP.put("DCS", "lastName");
      FIELD_MAP.put("DBB", "dateOfBirth");
      FIELD_MAP.put("DBC", "sex");
      FIELD_MAP.put("DAG", "addressLine1");
      FIELD_MAP.put("DAI", "city");
      FIELD_MAP.put("DAJ", "stateProvince");
      FIELD_MAP.put("DAK", "postalCode");
      FIELD_MAP.put("DAQ", "documentNumber");
      FIELD_MAP.put("DCF", "documentDiscriminator");
      FIELD_MAP.put("DCK", "inventoryControlNumber");
      FIELD_MAP.put("DBA", "expirationDate");
      FIELD_MAP.put("DBD", "issueDate");
      FIELD_MAP.put("DCG", "country");
      FIELD_MAP.put("DCA", "documentClass");
      FIELD_MAP.put("DCB", "restrictionCode");
      FIELD_MAP.put("DCD", "endorsementsCode");
      FIELD_MAP.put("DDA", "_complianceType");
      FIELD_MAP.put("DDK", "_organDonorRaw");
      FIELD_MAP.put("DDL", "_veteranRaw");
      FIELD_MAP.put("DDD", "_limitedDurationRaw");
      FIELD_PATTERN = Pattern.compile("(D[A-Z]{2})([^\\nD]*(?:D(?![A-Z]{2}[A-Za-z])[^\\nD]*)*)");
      HEADER_PATTERN = Pattern.compile("ANSI ?(\\d{6})(\\d{2})(\\d{2})(\\d{2})");
   }
}
