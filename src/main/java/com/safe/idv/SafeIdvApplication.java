package com.safe.idv;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Locale;
import java.util.Optional;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SafeIdvApplication {
   public static void main(String[] args) {
      SpringApplication application = new SpringApplication(new Class[]{SafeIdvApplication.class});
      externalPropertiesLocation().ifPresent((location) -> application.setDefaultProperties(Collections.singletonMap("spring.config.additional-location", location)));
      application.run(args);
   }

   private static Optional externalPropertiesLocation() {
      String classPath = System.getProperty("java.class.path");
      if (classPath != null && !classPath.isBlank()) {
         try {
            Path launchedJar = Paths.get(classPath).toAbsolutePath().normalize();
            if (Files.isRegularFile(launchedJar, new LinkOption[0]) && launchedJar.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
               Path var10000 = launchedJar.getParent();
               return Optional.of("optional:" + String.valueOf(var10000.resolve("application.properties").toUri()));
            } else {
               return Optional.empty();
            }
         } catch (RuntimeException var2) {
            return Optional.empty();
         }
      } else {
         return Optional.empty();
      }
   }
}
