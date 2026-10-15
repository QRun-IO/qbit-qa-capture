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


import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;


/*******************************************************************************
 ** Verifies Svix-signed webhooks (Jam uses Svix): the signature is
 ** base64(HMAC-SHA256(key, "{svix-id}.{svix-timestamp}.{body}")), the key is
 ** the base64 part of a "whsec_..." secret, and svix-signature carries one or
 ** more space-separated "v1,{signature}" entries (several during secret
 ** rotation). Timestamps more than five minutes away from now are rejected
 ** (replay protection); signatures are compared in constant time.
 *******************************************************************************/
public final class SvixSignatureVerifier
{
   public static final Duration TOLERANCE = Duration.ofMinutes(5);

   private static final String SECRET_PREFIX = "whsec_";



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private SvixSignatureVerifier()
   {
   }



   /*******************************************************************************
    ** Outcome of a verification.
    *******************************************************************************/
   public enum Outcome
   {
      VALID,
      MISSING_HEADERS,
      BAD_TIMESTAMP,
      BAD_SIGNATURE
   }



   /*******************************************************************************
    ** Verify one delivery.
    **
    ** @param secret    the endpoint secret ("whsec_" + base64, or plain base64)
    ** @param id        the svix-id header
    ** @param timestamp the svix-timestamp header (unix seconds)
    ** @param signature the svix-signature header
    ** @param body      the raw body
    ** @param now       the current time
    ** @return the outcome
    *******************************************************************************/
   public static Outcome verify(String secret, String id, String timestamp, String signature, String body, Instant now)
   {
      if(isBlank(secret) || isBlank(id) || isBlank(timestamp) || isBlank(signature))
      {
         return (Outcome.MISSING_HEADERS);
      }

      long seconds;
      try
      {
         seconds = Long.parseLong(timestamp.trim());
      }
      catch(NumberFormatException e)
      {
         return (Outcome.BAD_TIMESTAMP);
      }
      if(Math.abs(now.getEpochSecond() - seconds) > TOLERANCE.toSeconds())
      {
         return (Outcome.BAD_TIMESTAMP);
      }

      byte[] expected;
      try
      {
         expected = sign(secret, id.trim(), timestamp.trim(), body == null ? "" : body);
      }
      catch(IllegalArgumentException e)
      {
         return (Outcome.BAD_SIGNATURE);
      }

      boolean matched = false;
      for(String entry : signature.trim().split("\\s+"))
      {
         int comma = entry.indexOf(',');
         if(comma < 0 || !"v1".equals(entry.substring(0, comma)))
         {
            continue;
         }
         byte[] candidate;
         try
         {
            candidate = Base64.getDecoder().decode(entry.substring(comma + 1));
         }
         catch(IllegalArgumentException e)
         {
            continue;
         }
         //////////////////////////////////////////////////////////////////
         // constant time per candidate; keep checking all entries so the //
         // timing does not reveal which entry matched                    //
         //////////////////////////////////////////////////////////////////
         matched |= MessageDigest.isEqual(expected, candidate);
      }
      return (matched ? Outcome.VALID : Outcome.BAD_SIGNATURE);
   }



   /*******************************************************************************
    ** The raw signature bytes for a delivery (also used by tests to sign).
    **
    ** @throws IllegalArgumentException when the secret is not valid base64
    *******************************************************************************/
   public static byte[] sign(String secret, String id, String timestamp, String body)
   {
      String encodedKey = secret.trim().startsWith(SECRET_PREFIX) ? secret.trim().substring(SECRET_PREFIX.length()) : secret.trim();
      byte[] key        = Base64.getDecoder().decode(encodedKey);
      try
      {
         Mac mac = Mac.getInstance("HmacSHA256");
         mac.init(new SecretKeySpec(key, "HmacSHA256"));
         return (mac.doFinal((id + "." + timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
      }
      catch(GeneralSecurityException e)
      {
         throw (new IllegalStateException("HmacSHA256 is not available", e));
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static boolean isBlank(String value)
   {
      return (value == null || value.isBlank());
   }
}
