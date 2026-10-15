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

package com.kingsrook.qbits.qacapture;


import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.qacapture.service.QaCaptureDb;
import com.kingsrook.qbits.qacapture.service.QaCaptureService;
import com.kingsrook.qbits.qacapture.service.QaModePreference;
import com.kingsrook.qbits.qacapture.service.SanitizerRules;
import com.kingsrook.qbits.qacapture.service.SecuredDataRuleSync;
import com.kingsrook.qbits.qacapture.web.RecorderPage;
import com.kingsrook.qqq.backend.core.actions.preferences.UserPreferencesAction;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QBadRequestException;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.exceptions.QNotFoundException;
import com.kingsrook.qqq.backend.core.exceptions.QPermissionDeniedException;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.preferences.UserPreferenceSectionResult;
import com.kingsrook.qqq.middleware.javalin.routeproviders.NextDashboardSecurityHeaders;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** The service against the real changelog schema: capability shapes, report
 ** creation, QA mode storage, secured-data rules, the CSP helper, the
 ** recorder page and table permissions.
 *******************************************************************************/
class QaCaptureServiceTest extends BaseQaCaptureTest
{
   /*******************************************************************************
    ** No settings row: not configured, nothing provider-specific.
    *******************************************************************************/
   @Test
   void capabilityNotConfigured() throws Exception
   {
      Map<String, Object> capability = new QaCaptureService(qInstance).capability(loginAs(NOBODY));
      assertThat(capability).containsEntry("enabled", false).containsEntry("reason", "NOT_CONFIGURED")
         .containsEntry("provider", null).containsEntry("providerConfig", null).containsEntry("sanitizer", null)
         .containsEntry("canReport", false).containsEntry("reportsHref", null).containsEntry("requestIdHeader", "X-Request-Id")
         .containsEntry("blurSelectors", List.of());
      assertThat(capability.get("context")).isEqualTo(Map.of("environment", "prod", "backendVersion", "0.1.110", "node", "node-a"));
      assertThat(capability.get("cspOrigins")).isEqualTo(Map.of("scriptSrc", List.of(), "frameSrc", List.of(), "connectSrc", List.of()));
      @SuppressWarnings("unchecked")
      Map<String, Object> qaMode = (Map<String, Object>) capability.get("qaMode");
      assertThat(qaMode).containsEntry("available", false).containsEntry("active", false).containsEntry("preferenceSection", null);
      assertThat(new ArrayList<>(capability.keySet())).containsExactly("enabled", "reason", "provider", "providerConfig", "qaMode", "canReport",
         "reportsHref", "sanitizer", "blurSelectors", "cspOrigins", "context", "requestIdHeader");
      assertThat(new QaCaptureService(qInstance).capability(loginAs(VIEWER))).containsEntry("reportsHref", "/app/qa_capture_report");
   }



   /*******************************************************************************
    ** An audience member gets the full capability.
    *******************************************************************************/
   @Test
   @SuppressWarnings("unchecked")
   void capabilityForAudience() throws Exception
   {
      QRecord settings = insertSettings("AUDIENCE", TEAM_ID, "abc123");
      QaCaptureRuntime.asSystem(qInstance, () -> QaCaptureDb.update(QaCaptureSchema.SETTINGS, new QRecord().withValue("id", settings.getValue("id"))
         .withValue("extraBlurSelectors", ".secret-panel\n\n  [data-private]  ")));
      insertAudience(settings, "USER", NOBODY + "@EXAMPLE.com", true);
      SecuredDataRuleSync.sync(qInstance);

      Map<String, Object> capability = new QaCaptureService(qInstance).capability(loginAs(NOBODY));
      assertThat(capability).containsEntry("enabled", true).containsEntry("reason", "AUDIENCE").containsEntry("provider", "jam")
         .containsEntry("canReport", true).containsEntry("reportsHref", null);
      assertThat((Map<String, Object>) capability.get("providerConfig")).containsEntry("teamId", TEAM_ID).containsEntry("recordingLinkId", "abc123")
         .containsEntry("scriptUrls", List.of("https://js.jam.dev/recorder.js", "https://js.jam.dev/capture.js"));
      Map<String, Object> sanitizer = (Map<String, Object>) capability.get("sanitizer");
      assertThat(sanitizer).containsEntry("redaction", "[redacted]").containsKeys("headerNames", "bodyFieldNames", "queryParamNames", "valuePatterns", "dropPathPrefixes");
      assertThat((List<String>) sanitizer.get("headerNames")).contains("authorization", "cookie", "set-cookie").hasSize(8);
      assertThat((List<String>) sanitizer.get("bodyFieldNames")).contains("password", "accessToken", "ssn", "diagnosisCode");
      assertThat((List<String>) sanitizer.get("queryParamNames")).contains("code", "state", "token");
      assertThat((List<String>) sanitizer.get("valuePatterns")).hasSize(2);
      assertThat((List<String>) capability.get("blurSelectors")).containsExactly("[data-qqq-secure]", "input[type=password]", ".secret-panel", "[data-private]");
      assertThat(capability.get("cspOrigins")).isEqualTo(Map.of("scriptSrc", List.of("https://js.jam.dev"), "frameSrc", List.of("https://*.jam.dev"),
         "connectSrc", List.of("https://*.jam.dev")));

      assertThat(new QaCaptureService(qInstance).capability(loginAs(VIEWER))).containsEntry("enabled", false).containsEntry("reason", "NO_ACCESS");
   }



