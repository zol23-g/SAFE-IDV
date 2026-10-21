package com.safe.idv.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safe.idv.model.LiveKeys;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ApiKeyService {
   private final String serverOpenaiKey;
   private final String serverAnthropicKey;

   public ApiKeyService(@Value("${safe.openai-api-key:#{null}}") String envOpenaiKey, @Value("${safe.anthropic-api-key:#{null}}") String envAnthropicKey, @Value("${safe.api-keys-file:api_keys.json}") String apiKeysFilePath) {
      Map<String, String> localKeys = loadLocalKeys(apiKeysFilePath);
      this.serverOpenaiKey = firstNonBlank(envOpenaiKey, (String)localKeys.get("OPENAI_API_KEY"));
      this.serverAnthropicKey = firstNonBlank(envAnthropicKey, (String)localKeys.get("ANTHROPIC_API_KEY"));
   }

   private static Map loadLocalKeys(String path) {
      Map<String, String> out = new HashMap();
      File f = new File(path);
      if (!f.isFile()) {
         return out;
      } else {
         try {
            ObjectMapper mapper = new ObjectMapper();
            Map<?, ?> raw = (Map)mapper.readValue(f, Map.class);

            for(Map.Entry e : raw.entrySet()) {
               if (e.getValue() != null && !e.getValue().toString().isBlank()) {
                  out.put(String.valueOf(e.getKey()), e.getValue().toString().strip());
               }
            }
         } catch (Exception e) {
            System.err.println("Warning: could not read " + path + " (" + String.valueOf(e) + "); ignoring it.");
         }

         return out;
      }
   }

   private static String firstNonBlank(String a, String b) {
      if (a != null && !a.isBlank()) {
         return a;
      } else {
         return b != null && !b.isBlank() ? b : "";
      }
   }

   public String serverOpenaiKey() {
      return this.serverOpenaiKey;
   }

   public String serverAnthropicKey() {
      return this.serverAnthropicKey;
   }

   public boolean serverOpenaiConfigured() {
      return !this.serverOpenaiKey.isBlank();
   }

   public boolean serverAnthropicConfigured() {
      return !this.serverAnthropicKey.isBlank();
   }

   public Resolved resolveKeysForRequest(LiveKeys liveKeys) {
      String reqOpenai = liveKeys != null && liveKeys.openai != null && !liveKeys.openai.isBlank() ? liveKeys.openai.strip() : this.serverOpenaiKey;
      String reqAnthropic = liveKeys != null && liveKeys.anthropic != null && !liveKeys.anthropic.isBlank() ? liveKeys.anthropic.strip() : this.serverAnthropicKey;
      boolean usedRequestKeys = liveKeys != null && (liveKeys.openai != null && !liveKeys.openai.isBlank() || liveKeys.anthropic != null && !liveKeys.anthropic.isBlank());
      boolean liveMode = !reqOpenai.isBlank() || !reqAnthropic.isBlank();
      return new Resolved(reqOpenai, reqAnthropic, liveMode, usedRequestKeys);
   }

   public static class Resolved {
      public final String openaiKey;
      public final String anthropicKey;
      public final boolean liveMode;
      public final boolean usedRequestKeys;

      Resolved(String openaiKey, String anthropicKey, boolean liveMode, boolean usedRequestKeys) {
         this.openaiKey = openaiKey;
         this.anthropicKey = anthropicKey;
         this.liveMode = liveMode;
         this.usedRequestKeys = usedRequestKeys;
      }
   }
}
