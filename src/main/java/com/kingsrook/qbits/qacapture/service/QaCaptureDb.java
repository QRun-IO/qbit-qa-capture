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
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.actions.tables.UpdateAction;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterOrderBy;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;


/*******************************************************************************
 ** Standard QQQ table actions on the QBit's tables, in the caller's context
 ** (the QBit runs them in the system context). Writes fail loudly when a
 ** record comes back with errors.
 *******************************************************************************/
public final class QaCaptureDb
{
   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private QaCaptureDb()
   {
   }



   /*******************************************************************************
    ** A filter: field equals value.
    *******************************************************************************/
   public static QQueryFilter eq(String field, Serializable value)
   {
      return (new QQueryFilter().withCriteria(field, QCriteriaOperator.EQUALS, value));
   }



   /*******************************************************************************
    ** Rows of a table matching a filter, by id.
    *******************************************************************************/
   public static List<QRecord> query(String table, QQueryFilter filter) throws QException
   {
      QQueryFilter ordered = (filter == null ? new QQueryFilter() : filter);
      if(CollectionUtils.nullSafeIsEmpty(ordered.getOrderBys()))
      {
         ordered.withOrderBy(new QFilterOrderBy("id"));
      }
      return (new QueryAction().execute(new QueryInput(table).withFilter(ordered)).getRecords());
   }



   /*******************************************************************************
    ** The first row matching a filter, or null.
    *******************************************************************************/
   public static QRecord first(String table, QQueryFilter filter) throws QException
   {
      List<QRecord> records = query(table, filter.withLimit(1));
      return (records.isEmpty() ? null : records.get(0));
   }



   /*******************************************************************************
    ** Insert one row; returns it as stored.
    *******************************************************************************/
   public static QRecord insert(String table, QRecord record) throws QException
   {
      QRecord stored = new InsertAction().execute(new InsertInput(table).withRecord(record)).getRecords().get(0);
      failOnErrors(table, stored);
      return (stored);
   }



   /*******************************************************************************
    ** Update one row by id; returns it as stored.
    *******************************************************************************/
   public static QRecord update(String table, QRecord record) throws QException
   {
      QRecord stored = new UpdateAction().execute(new UpdateInput(table).withRecord(record)).getRecords().get(0);
      failOnErrors(table, stored);
      return (stored);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static void failOnErrors(String table, QRecord record) throws QException
   {
      if(CollectionUtils.nullSafeHasContents(record.getErrors()))
      {
         throw (new QException("Could not write " + table + ": " + record.getErrors()));
      }
   }
}
