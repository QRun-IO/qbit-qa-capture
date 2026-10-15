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


import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import com.kingsrook.qbits.qacapture.QaCaptureRuntime;
import com.kingsrook.qbits.qacapture.service.QaCaptureSettings;
import com.kingsrook.qbits.qacapture.service.SanitizerRules;
import com.kingsrook.qbits.qacapture.spi.QaCaptureCspOrigins;
import com.kingsrook.qbits.qacapture.spi.QaCaptureProvider;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.middleware.javalin.routeproviders.NextDashboardRouteProvider;


/*******************************************************************************
 ** The public recorder page (GET /qa/recorder): the page a provider verifies
 ** a connected domain with, and a landing page for recording links. It holds
 ** the provider's identification and scripts and one inline module script
 ** that installs the sanitizers built from every active rule (built-in,
 ** secured-data and administrator rules; nothing user dependent). Its own
 ** Content-Security-Policy allows exactly that inline script (by its sha256
 ** hash) and the provider's origins.
 *******************************************************************************/
public final class RecorderPage
{
   public static final String PATH = "/qa/recorder";

   /**
    ** The page.
    **
    ** @param html                  the document
    ** @param contentSecurityPolicy its Content-Security-Policy
    */
   public record Page(String html, String contentSecurityPolicy)
   {
   }



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private RecorderPage()
   {
   }



   /*******************************************************************************
    ** The page for the instance's environment, or null when capture is off or
    ** not configured there.
    *******************************************************************************/
   public static Page build(QInstance qInstance) throws QException
   {
      return (QaCaptureRuntime.asSystem(qInstance, () ->
      {
         QRecord settings = QaCaptureSettings.load(qInstance);
         if(!QaCaptureSettings.isActive(qInstance, settings))
         {
            return (null);
         }
         QaCaptureProvider provider = QaCaptureRuntime.of(qInstance).getConfig().getProvider(settings.getValueString("provider"));
         SanitizerRules    rules    = SanitizerRules.load();
         String html = "<!doctype html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n"
            + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n"
            + "<meta name=\"robots\" content=\"noindex, nofollow\">\n"
            + "<title>QA recorder</title>\n"
            + provider.recorderHead(settings, SanitizerScript.expression(rules))
            + "</head>\n<body>\n<main>\n<h1>QA recorder</h1>\n"
            + "<p>This page connects the bug recorder to this site. Open the app and use Report a bug to record an issue.</p>\n"
            + "</main>\n</body>\n</html>\n";
         return (new Page(html, contentSecurityPolicy(provider.getCspOrigins(), NextDashboardRouteProvider.inlineScriptHashes(html))));
      }));
   }



   /*******************************************************************************
    ** The page's policy: same-origin by default, the provider's script, frame
    ** and connect origins, and the inline script hashes.
    *******************************************************************************/
   static String contentSecurityPolicy(QaCaptureCspOrigins origins, List<String> scriptHashes)
   {
      Set<String> script = new LinkedHashSet<>(List.of("'self'"));
      script.addAll(origins.scriptSrc());
      script.addAll(scriptHashes);
      Set<String> frame = new LinkedHashSet<>(origins.frameSrc());
      if(frame.isEmpty())
      {
         frame.add("'none'");
      }
      Set<String> connect = new LinkedHashSet<>(List.of("'self'"));
      connect.addAll(origins.connectSrc());
      return ("default-src 'self'; script-src " + String.join(" ", script) + "; frame-src " + String.join(" ", frame)
         + "; connect-src " + String.join(" ", connect) + "; img-src 'self' data: blob: https:; style-src 'self' 'unsafe-inline'"
         + "; worker-src 'self' blob:; media-src 'self' data: blob:"
         + "; object-src 'none'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'");
   }
}
