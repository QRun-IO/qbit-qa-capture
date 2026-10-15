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

package com.kingsrook.qbits.qacapture.web;


import java.util.UUID;
import java.util.regex.Pattern;
import org.apache.logging.log4j.ThreadContext;


/*******************************************************************************
 ** Request correlation ids: every /qqq/* and /qa/* response carries
 ** X-Request-Id (a valid incoming value is reused, else a new UUID), and the
 ** id is in the log4j ThreadContext under "requestId" while the request runs
 ** (log pattern %X{requestId}). Other code reads it with current().
 *******************************************************************************/
public final class QaCaptureRequestIds
{
   public static final String HEADER          = "X-Request-Id";
   public static final String THREAD_CONTEXT_KEY = "requestId";

   private static final Pattern VALID = Pattern.compile("[A-Za-z0-9._:-]{8,128}");



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private QaCaptureRequestIds()
   {
   }



   /*******************************************************************************
    ** The current request's id, or null outside a request.
    *******************************************************************************/
   public static String current()
   {
      return (ThreadContext.get(THREAD_CONTEXT_KEY));
   }



   /*******************************************************************************
    ** The id to use for a request: the incoming value when it is valid, else a
    ** new random UUID.
    *******************************************************************************/
   public static String resolve(String incoming)
   {
      if(incoming != null && VALID.matcher(incoming.trim()).matches())
      {
         return (incoming.trim());
      }
      return (UUID.randomUUID().toString());
   }



   /*******************************************************************************
    ** Whether a value is a valid request id.
    *******************************************************************************/
   public static boolean isValid(String value)
   {
      return (value != null && VALID.matcher(value).matches());
   }



   /*******************************************************************************
    ** Put an id in the ThreadContext.
    *******************************************************************************/
   static void bind(String requestId)
   {
      ThreadContext.put(THREAD_CONTEXT_KEY, requestId);
   }



   /*******************************************************************************
    ** Remove the id from the ThreadContext.
    *******************************************************************************/
   static void clear()
   {
      ThreadContext.remove(THREAD_CONTEXT_KEY);
   }
}
