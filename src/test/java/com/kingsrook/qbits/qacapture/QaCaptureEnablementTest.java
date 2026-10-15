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


import java.util.List;
import com.kingsrook.qbits.qacapture.service.QaCaptureEnablement;
import com.kingsrook.qbits.qacapture.service.QaCaptureEnablement.Decision;
import com.kingsrook.qbits.qacapture.service.QaCaptureEnablement.Reason;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.model.session.QUser;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** The enablement rules, without a database.
 *******************************************************************************/
class QaCaptureEnablementTest
{
   private final QaCaptureQBitConfig config = new QaCaptureQBitConfig();



   /*******************************************************************************
    **
    *******************************************************************************/
   private static QRecord settings(String mode, String teamId)
   {
      return (new QRecord().withValue("id", 1).withValue("environment", "prod").withValue("provider", "jam").withValue("mode", mode).withValue("teamId", teamId));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static QRecord member(String type, String value, boolean active, String source)
   {
      return (new QRecord().withValue("settingsId", 1).withValue("audienceType", type).withValue("value", value).withValue("active", active).withValue("source", source));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static QSession session(String id, String email, String... permissions)
   {
      return (new QSession().withUser(new QUser().withIdReference(id).withEmail(email)).withPermissions(permissions));
   }



   /*******************************************************************************
    ** No row, OFF, no team id and an unknown provider.
    *******************************************************************************/
   @Test
   void notConfiguredAndOff()
   {
      QSession user = session("u1", "u1@example.com");
      assertThat(QaCaptureEnablement.decide(config, user, null, List.of(), false).reason()).isEqualTo(Reason.NOT_CONFIGURED);
      assertThat(QaCaptureEnablement.decide(config, user, settings("OFF", "team"), List.of(member("USER", "u1", true, "ADMIN")), true).reason()).isEqualTo(Reason.OFF);
      assertThat(QaCaptureEnablement.decide(config, user, settings("AUDIENCE", " "), List.of(member("USER", "u1", true, "ADMIN")), false).reason()).isEqualTo(Reason.NOT_CONFIGURED);
      assertThat(QaCaptureEnablement.decide(config, user, settings("AUDIENCE", "team").withValue("provider", "other"), List.of(), false).reason()).isEqualTo(Reason.NOT_CONFIGURED);
      assertThat(QaCaptureEnablement.decide(config, user, settings("bogus", "team"), List.of(), false).reason()).as("unknown mode fails closed").isEqualTo(Reason.OFF);
   }



   /*******************************************************************************
    ** USER rows match the id or the email, case-insensitively; inactive rows
    ** and QA mode rows do not count.
    *******************************************************************************/
   @Test
   void audienceUsers()
   {
      QSession user = session("James@Example.com", "james@example.com");
      Decision byId = QaCaptureEnablement.decide(config, user, settings("AUDIENCE", "team"), List.of(member("USER", "james@example.COM", true, "ADMIN")), false);
      assertThat(byId.enabled()).isTrue();
      assertThat(byId.reason()).isEqualTo(Reason.AUDIENCE);
      assertThat(byId.provider().getName()).isEqualTo("jam");

      QSession other = session("u2", "U2@example.com");
      assertThat(QaCaptureEnablement.decide(config, other, settings("AUDIENCE", "team"), List.of(member("USER", "u2@example.com", true, null)), false).enabled()).isTrue();
      assertThat(QaCaptureEnablement.decide(config, other, settings("AUDIENCE", "team"), List.of(member("USER", "u2", false, "ADMIN")), false).reason()).isEqualTo(Reason.NO_ACCESS);
      assertThat(QaCaptureEnablement.decide(config, other, settings("AUDIENCE", "team"), List.of(member("USER", "u2", true, "QA_MODE")), false).reason()).isEqualTo(Reason.NO_ACCESS);
      assertThat(QaCaptureEnablement.decide(config, other, settings("SELF_SERVE", "team"), List.of(member("USER", "u2", true, "ADMIN")), false).reason())
         .as("self-serve only ignores the audience").isEqualTo(Reason.NO_ACCESS);
      assertThat(QaCaptureEnablement.decide(config, new QSession(), settings("AUDIENCE", "team"), List.of(member("USER", "u2", true, "ADMIN")), false).enabled()).isFalse();
   }



   /*******************************************************************************
    ** ROLE rows match the session's permissions by default, or the host's
    ** role resolver.
    *******************************************************************************/
   @Test
   void audienceRoles()
   {
      QSession user = session("u1", null, "qa.testers");
      assertThat(QaCaptureEnablement.decide(config, user, settings("AUDIENCE", "team"), List.of(member("ROLE", "QA.Testers", true, "ADMIN")), false).enabled()).isTrue();

      QaCaptureQBitConfig withResolver = new QaCaptureQBitConfig().withRoleResolver(session -> List.of("kof22-admins"));
      assertThat(QaCaptureEnablement.decide(withResolver, user, settings("AUDIENCE", "team"), List.of(member("ROLE", "kof22-admins", true, "ADMIN")), false).enabled()).isTrue();
      assertThat(QaCaptureEnablement.decide(withResolver, user, settings("AUDIENCE", "team"), List.of(member("ROLE", "qa.testers", true, "ADMIN")), false).enabled()).isFalse();
      assertThat(new QaCaptureQBitConfig().withRoleResolver(session -> null).resolveRoles(user)).isEmpty();
      assertThat(config.resolveRoles(null)).isEmpty();
   }



   /*******************************************************************************
    ** Self-serve: the permission and the user's QA mode.
    *******************************************************************************/
   @Test
   void selfServe()
   {
      QSession tester = session("t1", null, QaCaptureSchema.PERMISSION_USE_QA_MODE);
      Decision off = QaCaptureEnablement.decide(config, tester, settings("SELF_SERVE", "team"), List.of(), false);
      assertThat(off.reason()).isEqualTo(Reason.QA_MODE_OFF);
      assertThat(off.qaModeAvailable()).isTrue();
      assertThat(off.qaModeActive()).isFalse();

      Decision on = QaCaptureEnablement.decide(config, tester, settings("AUDIENCE_AND_SELF_SERVE", "team"), List.of(), true);
      assertThat(on.enabled()).isTrue();
      assertThat(on.reason()).isEqualTo(Reason.QA_MODE);

      assertThat(QaCaptureEnablement.decide(config, session("t2", null), settings("SELF_SERVE", "team"), List.of(), true).reason())
         .as("QA mode without the permission is ignored").isEqualTo(Reason.NO_ACCESS);
      assertThat(QaCaptureEnablement.decide(config, tester, settings("AUDIENCE", "team"), List.of(), true).qaModeAvailable()).isFalse();

      Decision both = QaCaptureEnablement.decide(config, tester, settings("AUDIENCE_AND_SELF_SERVE", "team"), List.of(member("USER", "t1", true, "ADMIN")), false);
      assertThat(both.reason()).as("audience wins").isEqualTo(Reason.AUDIENCE);
      assertThat(both.qaModeAvailable()).isTrue();
   }
}
