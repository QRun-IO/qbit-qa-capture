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

package com.kingsrook.qbits.qacapture.api;


import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import com.kingsrook.qbits.qacapture.service.QaCaptureEnablement;
import com.kingsrook.qqq.openapi.model.Schema;
import com.kingsrook.qqq.openapi.model.Type;


/*******************************************************************************
 ** The versioned wire schemas of the qa-capture UI API (contract v1.0.0),
 ** independent of the tables.
 *******************************************************************************/
public final class QaCaptureSchemas
{
   public static final String CAPABILITY     = "QaCaptureCapabilityV1";
   public static final String PROVIDER_CONFIG = "QaCaptureProviderConfigV1";
   public static final String QA_MODE        = "QaCaptureQaModeV1";
   public static final String SANITIZER      = "QaCaptureSanitizerV1";
   public static final String CSP_ORIGINS    = "QaCaptureCspOriginsV1";
   public static final String CONTEXT        = "QaCaptureContextV1";
   public static final String REPORT_REQUEST = "QaCaptureReportRequestV1";
   public static final String REPORT_RESULT  = "QaCaptureReportResultV1";
   public static final String ERROR          = "QaCaptureErrorV1";



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private QaCaptureSchemas()
   {
   }



   /*******************************************************************************
    ** Every component schema (both operations publish the same set).
    *******************************************************************************/
   public static Map<String, Schema> components()
   {
      Map<String, Schema> components = new TreeMap<>();
      components.put(CAPABILITY, object("What QA capture means for the signed-in user. Clients that see enabled=false do nothing else.", Map.ofEntries(
         Map.entry("enabled", bool("Whether capture is on for this user.")),
         Map.entry("reason", new Schema().withType(Type.STRING).withEnumValues(reasons()).withDescription("Why capture is on or off.")),
         Map.entry("provider", string("The provider name (for example jam); null when not enabled.").withNullable(true)),
         Map.entry("providerConfig", nullableRef(PROVIDER_CONFIG).withDescription("Provider settings; null when not enabled.")),
         Map.entry("qaMode", ref(QA_MODE)),
         Map.entry("canReport", bool("Whether the user can file a report (Report a bug).")),
         Map.entry("reportsHref", string("The QA reports screen, only when the user can view reports. Clients follow it, never build it.").withNullable(true)),
         Map.entry("sanitizer", nullableRef(SANITIZER).withDescription("Redaction rules; null when not enabled.")),
         Map.entry("blurSelectors", stringArray("CSS selectors to blur in recordings; empty when not enabled.")),
         Map.entry("cspOrigins", ref(CSP_ORIGINS)),
         Map.entry("context", ref(CONTEXT)),
         Map.entry("requestIdHeader", string("The response header carrying the request id (X-Request-Id).")))));
      components.put(PROVIDER_CONFIG, object("Provider settings for the browser. For Jam: teamId, recordingLinkId and the script URLs.", Map.ofEntries(
         Map.entry("teamId", string("The provider team id.")),
         Map.entry("recordingLinkId", string("A reusable recording link id for in-app Report a bug; null when none is set.").withNullable(true)),
         Map.entry("scriptUrls", stringArray("The provider's scripts, in load order.")))));
      components.put(QA_MODE, object("The user's self-serve QA mode switch.", Map.ofEntries(
         Map.entry("available", bool("Whether the user can switch QA mode (self-serve mode and the qa_capture.use_qa_mode permission).")),
         Map.entry("active", bool("Whether the user's QA mode is on.")),
         Map.entry("preferenceSection", string("The user preference section holding the switch; null when unavailable.").withNullable(true)))));
      components.put(SANITIZER, object("Redaction rules applied in the browser before anything is sent to the provider.", Map.ofEntries(
         Map.entry("redaction", string("The replacement text.")),
         Map.entry("headerNames", stringArray("Lowercase header names deleted from request and response headers.")),
         Map.entry("bodyFieldNames", stringArray("JSON keys (any depth, case-insensitive) and form fields replaced by the redaction text.")),
         Map.entry("queryParamNames", stringArray("Query parameters redacted in request URLs and the page URL.")),
         Map.entry("valuePatterns", stringArray("JavaScript RegExp sources applied with flag g to bodies and console arguments.")),
         Map.entry("dropPathPrefixes", stringArray("Same-origin path prefixes whose request and response bodies are dropped.")))));
      components.put(CSP_ORIGINS, object("Content-Security-Policy origins the provider needs; empty when not enabled.", Map.ofEntries(
         Map.entry("scriptSrc", stringArray("script-src sources.")),
         Map.entry("frameSrc", stringArray("frame-src sources.")),
         Map.entry("connectSrc", stringArray("connect-src sources.")))));
      components.put(CONTEXT, object("Server context for capture metadata.", Map.ofEntries(
         Map.entry("environment", string("The environment name.")),
         Map.entry("backendVersion", string("The backend build version.").withNullable(true)),
         Map.entry("node", string("The serving node.").withNullable(true)))));
      components.put(REPORT_REQUEST, object("A new QA report. Only pageUrl is required.", Map.ofEntries(
         Map.entry("pageUrl", string("The page the user is on; sanitizer query parameters are removed.")),
         Map.entry("title", string("A short title; defaults to the table and record, process, app or page.").withNullable(true)),
         Map.entry("appName", string("The current app.").withNullable(true)),
         Map.entry("tableName", string("The current table.").withNullable(true)),
         Map.entry("recordId", string("The current record id.").withNullable(true)),
         Map.entry("processName", string("The current process.").withNullable(true)),
         Map.entry("requestIds", stringArray("Recent X-Request-Id values (at most 50 are kept, each at most 128 characters).").withNullable(true)),
         Map.entry("frontendVersion", string("The client build version.").withNullable(true)),
         Map.entry("userAgent", string("The browser user agent.").withNullable(true)),
         Map.entry("viewport", string("The viewport size, for example 1440x900.").withNullable(true)))));
      components.put(REPORT_RESULT, object("The created report and how to open the provider's recorder for it.", Map.ofEntries(
         Map.entry("id", new Schema().withType(Type.INTEGER).withDescription("The report id.")),
         Map.entry("reference", string("The report reference (qa-<uuid>), matched by the provider webhook.")),
         Map.entry("title", string("The report title.")),
         Map.entry("recordingParams", new Schema().withType(Type.OBJECT).withAdditionalProperties(new Schema().withType(Type.STRING)).withNullable(true)
            .withDescription("Query parameters that open the recorder (Jam: jam-recording, jam-title, jam-reference); null when no recording link is configured.")))));
      components.put(ERROR, object("An error.", Map.ofEntries(
         Map.entry("error", string("What went wrong.")))));
      return (components);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static List<String> reasons()
   {
      return (java.util.Arrays.stream(QaCaptureEnablement.Reason.values()).map(Enum::name).toList());
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static Schema object(String description, Map<String, Schema> properties)
   {
      return (new Schema().withType(Type.OBJECT).withDescription(description).withProperties(new TreeMap<>(properties)));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static Schema string(String description)
   {
      return (new Schema().withType(Type.STRING).withDescription(description));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static Schema bool(String description)
   {
      return (new Schema().withType(Type.BOOLEAN).withDescription(description));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static Schema stringArray(String description)
   {
      return (new Schema().withType(Type.ARRAY).withItems(new Schema().withType(Type.STRING)).withDescription(description));
   }



   /*******************************************************************************
    ** A nullable reference (allOf, since siblings of $ref are ignored).
    *******************************************************************************/
   private static Schema nullableRef(String name)
   {
      return (new Schema().withAllOf(List.of(ref(name))).withNullable(true));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static Schema ref(String name)
   {
      return (new Schema().withRefToSchema(name));
   }
}
