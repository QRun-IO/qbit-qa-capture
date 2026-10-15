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


import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.qacapture.spi.SvixSignatureVerifier;
import com.kingsrook.qbits.qacapture.web.QaCaptureRequestIds;
import com.kingsrook.qqq.backend.core.instances.AbstractQQQApplication;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import com.kingsrook.qqq.middleware.javalin.QApplicationJavalinServer;
import com.kingsrook.qqq.middleware.javalin.QJavalinRouteProviderInterface;
import com.kingsrook.qqq.middleware.javalin.routeproviders.NextDashboardRouteProvider;
import com.kingsrook.qqq.middleware.javalin.specs.v1.MiddlewareVersionV1;
import io.javalin.config.JavalinConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Real HTTP through QApplicationJavalinServer with a Next dashboard export
 ** at "/": request ids, the UI API extension, the recorder page winning over
 ** the dashboard's fallback, the dashboard CSP helper and the Jam webhook.
 *******************************************************************************/
class QaCaptureHttpTest extends BaseQaCaptureTest
{
   private QApplicationJavalinServer server;
   private int                       port;



   /*******************************************************************************
    ** Test routes reporting the request id seen by the handler.
    *******************************************************************************/
   public static class ProbeRoutes implements QJavalinRouteProviderInterface
   {
      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public void setQInstance(QInstance qInstance)
      {
         // stateless
      }



      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public void acceptJavalinConfig(JavalinConfig config)
      {
         config.routes.get("/qqq/probe/request-id", context -> context.result(String.valueOf(QaCaptureRequestIds.current())));
         config.routes.get("/other/request-id", context -> context.result(String.valueOf(QaCaptureRequestIds.current())));
         config.routes.get("/qqq/probe/boom", context ->
         {
            throw (new IllegalStateException("boom"));
         });
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @AfterEach
   void stopServer()
   {
      if(server != null)
      {
         server.stop();
      }
   }



   /*******************************************************************************
    ** Start the server (the dashboard CSP helper reads the settings now).
    *******************************************************************************/
   private void start() throws Exception
   {
      server = new QApplicationJavalinServer(new AbstractQQQApplication()
      {
         /***************************************************************************
          **
          ***************************************************************************/
         @Override
         public QInstance defineQInstance()
         {
            return (qInstance);
         }
      }).withPort(0).withServeFrontendNext(false).withServeFrontendMaterialDashboard(false)
         .withServeLegacyUnversionedMiddlewareAPI(false).withStrictUiApiRoutes(true)
         .withMiddlewareVersionList(List.of(new MiddlewareVersionV1()))
         .withAdditionalRouteProviders(new ArrayList<>(List.of(new ProbeRoutes(),
            new NextDashboardRouteProvider("test-next-dashboard").withSecurityHeadersCustomizer(QaCaptureCsp::customize))));
      server.start();
      port = server.getBoundPort();
   }



   /*******************************************************************************
    ** Send a request.
    *******************************************************************************/
   private HttpResponse<String> request(String method, String path, String body, String user, Map<String, String> headers) throws Exception
   {
      HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
      if(user != null)
      {
         request.header("Cookie", "sessionUUID=" + user);
      }
      if(body != null)
      {
         request.header("Content-Type", "application/json");
      }
      if(headers != null)
      {
         headers.forEach(request::header);
      }
      request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
      try(HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build())
      {
         return (client.send(request.build(), HttpResponse.BodyHandlers.ofString()));
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @SuppressWarnings("unchecked")
   private static Map<String, Object> json(HttpResponse<String> response) throws Exception
   {
      assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
      return (JsonUtils.toObject(response.body(), Map.class));
   }



   /*******************************************************************************
    ** X-Request-Id on every /qqq/* response, in the ThreadContext while the
    ** request runs, cleared afterwards (also after an exception).
    *******************************************************************************/
   @Test
   void requestIds() throws Exception
   {
      start();
      HttpResponse<String> reused = request("GET", "/qqq/probe/request-id", null, ADMIN, Map.of("X-Request-Id", "client:req-0001"));
      assertThat(reused.headers().firstValue("X-Request-Id")).hasValue("client:req-0001");
      assertThat(reused.body()).isEqualTo("client:req-0001");

      HttpResponse<String> replaced = request("GET", "/qqq/probe/request-id", null, ADMIN, Map.of("X-Request-Id", "bad id"));
      String generated = replaced.headers().firstValue("X-Request-Id").orElseThrow();
      assertThat(generated).matches("[0-9a-f-]{36}");
      assertThat(replaced.body()).isEqualTo(generated);

      HttpResponse<String> boom = request("GET", "/qqq/probe/boom", null, ADMIN, null);
      assertThat(boom.statusCode()).isEqualTo(500);
      assertThat(boom.headers().firstValue("X-Request-Id")).isPresent();

      HttpResponse<String> api = request("GET", "/qqq/v1/qbit/qa-capture/capability", null, ADMIN, Map.of("X-Request-Id", "abcdefgh"));
      assertThat(api.headers().firstValue("X-Request-Id")).hasValue("abcdefgh");
      assertThat(request("GET", "/qqq/v1/metaData", null, null, null).headers().firstValue("X-Request-Id")).isPresent();

      ///////////////////////////////////////////////////////////////////////////
      // outside /qqq/* and /qa/* there is no id: nothing leaked between       //
      // requests on the server's threads                                       //
      ///////////////////////////////////////////////////////////////////////////
      for(int i = 0; i < 10; i++)
      {
         HttpResponse<String> other = request("GET", "/other/request-id", null, ADMIN, null);
         assertThat(other.body()).isEqualTo("null");
         assertThat(other.headers().firstValue("X-Request-Id")).isEmpty();
      }
      assertThat(QaCaptureRequestIds.current()).isNull();
   }



   /*******************************************************************************
    ** The extension is discoverable and serves the contract.
    *******************************************************************************/
   @Test
   void uiApi() throws Exception
   {
      QRecord settings = insertSettings("AUDIENCE", TEAM_ID, "abc123");
      insertAudience(settings, "USER", NOBODY, true);
      start();

      assertThat(request("GET", "/qqq/v1/version", null, ADMIN, null).body()).contains("qa-capture");
      assertThat(json(request("GET", "/qqq/v1/qbit/qa-capture/capability", null, NOBODY, null))).containsEntry("enabled", true).containsEntry("reason", "AUDIENCE");
      assertThat(json(request("GET", "/qqq/v1/qbit/qa-capture/capability", null, VIEWER, null))).containsEntry("enabled", false).containsEntry("reason", "NO_ACCESS");

      Map<String, Object> created = json(request("POST", "/qqq/v1/qbit/qa-capture/report", JsonUtils.toJson(Map.of("pageUrl", "https://admin.example.com/app/x?code=1", "requestIds", List.of("abcdefgh"))), NOBODY, null));
      assertThat(created).containsKeys("id", "reference", "recordingParams");
      assertThat(request("POST", "/qqq/v1/qbit/qa-capture/report", JsonUtils.toJson(Map.of("pageUrl", "https://x")), VIEWER, null).statusCode()).isEqualTo(403);
      assertThat(request("POST", "/qqq/v1/qbit/qa-capture/report", "{}", NOBODY, null).statusCode()).isEqualTo(400);
      assertThat(request("POST", "/qqq/v1/qbit/qa-capture/report", "not json", NOBODY, null).statusCode()).isEqualTo(400);
   }



   /*******************************************************************************
    ** The recorder page wins over the dashboard fallback: 404 when off (not
    ** the dashboard), 200 with its own CSP when on; the dashboard CSP has
    ** the Jam origins only while capture is active.
    *******************************************************************************/
   @Test
   void recorderAndDashboard() throws Exception
   {
      start();
      HttpResponse<String> off = request("GET", "/qa/recorder?jam-recording=abc", null, null, null);
      assertThat(off.statusCode()).isEqualTo(404);
      assertThat(off.body()).doesNotContain("spa-index");
      assertThat(off.headers().firstValue("Cache-Control")).hasValue("no-store");
      HttpResponse<String> dashboard = request("GET", "/", null, null, null);
      assertThat(dashboard.body()).contains("spa-index");
      assertThat(dashboard.headers().firstValue("Content-Security-Policy").orElse("")).doesNotContain("jam.dev");
      server.stop();

      insertSettings("AUDIENCE", TEAM_ID, null);
      start();
      HttpResponse<String> on = request("GET", "/qa/recorder?jam-recording=abc&jam-title=x", null, null, null);
      assertThat(on.statusCode()).isEqualTo(200);
      assertThat(on.body()).contains("jam:team", TEAM_ID, "recorder.js", "capture.js", "setSanitizers").doesNotContain("spa-index");
      assertThat(on.headers().firstValue("Content-Security-Policy").orElse("")).contains("'sha256-", "https://js.jam.dev", "frame-src https://*.jam.dev");
      assertThat(on.headers().firstValue("Cache-Control")).hasValue("no-store");
      assertThat(on.headers().firstValue("Content-Type").orElse("")).startsWith("text/html");
      assertThat(on.headers().firstValue("X-Request-Id")).isPresent();
      assertThat(request("GET", "/", null, null, null).headers().firstValue("Content-Security-Policy").orElse(""))
         .contains("script-src 'self'", "https://js.jam.dev", "https://*.jam.dev");
   }



   /*******************************************************************************
    ** The Jam webhook over HTTP: signed delivery matched to a report; bad
    ** signature, unknown provider and oversized payloads rejected.
    *******************************************************************************/
   @Test
   void jamWebhook() throws Exception
   {
      QRecord settings = insertSettings("AUDIENCE", TEAM_ID, "abc123");
      insertAudience(settings, "USER", NOBODY, true);
      start();
      Map<String, Object> created = json(request("POST", "/qqq/v1/qbit/qa-capture/report", JsonUtils.toJson(Map.of("pageUrl", "https://admin.example.com/")), NOBODY, null));
      String reference = (String) created.get("reference");

      String body = JsonUtils.toJson(Map.of("type", "jam.created", "jamId", "jam-1", "jamUrl", "https://jam.dev/c/jam-1", "teamId", TEAM_ID,
         "recordingLink", Map.of("publicId", "abc123", "reference", reference, "submitterComment", "It broke")));
      HttpResponse<String> matched = request("POST", "/qqq/hooks/qa-capture/jam", body, null, signed("msg_1", body));
      assertThat(json(matched)).containsEntry("outcome", "matched").containsEntry("reference", reference);
      QRecord report = all(QaCaptureSchema.REPORT).get(0);
      assertThat(report.getValueString("status")).isEqualTo("CAPTURED");
      assertThat(report.getValueString("jamUrl")).isEqualTo("https://jam.dev/c/jam-1");
      assertThat(report.getValueString("submitterComment")).isEqualTo("It broke");

      assertThat(json(request("POST", "/qqq/hooks/qa-capture/jam", body, null, signed("msg_1", body)))).containsEntry("outcome", "duplicate");
      Map<String, String> badSignature = signed("msg_2", body);
      badSignature.put("svix-signature", "v1," + Base64.getEncoder().encodeToString(new byte[32]));
      assertThat(request("POST", "/qqq/hooks/qa-capture/jam", body, null, badSignature).statusCode()).isEqualTo(401);
      assertThat(request("POST", "/qqq/hooks/qa-capture/other", body, null, signed("msg_3", body)).statusCode()).isEqualTo(404);
      String huge = "{\"x\":\"" + "a".repeat(1024 * 1024) + "\"}";
      assertThat(request("POST", "/qqq/hooks/qa-capture/jam", huge, null, signed("msg_4", huge)).statusCode()).isEqualTo(413);
   }



   /*******************************************************************************
    ** Svix headers for a body, signed with the test secret.
    *******************************************************************************/
   private Map<String, String> signed(String id, String body)
   {
      String timestamp = String.valueOf(Instant.now().getEpochSecond());
      String secret    = config.getWebhookSecret("jam");
      String signature = Base64.getEncoder().encodeToString(SvixSignatureVerifier.sign(secret, id, timestamp, body));
      return (new java.util.HashMap<>(Map.of("svix-id", id, "svix-timestamp", timestamp, "svix-signature", "v1," + signature)));
   }
}
