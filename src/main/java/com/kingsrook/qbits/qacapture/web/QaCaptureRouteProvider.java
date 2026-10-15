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


import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.qacapture.QaCaptureQBitConfig;
import com.kingsrook.qbits.qacapture.QaCaptureRuntime;
import com.kingsrook.qbits.qacapture.service.QaCaptureService;
import com.kingsrook.qbits.qacapture.service.SecuredDataRuleSync;
import com.kingsrook.qbits.qacapture.spi.QaCaptureProvider;
import com.kingsrook.qbits.qacapture.spi.QaCaptureWebhookRequest;
import com.kingsrook.qbits.qacapture.spi.QaCaptureWebhookResult;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import com.kingsrook.qqq.middleware.javalin.QJavalinMetaData;
import com.kingsrook.qqq.middleware.javalin.QJavalinRouteProviderInterface;
import io.javalin.config.JavalinConfig;
import io.javalin.http.ContentType;
import io.javalin.http.Context;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** The QBit's raw Javalin routes (the UI routes are the qa-capture UI API
 ** extension):
 ** - before and after handlers on /qqq/* and /qa/* for request ids
 **   (QaCaptureRequestIds);
 ** - GET /qa/recorder, the public recorder page (404 when capture is off);
 ** - POST /qqq/hooks/qa-capture/{provider}, provider webhooks (Jam: Svix
 **   signed jam.created), at most 1 MB.
 ** Setting the instance also adds the secured-data sanitizer rules (once per
 ** instance, never failing startup).
 *******************************************************************************/
public class QaCaptureRouteProvider implements QJavalinRouteProviderInterface
{
   private static final QLogger LOG = QLogger.getLogger(QaCaptureRouteProvider.class);

   public static final String       WEBHOOK_PREFIX     = "/qqq/hooks/qa-capture/";
   public static final List<String> REQUEST_ID_PATHS   = List.of("/qqq/*", "/qa/*");
   public static final int          MAX_WEBHOOK_BYTES  = 1024 * 1024;

   private static final String REQUEST_ID_ATTRIBUTE = "qaCaptureRequestId";

   private volatile QInstance qInstance;
   private volatile QInstance syncedInstance;



   /*******************************************************************************
    ** Register this provider once with the instance's Javalin meta-data.
    *******************************************************************************/
   public static void register(QInstance qInstance)
   {
      QJavalinMetaData javalinMetaData = QJavalinMetaData.ofOrWithNew(qInstance);
      boolean registered = CollectionUtils.nonNullList(javalinMetaData.getAdditionalRouteProviderReferences()).stream()
         .anyMatch(reference -> reference != null && QaCaptureRouteProvider.class.getName().equals(reference.getName()));
      if(!registered)
      {
         javalinMetaData.withAdditionalRouteProviderReference(new QCodeReference(QaCaptureRouteProvider.class));
      }
   }



