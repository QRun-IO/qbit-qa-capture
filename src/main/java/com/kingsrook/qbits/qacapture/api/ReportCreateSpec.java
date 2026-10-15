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


import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.middleware.javalin.specs.BasicOperation;
import com.kingsrook.qqq.openapi.model.HttpMethod;
import com.kingsrook.qqq.openapi.model.Schema;


/*******************************************************************************
 ** POST /qqq/v1/qbit/qa-capture/report: create a QA report for the signed-in
 ** user and return how to open the provider's recorder for it. 403 when
 ** capture is not on for the user.
 *******************************************************************************/
public class ReportCreateSpec extends QaCaptureJsonSpec
{
   public static final String OPERATION_ID = "qaCaptureReportCreate";



   /*******************************************************************************
    ** Method and path.
    *******************************************************************************/
   @Override
   public BasicOperation defineBasicOperation()
   {
      return (new BasicOperation().withHttpMethod(HttpMethod.POST).withPath(QaCaptureUiApi.PATH_PREFIX + "/report")
         .withShortSummary("Create a QA report")
         .withLongDescription("Stores the report (page, app, table, record, process, recent request ids, client build) with the user, environment, "
            + "backend version, node and provider filled in by the server, and returns its reference and the recorder parameters."));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   protected String operationId()
   {
      return (OPERATION_ID);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   protected Schema defineRequestBodySchema()
   {
      return (new Schema().withRefToSchema(QaCaptureSchemas.REPORT_REQUEST));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   protected String getResponseSchemaName()
   {
      return (QaCaptureSchemas.REPORT_RESULT);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   protected Object handle(JsonInput input) throws Exception
   {
      return (service().createReport(QContext.getQSession(), input.getBody()));
   }
}
