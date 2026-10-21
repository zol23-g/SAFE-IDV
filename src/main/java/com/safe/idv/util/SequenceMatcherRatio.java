package com.safe.idv.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SequenceMatcherRatio {
   private SequenceMatcherRatio() {
   }

   public static double ratio(String a, String b) {
      if (a != null && !a.isEmpty() && b != null && !b.isEmpty()) {
         int matches = 0;

         for(int[] block : getMatchingBlocks(a, b)) {
            matches += block[2];
         }

         return (double)2.0F * (double)matches / (double)(a.length() + b.length());
      } else {
         return (double)0.0F;
      }
   }

   private static List<int[]> getMatchingBlocks(String a, String b) {
      Map<Character, List<Integer>> b2j = new HashMap();

      for(int j = 0; j < b.length(); ++j) {
         b2j.computeIfAbsent(b.charAt(j), (kx) -> new ArrayList<>()).add(j);
      }

      List<int[]> matchingBlocks = new ArrayList();
      Deque<int[]> queue = new ArrayDeque();
      queue.push(new int[]{0, a.length(), 0, b.length()});

      while(!queue.isEmpty()) {
         int[] range = (int[])queue.pop();
         int alo = range[0];
         int ahi = range[1];
         int blo = range[2];
         int bhi = range[3];
         int[] longest = findLongestMatch(a, b, alo, ahi, blo, bhi, b2j);
         int i = longest[0];
         int j = longest[1];
         int k = longest[2];
         if (k > 0) {
            matchingBlocks.add(new int[]{i, j, k});
            if (alo < i && blo < j) {
               queue.push(new int[]{alo, i, blo, j});
            }

            if (i + k < ahi && j + k < bhi) {
               queue.push(new int[]{i + k, ahi, j + k, bhi});
            }
         }
      }

      return matchingBlocks;
   }

   private static int[] findLongestMatch(String a, String b, int alo, int ahi, int blo, int bhi, Map b2j) {
      int besti = alo;
      int bestj = blo;
      int bestsize = 0;
      Map<Integer, Integer> j2len = new HashMap();

      for(int i = alo; i < ahi; ++i) {
         Map<Integer, Integer> newJ2len = new HashMap();
         List<Integer> js = (List)b2j.get(a.charAt(i));
         if (js != null) {
            for(int j : js) {
               if (j >= blo) {
                  if (j >= bhi) {
                     break;
                  }

                  int k = (Integer)j2len.getOrDefault(j - 1, 0) + 1;
                  newJ2len.put(j, k);
                  if (k > bestsize) {
                     besti = i - k + 1;
                     bestj = j - k + 1;
                     bestsize = k;
                  }
               }
            }
         }

         j2len = newJ2len;
      }

      return new int[]{besti, bestj, bestsize};
   }
}
