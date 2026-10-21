package com.safe.idv.service;

import com.safe.idv.model.ComparisonResult;
import com.safe.idv.model.FieldComparison;
import com.safe.idv.util.SequenceMatcherRatio;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ComparisonService {
   private static final String[][] COMPARABLE_FIELDS = new String[][]{{"firstName", "firstName"}, {"lastName", "lastName"}, {"dateOfBirth", "dateOfBirth"}, {"documentNumber", "documentNumber"}, {"expirationDate", "expirationDate"}, {"addressLine1", "addressLine1"}, {"city", "city"}, {"stateProvince", "stateProvince"}, {"postalCode", "postalCode"}, {"issuingJurisdiction", "issuingJurisdiction"}};
   private static final Set ADDRESS_LIKE_FIELDS = Set.of("addressLine1", "city");
   private static final Map STREET_ABBREV = Map.ofEntries(Map.entry("STREET", "ST"), Map.entry("AVENUE", "AVE"), Map.entry("BOULEVARD", "BLVD"), Map.entry("DRIVE", "DR"), Map.entry("ROAD", "RD"), Map.entry("LANE", "LN"), Map.entry("COURT", "CT"), Map.entry("PLACE", "PL"), Map.entry("CIRCLE", "CIR"), Map.entry("TERRACE", "TER"), Map.entry("PARKWAY", "PKWY"), Map.entry("HIGHWAY", "HWY"), Map.entry("SQUARE", "SQ"), Map.entry("TRAIL", "TRL"), Map.entry("APARTMENT", "APT"), Map.entry("SUITE", "STE"), Map.entry("UNIT", "UNIT"), Map.entry("BUILDING", "BLDG"), Map.entry("FLOOR", "FL"), Map.entry("NUMBER", "NO"), Map.entry("NORTH", "N"), Map.entry("SOUTH", "S"), Map.entry("EAST", "E"), Map.entry("WEST", "W"), Map.entry("NORTHEAST", "NE"), Map.entry("NORTHWEST", "NW"), Map.entry("SOUTHEAST", "SE"), Map.entry("SOUTHWEST", "SW"));
   private static final Pattern WHITESPACE = Pattern.compile("\\s+");
   private static final Pattern ADDRESS_PUNCT = Pattern.compile("[.,#]");
   private static final Pattern ZIP_STRIP = Pattern.compile("[\\s-]");
   private static final Pattern HOUSE_NUMBER = Pattern.compile("^(\\d+[A-Za-z]?)\\s+(.*)$");

   private ComparisonService() {
   }

   public static String norm(Object v) {
      return v == null ? null : WHITESPACE.matcher(v.toString().strip().toUpperCase()).replaceAll(" ");
   }

   public static String normalizeAddress(Object v) {
      if (v == null) {
         return null;
      } else {
         String s = ADDRESS_PUNCT.matcher(v.toString().toUpperCase()).replaceAll(" ");
         s = WHITESPACE.matcher(s).replaceAll(" ").strip();
         StringBuilder out = new StringBuilder();

         for(String tok : s.split(" ")) {
            if (out.length() > 0) {
               out.append(' ');
            }

            out.append((String)STREET_ABBREV.getOrDefault(tok, tok));
         }

         return out.toString();
      }
   }

   public static String normalizePostal(Object v) {
      if (v == null) {
         return null;
      } else {
         String s = ZIP_STRIP.matcher(v.toString()).replaceAll("");
         return !s.isEmpty() && s.chars().allMatch(Character::isDigit) && s.length() > 5 ? s.substring(0, 5) : s.toUpperCase().strip();
      }
   }

   public static double similarity(String a, String b) {
      return a != null && !a.isEmpty() && b != null && !b.isEmpty() ? SequenceMatcherRatio.ratio(a, b) : (double)0.0F;
   }

   static String[] splitHouseNumber(String s) {
      if (s != null && !s.isEmpty()) {
         Matcher m = HOUSE_NUMBER.matcher(s);
         return m.matches() ? new String[]{m.group(1), m.group(2)} : new String[]{null, s};
      } else {
         return new String[]{null, s};
      }
   }

   public static ComparisonResult compareThreeWay(Map applicant, Map ocr, Map barcode) {
      applicant = applicant != null ? applicant : Map.of();
      ocr = ocr != null ? ocr : Map.of();
      barcode = barcode != null ? barcode : Map.of();
      List<FieldComparison> fieldResults = new ArrayList();
      int matchCount = 0;
      int comparableCount = 0;

      for(String[] pair : COMPARABLE_FIELDS) {
         String appKey = pair[0];
         String ocrKey = pair[1];
         boolean isAddressLike = ADDRESS_LIKE_FIELDS.contains(appKey);
         String appVal;
         String ocrVal;
         String bcVal;
         if (isAddressLike) {
            appVal = normalizeAddress(applicant.get(appKey));
            ocrVal = normalizeAddress(ocr.get(ocrKey));
            bcVal = normalizeAddress(barcode.get(ocrKey));
         } else if (appKey.equals("postalCode")) {
            appVal = normalizePostal(applicant.get(appKey));
            ocrVal = normalizePostal(ocr.get(ocrKey));
            bcVal = normalizePostal(barcode.get(ocrKey));
         } else {
            appVal = norm(applicant.get(appKey));
            ocrVal = norm(ocr.get(ocrKey));
            bcVal = norm(barcode.get(ocrKey));
         }

         Set<String> present = new LinkedHashSet();
         int presentCount = 0;
         if (appVal != null) {
            present.add(appVal);
            ++presentCount;
         }

         if (ocrVal != null) {
            present.add(ocrVal);
            ++presentCount;
         }

         if (bcVal != null) {
            present.add(bcVal);
            ++presentCount;
         }

         String status;
         if (presentCount < 2) {
            status = "MISSING";
         } else if (present.size() == 1) {
            status = "MATCH";
         } else if (appVal != null && ocrVal != null && !appVal.equals(ocrVal)) {
            if (isAddressLike) {
               String[] appSplit = splitHouseNumber(appVal);
               String[] ocrSplit = splitHouseNumber(ocrVal);
               String appNum = appSplit[0];
               String appRest = appSplit[1];
               String ocrNum = ocrSplit[0];
               String ocrRest = ocrSplit[1];
               if (appNum != null && ocrNum != null && !appNum.equals(ocrNum)) {
                  status = "MISMATCH";
               } else {
                  double sim = similarity(appRest, ocrRest);
                  status = sim >= 0.92 ? "MATCH" : (sim >= (double)0.75F ? "PARTIAL" : "MISMATCH");
               }
            } else {
               status = "MISMATCH";
            }
         } else {
            status = "PARTIAL";
         }

         if (status.equals("MATCH") || status.equals("PARTIAL")) {
            ++matchCount;
         }

         if (!status.equals("MISSING")) {
            ++comparableCount;
         }

         fieldResults.add(new FieldComparison(appKey, applicant.get(appKey), ocr.get(ocrKey), barcode.get(ocrKey), status));
      }

      double score = comparableCount > 0 ? (double)Math.round((double)1000.0F * (double)matchCount / (double)comparableCount) / (double)10.0F : (double)0.0F;
      long mismatches = fieldResults.stream().filter((f) -> f.matchStatus.equals("MISMATCH")).count();
      String overallStatus = score >= (double)90.0F ? "Fields largely consistent" : (score >= (double)60.0F ? "Some fields inconsistent" : "Significant field inconsistencies");
      return new ComparisonResult(score, overallStatus, (int)mismatches, fieldResults);
   }
}
