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


import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import com.kingsrook.qbits.qacapture.service.QaCaptureService;
import com.kingsrook.qbits.qacapture.spi.JamProvider;
import com.kingsrook.qbits.qacapture.spi.QaCaptureWebhookRequest;
import com.kingsrook.qbits.qacapture.spi.QaCaptureWebhookResult;
import com.kingsrook.qbits.qacapture.spi.SvixSignatureVerifier;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** The jam.created webhook against the real schema: unmatched captures
 ** become reports, other teams and events are ignored, malformed payloads
 ** are rejected, and without a secret the webhook does not exist.
 *******************************************************************************/
class JamWebhookTest extends BaseQaCaptureTest
{
   private final JamProvider jam = new JamProvider();



   /*******************************************************************************
    ** Deliver a payload, signed now.
    *******************************************************************************/
   private QaCaptureWebhookResult deliver(String body) throws Exception
   {
      String timestamp = String.valueOf(Instant.now().getEpochSecond());
      String signature = "v1," + Base64.getEncoder().encodeToString(SvixSignatureVerifier.sign(config.getWebhookSecret("jam"), "msg_x", timestamp, body));
      QaCaptureWebhookRequest request = new QaCaptureWebhookRequest(Map.of("Svix-Id", "msg_x", "Svix-Timestamp", timestamp, "Svix-Signature", signature), body, Instant.now());
      return (jam.handleWebhook(request, new QaCaptureService(qInstance).webhookContext(jam)));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static String payload(Map<String, Object> overrides) throws Exception
   {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("type", "jam.created");
      payload.put("jamId", "jam-9");
      payload.put("jamUrl", "https://jam.dev/c/jam-9");
      payload.put("teamId", TEAM_ID);
      payload.put("title", "Checkout button does nothing");
      payload.put("originalUrl", "https://admin.example.com/app/orders?code=secret&tab=1");
      payload.put("author", Map.of("email", "qa@example.com", "name", "QA Person"));
      payload.putAll(overrides);
      return (JsonUtils.toJson(payload));
   }



   /*******************************************************************************
    ** An unmatched capture becomes a report; a repeat is a duplicate.
    *******************************************************************************/
   @Test
   void unmatchedCaptureCreatesReport() throws Exception
   {
      insertSettings("AUDIENCE", TEAM_ID, null);
      QaCaptureWebhookResult result = deliver(payload(Map.of("recordingLink", Map.of("publicId", "abc", "reference", "qa-unknown", "submitterComment", "note"))));
      assertThat(result.status()).isEqualTo(200);
      assertThat(result.body()).containsEntry("outcome", "created");

      QRecord report = all(QaCaptureSchema.REPORT).get(0);
      assertThat(report.getValueString("reference")).isEqualTo(result.body().get("reference")).startsWith("qa-");
      assertThat(report.getValueString("title")).isEqualTo("Checkout button does nothing");
      assertThat(report.getValueString("status")).isEqualTo("CAPTURED");
      assertThat(report.getValueString("pageUrl")).isEqualTo("https://admin.example.com/app/orders?tab=1");
      assertThat(report.getValueString("userId")).isEqualTo("qa@example.com");
      assertThat(report.getValueString("userLabel")).isEqualTo("QA Person");
      assertThat(report.getValueString("environment")).isEqualTo("prod");
      assertThat(report.getValueString("submitterComment")).isEqualTo("note");

      assertThat(deliver(payload(Map.of())).body()).containsEntry("outcome", "duplicate");
      assertThat(all(QaCaptureSchema.REPORT)).hasSize(1);
   }



   /*******************************************************************************
    ** Ignored and rejected deliveries.
    *******************************************************************************/
   @Test
   void ignoredAndRejected() throws Exception
   {
      assertThat(deliver(payload(Map.of())).body()).as("no settings row").containsEntry("outcome", "ignored");
      insertSettings("AUDIENCE", TEAM_ID, null);
      assertThat(deliver(payload(Map.of("teamId", "another-team"))).body()).containsEntry("outcome", "ignored");
      assertThat(deliver(payload(Map.of("type", "jam.deleted"))).body()).containsEntry("outcome", "ignored");
      assertThat(deliver(payload(Map.of("jamId", " "))).status()).isEqualTo(400);
      assertThat(deliver("[1, 2]").status()).isEqualTo(400);
      assertThat(deliver("not json").status()).isEqualTo(400);
      assertThat(all(QaCaptureSchema.REPORT)).isEmpty();

      config.withJamWebhookSecret(null);
      QaCaptureWebhookResult noSecret = jam.handleWebhook(new QaCaptureWebhookRequest(Map.of(), payload(Map.of()), Instant.now()), new QaCaptureService(qInstance).webhookContext(jam));
      assertThat(noSecret.status()).isEqualTo(404);
   }
}
