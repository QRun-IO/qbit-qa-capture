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

package com.kingsrook.qbits.qacapture.spi;


import java.util.List;
import java.util.Map;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.data.QRecord;


/*******************************************************************************
 ** A QA capture provider (Jam is the first). The host registers providers on
 ** QaCaptureQBitConfig; a settings row names one of them. A provider says which
 ** scripts and Content-Security-Policy origins it needs, what clients receive
 ** as providerConfig, how a report opens its recorder (recordingParams), what
 ** the public recorder page carries, and how its webhook is handled.
 **
 ** Implementations must be stateless and thread safe, and must never log or
 ** return secrets.
 *******************************************************************************/
public interface QaCaptureProvider
{
   /*******************************************************************************
    ** Stable name stored in qa_capture_settings.provider (lowercase, for
    ** example "jam"); also the webhook path segment.
    *******************************************************************************/
   String getName();

   /*******************************************************************************
    ** Label shown in the provider possible values.
    *******************************************************************************/
   String getLabel();

   /*******************************************************************************
    ** The provider's browser scripts, in load order.
    *******************************************************************************/
   List<String> getScriptUrls();

   /*******************************************************************************
    ** Origins the provider needs in a Content-Security-Policy.
    *******************************************************************************/
   QaCaptureCspOrigins getCspOrigins();

   /*******************************************************************************
    ** The capability response's providerConfig for an enabled settings row.
    *******************************************************************************/
   Map<String, Object> providerConfig(QRecord settings);

   /*******************************************************************************
    ** Query parameters that open the provider's recorder for a report, or null
    ** when the settings cannot open one (for example no recording link).
    *******************************************************************************/
   Map<String, String> recordingParams(QRecord report, QRecord settings);

   /*******************************************************************************
    ** The head content of the public recorder page: the provider's
    ** identification, its scripts and one inline module script that installs
    ** the sanitizers built by the given JavaScript expression.
    *******************************************************************************/
   String recorderHead(QRecord settings, String sanitizersExpression);

   /*******************************************************************************
    ** Handle a provider webhook (POST /qqq/hooks/qa-capture/{name}). The default
    ** has no webhook (404).
    *******************************************************************************/
   default QaCaptureWebhookResult handleWebhook(QaCaptureWebhookRequest request, QaCaptureWebhookContext context) throws QException
   {
      return (QaCaptureWebhookResult.notFound());
   }
}
