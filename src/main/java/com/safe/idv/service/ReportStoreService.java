package com.safe.idv.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ReportStoreService {
   private static final Pattern VALID_SESSION_ID = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");
   private final Path reportsDir;

   public ReportStoreService(@Value("${safe.reports-dir}") String reportsDir) {
      this.reportsDir = Path.of(reportsDir).toAbsolutePath().normalize();
   }

   @PostConstruct
   void init() throws IOException {
      Files.createDirectories(this.reportsDir);
   }

   public static boolean isValidSessionId(String sessionId) {
      return sessionId != null && VALID_SESSION_ID.matcher(sessionId).matches();
   }

   public ReportMeta save(String sessionId, String html) {
      if (!isValidSessionId(sessionId)) {
         throw new IllegalArgumentException("Invalid session id: " + sessionId);
      } else if (html != null && !html.isBlank()) {
         Path target = this.reportsDir.resolve(sessionId + ".html");

         try {
            Files.writeString(target, html, StandardCharsets.UTF_8);
         } catch (IOException e) {
            throw new UncheckedIOException("Failed to write report for session " + sessionId, e);
         }

         return this.toMeta(target);
      } else {
         throw new IllegalArgumentException("Report HTML body was empty.");
      }
   }

   public boolean delete(String sessionId) {
      if (!isValidSessionId(sessionId)) {
         throw new IllegalArgumentException("Invalid session id: " + sessionId);
      } else {
         Path target = this.reportsDir.resolve(sessionId + ".html");

         try {
            return Files.deleteIfExists(target);
         } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete report for session " + sessionId, e);
         }
      }
   }

   public List list() {
      try {
         Stream<Path> files = Files.list(this.reportsDir);

         List var2;
         try {
            var2 = (List)files.filter((p) -> p.toString().endsWith(".html")).map(this::toMeta).sorted(Comparator.comparing(ReportMeta::savedAtEpochMs).reversed()).collect(Collectors.toList());
         } catch (Throwable var5) {
            if (files != null) {
               try {
                  files.close();
               } catch (Throwable var4) {
                  var5.addSuppressed(var4);
               }
            }

            throw var5;
         }

         if (files != null) {
            files.close();
         }

         return var2;
      } catch (IOException e) {
         throw new UncheckedIOException("Failed to list reports directory: " + String.valueOf(this.reportsDir), e);
      }
   }

   private ReportMeta toMeta(Path file) {
      try {
         String filename = file.getFileName().toString();
         String sessionId = filename.substring(0, filename.length() - ".html".length());
         long sizeBytes = Files.size(file);
         long savedAtEpochMs = Files.getLastModifiedTime(file).toMillis();
         String savedAtIso = Instant.ofEpochMilli(savedAtEpochMs).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
         return new ReportMeta(sessionId, "/reports/" + filename, savedAtIso, savedAtEpochMs, sizeBytes);
      } catch (IOException e) {
         throw new UncheckedIOException("Failed to read report metadata: " + String.valueOf(file), e);
      }
   }

   public static final class ReportMeta {
      private final String sessionId;
      private final String url;
      private final String savedAt;
      private final long savedAtEpochMs;
      private final long sizeBytes;

      public ReportMeta(String sessionId, String url, String savedAt, long savedAtEpochMs, long sizeBytes) {
         this.sessionId = sessionId;
         this.url = url;
         this.savedAt = savedAt;
         this.savedAtEpochMs = savedAtEpochMs;
         this.sizeBytes = sizeBytes;
      }

      public String sessionId() {
         return this.sessionId;
      }

      public String url() {
         return this.url;
      }

      public String savedAt() {
         return this.savedAt;
      }

      public long savedAtEpochMs() {
         return this.savedAtEpochMs;
      }

      public long sizeBytes() {
         return this.sizeBytes;
      }
   }
}
