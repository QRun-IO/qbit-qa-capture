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


import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;


/*******************************************************************************
 ** One provider webhook delivery: its headers (names lowercase), raw body and
 ** the time it was received.
 *******************************************************************************/
public class QaCaptureWebhookRequest
{
   private final Map<String, String> headers = new TreeMap<>();
   private final String              body;
   private final Instant             receivedAt;



   /*******************************************************************************
    ** Constructor.
    **
    ** @param headers    request headers (any case)
    ** @param body       the raw body
    ** @param receivedAt when it arrived
    *******************************************************************************/
   public QaCaptureWebhookRequest(Map<String, String> headers, String body, Instant receivedAt)
   {
      if(headers != null)
      {
         headers.forEach((name, value) -> this.headers.put(name.toLowerCase(Locale.ROOT), value));
      }
      this.body = body == null ? "" : body;
      this.receivedAt = receivedAt == null ? Instant.now() : receivedAt;
   }



   /*******************************************************************************
    ** A header by (case-insensitive) name, or null.
    *******************************************************************************/
   public String getHeader(String name)
   {
      return (name == null ? null : headers.get(name.toLowerCase(Locale.ROOT)));
   }



   /*******************************************************************************
    ** Getter for body
    *******************************************************************************/
   public String getBody()
   {
      return (this.body);
   }



   /*******************************************************************************
    ** Getter for receivedAt
    *******************************************************************************/
   public Instant getReceivedAt()
   {
      return (this.receivedAt);
   }
}
