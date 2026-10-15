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


/*******************************************************************************
 ** GET /qqq/v1/qbit/qa-capture/capability: what QA capture means for the
 ** signed-in user. Never 403 for "not allowed": enabled=false with a reason.
 *******************************************************************************/
public class CapabilitySpec extends QaCaptureJsonSpec
{
   public static final String OPERATION_ID = "qaCaptureCapability";



   /*******************************************************************************
    ** Method and path.
    *******************************************************************************/
   @Override
   public BasicOperation defineBasicOperation()
   {
      return (new BasicOperation().withHttpMethod(HttpMethod.GET).withPath(QaCaptureUiApi.PATH_PREFIX + "/capability")
         .withShortSummary("Read the QA capture capability")
         .withLongDescription("Whether QA capture is on for the signed-in user and why, the provider settings, sanitizer rules, blur selectors, "
            + "Content-Security-Policy origins and server context. Requires a session; a user without access gets enabled=false, never 403."));
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
   protected String getResponseSchemaName()
   {
      return (QaCaptureSchemas.CAPABILITY);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   protected Object handle(JsonInput input) throws Exception
   {
      return (service().capability(QContext.getQSession()));
   }
}
