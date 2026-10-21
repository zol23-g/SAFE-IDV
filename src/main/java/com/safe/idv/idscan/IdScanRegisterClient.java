package com.safe.idv.idscan;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;

public class IdScanRegisterClient {
   private static HttpClient newClient() {
      return HttpClient.newBuilder().version(Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(15L)).build();
   }

   public RegisterResult post(String url, String json, int timeoutSeconds, String authHeaderName, String authHeaderValue) {
      long t0 = System.currentTimeMillis();

      try {
         HttpRequest.Builder rb = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds((long)timeoutSeconds)).header("Content-Type", "application/json").header("Accept", "application/json").POST(BodyPublishers.ofString(json));
         if (authHeaderName != null && !authHeaderName.isBlank()) {
            rb.header(authHeaderName.strip(), authHeaderValue == null ? "" : authHeaderValue);
         }

         HttpResponse<String> resp = newClient().send(rb.build(), BodyHandlers.ofString());
         return new RegisterResult(resp.statusCode(), (String)resp.body(), (String)null, System.currentTimeMillis() - t0);
      } catch (InterruptedException ie) {
         Thread.currentThread().interrupt();
         return new RegisterResult((Integer)null, (String)null, "interrupted: " + String.valueOf(ie), System.currentTimeMillis() - t0);
      } catch (Exception e) {
         return new RegisterResult((Integer)null, (String)null, e.getClass().getSimpleName() + ": " + e.getMessage(), System.currentTimeMillis() - t0);
      }
   }

   public static final class RegisterResult {
      public final Integer httpStatus;
      public final String body;
      public final String transportError;
      public final long elapsedMs;

      RegisterResult(Integer httpStatus, String body, String transportError, long elapsedMs) {
         this.httpStatus = httpStatus;
         this.body = body;
         this.transportError = transportError;
         this.elapsedMs = elapsedMs;
      }

      public boolean gotResponse() {
         return this.transportError == null;
      }
   }
}
