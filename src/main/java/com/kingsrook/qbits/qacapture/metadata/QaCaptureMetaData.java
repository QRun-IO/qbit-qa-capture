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

package com.kingsrook.qbits.qacapture.metadata;


import java.util.ArrayList;
import java.util.List;
import com.kingsrook.qbits.qacapture.QaCaptureQBitConfig;
import com.kingsrook.qbits.qacapture.QaCaptureSchema;
import com.kingsrook.qbits.qacapture.QaCaptureSchema.AudienceSource;
import com.kingsrook.qbits.qacapture.QaCaptureSchema.AudienceType;
import com.kingsrook.qbits.qacapture.QaCaptureSchema.Mode;
import com.kingsrook.qbits.qacapture.QaCaptureSchema.ReportStatus;
import com.kingsrook.qbits.qacapture.QaCaptureSchema.RuleSource;
import com.kingsrook.qbits.qacapture.QaCaptureSchema.RuleType;
import com.kingsrook.qbits.qacapture.spi.QaCaptureProvider;
import com.kingsrook.qqq.backend.core.actions.customizers.TableCustomizers;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.fields.AdornmentType;
import com.kingsrook.qqq.backend.core.model.metadata.fields.FieldAdornment;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QAppMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QAppSection;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QIcon;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValue;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValueSource;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValueSourceType;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QFieldSection;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.tables.Tier;
import com.kingsrook.qqq.backend.core.model.metadata.tables.UniqueKey;


/*******************************************************************************
 ** The QBit's tables, possible-value sources and app. Field names are
 ** camelCase with snake_case backend names; table names equal the backend
 ** names. Settings, audience and sanitizer rules need qa_capture.manage;
 ** reports need qa_capture.view_reports (or manage). Nothing is hidden.
 *******************************************************************************/
public class QaCaptureMetaData
{
   private final QaCaptureQBitConfig config;



   /*******************************************************************************
    ** Constructor.
    *******************************************************************************/
   public QaCaptureMetaData(QaCaptureQBitConfig config)
   {
      this.config = config;
   }



   /*******************************************************************************
    ** Add everything to the instance.
    *******************************************************************************/
   public void addTo(QInstance qInstance)
   {
      addPossibleValueSources(qInstance);
      qInstance.addTable(settings());
      qInstance.addTable(audience());
      qInstance.addTable(sanitizerRule());
      qInstance.addTable(report());
      qInstance.addPossibleValueSource(QPossibleValueSource.newForTable(QaCaptureSchema.SETTINGS));

      QAppMetaData app = new QAppMetaData()
         .withName(config.getAppName())
         .withLabel(config.getAppLabel())
         .withIcon(new QIcon("bug_report"))
         .withPermissionRules(QaCapturePermissionChecker.rules(QaCaptureSchema.PERMISSION_VIEW_REPORTS, QaCaptureSchema.PERMISSION_MANAGE));
      for(String table : QaCaptureSchema.TABLES)
      {
         app.withChild(qInstance.getTable(table));
      }
      app.withSection(new QAppSection("qaCapture", config.getAppLabel(), new QIcon("bug_report"), new ArrayList<>(QaCaptureSchema.TABLES), null, null));
      qInstance.addApp(app);
      if(config.getParentAppName() != null)
      {
         qInstance.getApp(config.getParentAppName()).withChild(app);
      }
   }



