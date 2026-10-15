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

package com.kingsrook.qbits.qacapture.web;


import java.util.LinkedHashMap;
import java.util.Map;
import com.kingsrook.qbits.qacapture.service.SanitizerRules;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;


/*******************************************************************************
 ** A JavaScript expression that builds Jam-style sanitizers (network
 ** request, response and WebSocket sanitizers and a console log sanitizer)
 ** from a rule set, with the same semantics the capability contract gives
 ** clients:
 ** - headerNames: deleted from request and response headers (lowercase);
 ** - bodyFieldNames: JSON keys at any depth and form fields, case-insensitive,
 **   replaced by the redaction text (a JSON body stays JSON);
 ** - queryParamNames: redacted in request URLs;
 ** - valuePatterns: applied with the "g" flag to bodies, header values,
 **   WebSocket data and console arguments;
 ** - dropPathPrefixes: same-origin requests under these paths lose their
 **   request and response bodies.
 ** The sanitizers are synchronous, never log or fetch, and return copies.
 ** The rules are embedded as JSON escaped for an inline script element.
 *******************************************************************************/
public final class SanitizerScript
{
   private static final String FUNCTION = """
      (function (R) {
        var lower = function (s) { return String(s).toLowerCase(); };
        var H = new Set(R.headerNames.map(lower));
        var B = new Set(R.bodyFieldNames.map(lower));
        var Q = new Set(R.queryParamNames.map(lower));
        var P = R.valuePatterns.map(function (p) { return new RegExp(p, "g"); });
        var scrub = function (t) {
          if (typeof t !== "string") { return t; }
          for (var i = 0; i < P.length; i++) { P[i].lastIndex = 0; t = t.replace(P[i], R.redaction); }
          return t;
        };
        var scrubValue = function (v) {
          if (Array.isArray(v)) { return v.map(scrubValue); }
          if (v && typeof v === "object") {
            var o = {};
            Object.keys(v).forEach(function (k) { o[k] = B.has(lower(k)) ? R.redaction : scrubValue(v[k]); });
            return o;
          }
          return scrub(v);
        };
        var body = function (b) {
          if (typeof b !== "string" || b === "") { return b; }
          try { return JSON.stringify(scrubValue(JSON.parse(b))); } catch (e) { /* not JSON */ }
          if (/^[^\\s=&]+=[^\\s]*$/.test(b)) {
            try {
              var form = new URLSearchParams(b);
              Array.from(form.keys()).forEach(function (k) { form.set(k, B.has(lower(k)) ? R.redaction : scrub(form.get(k))); });
              return form.toString();
            } catch (e) { /* not a form */ }
          }
          return scrub(b);
        };
        var url = function (u) {
          try {
            var x = new URL(u, location.href);
            var changed = false;
            Array.from(x.searchParams.keys()).forEach(function (k) { if (Q.has(lower(k))) { x.searchParams.set(k, R.redaction); changed = true; } });
            return changed ? x.toString() : u;
          } catch (e) { return u; }
        };
        var headers = function (h) {
          var o = {};
          Object.keys(h || {}).forEach(function (k) { if (!H.has(lower(k))) { o[k] = scrub(h[k]); } });
          return o;
        };
        var dropped = function (u) {
          try {
            var x = new URL(u, location.href);
            return x.origin === location.origin && R.dropPathPrefixes.some(function (p) { return x.pathname.indexOf(p) === 0; });
          } catch (e) { return false; }
        };
        return {
          network: {
            requestSanitizer: function (request) {
              var drop = dropped(request.url);
              request.url = url(request.url);
              request.headers = headers(request.headers);
              request.body = drop ? undefined : body(request.body);
              return request;
            },
            responseSanitizer: function (response) {
              response.headers = headers(response.headers);
              response.body = dropped(response.url) ? undefined : body(response.body);
              return response;
            },
            webSocketSanitizer: function (message) {
              message.data = body(message.data);
              return message;
            }
          },
          console: {
            logSanitizer: function (log) {
              log.args = (log.args || []).map(scrubValue);
              return log;
            }
          }
        };
      })""";



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private SanitizerScript()
   {
   }



   /*******************************************************************************
    ** The expression (an immediately invoked function returning the sanitizers
    ** object) for these rules.
    *******************************************************************************/
   public static String expression(SanitizerRules rules)
   {
      Map<String, Object> config = new LinkedHashMap<>(rules.toCapabilityMap());
      return (FUNCTION + "(" + scriptSafeJson(config) + ")");
   }



   /*******************************************************************************
    ** JSON that cannot end or confuse an inline script element: "<", ">", "&"
    ** and the JavaScript line separators are written as unicode escapes.
    *******************************************************************************/
   static String scriptSafeJson(Object value)
   {
      return (JsonUtils.toJson(value)
         .replace("<", "\\u003c")
         .replace(">", "\\u003e")
         .replace("&", "\\u0026")
         .replace(" ", "\\u2028")
         .replace(" ", "\\u2029"));
   }
}
