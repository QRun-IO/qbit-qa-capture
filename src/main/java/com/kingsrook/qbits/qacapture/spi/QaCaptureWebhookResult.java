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


import java.util.LinkedHashMap;
import java.util.Map;


/*******************************************************************************
 ** The HTTP answer to a provider webhook: a status and a small JSON object.
 **
 ** @param status HTTP status
 ** @param body   JSON body
 *******************************************************************************/
public record QaCaptureWebhookResult(int status, Map<String, Object> body)
{
   /*******************************************************************************
    ** Copy the body, keeping its order.
    *******************************************************************************/
   public QaCaptureWebhookResult
   {
      body = body == null ? Map.of() : new LinkedHashMap<>(body);
   }



   /*******************************************************************************
    ** 200 with an outcome ("matched", "created", "duplicate", "ignored").
    *******************************************************************************/
   public static QaCaptureWebhookResult ok(String outcome, String reference)
   {
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("outcome", outcome);
      body.put("reference", reference);
      return (new QaCaptureWebhookResult(200, body));
   }



   /*******************************************************************************
    ** An error status with a message (never echoing secrets or the payload).
    *******************************************************************************/
   public static QaCaptureWebhookResult error(int status, String message)
   {
      return (new QaCaptureWebhookResult(status, Map.of("error", message)));
   }



   /*******************************************************************************
    ** 404: the provider has no webhook, or it is not configured.
    *******************************************************************************/
   public static QaCaptureWebhookResult notFound()
   {
      return (error(404, "Not found"));
   }
}
