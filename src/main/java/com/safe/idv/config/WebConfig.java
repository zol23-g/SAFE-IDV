package com.safe.idv.config;

import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
   @Value("${safe.reports-dir}")
   private String reportsDir;

   public void addCorsMappings(CorsRegistry registry) {
      registry.addMapping("/**").allowedOriginPatterns(new String[]{"*"}).allowedMethods(new String[]{"GET", "POST", "DELETE", "OPTIONS"}).allowedHeaders(new String[]{"Content-Type", "Authorization"});
   }

   public void addViewControllers(ViewControllerRegistry registry) {
      registry.addRedirectViewController("/", "/safe_idv_launch_v70.html").setKeepQueryParams(true);
   }

   public void addResourceHandlers(ResourceHandlerRegistry registry) {
      String location = Path.of(this.reportsDir).toAbsolutePath().normalize().toUri().toString();
      registry.addResourceHandler(new String[]{"/reports/**"}).addResourceLocations(new String[]{location});
   }

   @Bean
   public ObjectMapper objectMapper() {
      ObjectMapper mapper = new ObjectMapper();
      mapper.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
      mapper.setVisibility(PropertyAccessor.GETTER, Visibility.NONE);
      mapper.setVisibility(PropertyAccessor.IS_GETTER, Visibility.NONE);
      return mapper;
   }
}
