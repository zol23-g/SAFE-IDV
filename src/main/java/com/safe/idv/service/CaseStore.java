package com.safe.idv.service;

import com.safe.idv.model.CaseResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;

@Service
public class CaseStore {
   private final Map cases = new ConcurrentHashMap();
   private final AtomicInteger nextId = new AtomicInteger(1);

   public int allocateId() {
      return this.nextId.getAndIncrement();
   }

   public String caseNumberFor(int id) {
      String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
      return String.format("SAFE-%s-DEMO%04d", today, id);
   }

   public void put(String id, CaseResponse response) {
      this.cases.put(id, response);
   }

   public CaseResponse get(String id) {
      return (CaseResponse)this.cases.get(id);
   }

   public Collection all() {
      return List.copyOf(this.cases.values());
   }
}
