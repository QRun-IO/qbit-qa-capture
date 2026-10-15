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


import java.io.Serializable;
import java.util.List;
import com.kingsrook.qbits.qacapture.QaCaptureRuntime;
import com.kingsrook.qbits.qacapture.QaCaptureSchema;
import com.kingsrook.qbits.qacapture.QaCaptureSchema.AudienceSource;
import com.kingsrook.qbits.qacapture.QaCaptureSchema.AudienceType;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.help.QHelpContent;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QIcon;
import com.kingsrook.qqq.backend.core.model.metadata.preferences.UserPreferenceChange;
import com.kingsrook.qqq.backend.core.model.metadata.preferences.UserPreferenceProviderInterface;
import com.kingsrook.qqq.backend.core.model.metadata.preferences.UserPreferenceRequestContext;
import com.kingsrook.qqq.backend.core.model.metadata.preferences.UserPreferenceSectionMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.preferences.UserPreferenceValues;
import com.kingsrook.qqq.backend.core.model.metadata.preferences.UserPreferencesMetaData;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import com.kingsrook.qqq.backend.core.utils.ValueUtils;


/*******************************************************************************
 ** The "qa-capture" user preference section: one boolean, QA mode, visible
 ** and patchable only by holders of qa_capture.use_qa_mode (core's
 ** section-level permission gate).
 **
 ** Storage: core's key-value preference store when the host defines it
 ** (UserPreferencesKeyValueStoreMetaDataProvider); otherwise a
 ** qa_capture_audience row with source QA_MODE, audience type USER and the
 ** user id as value on the environment's settings row (active = QA mode on).
 ** Those rows never grant audience membership; they are visible to
 ** administrators like every other row.
 *******************************************************************************/
public class QaModePreference implements UserPreferenceProviderInterface
{
   public static final String SECTION_NAME = "qa-capture";
   public static final String FIELD_NAME   = "qaMode";



   /*******************************************************************************
    ** Register the section once.
    *******************************************************************************/
   public static void register(QInstance qInstance)
   {
      if(isRegistered(qInstance))
      {
         return;
      }
      UserPreferencesMetaData.ofOrWithNew(qInstance).withSection(new UserPreferenceSectionMetaData()
         .withName(SECTION_NAME).withLabel("QA capture").withIcon(new QIcon("bug_report")).withOrder(80)
         .withDescription("Record bug reports with screen, console and network context while you test.")
         .withPermissionName(QaCaptureSchema.PERMISSION_USE_QA_MODE)
         .withProvider(new QCodeReference(QaModePreference.class))
         .withField(new QFieldMetaData(FIELD_NAME, QFieldType.BOOLEAN).withLabel("QA mode").withDefaultValue(false)
            .withHelpContent(new QHelpContent("Turn on to show Report a bug and capture recordings while you test. Secure values are blurred and secrets are removed before anything leaves your browser."))));
   }



   /*******************************************************************************
    ** Whether the section is installed in an instance.
    *******************************************************************************/
   public static boolean isRegistered(QInstance qInstance)
   {
      UserPreferencesMetaData metaData = UserPreferencesMetaData.of(qInstance);
      return (metaData != null && metaData.getSections().stream().anyMatch(section -> SECTION_NAME.equals(section.getName())));
   }



   /*******************************************************************************
    ** The key-value store table name when the host defines the store, else null.
    *******************************************************************************/
   public static String keyValueTableName(QInstance qInstance)
   {
      UserPreferencesMetaData metaData = UserPreferencesMetaData.of(qInstance);
      String                  table    = metaData == null ? null : metaData.getKeyValueTableName();
      return (table != null && qInstance.getTable(table) != null ? table : null);
   }



   /*******************************************************************************
    ** A user's QA mode (call in the system context).
    **
    ** @param userId   the user's id reference
    ** @param settings the environment's settings row (for the fallback store)
    *******************************************************************************/
   public static boolean read(String userId, QRecord settings) throws QException
   {
      if(userId == null)
      {
         return (false);
      }
      String keyValueTable = keyValueTableName(QContext.getQInstance());
      if(keyValueTable != null)
      {
         QRecord stored = QaCaptureDb.first(keyValueTable, QaCaptureDb.eq("userId", userId)
            .withCriteria("sectionName", QCriteriaOperator.EQUALS, SECTION_NAME)
            .withCriteria("preferenceName", QCriteriaOperator.EQUALS, FIELD_NAME));
         if(stored == null)
         {
            return (false);
         }
         try
         {
            return (Boolean.TRUE.equals(ValueUtils.getValueAsBoolean(JsonUtils.toObject(stored.getValueString("value"), Object.class))));
         }
         catch(Exception e)
         {
            return (false);
         }
      }
      QRecord row = fallbackRow(userId, settings);
      return (row != null && Boolean.TRUE.equals(row.getValueBoolean("active")));
   }