   /*******************************************************************************
    ** Reports: stripped URL, capped request ids, server-filled fields,
    ** recording parameters; 403 and 400 cases.
    *******************************************************************************/
   @Test
   void createReport() throws Exception
   {
      QRecord settings = insertSettings("AUDIENCE", TEAM_ID, "abc123");
      insertAudience(settings, "USER", NOBODY, true);
      QaCaptureService service = new QaCaptureService(qInstance);

      List<Object> requestIds = new ArrayList<>();
      for(int i = 0; i < 60; i++)
      {
         requestIds.add("req-00000-" + i);
      }
      requestIds.add("bad id with spaces");
      requestIds.add(42);
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("pageUrl", "https://admin.kof22.com/app/blog_post/42?code=xyz&tab=main&STATE=s#top");
      body.put("tableName", "blog_post");
      body.put("recordId", "42");
      body.put("requestIds", requestIds);
      body.put("frontendVersion", "1.0.0-RC.30");
      body.put("userAgent", "Mozilla/5.0");
      body.put("viewport", "1440x900");

      Map<String, Object> result = service.createReport(loginAs(NOBODY), body);
      assertThat(result).containsKeys("id", "reference", "title", "recordingParams").containsEntry("title", "Bug on blog_post 42");
      String reference = (String) result.get("reference");
      assertThat(reference).matches("qa-[0-9a-f-]{36}");
      assertThat(result.get("recordingParams")).isEqualTo(Map.of("jam-recording", "abc123", "jam-title", "Bug on blog_post 42", "jam-reference", reference));

      QRecord stored = all(QaCaptureSchema.REPORT).get(0);
      assertThat(stored.getValueString("pageUrl")).isEqualTo("https://admin.kof22.com/app/blog_post/42?tab=main#top");
      assertThat(stored.getValueString("requestIds").split("\n")).hasSize(50).startsWith("req-00000-0");
      assertThat(stored.getValueString("status")).isEqualTo("OPEN");
      assertThat(stored.getValueString("userId")).isEqualTo(NOBODY);
      assertThat(stored.getValueString("userLabel")).isEqualTo("Name of " + NOBODY);
      assertThat(stored.getValueString("environment")).isEqualTo("prod");
      assertThat(stored.getValueString("provider")).isEqualTo("jam");
      assertThat(stored.getValueString("backendVersion")).isEqualTo("0.1.110");
      assertThat(stored.getValueString("node")).isEqualTo("node-a");
      assertThat(stored.getValueString("frontendVersion")).isEqualTo("1.0.0-RC.30");
      assertThat(stored.getValueString("viewport")).isEqualTo("1440x900");

      ///////////////////////////////////////////////////////////////////////
      // default titles; no recording link means no recording parameters  //
      ///////////////////////////////////////////////////////////////////////
      QaCaptureRuntime.asSystem(qInstance, () -> QaCaptureDb.update(QaCaptureSchema.SETTINGS, new QRecord().withValue("id", settings.getValue("id")).withValue("recordingLinkId", null)));
      Map<String, Object> second = service.createReport(loginAs(NOBODY), Map.of("pageUrl", "https://admin.kof22.com/app/orders?token=1", "processName", "orders.ship"));
      assertThat(second).containsEntry("title", "Bug in orders.ship").containsEntry("recordingParams", null);
      assertThat(service.createReport(loginAs(NOBODY), Map.of("pageUrl", "https://x.example.com/a/b?c=1", "appName", "crm"))).containsEntry("title", "Bug in crm");
      assertThat(service.createReport(loginAs(NOBODY), Map.of("pageUrl", "https://x.example.com/a/b?c=1"))).containsEntry("title", "Bug on /a/b");
      assertThat(service.createReport(loginAs(NOBODY), Map.of("pageUrl", "https://x.example.com/", "title", "  Custom  "))).containsEntry("title", "Custom");

      assertThatThrownBy(() -> service.createReport(loginAs(NOBODY), Map.of())).isInstanceOf(QBadRequestException.class);
      assertThatThrownBy(() -> service.createReport(loginAs(NOBODY), Map.of("pageUrl", 5))).isInstanceOf(QBadRequestException.class);
      assertThatThrownBy(() -> service.createReport(loginAs(NOBODY), Map.of("pageUrl", "https://x", "requestIds", "nope"))).isInstanceOf(QBadRequestException.class);
      assertThatThrownBy(() -> service.createReport(loginAs(VIEWER), Map.of("pageUrl", "https://x"))).isInstanceOf(QPermissionDeniedException.class);
   }