   /*******************************************************************************
    ** Enum sources for the stored values, and the registered providers.
    *******************************************************************************/
   private void addPossibleValueSources(QInstance qInstance)
   {
      List<QPossibleValue<?>> providers = new ArrayList<>();
      for(QaCaptureProvider provider : config.getProviders().values())
      {
         providers.add(new QPossibleValue<>(provider.getName(), provider.getLabel()));
      }
      qInstance.addPossibleValueSource(enumSource(QaCaptureSchema.PVS_PROVIDER, providers));

      List<QPossibleValue<?>> modes = new ArrayList<>();
      for(Mode mode : Mode.values())
      {
         modes.add(new QPossibleValue<>(mode.name(), mode.getLabel()));
      }
      qInstance.addPossibleValueSource(enumSource(QaCaptureSchema.PVS_MODE, modes));

      List<QPossibleValue<?>> audienceTypes = new ArrayList<>();
      for(AudienceType type : AudienceType.values())
      {
         audienceTypes.add(new QPossibleValue<>(type.name(), type.getLabel()));
      }
      qInstance.addPossibleValueSource(enumSource(QaCaptureSchema.PVS_AUDIENCE_TYPE, audienceTypes));

      List<QPossibleValue<?>> audienceSources = new ArrayList<>();
      for(AudienceSource source : AudienceSource.values())
      {
         audienceSources.add(new QPossibleValue<>(source.name(), source.getLabel()));
      }
      qInstance.addPossibleValueSource(enumSource(QaCaptureSchema.PVS_AUDIENCE_SOURCE, audienceSources));

      List<QPossibleValue<?>> ruleTypes = new ArrayList<>();
      for(RuleType type : RuleType.values())
      {
         ruleTypes.add(new QPossibleValue<>(type.name(), type.getLabel()));
      }
      qInstance.addPossibleValueSource(enumSource(QaCaptureSchema.PVS_RULE_TYPE, ruleTypes));

      List<QPossibleValue<?>> ruleSources = new ArrayList<>();
      for(RuleSource source : RuleSource.values())
      {
         ruleSources.add(new QPossibleValue<>(source.name(), source.getLabel()));
      }
      qInstance.addPossibleValueSource(enumSource(QaCaptureSchema.PVS_RULE_SOURCE, ruleSources));

      List<QPossibleValue<?>> statuses = new ArrayList<>();
      for(ReportStatus status : ReportStatus.values())
      {
         statuses.add(new QPossibleValue<>(status.name(), status.getLabel()));
      }
      qInstance.addPossibleValueSource(enumSource(QaCaptureSchema.PVS_REPORT_STATUS, statuses));
   }



   /*******************************************************************************
    ** qa_capture_settings.
    *******************************************************************************/
   QTableMetaData settings()
   {
      return (table(QaCaptureSchema.SETTINGS, "QA Capture Settings", "tune", QaCaptureSchema.PERMISSION_MANAGE)
         .withRecordLabelFormatAndFields("%s", "environment")
         .withUniqueKey(new UniqueKey("environment"))
         .withField(field("environment", QFieldType.STRING).withLabel("Environment").withIsRequired(true).withMaxLength(50))
         .withField(field("provider", QFieldType.STRING).withLabel("Provider").withIsRequired(true).withMaxLength(50)
            .withPossibleValueSourceName(QaCaptureSchema.PVS_PROVIDER).withDefaultValue(config.getProviders().keySet().iterator().next()))
         .withField(field("teamId", QFieldType.STRING).withLabel("Team ID").withMaxLength(100))
         .withField(field("recordingLinkId", QFieldType.STRING).withLabel("Recording Link ID").withMaxLength(100))
         .withField(field("mode", QFieldType.STRING).withLabel("Mode").withIsRequired(true).withMaxLength(30)
            .withPossibleValueSourceName(QaCaptureSchema.PVS_MODE).withDefaultValue(Mode.OFF.name()))
         .withField(field("extraBlurSelectors", QFieldType.TEXT).withLabel("Extra Blur Selectors"))
         .withField(field("notes", QFieldType.TEXT).withLabel("Notes"))
         .withSection(section("identity", "Settings", "tune", Tier.T1, "id", "environment", "provider", "mode"))
         .withSection(section("provider", "Provider", "link", Tier.T2, "teamId", "recordingLinkId", "extraBlurSelectors", "notes"))
         .withSection(section("dates", "Dates", "calendar_month", Tier.T3, "createDate", "modifyDate"))
         .withCustomizer(TableCustomizers.PRE_INSERT_RECORD, new QCodeReference(QaCaptureWriteValidator.class))
         .withCustomizer(TableCustomizers.PRE_UPDATE_RECORD, new QCodeReference(QaCaptureWriteValidator.class)));
   }



