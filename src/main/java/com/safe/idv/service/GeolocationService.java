package com.safe.idv.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

@Service
public class GeolocationService {
   private static final String IP_GEO_SOURCE = "ip-api.com";
   private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4L)).build();
   private final ObjectMapper mapper = new ObjectMapper();

   public String clientIpFor(HttpServletRequest request) {
      String xff = request.getHeader("X-Forwarded-For");
      return xff != null && !xff.isBlank() ? xff.split(",")[0].strip() : request.getRemoteAddr();
   }

   private boolean isGeolocatableIp(String ip) {
      if (ip != null && !ip.isBlank()) {
         try {
            InetAddress addr = InetAddress.getByName(ip);
            return !addr.isSiteLocalAddress() && !addr.isLoopbackAddress() && !addr.isLinkLocalAddress() && !addr.isAnyLocalAddress() && !addr.isMulticastAddress();
         } catch (UnknownHostException var3) {
            return false;
         }
      } else {
         return false;
      }
   }

   public Map geolocate(String ip, boolean liveMode) {
      Map<String, Object> out = new LinkedHashMap();
      if (!liveMode) {
         out.put("status", "mock_mode");
         out.put("ip", ip);
         out.put("reason", "Mock Mode makes no outbound network calls by design -- IP geolocation is not attempted for this session. This is expected, not a failure.");
         out.put("source", "ip-api.com");
         return out;
      } else if (!this.isGeolocatableIp(ip)) {
         out.put("status", "unavailable");
         out.put("ip", ip);
         out.put("reason", "This request arrived from a private, loopback, or otherwise non-public IP address (e.g. localhost or same-Wi-Fi LAN testing without a public tunnel) -- it cannot be geolocated.");
         out.put("source", "ip-api.com");
         return out;
      } else {
         try {
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create("http://ip-api.com/json/" + ip + "?fields=status,message,country,regionName,city,lat,lon,isp,query")).timeout(Duration.ofSeconds(4L)).GET().build();
            HttpResponse<String> resp = CLIENT.send(req, BodyHandlers.ofString());
            JsonNode data = this.mapper.readTree((String)resp.body());
            if (!"success".equals(data.path("status").asText((String)null))) {
               String reason = "ip-api.com could not resolve this address";
               if (data.hasNonNull("message")) {
                  reason = reason + ": " + data.path("message").asText();
               }

               out.put("status", "unavailable");
               out.put("ip", ip);
               out.put("reason", reason + ".");
               out.put("source", "ip-api.com");
               return out;
            } else {
               out.put("status", "resolved");
               out.put("ip", data.hasNonNull("query") ? data.path("query").asText() : ip);
               out.put("lat", data.hasNonNull("lat") ? data.path("lat").asDouble() : null);
               out.put("lon", data.hasNonNull("lon") ? data.path("lon").asDouble() : null);
               out.put("city", data.hasNonNull("city") ? data.path("city").asText() : null);
               out.put("region", data.hasNonNull("regionName") ? data.path("regionName").asText() : null);
               out.put("country", data.hasNonNull("country") ? data.path("country").asText() : null);
               out.put("isp", data.hasNonNull("isp") ? data.path("isp").asText() : null);
               out.put("source", "ip-api.com");
               return out;
            }
         } catch (Exception e) {
            out.put("status", "error");
            out.put("ip", ip);
            out.put("reason", "IP geolocation lookup failed: " + String.valueOf(e));
            out.put("source", "ip-api.com");
            return out;
         }
      }
   }
}
