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


import com.kingsrook.qbits.qacapture.service.QaCaptureSettings;
import com.kingsrook.qbits.qacapture.spi.QaCaptureCspOrigins;
import com.kingsrook.qbits.qacapture.spi.QaCaptureProvider;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.middleware.javalin.routeproviders.NextDashboardSecurityHeaders;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** Adds the capture provider's origins to the Next dashboard's
 ** Content-Security-Policy, only while capture is active for the configured
 ** environment (settings row with a mode other than OFF, a team id and a
 ** registered provider). For Jam: script-src https://js.jam.dev, frame-src
 ** and connect-src https://*.jam.dev; 'self' and every other source are left
 ** untouched.
 **
 ** Host usage (QApplicationJavalinServer):
 ** <pre>
 ** .withNextDashboardSecurityHeadersCustomizer(headers -&gt;
 ** {
 **    myOtherCustomizations(headers);
 **    QaCaptureCsp.customize(headers);
 ** })
 ** </pre>
 ** The dashboard rebuilds its headers when the QInstance is set (startup and
 ** every hot swap), so a settings change takes effect on the next restart or
 ** instance hot swap. Reads the database in the system context; never throws
 ** (a failure is logged and the headers are left as they were).
 *******************************************************************************/
public final class QaCaptureCsp
{
   private static final QLogger LOG = QLogger.getLogger(QaCaptureCsp.class);



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private QaCaptureCsp()
   {
   }



   /*******************************************************************************
    ** Customize with the instance the QBit was most recently produced into or
    ** served from (a no-op before there is one).
    *******************************************************************************/
   public static void customize(NextDashboardSecurityHeaders headers)
   {
      customize(headers, QaCaptureRuntime.getCurrentInstance());
   }



   /*******************************************************************************
    ** Customize for an instance.
    **
    ** @param headers   the dashboard's headers (changed in place)
    ** @param qInstance the instance holding the QBit (null: no-op)
    ** @return whether origins were added
    *******************************************************************************/
   public static boolean customize(NextDashboardSecurityHeaders headers, QInstance qInstance)
   {
      if(headers == null || qInstance == null || QaCaptureRuntime.find(qInstance) == null)
      {
         return (false);
      }
      try
      {
         QRecord settings = QaCaptureRuntime.asSystem(qInstance, () -> QaCaptureSettings.load(qInstance));
         if(!QaCaptureSettings.isActive(qInstance, settings))
         {
            return (false);
         }
         QaCaptureProvider   provider = QaCaptureRuntime.of(qInstance).getConfig().getProvider(settings.getValueString("provider"));
         QaCaptureCspOrigins origins  = provider.getCspOrigins();
         origins.scriptSrc().forEach(origin -> headers.withSources(NextDashboardSecurityHeaders.SCRIPT_SRC, origin));
         origins.frameSrc().forEach(origin -> headers.withSources("frame-src", origin));
         origins.connectSrc().forEach(origin -> headers.withSources("connect-src", origin));
         return (true);
      }
      catch(Exception e)
      {
         LOG.warn("Could not add the QA capture origins to the dashboard Content-Security-Policy (left unchanged)", e, logPair("exceptionClass", e.getClass().getSimpleName()));
         return (false);
      }
   }
}
