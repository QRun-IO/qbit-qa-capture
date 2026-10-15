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


import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.middleware.javalin.specs.extensions.MiddlewareSpecExtension;
import com.kingsrook.qqq.middleware.javalin.specs.extensions.MiddlewareSpecExtensionsMetaData;


/*******************************************************************************
 ** The qa-capture UI API extension (contract v1.0.0): independently
 ** versioned, discoverable through /qqq/v1/version, served under
 ** /qqq/v1/qbit/qa-capture. Committed fragment:
 ** src/main/resources/openapi/qqq-v1/qa-capture.yaml.
 *******************************************************************************/
public final class QaCaptureUiApi
{
   public static final String NAME             = "qa-capture";
   public static final String SEMANTIC_VERSION = "1.0.0";
   public static final String PATH_PREFIX      = "/qbit/" + NAME;



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private QaCaptureUiApi()
   {
   }



   /*******************************************************************************
    ** Register the extension (idempotent: same name replaces).
    *******************************************************************************/
   public static void register(QInstance qInstance)
   {
      MiddlewareSpecExtensionsMetaData.ofOrWithNew(qInstance).withExtension(extension());
   }



   /*******************************************************************************
    ** The extension.
    *******************************************************************************/
   public static MiddlewareSpecExtension extension()
   {
      return (new MiddlewareSpecExtension().withName(NAME).withSemanticVersion(SEMANTIC_VERSION)
         .withDescription("QA bug capture: the user's capture capability and QA report creation")
         .withSpec(CapabilitySpec.class).withSpec(ReportCreateSpec.class));
   }
}
