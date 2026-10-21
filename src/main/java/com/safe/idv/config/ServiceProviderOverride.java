package com.safe.idv.config;

import java.util.Objects;

public final class ServiceProviderOverride {
   private final String provider;
   private final String apiKey;
   private final String baseUrl;
   private final String model;

   public ServiceProviderOverride(String provider, String apiKey, String baseUrl, String model) {
      this.provider = provider;
      this.apiKey = apiKey;
      this.baseUrl = baseUrl;
      this.model = model;
   }

   public String provider() {
      return this.provider;
   }

   public String apiKey() {
      return this.apiKey;
   }

   public String baseUrl() {
      return this.baseUrl;
   }

   public String model() {
      return this.model;
   }

   public boolean isSet() {
      return this.provider != null && !this.provider.isBlank();
   }

   public String normalizedProvider() {
      if (this.provider == null) {
         return null;
      } else {
         String lower = this.provider.strip().toLowerCase();
         return "anthropic".equals(lower) ? "claude" : lower;
      }
   }

   public boolean appliesTo(String internalProviderToken) {
      return this.isSet() && this.normalizedProvider().equals(internalProviderToken);
   }

   public boolean equals(Object o) {
      if (this == o) {
         return true;
      } else if (!(o instanceof ServiceProviderOverride)) {
         return false;
      } else {
         ServiceProviderOverride that = (ServiceProviderOverride)o;
         return Objects.equals(this.provider, that.provider) && Objects.equals(this.apiKey, that.apiKey) && Objects.equals(this.baseUrl, that.baseUrl) && Objects.equals(this.model, that.model);
      }
   }

   public int hashCode() {
      return Objects.hash(new Object[]{this.provider, this.apiKey, this.baseUrl, this.model});
   }

   public String toString() {
      return "ServiceProviderOverride[provider=" + this.provider + ", apiKey=" + this.apiKey + ", baseUrl=" + this.baseUrl + ", model=" + this.model + "]";
   }
}