   /*******************************************************************************
    ** Remember the (new) instance; add secured-data rules once per instance.
    *******************************************************************************/
   @Override
   public void setQInstance(QInstance qInstance)
   {
      this.qInstance = qInstance;
      QaCaptureRuntime.setCurrentInstance(qInstance);
      QaCaptureRuntime runtime = QaCaptureRuntime.find(qInstance);
      if(runtime != null && Boolean.TRUE.equals(runtime.getConfig().getSyncSecuredDataRules()) && syncedInstance != qInstance)
      {
         syncedInstance = qInstance;
         try
         {
            SecuredDataRuleSync.sync(qInstance);
         }
         catch(Exception e)
         {
            LOG.warn("Could not add QA capture sanitizer rules for secure fields (capture still uses the existing rules)", e);
         }
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public void acceptJavalinConfig(JavalinConfig config)
   {
      for(String path : REQUEST_ID_PATHS)
      {
         config.routes.before(path, this::beforeRequest);
         config.routes.after(path, this::afterRequest);
      }
      config.routes.get(RecorderPage.PATH, this::recorder);
      config.routes.post(WEBHOOK_PREFIX + "{provider}", this::webhook);
   }



   /*******************************************************************************
    ** Assign the request id: response header and ThreadContext.
    *******************************************************************************/
   void beforeRequest(Context context)
   {
      String requestId = QaCaptureRequestIds.resolve(context.header(QaCaptureRequestIds.HEADER));
      context.attribute(REQUEST_ID_ATTRIBUTE, requestId);
      context.header(QaCaptureRequestIds.HEADER, requestId);
      QaCaptureRequestIds.bind(requestId);
   }



   /*******************************************************************************
    ** Keep the header (an error handler may have replaced the response) and
    ** clear the ThreadContext; after handlers run after exceptions too.
    *******************************************************************************/
   void afterRequest(Context context)
   {
      try
      {
         String requestId = context.attribute(REQUEST_ID_ATTRIBUTE);
         if(requestId != null && context.res().getHeader(QaCaptureRequestIds.HEADER) == null)
         {
            context.header(QaCaptureRequestIds.HEADER, requestId);
         }
      }
      finally
      {
         QaCaptureRequestIds.clear();
      }
   }



   /*******************************************************************************
    ** GET /qa/recorder: 200 with the page (never a redirect), or 404.
    *******************************************************************************/
   void recorder(Context context)
   {
      context.header("Cache-Control", "no-store");
      context.header("X-Content-Type-Options", "nosniff");
      context.header("Referrer-Policy", "strict-origin-when-cross-origin");
      try
      {
         RecorderPage.Page page = RecorderPage.build(qInstance);
         if(page == null)
         {
            context.status(404).contentType(ContentType.TEXT_PLAIN).result("Not found");
            return;
         }
         context.header("Content-Security-Policy", page.contentSecurityPolicy());
         context.status(200).contentType(ContentType.TEXT_HTML).result(page.html());
      }
      catch(Exception e)
      {
         LOG.warn("Could not render the QA recorder page", e);
         context.status(500).contentType(ContentType.TEXT_PLAIN).result("QA recorder unavailable");
      }
      finally
      {
         QContext.clear();
      }
   }



   /*******************************************************************************
    ** POST /qqq/hooks/qa-capture/{provider}.
    *******************************************************************************/
   void webhook(Context context)
   {
      QaCaptureWebhookResult result;
      try
      {
         QaCaptureQBitConfig config   = QaCaptureRuntime.of(qInstance).getConfig();
         QaCaptureProvider   provider = config.getProvider(context.pathParam("provider"));
         if(provider == null)
         {
            result = QaCaptureWebhookResult.notFound();
         }
         else if(context.contentLength() > MAX_WEBHOOK_BYTES)
         {
            result = QaCaptureWebhookResult.error(413, "The payload is larger than 1 MB.");
         }
         else
         {
            byte[] bytes = context.bodyAsBytes();
            if(bytes.length > MAX_WEBHOOK_BYTES)
            {
               result = QaCaptureWebhookResult.error(413, "The payload is larger than 1 MB.");
            }
            else
            {
               QaCaptureWebhookRequest request = new QaCaptureWebhookRequest(context.headerMap(), new String(bytes, StandardCharsets.UTF_8),
                  QaCaptureRuntime.of(qInstance).getClock().instant());
               result = provider.handleWebhook(request, new QaCaptureService(qInstance).webhookContext(provider));
            }
         }
      }
      catch(Exception e)
      {
         LOG.warn("QA capture webhook failed", e, logPair("provider", context.pathParam("provider")));
         result = QaCaptureWebhookResult.error(500, "The webhook could not be processed.");
      }
      finally
      {
         QContext.clear();
      }
      context.status(result.status()).contentType(ContentType.APPLICATION_JSON).result(JsonUtils.toJson(result.body() == null ? Map.of() : result.body()));
   }
}
