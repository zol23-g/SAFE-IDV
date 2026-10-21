package com.safe.idv.controller;

import com.safe.idv.idscan.IdScanSubmissionService;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v1/idscan"})
public class IdScanController {
   private final IdScanSubmissionService service;

   public IdScanController(IdScanSubmissionService service) {
      this.service = service;
   }

   @PostMapping({"/submit"})
   public ResponseEntity submit(@RequestBody Map body, HttpServletRequest request) {
      try {
         return ResponseEntity.status(HttpStatus.ACCEPTED).body(this.service.submit(body, request));
      } catch (IllegalArgumentException e) {
         return error(HttpStatus.BAD_REQUEST, e.getMessage());
      } catch (IdScanSubmissionService.TransportException var5) {
         return error(HttpStatus.BAD_GATEWAY, "Could not complete the submission. Please try again.");
      }
   }

   private static ResponseEntity error(HttpStatus status, String message) {
      Map<String, Object> m = new LinkedHashMap();
      m.put("error", message);
      return ResponseEntity.status(status).body(m);
   }
}
