/*
 * Copyright 2026 Kingsrook, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kingsrook.qbits.qacapture.service;


import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import com.kingsrook.qbits.qacapture.QaCaptureSchema;
import com.kingsrook.qbits.qacapture.QaCaptureSchema.RuleType;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** The rule set served to clients: the active rows of
 ** qa_capture_sanitizer_rule (built-in, secured-data and administrator rules
 ** alike), grouped by type. Invalid rows are skipped (and logged), never
 ** served: a value pattern must compile in Java and use only syntax
 ** JavaScript's RegExp shares; a drop path must start with "/".
 *******************************************************************************/
public final class SanitizerRules
{
   public static final String REDACTION = "[redacted]";

   ///////////////////////////////////////////////////////////////////////////
   // Java-only regex syntax (JavaScript reads these differently or not at  //
   // all): possessive quantifiers, atomic groups, inline flags, \A \Z \z   //
   // \G \h \Q..\E, \p{..} (needs JS's u flag), class intersection (&&).    //
   ///////////////////////////////////////////////////////////////////////////
   private static final Pattern JAVA_ONLY = Pattern.compile("[*+?}]\\+|\\(\\?[>a-zA-Z]|\\\\[AZzGhHQEpP]|&&");

   private final List<String> headerNames      = new ArrayList<>();
   private final List<String> bodyFieldNames   = new ArrayList<>();
   private final List<String> queryParamNames  = new ArrayList<>();
   private final List<String> valuePatterns    = new ArrayList<>();
   private final List<String> dropPathPrefixes = new ArrayList<>();
   private final List<String> blurSelectors    = new ArrayList<>();

   private static final QLogger LOG = QLogger.getLogger(SanitizerRules.class);



   /*******************************************************************************
    ** Use load or fromRecords.
    *******************************************************************************/
   private SanitizerRules()
   {
   }



   /*******************************************************************************
    ** The active rules from the table (call in the system context).
    *******************************************************************************/
   public static SanitizerRules load() throws QException
   {
      return (fromRecords(QaCaptureDb.query(QaCaptureSchema.SANITIZER_RULE, QaCaptureDb.eq("active", true))));
   }



   /*******************************************************************************
    ** Group rule rows (inactive and invalid rows are skipped).
    *******************************************************************************/
   public static SanitizerRules fromRecords(List<QRecord> records)
   {
      SanitizerRules rules = new SanitizerRules();
      Set<String>    seen  = new LinkedHashSet<>();
      for(QRecord record : records)
      {
         if(!Boolean.TRUE.equals(record.getValueBoolean("active")))
         {
            continue;
         }
         String type  = record.getValueString("ruleType");
         String value = record.getValueString("value");
         if(type == null || value == null || value.isBlank())
         {
            continue;
         }
         value = value.trim();
         String problem = validate(type, value);
         if(problem != null)
         {
            LOG.warn("Skipping an invalid QA capture sanitizer rule", logPair("id", record.getValue("id")), logPair("ruleType", type), logPair("problem", problem));
            continue;
         }

         String normalized = RuleType.HEADER.name().equals(type) ? value.toLowerCase(Locale.ROOT) : value;
         if(!seen.add(type + "\n" + normalized))
         {
            continue;
         }
         switch(RuleType.valueOf(type))
         {
            case HEADER -> rules.headerNames.add(normalized);
            case BODY_FIELD -> rules.bodyFieldNames.add(normalized);
            case QUERY_PARAM -> rules.queryParamNames.add(normalized);
            case VALUE_PATTERN -> rules.valuePatterns.add(normalized);
            case DROP_PATH -> rules.dropPathPrefixes.add(normalized);
            case BLUR_SELECTOR -> rules.blurSelectors.add(normalized);
            default -> LOG.warn("Unhandled sanitizer rule type", logPair("ruleType", type));
         }
      }
      return (rules);
   }