   /*******************************************************************************
    ** Store a user's QA mode in the fallback store (call in the system context).
    *******************************************************************************/
   public static void writeFallback(String userId, QRecord settings, boolean on) throws QException
   {
      if(settings == null)
      {
         throw (new QException("QA capture is not set up for this environment yet, so QA mode can't be saved."));
      }
      QRecord row = fallbackRow(userId, settings);
      if(row == null)
      {
         QaCaptureDb.insert(QaCaptureSchema.AUDIENCE, new QRecord()
            .withValue("settingsId", settings.getValue("id"))
            .withValue("audienceType", AudienceType.USER.name())
            .withValue("value", userId)
            .withValue("source", AudienceSource.QA_MODE.name())
            .withValue("active", on)
            .withValue("notes", "The user's own QA mode switch (preferences). It does not add the user to the audience."));
      }
      else
      {
         QaCaptureDb.update(QaCaptureSchema.AUDIENCE, new QRecord().withValue("id", row.getValue("id")).withValue("active", on));
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static QRecord fallbackRow(String userId, QRecord settings) throws QException
   {
      if(settings == null)
      {
         return (null);
      }
      return (QaCaptureDb.first(QaCaptureSchema.AUDIENCE, QaCaptureDb.eq("settingsId", settings.getValue("id"))
         .withCriteria("source", QCriteriaOperator.EQUALS, AudienceSource.QA_MODE.name())
         .withCriteria("audienceType", QCriteriaOperator.EQUALS, AudienceType.USER.name())
         .withCriteria("value", QCriteriaOperator.EQUALS, userId)));
   }



   /*******************************************************************************
    ** Load the session user's QA mode.
    *******************************************************************************/
   @Override
   public UserPreferenceValues load(UserPreferenceRequestContext context) throws QException
   {
      QInstance qInstance = QContext.getQInstance();
      boolean on = QaCaptureRuntime.asSystem(qInstance, () -> read(context.getUserId(), QaCaptureSettings.load(qInstance)));
      UserPreferenceValues values = new UserPreferenceValues();
      values.getValues().put(FIELD_NAME, on);
      return (values);
   }



   /*******************************************************************************
    ** The fallback store needs the environment's settings row.
    *******************************************************************************/
   @Override
   public List<String> validate(UserPreferenceRequestContext context, UserPreferenceChange change) throws QException
   {
      QInstance qInstance = QContext.getQInstance();
      if(keyValueTableName(qInstance) == null && QaCaptureRuntime.asSystem(qInstance, () -> QaCaptureSettings.load(qInstance)) == null)
      {
         return (List.of("QA capture is not set up for this environment yet, so QA mode can't be saved."));
      }
      return (List.of());
   }



   /*******************************************************************************
    ** Store the session user's QA mode.
    *******************************************************************************/
   @Override
   public void store(UserPreferenceRequestContext context, UserPreferenceChange change) throws QException
   {
      if(!change.getValues().containsKey(FIELD_NAME))
      {
         return;
      }
      Serializable value     = change.getValues().get(FIELD_NAME);
      boolean      on        = Boolean.TRUE.equals(ValueUtils.getValueAsBoolean(value));
      QInstance    qInstance = QContext.getQInstance();
      String       userId    = context.getUserId();
      QaCaptureRuntime.asSystem(qInstance, () ->
      {
         String keyValueTable = keyValueTableName(qInstance);
         if(keyValueTable != null)
         {
            QRecord existing = QaCaptureDb.first(keyValueTable, QaCaptureDb.eq("userId", userId)
               .withCriteria("sectionName", QCriteriaOperator.EQUALS, SECTION_NAME)
               .withCriteria("preferenceName", QCriteriaOperator.EQUALS, FIELD_NAME));
            QRecord record = new QRecord().withValue("value", JsonUtils.toJson(on));
            if(existing == null)
            {
               QaCaptureDb.insert(keyValueTable, record.withValue("userId", userId).withValue("sectionName", SECTION_NAME).withValue("preferenceName", FIELD_NAME));
            }
            else
            {
               QaCaptureDb.update(keyValueTable, record.withValue("id", existing.getValue("id")));
            }
         }
         else
         {
            writeFallback(userId, QaCaptureSettings.load(qInstance), on);
         }
         return (null);
      });
   }
}
