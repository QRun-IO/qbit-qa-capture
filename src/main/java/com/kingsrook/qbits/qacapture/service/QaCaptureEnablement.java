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


import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import com.kingsrook.qbits.qacapture.QaCaptureQBitConfig;
import com.kingsrook.qbits.qacapture.QaCaptureSchema;
import com.kingsrook.qbits.qacapture.QaCaptureSchema.AudienceSource;
import com.kingsrook.qbits.qacapture.QaCaptureSchema.AudienceType;
import com.kingsrook.qbits.qacapture.QaCaptureSchema.Mode;
import com.kingsrook.qbits.qacapture.spi.QaCaptureProvider;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.utils.StringUtils;


/*******************************************************************************
 ** Whether QA capture is on for a user, and why (the capability reason).
 **
 ** Capture is on iff the environment's settings row has a mode other than
 ** OFF, a team id and a registered provider, and either
 ** - the mode includes AUDIENCE and an active administrator audience row
 **   names the user (USER: user id or email, case-insensitive) or one of the
 **   user's roles (ROLE: the config's role resolver, by default the session's
 **   permission names, case-insensitive); or
 ** - the mode includes SELF_SERVE, the user holds qa_capture.use_qa_mode and
 **   their QA mode preference is on.
 **
 ** Pure: callers load the settings, audience and QA mode state.
 *******************************************************************************/
public final class QaCaptureEnablement
{
   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private QaCaptureEnablement()
   {
   }



   /*******************************************************************************
    ** The capability reason values.
    *******************************************************************************/
   public enum Reason
   {
      AUDIENCE,
      QA_MODE,
      OFF,
      NOT_CONFIGURED,
      NO_ACCESS,
      QA_MODE_OFF
   }



   /*******************************************************************************
    ** A decision.
    **
    ** @param enabled         whether capture is on for the user
    ** @param reason          why
    ** @param settings        the settings row (null when there is none)
    ** @param provider        the settings' provider (null when not configured)
    ** @param qaModeAvailable whether the user can switch QA mode in this mode
    ** @param qaModeActive    whether the user's QA mode preference is on
    *******************************************************************************/
   public record Decision(boolean enabled, Reason reason, QRecord settings, QaCaptureProvider provider, boolean qaModeAvailable, boolean qaModeActive)
   {
   }



   /*******************************************************************************
    ** Decide for a session.
    **
    ** @param config   the QBit config (providers, role resolver)
    ** @param session  the user's session
    ** @param settings the environment's settings row, or null
    ** @param audience the settings' audience rows (any state)
    ** @param qaModeOn the user's QA mode preference
    *******************************************************************************/
   public static Decision decide(QaCaptureQBitConfig config, QSession session, QRecord settings, List<QRecord> audience, boolean qaModeOn)
   {
      if(settings == null)
      {
         return (new Decision(false, Reason.NOT_CONFIGURED, null, null, false, false));
      }

      Mode mode = Mode.parse(settings.getValueString("mode"));
      if(mode == Mode.OFF)
      {
         return (new Decision(false, Reason.OFF, settings, null, false, false));
      }

      QaCaptureProvider provider = config.getProvider(settings.getValueString("provider"));
      if(provider == null || !StringUtils.hasContent(settings.getValueString("teamId")))
      {
         return (new Decision(false, Reason.NOT_CONFIGURED, settings, null, false, false));
      }

      boolean mayUseQaMode   = session != null && session.hasPermission(QaCaptureSchema.PERMISSION_USE_QA_MODE);
      boolean qaModeAvailable = mode.includesSelfServe() && mayUseQaMode;
      boolean qaModeActive    = qaModeAvailable && qaModeOn;

      if(mode.includesAudience() && inAudience(config, session, audience))
      {
         return (new Decision(true, Reason.AUDIENCE, settings, provider, qaModeAvailable, qaModeActive));
      }
      if(qaModeAvailable)
      {
         return (qaModeActive
            ? new Decision(true, Reason.QA_MODE, settings, provider, true, true)
            : new Decision(false, Reason.QA_MODE_OFF, settings, provider, true, false));
      }
      return (new Decision(false, Reason.NO_ACCESS, settings, provider, false, false));
   }



   /*******************************************************************************
    ** Whether an active administrator audience row names the user or a role.
    *******************************************************************************/
   public static boolean inAudience(QaCaptureQBitConfig config, QSession session, List<QRecord> audience)
   {
      if(session == null || session.getUser() == null || audience == null || audience.isEmpty())
      {
         return (false);
      }

      Set<String> userKeys = new LinkedHashSet<>();
      addLower(userKeys, session.getUser().getIdReference());
      addLower(userKeys, session.getUser().getEmail());

      Set<String> roles = null;
      for(QRecord row : audience)
      {
         String source = row.getValueString("source");
         if(!Boolean.TRUE.equals(row.getValueBoolean("active")) || (source != null && !AudienceSource.ADMIN.name().equals(source)))
         {
            continue;
         }
         String value = lower(row.getValueString("value"));
         if(value == null)
         {
            continue;
         }
         String type = row.getValueString("audienceType");
         if(AudienceType.USER.name().equals(type) && userKeys.contains(value))
         {
            return (true);
         }
         if(AudienceType.ROLE.name().equals(type))
         {
            if(roles == null)
            {
               roles = new LinkedHashSet<>();
               Collection<String> resolved = config.resolveRoles(session);
               for(String role : resolved)
               {
                  addLower(roles, role);
               }
            }
            if(roles.contains(value))
            {
               return (true);
            }
         }
      }
      return (false);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static void addLower(Set<String> set, String value)
   {
      String lower = lower(value);
      if(lower != null)
      {
         set.add(lower);
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static String lower(String value)
   {
      return (value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT));
   }
}