   /*******************************************************************************
    ** QA mode through core's preference action, stored in the audience table
    ** (no key-value store in this host); the section is hidden without the
    ** permission.
    *******************************************************************************/
   @Test
   void qaModeFallbackStore() throws Exception
   {
      QaCaptureService service = new QaCaptureService(qInstance);
      loginAs(TESTER);
      assertThatThrownBy(() -> new UserPreferencesAction().patchSection(QaModePreference.SECTION_NAME, Map.of("values", Map.of("qaMode", true))))
         .as("no settings row yet").isInstanceOf(QException.class);

      insertSettings("SELF_SERVE", TEAM_ID, null);
      assertThat(service.capability(loginAs(TESTER))).containsEntry("reason", "QA_MODE_OFF")
         .extractingByKey("qaMode").isEqualTo(Map.of("available", true, "active", false, "preferenceSection", "qa-capture"));

      UserPreferenceSectionResult patched = new UserPreferencesAction().patchSection(QaModePreference.SECTION_NAME, Map.of("values", Map.of("qaMode", true)));
      assertThat(patched.getValues().getValues()).containsEntry("qaMode", true);
      assertThat(service.capability(loginAs(TESTER))).containsEntry("enabled", true).containsEntry("reason", "QA_MODE");

      QRecord row = all(QaCaptureSchema.AUDIENCE).get(0);
      assertThat(row.getValueString("source")).isEqualTo("QA_MODE");
      assertThat(row.getValueString("value")).isEqualTo(TESTER);

      new UserPreferencesAction().patchSection(QaModePreference.SECTION_NAME, Map.of("values", Map.of("qaMode", false)));
      assertThat(service.capability(loginAs(TESTER))).containsEntry("reason", "QA_MODE_OFF");
      assertThat(all(QaCaptureSchema.AUDIENCE)).hasSize(1);

      loginAs(VIEWER);
      assertThatThrownBy(() -> new UserPreferencesAction().getSection(QaModePreference.SECTION_NAME)).isInstanceOf(QNotFoundException.class);
   }



   /*******************************************************************************
    ** Secured-data rules: added once, insert-only, never touching admin edits.
    *******************************************************************************/
   @Test
   void securedDataRules() throws Exception
   {
      assertThat(SecuredDataRuleSync.secureFields(qInstance)).containsEntry("ssn", "patient.ssn").containsEntry("diagnosisCode", "patient.diagnosisCode")
         .doesNotContainKey("name");
      assertThat(SecuredDataRuleSync.sync(qInstance)).as("ssn is already built in").isEqualTo(1);
      assertThat(SecuredDataRuleSync.sync(qInstance)).isZero();

      QRecord rule = all(QaCaptureSchema.SANITIZER_RULE).stream().filter(r -> "diagnosisCode".equals(r.getValueString("value"))).findFirst().orElseThrow();
      assertThat(rule.getValueString("source")).isEqualTo("SECURED_DATA");
      assertThat(rule.getValueString("sourceRef")).isEqualTo("patient.diagnosisCode");

      QaCaptureRuntime.asSystem(qInstance, () -> QaCaptureDb.update(QaCaptureSchema.SANITIZER_RULE, new QRecord().withValue("id", rule.getValue("id")).withValue("active", false)));
      assertThat(SecuredDataRuleSync.sync(qInstance)).isZero();
      assertThat(QaCaptureRuntime.asSystem(qInstance, SanitizerRules::load).getBodyFieldNames()).doesNotContain("diagnosisCode").contains("ssn");
   }



