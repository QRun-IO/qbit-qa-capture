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


import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.qacapture.service.QaCaptureService;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.middleware.javalin.specs.BasicResponse;
import com.kingsrook.qqq.middleware.javalin.specs.extensions.AbstractJsonEndpointSpec;
import com.kingsrook.qqq.openapi.model.Method;
import com.kingsrook.qqq.openapi.model.Schema;
import io.javalin.http.HttpStatus;


/*******************************************************************************
 ** Shared JSON transport of the qa-capture operations; QaCaptureService owns
 ** the behavior and decides who may do what.
 *******************************************************************************/
abstract class QaCaptureJsonSpec extends AbstractJsonEndpointSpec
{
   /*******************************************************************************
    ** The stable operation identifier exposed to clients.
    *******************************************************************************/
   protected abstract String operationId();



   /*******************************************************************************
    ** Publish operation identifiers in both the fragment and served document.
    *******************************************************************************/
   @Override
   public Method defineMethod()
   {
      return (super.defineMethod().withOperationId(operationId()));
   }



   /*******************************************************************************
    ** Error responses.
    *******************************************************************************/
   @Override
   public List<BasicResponse> defineAdditionalBasicResponses()
   {
      return (List.of(new BasicResponse(HttpStatus.BAD_REQUEST, "Invalid input", QaCaptureSchemas.ERROR),
         new BasicResponse(HttpStatus.UNAUTHORIZED, "Sign in required", QaCaptureSchemas.ERROR),
         new BasicResponse(HttpStatus.FORBIDDEN, "QA capture is not enabled for this user", QaCaptureSchemas.ERROR)));
   }



   /*******************************************************************************
    ** Publish the shared components (both specs register together).
    *******************************************************************************/
   @Override
   public Map<String, Schema> defineComponentSchemas()
   {
      return (QaCaptureSchemas.components());
   }



   /*******************************************************************************
    ** The service on the caller's instance.
    *******************************************************************************/
   protected QaCaptureService service()
   {
      return (new QaCaptureService(QContext.getQInstance()));
   }
}
