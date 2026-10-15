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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import com.kingsrook.qbits.qacapture.QaCaptureQBitConfig;
import com.kingsrook.qbits.qacapture.QaCaptureRuntime;
import com.kingsrook.qbits.qacapture.QaCaptureSchema;
import com.kingsrook.qbits.qacapture.QaCaptureSchema.ReportStatus;
import com.kingsrook.qbits.qacapture.spi.QaCaptureCspOrigins;
import com.kingsrook.qbits.qacapture.spi.QaCaptureProvider;
import com.kingsrook.qbits.qacapture.spi.QaCaptureWebhookContext;
import com.kingsrook.qbits.qacapture.web.QaCaptureRequestIds;
import com.kingsrook.qqq.backend.core.exceptions.QBadRequestException;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.exceptions.QPermissionDeniedException;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.utils.StringUtils;


/*******************************************************************************
 ** The QA capture behaviors behind the UI API and the webhooks: the
 ** capability of the signed-in user, report creation, and the context a
 ** provider webhook works in. Table reads and writes run in the system
 ** context; who may do what is decided here (QaCaptureEnablement).
 *******************************************************************************/
public class QaCaptureService
{
   public static final int MAX_REQUEST_IDS = 50;
   public static final int MAX_PAGE_URL    = 2000;

   private static final Pattern REQUEST_ID = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

   private final QInstance           qInstance;
   private final QaCaptureQBitConfig config;



   /*******************************************************************************
    ** The service of an instance.
    *******************************************************************************/
   public QaCaptureService(QInstance qInstance)
   {
      this.qInstance = qInstance;
      this.config = QaCaptureRuntime.of(qInstance).getConfig();
   }



   /*******************************************************************************
    ** What the user's session gets: the decision and, when enabled, the rules.
    **
    ** @param decision the enablement decision
    ** @param rules    the sanitizer rules (null unless enabled)
    *******************************************************************************/
   public record State(QaCaptureEnablement.Decision decision, SanitizerRules rules)
   {
   }



   /*******************************************************************************
    ** Decide for a session (reads in the system context).
    *******************************************************************************/
   public State state(QSession session) throws QException
   {
      String userId = session == null || session.getUser() == null ? null : session.getUser().getIdReference();
      return (QaCaptureRuntime.asSystem(qInstance, () ->
      {
         QRecord settings = QaCaptureSettings.load(qInstance);
         boolean mayUseQaMode = session != null && session.hasPermission(QaCaptureSchema.PERMISSION_USE_QA_MODE);
         boolean qaModeOn     = settings != null && mayUseQaMode && QaModePreference.read(userId, settings);
         QaCaptureEnablement.Decision decision = QaCaptureEnablement.decide(config, session, settings, QaCaptureSettings.audience(settings), qaModeOn);
         SanitizerRules rules = decision.enabled() ? SanitizerRules.load().withExtraBlurSelectors(settings.getValueString("extraBlurSelectors")) : null;
         return (new State(decision, rules));
      }));
   }



   /*******************************************************************************
    ** The capability response (contract qaCaptureCapability).
    *******************************************************************************/
   public Map<String, Object> capability(QSession session) throws QException
   {
      State                        state    = state(session);
      QaCaptureEnablement.Decision decision = state.decision();
      boolean                      enabled  = decision.enabled();

      Map<String, Object> qaMode = new LinkedHashMap<>();
      qaMode.put("available", decision.qaModeAvailable());
      qaMode.put("active", decision.qaModeActive());
      qaMode.put("preferenceSection", decision.qaModeAvailable() && QaModePreference.isRegistered(qInstance) ? QaModePreference.SECTION_NAME : null);

      boolean canViewReports = session != null && (session.hasPermission(QaCaptureSchema.PERMISSION_VIEW_REPORTS) || session.hasPermission(QaCaptureSchema.PERMISSION_MANAGE));

      Map<String, Object> context = new LinkedHashMap<>();
      context.put("environment", config.getEnvironment());
      context.put("backendVersion", config.getBackendVersion());
      context.put("node", config.getEffectiveNode());

      Map<String, Object> result = new LinkedHashMap<>();
      result.put("enabled", enabled);
      result.put("reason", decision.reason().name());
      result.put("provider", enabled ? decision.provider().getName() : null);
      result.put("providerConfig", enabled ? decision.provider().providerConfig(decision.settings()) : null);
      result.put("qaMode", qaMode);
      result.put("canReport", enabled);
      result.put("reportsHref", canViewReports ? "/app/" + QaCaptureSchema.REPORT : null);
      result.put("sanitizer", enabled ? state.rules().toCapabilityMap() : null);
      result.put("blurSelectors", enabled ? state.rules().getBlurSelectors() : List.of());
      result.put("cspOrigins", (enabled ? decision.provider().getCspOrigins() : QaCaptureCspOrigins.none()).toMap());
      result.put("context", context);
      result.put("requestIdHeader", QaCaptureRequestIds.HEADER);
      return (result);
   }



