package com.safe.idv.idscan;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

public class IdScanCaseStore {
   private final Path dir;

   public IdScanCaseStore(Path dir) {
      this.dir = dir.toAbsolutePath();
   }

   public Path dir() {
      return this.dir;
   }

   public Path caseFile(String reference) {
      return this.dir.resolve(safeName(reference) + ".json");
   }

   public Path ocrFile(String reference) {
      return this.dir.resolve(safeName(reference) + ".ocr.json");
   }

   public void writeCase(String reference, Map record) {
      this.write(this.caseFile(reference), record);
   }

   public void writeOcr(String reference, Map ocr) {
      this.write(this.ocrFile(reference), ocr);
   }

   private void write(Path target, Map content) {
      try {
         Files.createDirectories(this.dir);
         Path tmp = Files.createTempFile(this.dir, ".tmp-", ".json");
         Files.write(tmp, MiniJson.write(content).getBytes(StandardCharsets.UTF_8), new OpenOption[0]);
         Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
      } catch (IOException e) {
         throw new UncheckedIOException("Failed to write " + String.valueOf(target), e);
      }
   }

   static String safeName(String s) {
      return s == null ? "unknown" : s.replaceAll("[^A-Za-z0-9._-]", "_");
   }
}
