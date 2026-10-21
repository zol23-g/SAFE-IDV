package com.safe.idv.idscan;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class IdScanPayloadBuilder {
   private static final Pattern WIN = Pattern.compile("Windows NT ([0-9.]+)");
   private static final Pattern IOS = Pattern.compile("(?:iPhone|CPU) OS ([0-9_]+)");
   private static final Pattern MAC = Pattern.compile("Mac OS X ([0-9_.]+)");
   private static final Pattern AND = Pattern.compile("Android ([0-9.]+)");
   private static final String[] SIGNATURE_FIELDS = new String[]{"canvasFingerprint", "webglRenderer", "webglVendor", "platform", "hardwareConcurrency", "deviceMemory", "screenWidth", "screenHeight", "devicePixelRatio", "colorDepth", "maxTouchPoints", "timezone", "language", "cameraDeviceId", "userAgent"};

   private IdScanPayloadBuilder() {
   }

   public static String idTypeFor(String docType) {
      if (docType == null) {
         return null;
      } else {
         switch (docType.trim().toLowerCase()) {
            case "drivers_license":
               return "DL";
            case "state_id":
               return "IC";
            case "passport_card":
               return "PassportCard";
            case "passport":
               return "PASSPORT";
            case "other":
               return "OTHERS";
            default:
               return null;
         }
      }
   }

   public static boolean sendsBackImage(String idType) {
      return idType != null && !"PASSPORT".equals(idType);
   }

   public static String stripDataUri(Object v) {
      if (!(v instanceof String)) {
         return null;
      } else {
         String s = ((String)v).trim();
         if (s.isEmpty()) {
            return null;
         } else {
            if (s.startsWith("data:")) {
               int comma = s.indexOf(44);
               if (comma < 0) {
                  return null;
               }

               s = s.substring(comma + 1);
            }

            return s.isEmpty() ? null : s;
         }
      }
   }

   public static String mimeOf(Object v) {
      if (v instanceof String) {
         String s = (String)v;
         if (s.startsWith("data:")) {
            int semi = s.indexOf(59);
            int comma = s.indexOf(44);
            int end = semi <= 0 || comma >= 0 && semi >= comma ? comma : semi;
            if (end > 5) {
               return s.substring(5, end);
            }
         }
      }

      return "image/jpeg";
   }

   public static String coord(Object v) {
      if (v == null) {
         return "";
      } else if (v instanceof Number) {
         double d = ((Number)v).doubleValue();
         return !Double.isNaN(d) && !Double.isInfinite(d) ? (new BigDecimal(v.toString())).toPlainString() : "";
      } else {
         String s = v.toString().trim();
         if (s.isEmpty()) {
            return "";
         } else {
            try {
               return (new BigDecimal(s)).toPlainString();
            } catch (NumberFormatException var3) {
               return "";
            }
         }
      }
   }

   public static Map parseUserAgent(String ua, String platform) {
      return parseUserAgent(ua, platform, (Map)null);
   }

   public static Map parseUserAgent(String ua, String platform, Map uaData) {
      String u = ua == null ? "" : ua;
      String os = null;
      String osVersion = null;
      String browser = null;
      String browserVersion = null;
      String deviceType = "desktop";
      Matcher m;
      if ((m = IOS.matcher(u)).find() && (u.contains("iPhone") || u.contains("iPad") || u.contains("iPod"))) {
         os = "iOS";
         osVersion = m.group(1).replace('_', '.');
         deviceType = u.contains("iPad") ? "tablet" : "phone";
      } else if ((m = AND.matcher(u)).find()) {
         os = "Android";
         osVersion = m.group(1);
         deviceType = u.contains("Mobile") ? "phone" : "tablet";
      } else if ((m = WIN.matcher(u)).find()) {
         os = "Windows";
         osVersion = m.group(1);
      } else if (u.contains("CrOS")) {
         os = "ChromeOS";
      } else if ((m = MAC.matcher(u)).find()) {
         os = "macOS";
         osVersion = m.group(1).replace('_', '.');
      } else if (u.contains("Linux")) {
         os = "Linux";
      }

      String[][] rules = new String[][]{{"Edg/", "Edge"}, {"EdgA/", "Edge"}, {"EdgiOS/", "Edge"}, {"OPR/", "Opera"}, {"SamsungBrowser/", "Samsung Internet"}, {"CriOS/", "Chrome"}, {"FxiOS/", "Firefox"}, {"Firefox/", "Firefox"}, {"Chrome/", "Chrome"}};

      for(String[] r : rules) {
         int i = u.indexOf(r[0]);
         if (i >= 0) {
            browser = r[1];
            browserVersion = versionAfter(u, i + r[0].length());
            break;
         }
      }

      if (browser == null && u.contains("Safari/")) {
         int i = u.indexOf("Version/");
         browser = "Safari";
         browserVersion = i >= 0 ? versionAfter(u, i + "Version/".length()) : null;
      }

      Map<String, Object> out = new LinkedHashMap();
      out.put("os", os);
      out.put("osVersion", osVersion);
      out.put("browser", browser);
      out.put("browserVersion", browserVersion);
      out.put("deviceType", deviceType);
      out.put("platform", platform);
      out.put("userAgent", ua);
      out.put("summary", summary(os, osVersion, browser, browserVersion, uaData));
      return out;
   }

   static String summary(String os, String osVersion, String browser, String browserVersion, Map uaData) {
      String osLabel = null;
      Integer hintMajor = null;
      Integer hintMinor = null;
      if (uaData != null && uaData.get("platformVersion") != null) {
         String[] parts = uaData.get("platformVersion").toString().split("\\.");

         try {
            hintMajor = Integer.parseInt(parts[0]);
            if (parts.length > 1) {
               hintMinor = Integer.parseInt(parts[1]);
            }
         } catch (NumberFormatException var12) {
            hintMajor = null;
            hintMinor = null;
         }
      }

      if ("Windows".equals(os)) {
         if (hintMajor != null) {
            osLabel = hintMajor >= 13 ? "Windows 11" : "Windows 10";
         } else if ("10.0".equals(osVersion)) {
            osLabel = "Windows 10/11";
         } else {
            String var10000 = osVersion == null ? "" : " NT " + osVersion;
            osLabel = "Windows" + var10000;
         }
      } else if ("macOS".equals(os)) {
         osLabel = hintMajor != null ? "macOS " + hintMajor + (hintMinor != null ? "." + hintMinor : "") : "macOS";
      } else if (!"iOS".equals(os) && !"Android".equals(os)) {
         if (os != null) {
            osLabel = os;
         }
      } else {
         osLabel = os + (osVersion == null ? "" : " " + majorMinor(osVersion));
      }

      String browserLabel = null;
      if (browser != null) {
         String name;
         switch (browser) {
            case "Chrome":
               name = "Google Chrome";
               break;
            case "Edge":
               name = "Microsoft Edge";
               break;
            case "Firefox":
               name = "Mozilla Firefox";
               break;
            default:
               name = browser;
         }

         browserLabel = name + (browserVersion == null ? "" : " " + major(browserVersion));
      }

      if (osLabel != null && browserLabel != null) {
         return osLabel + ", " + browserLabel;
      } else if (osLabel != null) {
         return osLabel;
      } else {
         return browserLabel != null ? browserLabel : "Unknown";
      }
   }

   private static String major(String v) {
      int i = v.indexOf(46);
      return i < 0 ? v : v.substring(0, i);
   }

   private static String majorMinor(String v) {
      String[] p = v.split("\\.");
      return p.length >= 2 ? p[0] + "." + p[1] : v;
   }

   private static String versionAfter(String s, int from) {
      int end;
      for(end = from; end < s.length() && (Character.isDigit(s.charAt(end)) || s.charAt(end) == '.'); ++end) {
      }

      return end > from ? s.substring(from, end) : null;
   }

   public static Map signatureInputs(Map device) {
      Map<String, Object> in = new LinkedHashMap();
      if (device == null) {
         return in;
      } else {
         for(String k : SIGNATURE_FIELDS) {
            if (!k.equals("webglRenderer") && !k.equals("webglVendor")) {
               in.put(k, device.get(k));
            }
         }

         Object gl = device.get("webgl");
         if (gl instanceof Map) {
            in.put("webglRenderer", ((Map)gl).get("renderer"));
            in.put("webglVendor", ((Map)gl).get("vendor"));
         } else {
            in.put("webglRenderer", (Object)null);
            in.put("webglVendor", (Object)null);
         }

         return in;
      }
   }

   public static String deviceSignature(String salt, Map device) {
      Map<String, Object> in = signatureInputs(device);
      StringBuilder canon = new StringBuilder();

      for(String k : SIGNATURE_FIELDS) {
         Object v = in.get(k);
         canon.append(k).append('=').append(v == null ? "" : v.toString()).append('|');
      }

      try {
         MessageDigest md = MessageDigest.getInstance("SHA-256");
         md.update((salt == null ? "" : salt).getBytes(StandardCharsets.UTF_8));
         md.update((byte)124);
         byte[] h = md.digest(canon.toString().getBytes(StandardCharsets.UTF_8));
         StringBuilder hex = new StringBuilder(h.length * 2);

         for(byte b : h) {
            hex.append(String.format("%02x", b));
         }

         return hex.toString();
      } catch (Exception e) {
         throw new IllegalStateException("SHA-256 unavailable", e);
      }
   }

   public static Map buildRegisterPayload(String magicLinkToken, String idType, Object gpsLat, Object gpsLon, Object ipLat, Object ipLon, String ip, String deviceSignature, String deviceOsBrowserInfo, String selfieB64, String idB64, String idBackB64) {
      Map<String, Object> p = new LinkedHashMap();
      p.put("magic_link_token", magicLinkToken);
      p.put("latitude", coord(gpsLat));
      p.put("longitude", coord(gpsLon));
      p.put("latitude_ip", coord(ipLat));
      p.put("longitude_ip", coord(ipLon));
      p.put("ip", ip == null ? "" : ip);
      p.put("location_distance", "");
      p.put("device_digital_signature", deviceSignature);
      p.put("device_os_browser_info", deviceOsBrowserInfo);
      p.put("id_type", idType);
      p.put("base64_selfie", selfieB64);
      p.put("base64_id", idB64);
      if (idBackB64 != null) {
         p.put("base64_id_back", idBackB64);
      }

      return p;
   }
}
