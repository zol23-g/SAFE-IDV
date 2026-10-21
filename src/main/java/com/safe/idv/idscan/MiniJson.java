package com.safe.idv.idscan;

import java.util.List;
import java.util.Map;

public final class MiniJson {
   private MiniJson() {
   }

   public static String write(Object v) {
      StringBuilder sb = new StringBuilder(256);
      write(sb, v);
      return sb.toString();
   }

   private static void write(StringBuilder sb, Object v) {
      if (v == null) {
         sb.append("null");
      } else if (v instanceof String) {
         quote(sb, (String)v);
      } else if (v instanceof Boolean) {
         sb.append((Boolean)v ? "true" : "false");
      } else if (!(v instanceof Number)) {
         if (v instanceof Map) {
            sb.append('{');
            boolean first = true;

            for(Map.Entry<?, ?> e : ((Map<?, ?>)v).entrySet()) {
               if (!first) {
                  sb.append(',');
               }

               first = false;
               quote(sb, String.valueOf(e.getKey()));
               sb.append(':');
               write(sb, e.getValue());
            }

            sb.append('}');
         } else if (v instanceof List) {
            sb.append('[');
            boolean first = true;

            for(Object o : (List)v) {
               if (!first) {
                  sb.append(',');
               }

               first = false;
               write(sb, o);
            }

            sb.append(']');
         } else {
            quote(sb, v.toString());
         }
      } else if (!(v instanceof Double) || !((Double)v).isNaN() && !((Double)v).isInfinite()) {
         sb.append(v.toString());
      } else {
         sb.append("null");
      }
   }

   private static void quote(StringBuilder sb, String s) {
      sb.append('"');

      for(int i = 0; i < s.length(); ++i) {
         char c = s.charAt(i);
         switch (c) {
            case '\b':
               sb.append("\\b");
               break;
            case '\t':
               sb.append("\\t");
               break;
            case '\n':
               sb.append("\\n");
               break;
            case '\f':
               sb.append("\\f");
               break;
            case '\r':
               sb.append("\\r");
               break;
            case '"':
               sb.append("\\\"");
               break;
            case '\\':
               sb.append("\\\\");
               break;
            default:
               if (c < ' ') {
                  sb.append(String.format("\\u%04x", Integer.valueOf(c)));
               } else {
                  sb.append(c);
               }
         }
      }

      sb.append('"');
   }
}