   /*******************************************************************************
    ** The dashboard CSP gets the provider origins only while capture is active.
    *******************************************************************************/
   @Test
   void cspHelper() throws Exception
   {
      NextDashboardSecurityHeaders headers = new NextDashboardSecurityHeaders();
      assertThat(QaCaptureCsp.customize(headers, qInstance)).isFalse();
      assertThat(QaCaptureCsp.customize(headers, null)).isFalse();
      assertThat(QaCaptureCsp.customize(null, qInstance)).isFalse();

      QRecord settings = insertSettings("OFF", TEAM_ID, null);
      assertThat(QaCaptureCsp.customize(headers, qInstance)).isFalse();
      assertThat(headers.getSources("script-src")).containsExactly("'self'");

      QaCaptureRuntime.asSystem(qInstance, () -> QaCaptureDb.update(QaCaptureSchema.SETTINGS, new QRecord().withValue("id", settings.getValue("id")).withValue("mode", "SELF_SERVE")));
      QaCaptureCsp.customize(headers);
      assertThat(headers.getSources("script-src")).containsExactly("'self'", "https://js.jam.dev");
      assertThat(headers.getSources("frame-src")).containsExactly("'self'", "https://*.jam.dev");
      assertThat(headers.getSources("connect-src")).containsExactly("'self'", "https://*.jam.dev");

      ///////////////////////////////////////////////////////////////////
      // a broken database is logged and leaves the headers unchanged  //
      ///////////////////////////////////////////////////////////////////
      try(java.sql.Connection connection = java.sql.DriverManager.getConnection(JDBC_URL, "sa", ""); java.sql.Statement statement = connection.createStatement())
      {
         statement.execute("DROP ALL OBJECTS");
      }
      NextDashboardSecurityHeaders fresh = new NextDashboardSecurityHeaders();
      assertThat(QaCaptureCsp.customize(fresh, qInstance)).isFalse();
      assertThat(fresh.getSources("script-src")).containsExactly("'self'");
   }



   /*******************************************************************************
    ** The recorder page: provider markup, sanitizers, hash-based CSP.
    *******************************************************************************/
   @Test
   void recorderPage() throws Exception
   {
      assertThat(RecorderPage.build(qInstance)).isNull();
      insertSettings("AUDIENCE", TEAM_ID, null);
      RecorderPage.Page page = RecorderPage.build(qInstance);
      assertThat(page.html()).contains("<meta name=\"jam:team\" content=\"" + TEAM_ID + "\">",
         "<script type=\"module\" src=\"https://js.jam.dev/recorder.js\"></script>",
         "<script type=\"module\" src=\"https://js.jam.dev/capture.js\"></script>",
         "window.jam.capture.setSanitizers(sanitizers)");
      assertThat(page.html().indexOf("capture.js")).isLessThan(page.html().indexOf("setSanitizers"));
      assertThat(page.contentSecurityPolicy()).contains("script-src 'self' https://js.jam.dev 'sha256-", "frame-src https://*.jam.dev",
         "connect-src 'self' https://*.jam.dev", "frame-ancestors 'none'");
   }



   /*******************************************************************************
    ** Table access follows the QBit's permissions; writes are validated.
    *******************************************************************************/
   @Test
   void tablePermissionsAndValidation() throws Exception
   {
      loginAs(VIEWER);
      assertThatThrownBy(() -> new QueryAction().execute(new QueryInput(QaCaptureSchema.SETTINGS))).isInstanceOf(QPermissionDeniedException.class);
      assertThat(new QueryAction().execute(new QueryInput(QaCaptureSchema.REPORT)).getRecords()).isEmpty();
      loginAs(TESTER);
      assertThatThrownBy(() -> new QueryAction().execute(new QueryInput(QaCaptureSchema.REPORT))).isInstanceOf(QPermissionDeniedException.class);

      loginAs(ADMIN);
      assertThat(new QueryAction().execute(new QueryInput(QaCaptureSchema.SANITIZER_RULE)).getRecords()).hasSize(50);
      QRecord bad = new InsertAction().execute(new InsertInput(QaCaptureSchema.SANITIZER_RULE).withRecord(new QRecord()
         .withValue("ruleType", "VALUE_PATTERN").withValue("value", "a++").withValue("source", "ADMIN").withValue("active", true))).getRecords().get(0);
      assertThat(bad.getErrors()).isNotEmpty();
      QRecord header = new InsertAction().execute(new InsertInput(QaCaptureSchema.SANITIZER_RULE).withRecord(new QRecord()
         .withValue("ruleType", "HEADER").withValue("value", "  X-Tenant-Secret ").withValue("source", "ADMIN").withValue("active", true))).getRecords().get(0);
      assertThat(header.getErrors()).isEmpty();
      assertThat(header.getValueString("value")).isEqualTo("x-tenant-secret");
      QRecord badUpdate = new com.kingsrook.qqq.backend.core.actions.tables.UpdateAction().execute(new com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput(QaCaptureSchema.SANITIZER_RULE)
         .withRecord(new QRecord().withValue("id", header.getValue("id")).withValue("ruleType", "DROP_PATH"))).getRecords().get(0);
      assertThat(badUpdate.getErrors()).as("x-tenant-secret is not a path").isNotEmpty();
      assertThat(QContext.getQSession().getUser().getIdReference()).isEqualTo(ADMIN);
   }
}