   /*******************************************************************************
    ** qa_capture_audience.
    *******************************************************************************/
   QTableMetaData audience()
   {
      return (table(QaCaptureSchema.AUDIENCE, "QA Capture Audience", "group", QaCaptureSchema.PERMISSION_MANAGE)
         .withRecordLabelFormatAndFields("%s %s", "audienceType", "value")
         .withUniqueKey(new UniqueKey("settingsId", "audienceType", "value", "source"))
         .withField(field("settingsId", QFieldType.INTEGER).withLabel("Settings").withIsRequired(true).withPossibleValueSourceName(QaCaptureSchema.SETTINGS))
         .withField(field("audienceType", QFieldType.STRING).withLabel("Audience Type").withIsRequired(true).withMaxLength(10)
            .withPossibleValueSourceName(QaCaptureSchema.PVS_AUDIENCE_TYPE).withDefaultValue(AudienceType.USER.name()))
         .withField(field("value", QFieldType.STRING).withLabel("Value").withIsRequired(true).withMaxLength(250))
         .withField(field("source", QFieldType.STRING).withLabel("Source").withIsRequired(true).withMaxLength(20)
            .withPossibleValueSourceName(QaCaptureSchema.PVS_AUDIENCE_SOURCE).withDefaultValue(AudienceSource.ADMIN.name()))
         .withField(field("active", QFieldType.BOOLEAN).withLabel("Active").withIsRequired(true).withDefaultValue(true))
         .withField(field("notes", QFieldType.TEXT).withLabel("Notes"))
         .withSection(section("member", "Member", "group", Tier.T1, "id", "settingsId", "audienceType", "value", "source", "active", "notes"))
         .withSection(section("dates", "Dates", "calendar_month", Tier.T3, "createDate", "modifyDate"))
         .withCustomizer(TableCustomizers.PRE_INSERT_RECORD, new QCodeReference(QaCaptureWriteValidator.class))
         .withCustomizer(TableCustomizers.PRE_UPDATE_RECORD, new QCodeReference(QaCaptureWriteValidator.class)));
   }



   /*******************************************************************************
    ** qa_capture_sanitizer_rule.
    *******************************************************************************/
   QTableMetaData sanitizerRule()
   {
      return (table(QaCaptureSchema.SANITIZER_RULE, "QA Capture Sanitizer Rules", "shield", QaCaptureSchema.PERMISSION_MANAGE)
         .withRecordLabelFormatAndFields("%s %s", "ruleType", "value")
         .withUniqueKey(new UniqueKey("ruleType", "value"))
         .withField(field("ruleType", QFieldType.STRING).withLabel("Rule Type").withIsRequired(true).withMaxLength(20)
            .withPossibleValueSourceName(QaCaptureSchema.PVS_RULE_TYPE))
         .withField(field("value", QFieldType.STRING).withLabel("Value").withIsRequired(true).withMaxLength(500))
         .withField(field("source", QFieldType.STRING).withLabel("Source").withIsRequired(true).withMaxLength(20)
            .withPossibleValueSourceName(QaCaptureSchema.PVS_RULE_SOURCE).withDefaultValue(RuleSource.ADMIN.name()))
         .withField(field("sourceRef", QFieldType.STRING).withLabel("Source Reference").withMaxLength(250))
         .withField(field("active", QFieldType.BOOLEAN).withLabel("Active").withIsRequired(true).withDefaultValue(true))
         .withField(field("description", QFieldType.STRING).withLabel("Description").withMaxLength(500))
         .withSection(section("rule", "Rule", "shield", Tier.T1, "id", "ruleType", "value", "active", "source", "sourceRef", "description"))
         .withSection(section("dates", "Dates", "calendar_month", Tier.T3, "createDate", "modifyDate"))
         .withCustomizer(TableCustomizers.PRE_INSERT_RECORD, new QCodeReference(QaCaptureWriteValidator.class))
         .withCustomizer(TableCustomizers.PRE_UPDATE_RECORD, new QCodeReference(QaCaptureWriteValidator.class)));
   }



