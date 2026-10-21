package com.safe.idv.controller;

import com.safe.idv.idscan.IdScanSubmissionService;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v1/idscan"})
public class IdScanController {
   private static final Logger log = LoggerFactory.getLogger(IdScanController.class);
   private final IdScanSubmissionService service;

   public IdScanController(IdScanSubmissionService service) {
      this.service = service;
   }

   @PostMapping({"/check_status"})
   public ResponseEntity<?> checkStatus(@RequestBody(required = false) Map<String, Object> body) {
      String token = null;
      if (body != null) {
         if (body.get("magic_link_token") != null) {
            token = String.valueOf(body.get("magic_link_token"));
         } else if (body.get("token") != null) {
            token = String.valueOf(body.get("token"));
         }
      }
      log.info("===> Incoming /api/v1/idscan/check_status request for token: {}", token);
      Map<String, Object> result = this.service.checkStatus(token);
      log.info("<=== Response from /api/v1/idscan/check_status: {}", result);
      return ResponseEntity.ok(result);
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