   /*******************************************************************************
    ** Create a report for the signed-in user (contract qaCaptureReportCreate).
    **
    ** @throws QPermissionDeniedException when capture is not on for the user
    ** @throws QBadRequestException       when the body is invalid
    *******************************************************************************/
   public Map<String, Object> createReport(QSession session, Map<String, Object> body) throws QException
   {
      State state = state(session);
      if(!state.decision().enabled())
      {
         throw (new QPermissionDeniedException("QA capture is not enabled for you."));
      }

      String pageUrl = optionalString(body, "pageUrl", MAX_PAGE_URL * 2);
      if(pageUrl == null)
      {
         throw (new QBadRequestException("pageUrl is required."));
      }

      String tableName = optionalString(body, "tableName", 100);
      String recordId  = optionalString(body, "recordId", 100);
      String process   = optionalString(body, "processName", 100);
      String appName   = optionalString(body, "appName", 100);
      String title     = optionalString(body, "title", 250);
      if(title == null)
      {
         title = defaultTitle(tableName, recordId, process, appName, state.rules().stripQueryParams(pageUrl, MAX_PAGE_URL));
      }

      QaCaptureProvider provider = state.decision().provider();
      QRecord           settings = state.decision().settings();
      QRecord report = new QRecord()
         .withValue("reference", newReference())
         .withValue("title", truncate(title, 250))
         .withValue("status", ReportStatus.OPEN.name())
         .withValue("pageUrl", state.rules().stripQueryParams(pageUrl, MAX_PAGE_URL))
         .withValue("appName", appName)
         .withValue("tableName", tableName)
         .withValue("recordId", recordId)
         .withValue("processName", process)
         .withValue("requestIds", requestIds(body.get("requestIds")))
         .withValue("userId", truncate(session.getUser().getIdReference(), 250))
         .withValue("userLabel", truncate(userLabel(session), 250))
         .withValue("environment", config.getEnvironment())
         .withValue("provider", provider.getName())
         .withValue("frontendVersion", optionalString(body, "frontendVersion", 100))
         .withValue("backendVersion", truncate(config.getBackendVersion(), 100))
         .withValue("node", truncate(config.getEffectiveNode(), 250))
         .withValue("userAgent", optionalString(body, "userAgent", 1000))
         .withValue("viewport", optionalString(body, "viewport", 30));

      QRecord stored = QaCaptureRuntime.asSystem(qInstance, () -> QaCaptureDb.insert(QaCaptureSchema.REPORT, report));

      Map<String, Object> result = new LinkedHashMap<>();
      result.put("id", stored.getValue("id"));
      result.put("reference", stored.getValueString("reference"));
      result.put("title", stored.getValueString("title"));
      result.put("recordingParams", provider.recordingParams(stored, settings));
      return (result);
   }



