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


import java.util.List;
import java.util.Locale;
import java.util.Optional;
import com.kingsrook.qbits.qacapture.QaCaptureSchema;
import com.kingsrook.qbits.qacapture.QaCaptureSchema.RuleType;
import com.kingsrook.qbits.qacapture.service.SanitizerRules;
import com.kingsrook.qqq.backend.core.actions.customizers.TableCustomizerInterface;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.statusmessages.BadInputStatusMessage;


/*******************************************************************************
 ** Validates and normalizes writes to the settings, audience and sanitizer
 ** rule tables, from the UI, the API and the QBit alike:
 ** - text values are trimmed;
 ** - a sanitizer rule must be usable (SanitizerRules.validate: a value
 **   pattern valid in Java and JavaScript, a drop path starting with "/",
 **   a header name); header names are stored lowercase.
 *******************************************************************************/
public class QaCaptureWriteValidator implements TableCustomizerInterface
{
   private static final List<String> TRIMMED = List.of("environment", "teamId", "recordingLinkId", "value");



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public List<QRecord> preInsert(InsertInput insertInput, List<QRecord> records, boolean isPreview) throws QException
   {
      return (check(insertInput.getTableName(), records, null));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public List<QRecord> preUpdate(UpdateInput updateInput, List<QRecord> records, boolean isPreview, Optional<List<QRecord>> oldRecordList) throws QException
   {
      return (check(updateInput.getTableName(), records, oldRecordList.orElse(null)));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private List<QRecord> check(String tableName, List<QRecord> records, List<QRecord> oldRecords)
   {
      for(QRecord record : records)
      {
         for(String field : TRIMMED)
         {
            if(record.getValues().containsKey(field) && record.getValue(field) instanceof String text)
            {
               record.setValue(field, text.isBlank() ? null : text.trim());
            }
         }

         if(QaCaptureSchema.SANITIZER_RULE.equals(tableName) && (record.getValues().containsKey("value") || record.getValues().containsKey("ruleType")))
         {
            QRecord old   = findOld(oldRecords, record);
            String  type  = record.getValues().containsKey("ruleType") ? record.getValueString("ruleType") : old == null ? null : old.getValueString("ruleType");
            String  value = record.getValues().containsKey("value") ? record.getValueString("value") : old == null ? null : old.getValueString("value");
            String  problem = SanitizerRules.validate(type, value);
            if(problem != null)
            {
               record.addError(new BadInputStatusMessage(problem));
            }
            else if(RuleType.HEADER.name().equals(type) && record.getValues().containsKey("value"))
            {
               record.setValue("value", value.trim().toLowerCase(Locale.ROOT));
            }
         }
      }
      return (records);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static QRecord findOld(List<QRecord> oldRecords, QRecord record)
   {
      if(oldRecords == null || record.getValue("id") == null)
      {
         return (null);
      }
      String id = String.valueOf(record.getValue("id"));
      return (oldRecords.stream().filter(old -> id.equals(String.valueOf(old.getValue("id")))).findFirst().orElse(null));
   }
}
