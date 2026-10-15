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

package com.kingsrook.qbits.qacapture.service;


import java.util.List;
import com.kingsrook.qbits.qacapture.QaCaptureRuntime;
import com.kingsrook.qbits.qacapture.QaCaptureSchema;
import com.kingsrook.qbits.qacapture.QaCaptureSchema.Mode;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.utils.StringUtils;


/*******************************************************************************
 ** Reads of the environment's settings row and its audience (call in the
 ** system context).
 *******************************************************************************/
public final class QaCaptureSettings
{
   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private QaCaptureSettings()
   {
   }



   /*******************************************************************************
    ** The settings row of the instance's configured environment, or null.
    *******************************************************************************/
   public static QRecord load(QInstance qInstance) throws QException
   {
      String environment = QaCaptureRuntime.of(qInstance).getConfig().getEnvironment();
      return (QaCaptureDb.first(QaCaptureSchema.SETTINGS, QaCaptureDb.eq("environment", environment)));
   }



   /*******************************************************************************
    ** The audience rows of a settings row.
    *******************************************************************************/
   public static List<QRecord> audience(QRecord settings) throws QException
   {
      if(settings == null)
      {
         return (List.of());
      }
      return (QaCaptureDb.query(QaCaptureSchema.AUDIENCE, QaCaptureDb.eq("settingsId", settings.getValue("id"))));
   }



   /*******************************************************************************
    ** Whether a settings row turns capture on for someone at all: a mode other
    ** than OFF, a team id and a registered provider. Decides the recorder page
    ** and the Content-Security-Policy additions.
    *******************************************************************************/
   public static boolean isActive(QInstance qInstance, QRecord settings)
   {
      return (settings != null
         && Mode.parse(settings.getValueString("mode")) != Mode.OFF
         && StringUtils.hasContent(settings.getValueString("teamId"))
         && QaCaptureRuntime.of(qInstance).getConfig().getProvider(settings.getValueString("provider")) != null);
   }
}