   /*******************************************************************************
    ** Why a rule value is not usable, or null when it is.
    *******************************************************************************/
   public static String validate(String ruleType, String value)
   {
      RuleType type;
      try
      {
         type = RuleType.valueOf(ruleType);
      }
      catch(IllegalArgumentException | NullPointerException e)
      {
         return ("Unknown rule type " + ruleType + ".");
      }
      if(value == null || value.isBlank())
      {
         return ("A value is required.");
      }
      String trimmed = value.trim();
      switch(type)
      {
         case VALUE_PATTERN ->
         {
            try
            {
               Pattern.compile(trimmed);
            }
            catch(PatternSyntaxException e)
            {
               return ("The pattern is not a valid regular expression: " + e.getDescription() + ".");
            }
            if(JAVA_ONLY.matcher(trimmed).find())
            {
               return ("The pattern uses syntax JavaScript does not share (possessive or atomic groups, inline flags, \\A, \\Z, \\z, \\G, \\h, \\Q, \\p or &&).");
            }
            if(Pattern.compile(trimmed).matcher("").matches())
            {
               return ("The pattern matches empty text, which would redact everything.");
            }
         }
         case DROP_PATH ->
         {
            if(!trimmed.startsWith("/"))
            {
               return ("A dropped path prefix starts with / (a same-origin path, for example /qqq/v1/secret).");
            }
         }
         case HEADER ->
         {
            if(!trimmed.matches("[A-Za-z0-9!#$%&'*+.^_`|~-]+"))
            {
               return ("A header name has letters, digits and - only.");
            }
         }
         default ->
         {
            // body fields, query parameters and blur selectors take any text
         }
      }
      return (null);
   }



   /*******************************************************************************
    ** Add blur selectors (one per line) from a settings row's
    ** extra_blur_selectors.
    *******************************************************************************/
   public SanitizerRules withExtraBlurSelectors(String text)
   {
      if(text != null)
      {
         for(String line : text.split("\\R"))
         {
            String selector = line.trim();
            if(!selector.isEmpty() && !blurSelectors.contains(selector))
            {
               blurSelectors.add(selector);
            }
         }
      }
      return (this);
   }



   /*******************************************************************************
    ** The capability response's sanitizer object.
    *******************************************************************************/
   public Map<String, Object> toCapabilityMap()
   {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("redaction", REDACTION);
      map.put("headerNames", List.copyOf(headerNames));
      map.put("bodyFieldNames", List.copyOf(bodyFieldNames));
      map.put("queryParamNames", List.copyOf(queryParamNames));
      map.put("valuePatterns", List.copyOf(valuePatterns));
      map.put("dropPathPrefixes", List.copyOf(dropPathPrefixes));
      return (map);
   }



   /*******************************************************************************
    ** A URL with the query parameters named by QUERY_PARAM rules removed
    ** (case-insensitive), capped at maxLength. Other parameters, their order
    ** and the fragment are kept.
    *******************************************************************************/
   public String stripQueryParams(String url, int maxLength)
   {
      if(url == null || url.isBlank())
      {
         return (null);
      }
      String trimmed  = url.trim();
      String fragment = "";
      int    hash     = trimmed.indexOf('#');
      if(hash >= 0)
      {
         fragment = trimmed.substring(hash);
         trimmed = trimmed.substring(0, hash);
      }
      int question = trimmed.indexOf('?');
      if(question >= 0)
      {
         Set<String> names = new LinkedHashSet<>();
         queryParamNames.forEach(name -> names.add(name.toLowerCase(Locale.ROOT)));
         List<String> kept = new ArrayList<>();
         for(String pair : trimmed.substring(question + 1).split("&"))
         {
            if(pair.isEmpty())
            {
               continue;
            }
            int    equals = pair.indexOf('=');
            String name   = java.net.URLDecoder.decode(equals < 0 ? pair : pair.substring(0, equals), java.nio.charset.StandardCharsets.UTF_8);
            if(!names.contains(name.toLowerCase(Locale.ROOT)))
            {
               kept.add(pair);
            }
         }
         trimmed = trimmed.substring(0, question) + (kept.isEmpty() ? "" : "?" + String.join("&", kept));
      }
      String result = trimmed + fragment;
      return (result.length() <= maxLength ? result : result.substring(0, maxLength));
   }



   /*******************************************************************************
    ** Getter for headerNames
    *******************************************************************************/
   public List<String> getHeaderNames()
   {
      return (List.copyOf(headerNames));
   }



   /*******************************************************************************
    ** Getter for bodyFieldNames
    *******************************************************************************/
   public List<String> getBodyFieldNames()
   {
      return (List.copyOf(bodyFieldNames));
   }



   /*******************************************************************************
    ** Getter for queryParamNames
    *******************************************************************************/
   public List<String> getQueryParamNames()
   {
      return (List.copyOf(queryParamNames));
   }



   /*******************************************************************************
    ** Getter for valuePatterns
    *******************************************************************************/
   public List<String> getValuePatterns()
   {
      return (List.copyOf(valuePatterns));
   }



   /*******************************************************************************
    ** Getter for dropPathPrefixes
    *******************************************************************************/
   public List<String> getDropPathPrefixes()
   {
      return (List.copyOf(dropPathPrefixes));
   }



   /*******************************************************************************
    ** Getter for blurSelectors
    *******************************************************************************/
   public List<String> getBlurSelectors()
   {
      return (List.copyOf(blurSelectors));
   }
}
