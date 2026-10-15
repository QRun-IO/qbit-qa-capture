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

package com.kingsrook.qbits.qacapture;


import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import com.kingsrook.qbits.qacapture.spi.SvixSignatureVerifier;
import com.kingsrook.qbits.qacapture.spi.SvixSignatureVerifier.Outcome;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Svix signature verification.
 *******************************************************************************/
class SvixSignatureVerifierTest
{
   private static final String  SECRET = "whsec_" + Base64.getEncoder().encodeToString("an-example-secret-key".getBytes(StandardCharsets.UTF_8));
   private static final Instant NOW    = Instant.ofEpochSecond(1_760_000_000L);
   private static final String  BODY   = "{\"type\":\"jam.created\"}";



   /*******************************************************************************
    **
    *******************************************************************************/
   private static String signature(String secret, String id, long timestamp, String body)
   {
      return ("v1," + Base64.getEncoder().encodeToString(SvixSignatureVerifier.sign(secret, id, String.valueOf(timestamp), body)));
   }



   /*******************************************************************************
    ** A known vector: the reference Svix example (secret, id, timestamp, body).
    *******************************************************************************/
   @Test
   void svixReferenceVector()
   {
      String secret    = "whsec_MfKQ9r8GKYqrTwjUPD8ILPZIo2LaLaSw";
      String id        = "msg_p5jXN8AQM9LWM0D4loKWxJek";
      String timestamp = "1614265330";
      String body      = "{\"test\": 2432232314}";
      String expected  = "v1,g0hM9SsE+OTPJTGt/tmIKtSyZlE3uFJELVlNIOLJ1OE=";
      assertThat(SvixSignatureVerifier.verify(secret, id, timestamp, expected, body, Instant.ofEpochSecond(1614265330L))).isEqualTo(Outcome.VALID);
   }



   /*******************************************************************************
    ** Valid, rotated (several signatures), tampered, stale and malformed.
    *******************************************************************************/
   @Test
   void outcomes()
   {
      long   ts  = NOW.getEpochSecond();
      String sig = signature(SECRET, "msg_1", ts, BODY);
      assertThat(SvixSignatureVerifier.verify(SECRET, "msg_1", String.valueOf(ts), sig, BODY, NOW)).isEqualTo(Outcome.VALID);

      String other   = "whsec_" + Base64.getEncoder().encodeToString("old-secret".getBytes(StandardCharsets.UTF_8));
      String rotated = signature(other, "msg_1", ts, BODY) + " v2,abc " + sig;
      assertThat(SvixSignatureVerifier.verify(SECRET, "msg_1", String.valueOf(ts), rotated, BODY, NOW)).isEqualTo(Outcome.VALID);

      assertThat(SvixSignatureVerifier.verify(SECRET, "msg_1", String.valueOf(ts), sig, BODY + " ", NOW)).isEqualTo(Outcome.BAD_SIGNATURE);
      assertThat(SvixSignatureVerifier.verify(SECRET, "msg_2", String.valueOf(ts), sig, BODY, NOW)).isEqualTo(Outcome.BAD_SIGNATURE);
      assertThat(SvixSignatureVerifier.verify(SECRET, "msg_1", String.valueOf(ts), "v1,!!!notbase64", BODY, NOW)).isEqualTo(Outcome.BAD_SIGNATURE);
      assertThat(SvixSignatureVerifier.verify("whsec_%%%", "msg_1", String.valueOf(ts), sig, BODY, NOW)).isEqualTo(Outcome.BAD_SIGNATURE);

      long stale = ts - 301;
      assertThat(SvixSignatureVerifier.verify(SECRET, "msg_1", String.valueOf(stale), signature(SECRET, "msg_1", stale, BODY), BODY, NOW)).isEqualTo(Outcome.BAD_TIMESTAMP);
      long future = ts + 301;
      assertThat(SvixSignatureVerifier.verify(SECRET, "msg_1", String.valueOf(future), signature(SECRET, "msg_1", future, BODY), BODY, NOW)).isEqualTo(Outcome.BAD_TIMESTAMP);
      long edge = ts - 300;
      assertThat(SvixSignatureVerifier.verify(SECRET, "msg_1", String.valueOf(edge), signature(SECRET, "msg_1", edge, BODY), BODY, NOW)).isEqualTo(Outcome.VALID);
      assertThat(SvixSignatureVerifier.verify(SECRET, "msg_1", "yesterday", sig, BODY, NOW)).isEqualTo(Outcome.BAD_TIMESTAMP);

      assertThat(SvixSignatureVerifier.verify(null, "msg_1", String.valueOf(ts), sig, BODY, NOW)).isEqualTo(Outcome.MISSING_HEADERS);
      assertThat(SvixSignatureVerifier.verify(SECRET, " ", String.valueOf(ts), sig, BODY, NOW)).isEqualTo(Outcome.MISSING_HEADERS);
      assertThat(SvixSignatureVerifier.verify(SECRET, "msg_1", String.valueOf(ts), null, BODY, NOW)).isEqualTo(Outcome.MISSING_HEADERS);
      assertThat(SvixSignatureVerifier.verify(SECRET.substring("whsec_".length()), "msg_1", String.valueOf(ts), sig, BODY, NOW))
         .as("a secret without the prefix works").isEqualTo(Outcome.VALID);
   }
}
