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


import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import com.kingsrook.qbits.qacapture.QaCaptureRuntime;
import com.kingsrook.qbits.qacapture.QaCaptureSchema;
import com.kingsrook.qbits.qacapture.QaCaptureSchema.RuleSource;
import com.kingsrook.qbits.qacapture.QaCaptureSchema.RuleType;
import com.kingsrook.qqq.backend.core.actions.ai.AiFieldMetaData;
import com.kingsrook.qqq.backend.core.actions.ai.DataClassification;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** Adds a BODY_FIELD sanitizer rule (source SECURED_DATA) for every field the
 ** instance marks secure, so captures redact those values in API bodies and
 ** console output. A field is secure when core metadata says so:
 ** - QFieldMetaData.isSensitive (excluded from exports), or
 ** - a field-level data classification of PII or PHI (core's
 **   AiFieldMetaData, which qbit-secured-data's classifications also set).
 ** Table-level classifications alone do not add rules (they would redact
 ** every field of the table, ids included).
 **
 ** Insert-only and idempotent: a rule that exists for the field name (any
 ** source, active or not) is never changed or deleted, so administrators can
 ** deactivate a secured-data rule and it stays deactivated.
 *******************************************************************************/
public final class SecuredDataRuleSync
{
   private static final QLogger LOG = QLogger.getLogger(SecuredDataRuleSync.class);



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private SecuredDataRuleSync()
   {
   }



   /*******************************************************************************
    ** The secure fields of an instance: field name to "table.field" (the
    ** first table that marks it).
    *******************************************************************************/
   public static Map<String, String> secureFields(QInstance qInstance)
   {
      Map<String, String> fields = new LinkedHashMap<>();
      for(QTableMetaData table : qInstance.getTables().values())
      {
         if(table.getName().startsWith("qa_capture_") || table.getFields() == null)
         {
            continue;
         }
         for(QFieldMetaData field : table.getFields().values())
         {
            if(isSecure(field) && !fields.containsKey(field.getName()))
            {
               fields.put(field.getName(), table.getName() + "." + field.getName());
            }
         }
      }
      return (fields);
   }



   /*******************************************************************************
    ** Whether core metadata marks a field secure.
    *******************************************************************************/
   public static boolean isSecure(QFieldMetaData field)
   {
      if(field.getIsSensitive())
      {
         return (true);
      }
      AiFieldMetaData ai = AiFieldMetaData.of(field);
      return (ai != null && ai.getClassification() != null && ai.getClassification().isAtLeast(DataClassification.PII));
   }



   /*******************************************************************************
    ** Insert the missing rules (in the system context). Returns how many were
    ** added.
    *******************************************************************************/
   public static int sync(QInstance qInstance) throws QException
   {
      Map<String, String> secure = secureFields(qInstance);
      if(secure.isEmpty())
      {
         return (0);
      }
      return (QaCaptureRuntime.asSystem(qInstance, () ->
      {
         Set<String> existing = new HashSet<>();
         for(QRecord rule : QaCaptureDb.query(QaCaptureSchema.SANITIZER_RULE, QaCaptureDb.eq("ruleType", RuleType.BODY_FIELD.name())))
         {
            existing.add(rule.getValueString("value").toLowerCase(Locale.ROOT));
         }
         int added = 0;
         for(Map.Entry<String, String> entry : secure.entrySet())
         {
            if(existing.add(entry.getKey().toLowerCase(Locale.ROOT)))
            {
               QaCaptureDb.insert(QaCaptureSchema.SANITIZER_RULE, new QRecord()
                  .withValue("ruleType", RuleType.BODY_FIELD.name())
                  .withValue("value", entry.getKey())
                  .withValue("source", RuleSource.SECURED_DATA.name())
                  .withValue("sourceRef", entry.getValue())
                  .withValue("active", true)
                  .withValue("description", "Marked secure in the instance metadata (" + entry.getValue() + ")."));
               added++;
            }
         }
         if(added > 0)
         {
            LOG.info("Added QA capture sanitizer rules for secure fields", logPair("added", added));
         }
         return (added);
      }));
   }
}