   /*******************************************************************************
    ** qa_capture_report.
    *******************************************************************************/
   QTableMetaData report()
   {
      return (table(QaCaptureSchema.REPORT, "QA Reports", "bug_report", QaCaptureSchema.PERMISSION_VIEW_REPORTS, QaCaptureSchema.PERMISSION_MANAGE)
         .withRecordLabelFormatAndFields("%s", "title")
         .withUniqueKey(new UniqueKey("reference"))
         .withUniqueKey(new UniqueKey("jamId"))
         .withField(readOnly("reference", "Reference", 50).withIsRequired(true))
         .withField(field("title", QFieldType.STRING).withLabel("Title").withMaxLength(250))
         .withField(field("status", QFieldType.STRING).withLabel("Status").withIsRequired(true).withMaxLength(20)
            .withPossibleValueSourceName(QaCaptureSchema.PVS_REPORT_STATUS).withDefaultValue(ReportStatus.OPEN.name()))
         .withField(readOnly("pageUrl", "Page URL", 2000))
         .withField(readOnly("appName", "App", 100))
         .withField(readOnly("tableName", "Table", 100))
         .withField(readOnly("recordId", "Record ID", 100))
         .withField(readOnly("processName", "Process", 100))
         .withField(field("requestIds", QFieldType.TEXT).withLabel("Request IDs").withIsEditable(false))
         .withField(readOnly("userId", "User ID", 250))
         .withField(readOnly("userLabel", "User", 250))
         .withField(readOnly("environment", "Environment", 50))
         .withField(readOnly("provider", "Provider", 50).withPossibleValueSourceName(QaCaptureSchema.PVS_PROVIDER))
         .withField(readOnly("frontendVersion", "Frontend Version", 100))
         .withField(readOnly("backendVersion", "Backend Version", 100))
         .withField(readOnly("node", "Node", 250))
         .withField(readOnly("userAgent", "User Agent", 1000))
         .withField(readOnly("viewport", "Viewport", 30))
         .withField(readOnly("jamId", "Jam ID", 100))
         .withField(readOnly("jamUrl", "Jam", 1000).withFieldAdornment(link()))
         .withField(field("githubIssueUrl", QFieldType.STRING).withLabel("GitHub Issue").withMaxLength(1000).withFieldAdornment(link()))
         .withField(field("submitterComment", QFieldType.TEXT).withLabel("Submitter Comment"))
         .withSection(section("report", "Report", "bug_report", Tier.T1, "id", "reference", "title", "status", "jamUrl", "githubIssueUrl", "submitterComment"))
         .withSection(section("where", "Where", "place", Tier.T2, "pageUrl", "appName", "tableName", "recordId", "processName", "requestIds"))
         .withSection(section("who", "Who and Build", "person", Tier.T2, "userId", "userLabel", "environment", "provider", "jamId",
            "frontendVersion", "backendVersion", "node", "userAgent", "viewport"))
         .withSection(section("dates", "Dates", "calendar_month", Tier.T3, "createDate", "modifyDate")));
   }



   /*******************************************************************************
    ** A table with id and dates, gated on the permissions.
    *******************************************************************************/
   private QTableMetaData table(String name, String label, String icon, String... permissions)
   {
      return (new QTableMetaData()
         .withName(name)
         .withLabel(label)
         .withBackendName(config.getBackendName())
         .withIcon(new QIcon(icon))
         .withPrimaryKeyField("id")
         .withPermissionRules(QaCapturePermissionChecker.rules(permissions))
         .withField(new QFieldMetaData("id", QFieldType.INTEGER).withLabel("ID").withIsEditable(false))
         .withField(field("createDate", QFieldType.DATE_TIME).withLabel("Create Date").withIsEditable(false))
         .withField(field("modifyDate", QFieldType.DATE_TIME).withLabel("Modify Date").withIsEditable(false)));
   }



   /*******************************************************************************
    ** A field with its snake_case backend name.
    *******************************************************************************/
   static QFieldMetaData field(String name, QFieldType type)
   {
      return (new QFieldMetaData(name, type).withBackendName(snakeCase(name)));
   }



   /*******************************************************************************
    ** A read-only string field.
    *******************************************************************************/
   private static QFieldMetaData readOnly(String name, String label, int maxLength)
   {
      return (field(name, QFieldType.STRING).withLabel(label).withMaxLength(maxLength).withIsEditable(false));
   }



   /*******************************************************************************
    ** A link opening in a new tab.
    *******************************************************************************/
   private static FieldAdornment link()
   {
      return (new FieldAdornment(AdornmentType.LINK).withValue(AdornmentType.LinkValues.TARGET, "_blank"));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static QFieldSection section(String name, String label, String icon, Tier tier, String... fields)
   {
      return (new QFieldSection(name, label, new QIcon(icon), tier, List.of(fields)));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static QPossibleValueSource enumSource(String name, List<QPossibleValue<?>> values)
   {
      return (new QPossibleValueSource().withName(name).withType(QPossibleValueSourceType.ENUM).withEnumValues(values));
   }



   /*******************************************************************************
    ** camelCase to snake_case.
    *******************************************************************************/
   static String snakeCase(String name)
   {
      return (name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(java.util.Locale.ROOT));
   }
}
