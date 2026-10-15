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

package com.kingsrook.qbits.qacapture.spi;


import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.qacapture.QaCaptureSchema.ReportStatus;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** Jam (https://jam.dev): a connected domain identifies the team with a
 ** jam:team meta tag and loads recorder.js and capture.js from js.jam.dev;
 ** a reusable Recording Link opens the recorder on any same-origin page with
 ** jam-recording, jam-title and jam-reference query parameters; the
 ** Svix-signed jam.created webhook reports the finished capture, matched to
 ** the QA report by recordingLink.reference.
 *******************************************************************************/
public class JamProvider implements QaCaptureProvider
{
   private static final QLogger LOG = QLogger.getLogger(JamProvider.class);

   public static final String NAME         = "jam";
   public static final String RECORDER_JS  = "https://js.jam.dev/recorder.js";
   public static final String CAPTURE_JS   = "https://js.jam.dev/capture.js";
   public static final String EVENT_CREATED = "jam.created";

   private static final List<String>        SCRIPT_URLS = List.of(RECORDER_JS, CAPTURE_JS);
   private static final QaCaptureCspOrigins CSP_ORIGINS = new QaCaptureCspOrigins(
      List.of("https://js.jam.dev"), List.of("https://*.jam.dev"), List.of("https://*.jam.dev"));



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public String getName()
   {
      return (NAME);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public String getLabel()
   {
      return ("Jam");
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public List<String> getScriptUrls()
   {
      return (SCRIPT_URLS);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public QaCaptureCspOrigins getCspOrigins()
   {
      return (CSP_ORIGINS);
   }



   /*******************************************************************************
    ** teamId, recordingLinkId (nullable) and scriptUrls.
    *******************************************************************************/
   @Override
   public Map<String, Object> providerConfig(QRecord settings)
   {
      Map<String, Object> config = new LinkedHashMap<>();
      config.put("teamId", settings.getValueString("teamId"));
      config.put("recordingLinkId", blankToNull(settings.getValueString("recordingLinkId")));
      config.put("scriptUrls", SCRIPT_URLS);
      return (config);
   }



   /*******************************************************************************
    ** jam-recording, jam-title and jam-reference; null without a recording link.
    *******************************************************************************/
   @Override
   public Map<String, String> recordingParams(QRecord report, QRecord settings)
   {
      String linkId = blankToNull(settings.getValueString("recordingLinkId"));
      if(linkId == null)
      {
         return (null);
      }
      Map<String, String> params = new LinkedHashMap<>();
      params.put("jam-recording", linkId);
      params.put("jam-title", report.getValueString("title"));
      params.put("jam-reference", report.getValueString("reference"));
      return (params);
   }



   /*******************************************************************************
    ** The connected-domain markup Jam verifies (team meta and both module
    ** scripts), then the sanitizers in a module script right after capture.js,
    ** as Jam documents for script-tag installs.
    *******************************************************************************/
   @Override
   public String recorderHead(QRecord settings, String sanitizersExpression)
   {
      return ("<meta name=\"jam:team\" content=\"" + escapeAttribute(settings.getValueString("teamId")) + "\">\n"
         + "<script type=\"module\" src=\"" + RECORDER_JS + "\"></script>\n"
         + "<script type=\"module\" src=\"" + CAPTURE_JS + "\"></script>\n"
         + "<script type=\"module\">" + sanitizerInstaller(sanitizersExpression) + "</script>\n");
   }



   /*******************************************************************************
    ** The inline module script body: install the sanitizers through
    ** window.jam.capture.setSanitizers, or say clearly that capture.js did not
    ** load first.
    *******************************************************************************/
   public static String sanitizerInstaller(String sanitizersExpression)
   {
      return ("\nconst sanitizers = " + sanitizersExpression + ";\n"
         + "if (window.jam && window.jam.capture && typeof window.jam.capture.setSanitizers === \"function\") {\n"
         + "  window.jam.capture.setSanitizers(sanitizers);\n"
         + "} else {\n"
         + "  console.error(\"Jam capture.js did not load first: sanitizers are not set.\");\n"
         + "}\n");
   }



   /*******************************************************************************
    ** jam.created: verify the Svix signature, then attach the capture to the
    ** report named by recordingLink.reference, or record an unmatched capture
    ** as a new report. Idempotent on jamId.
    *******************************************************************************/
   @Override
   @SuppressWarnings("unchecked")
   public QaCaptureWebhookResult handleWebhook(QaCaptureWebhookRequest request, QaCaptureWebhookContext context) throws QException
   {
      String secret = context.getWebhookSecret();
      if(!StringUtils.hasContent(secret))
      {
         return (QaCaptureWebhookResult.notFound());
      }

      SvixSignatureVerifier.Outcome outcome = SvixSignatureVerifier.verify(secret, request.getHeader("svix-id"), request.getHeader("svix-timestamp"),
         request.getHeader("svix-signature"), request.getBody(), request.getReceivedAt());
      if(outcome != SvixSignatureVerifier.Outcome.VALID)
      {
         LOG.info("Rejected a Jam webhook", logPair("outcome", outcome), logPair("svixId", request.getHeader("svix-id")));
         return (QaCaptureWebhookResult.error(401, "Invalid webhook signature"));
      }

      Map<String, Object> payload;
      try
      {
         payload = JsonUtils.toObject(request.getBody(), Map.class);
      }
      catch(Exception e)
      {
         return (QaCaptureWebhookResult.error(400, "The body is not a JSON object"));
      }

      if(!EVENT_CREATED.equals(string(payload.get("type"))))
      {
         return (QaCaptureWebhookResult.ok("ignored", null));
      }

      QRecord settings = context.getSettings();
      String  teamId   = string(payload.get("teamId"));
      if(settings == null || teamId == null || !teamId.equals(settings.getValueString("teamId")))
      {
         LOG.info("Ignored a Jam webhook for another team or an unconfigured environment", logPair("environment", context.getEnvironment()));
         return (QaCaptureWebhookResult.ok("ignored", null));
      }

      String jamId  = string(payload.get("jamId"));
      String jamUrl = string(payload.get("jamUrl"));
      if(jamId == null)
      {
         return (QaCaptureWebhookResult.error(400, "jamId is required"));
      }

      QRecord existing = context.findReportByCaptureId(jamId);
      if(existing != null)
      {
         return (QaCaptureWebhookResult.ok("duplicate", existing.getValueString("reference")));
      }

      Map<String, Object> link      = payload.get("recordingLink") instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
      String              reference = string(link.get("reference"));
      String              comment   = string(link.get("submitterComment"));

      QRecord report = reference == null ? null : context.findReportByReference(reference);
      if(report != null)
      {
         QRecord update = new QRecord()
            .withValue("id", report.getValue("id"))
            .withValue("jamId", jamId)
            .withValue("jamUrl", jamUrl);
         if(ReportStatus.OPEN.name().equals(report.getValueString("status")))
         {
            update.withValue("status", ReportStatus.CAPTURED.name());
         }
         if(comment != null && !StringUtils.hasContent(report.getValueString("submitterComment")))
         {
            update.withValue("submitterComment", comment);
         }
         if(!StringUtils.hasContent(report.getValueString("title")) && string(payload.get("title")) != null)
         {
            update.withValue("title", truncate(string(payload.get("title")), 250));
         }
         context.updateReport(update);
         return (QaCaptureWebhookResult.ok("matched", report.getValueString("reference")));
      }

      Map<String, Object> author = payload.get("author") instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
      String              title  = string(payload.get("title"));
      QRecord created = context.insertReport(new QRecord()
         .withValue("title", truncate(title == null ? "Jam " + jamId : title, 250))
         .withValue("status", ReportStatus.CAPTURED.name())
         .withValue("pageUrl", context.sanitizeUrl(string(payload.get("originalUrl"))))
         .withValue("userId", truncate(string(author.get("email")), 250))
         .withValue("userLabel", truncate(string(author.get("name")), 250))
         .withValue("provider", NAME)
         .withValue("jamId", truncate(jamId, 100))
         .withValue("jamUrl", truncate(jamUrl, 1000))
         .withValue("submitterComment", comment));
      return (QaCaptureWebhookResult.ok("created", created.getValueString("reference")));
   }



   /*******************************************************************************
    ** A non-blank string value, or null.
    *******************************************************************************/
   private static String string(Object value)
   {
      return (value == null ? null : blankToNull(String.valueOf(value)));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static String blankToNull(String value)
   {
      return (value == null || value.isBlank() ? null : value.trim());
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static String truncate(String value, int length)
   {
      return (value == null || value.length() <= length ? value : value.substring(0, length));
   }



   /*******************************************************************************
    ** Escape a value for a double-quoted HTML attribute.
    *******************************************************************************/
   static String escapeAttribute(String value)
   {
      if(value == null)
      {
         return ("");
      }
      return (value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;"));
   }
}