   /*******************************************************************************
    ** The context a provider's webhook handler works in.
    *******************************************************************************/
   public QaCaptureWebhookContext webhookContext(QaCaptureProvider provider)
   {
      return (new QaCaptureWebhookContext()
      {
         /***************************************************************************
          **
          ***************************************************************************/
         @Override
         public String getWebhookSecret()
         {
            return (config.getWebhookSecret(provider.getName()));
         }



         /***************************************************************************
          **
          ***************************************************************************/
         @Override
         public QRecord getSettings() throws QException
         {
            return (QaCaptureRuntime.asSystem(qInstance, () -> QaCaptureSettings.load(qInstance)));
         }



         /***************************************************************************
          **
          ***************************************************************************/
         @Override
         public String getEnvironment()
         {
            return (config.getEnvironment());
         }



         /***************************************************************************
          **
          ***************************************************************************/
         @Override
         public String sanitizeUrl(String url) throws QException
         {
            return (QaCaptureRuntime.asSystem(qInstance, () -> SanitizerRules.load().stripQueryParams(url, MAX_PAGE_URL)));
         }



         /***************************************************************************
          **
          ***************************************************************************/
         @Override
         public QRecord findReportByReference(String reference) throws QException
         {
            return (QaCaptureRuntime.asSystem(qInstance, () -> QaCaptureDb.first(QaCaptureSchema.REPORT, QaCaptureDb.eq("reference", reference))));
         }



         /***************************************************************************
          **
          ***************************************************************************/
         @Override
         public QRecord findReportByCaptureId(String captureId) throws QException
         {
            return (QaCaptureRuntime.asSystem(qInstance, () -> QaCaptureDb.first(QaCaptureSchema.REPORT, QaCaptureDb.eq("jamId", captureId))));
         }



         /***************************************************************************
          **
          ***************************************************************************/
         @Override
         public QRecord insertReport(QRecord report) throws QException
         {
            if(!StringUtils.hasContent(report.getValueString("reference")))
            {
               report.setValue("reference", newReference());
            }
            if(!StringUtils.hasContent(report.getValueString("environment")))
            {
               report.setValue("environment", config.getEnvironment());
            }
            if(!StringUtils.hasContent(report.getValueString("status")))
            {
               report.setValue("status", ReportStatus.OPEN.name());
            }
            return (QaCaptureRuntime.asSystem(qInstance, () -> QaCaptureDb.insert(QaCaptureSchema.REPORT, report)));
         }



         /***************************************************************************
          **
          ***************************************************************************/
         @Override
         public QRecord updateReport(QRecord report) throws QException
         {
            return (QaCaptureRuntime.asSystem(qInstance, () -> QaCaptureDb.update(QaCaptureSchema.REPORT, report)));
         }
      });
   }



   /*******************************************************************************
    ** A new report reference: "qa-" and a random UUID.
    *******************************************************************************/
   public static String newReference()
   {
      return ("qa-" + UUID.randomUUID());
   }



   /*******************************************************************************
    ** "Bug on {table} {record}", else on the process, the app or the page.
    *******************************************************************************/
   static String defaultTitle(String tableName, String recordId, String processName, String appName, String pageUrl)
   {
      if(tableName != null)
      {
         return ("Bug on " + tableName + (recordId == null ? "" : " " + recordId));
      }
      if(processName != null)
      {
         return ("Bug in " + processName);
      }
      if(appName != null)
      {
         return ("Bug in " + appName);
      }
      String path = pageUrl;
      try
      {
         String parsed = java.net.URI.create(pageUrl).getPath();
         if(StringUtils.hasContent(parsed))
         {
            path = parsed;
         }
      }
      catch(IllegalArgumentException e)
      {
         // keep the URL as given
      }
      return ("Bug on " + path);
   }



   /*******************************************************************************
    ** The request ids to keep: valid ids only, at most 50, newline separated.
    *******************************************************************************/
   static String requestIds(Object value) throws QBadRequestException
   {
      if(value == null)
      {
         return (null);
      }
      if(!(value instanceof List<?> list))
      {
         throw (new QBadRequestException("requestIds must be an array of strings."));
      }
      List<String> kept = new ArrayList<>();
      for(Object item : list)
      {
         if(item instanceof String id && REQUEST_ID.matcher(id.trim()).matches() && !kept.contains(id.trim()))
         {
            kept.add(id.trim());
            if(kept.size() == MAX_REQUEST_IDS)
            {
               break;
            }
         }
      }
      return (kept.isEmpty() ? null : String.join("\n", kept));
   }



   /*******************************************************************************
    ** An optional string property (blank is null), capped at maxLength.
    *******************************************************************************/
   static String optionalString(Map<String, Object> body, String name, int maxLength) throws QBadRequestException
   {
      Object value = body == null ? null : body.get(name);
      if(value == null)
      {
         return (null);
      }
      if(!(value instanceof String string))
      {
         throw (new QBadRequestException(name + " must be a string."));
      }
      return (string.isBlank() ? null : truncate(string.trim(), maxLength));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static String userLabel(QSession session)
   {
      if(StringUtils.hasContent(session.getUser().getFullName()))
      {
         return (session.getUser().getFullName());
      }
      return (StringUtils.hasContent(session.getUser().getEmail()) ? session.getUser().getEmail() : session.getUser().getIdReference());
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static String truncate(String value, int length)
   {
      return (value == null || value.length() <= length ? value : value.substring(0, length));
   }
}
