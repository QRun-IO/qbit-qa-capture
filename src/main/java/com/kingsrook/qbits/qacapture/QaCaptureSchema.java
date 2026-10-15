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


/*******************************************************************************
 ** Names shared by the metadata, the runtime and the Liquibase changelog
 ** (db/liquibase/qbit-qa-capture/qa_capture.changelog.sql): the four tables,
 ** their stored enum values and the permission names. QQQ table names equal
 ** the backend (SQL) names.
 *******************************************************************************/
public final class QaCaptureSchema
{
   public static final String CHANGELOG = "db/liquibase/qbit-qa-capture/qa_capture.changelog.sql";

   public static final String SETTINGS       = "qa_capture_settings";
   public static final String AUDIENCE       = "qa_capture_audience";
   public static final String SANITIZER_RULE = "qa_capture_sanitizer_rule";
   public static final String REPORT         = "qa_capture_report";

   /** every table of the QBit, in navigation order. */
   public static final List<String> TABLES = List.of(REPORT, SETTINGS, AUDIENCE, SANITIZER_RULE);

   public static final String PERMISSION_MANAGE       = "qa_capture.manage";
   public static final String PERMISSION_USE_QA_MODE  = "qa_capture.use_qa_mode";
   public static final String PERMISSION_VIEW_REPORTS = "qa_capture.view_reports";

   /** every permission of the QBit (separately grantable). */
   public static final List<String> PERMISSIONS = List.of(PERMISSION_MANAGE, PERMISSION_USE_QA_MODE, PERMISSION_VIEW_REPORTS);

   public static final String PVS_PROVIDER        = "qa_capture_provider";
   public static final String PVS_MODE            = "qa_capture_mode";
   public static final String PVS_AUDIENCE_TYPE   = "qa_capture_audience_type";
   public static final String PVS_AUDIENCE_SOURCE = "qa_capture_audience_source";
   public static final String PVS_RULE_TYPE       = "qa_capture_rule_type";
   public static final String PVS_RULE_SOURCE     = "qa_capture_rule_source";
   public static final String PVS_REPORT_STATUS   = "qa_capture_report_status";



   /*******************************************************************************
    ** Constants only.
    *******************************************************************************/
   private QaCaptureSchema()
   {
   }



   /*******************************************************************************
    ** Capture modes of a settings row.
    *******************************************************************************/
   public enum Mode
   {
      OFF("Off"),
      AUDIENCE("Audience only"),
      SELF_SERVE("Self-serve QA mode"),
      AUDIENCE_AND_SELF_SERVE("Audience and self-serve QA mode");

      private final String label;



      /*******************************************************************************
       ** Constructor.
       *******************************************************************************/
      Mode(String label)
      {
         this.label = label;
      }



      /*******************************************************************************
       ** Whether audience membership turns capture on.
       *******************************************************************************/
      public boolean includesAudience()
      {
         return (this == AUDIENCE || this == AUDIENCE_AND_SELF_SERVE);
      }



      /*******************************************************************************
       ** Whether a user's own QA mode switch turns capture on.
       *******************************************************************************/
      public boolean includesSelfServe()
      {
         return (this == SELF_SERVE || this == AUDIENCE_AND_SELF_SERVE);
      }



      /*******************************************************************************
       ** The stored value, or OFF for null or an unknown value (fail closed).
       *******************************************************************************/
      public static Mode parse(String value)
      {
         for(Mode mode : values())
         {
            if(mode.name().equals(value))
            {
               return (mode);
            }
         }
         return (OFF);
      }



      /*******************************************************************************
       ** Getter for label
       *******************************************************************************/
      public String getLabel()
      {
         return (this.label);
      }
   }



   /*******************************************************************************
    ** Kinds of audience members.
    *******************************************************************************/
   public enum AudienceType
   {
      USER("User"),
      ROLE("Role");

      private final String label;



      /*******************************************************************************
       ** Constructor.
       *******************************************************************************/
      AudienceType(String label)
      {
         this.label = label;
      }



      /*******************************************************************************
       ** Getter for label
       *******************************************************************************/
      public String getLabel()
      {
         return (this.label);
      }
   }



   /*******************************************************************************
    ** Where an audience row came from.
    *******************************************************************************/
   public enum AudienceSource
   {
      ADMIN("Administrator"),
      QA_MODE("User's QA mode switch");

      private final String label;



      /*******************************************************************************
       ** Constructor.
       *******************************************************************************/
      AudienceSource(String label)
      {
         this.label = label;
      }



      /*******************************************************************************
       ** Getter for label
       *******************************************************************************/
      public String getLabel()
      {
         return (this.label);
      }
   }



   /*******************************************************************************
    ** Kinds of sanitizer rules.
    *******************************************************************************/
   public enum RuleType
   {
      HEADER("Header"),
      BODY_FIELD("Body field"),
      QUERY_PARAM("Query parameter"),
      VALUE_PATTERN("Value pattern"),
      DROP_PATH("Dropped path prefix"),
      BLUR_SELECTOR("Blur selector");

      private final String label;



      /*******************************************************************************
       ** Constructor.
       *******************************************************************************/
      RuleType(String label)
      {
         this.label = label;
      }



      /*******************************************************************************
       ** Getter for label
       *******************************************************************************/
      public String getLabel()
      {
         return (this.label);
      }
   }



   /*******************************************************************************
    ** Where a sanitizer rule came from.
    *******************************************************************************/
   public enum RuleSource
   {
      BUILT_IN("Built in"),
      SECURED_DATA("Secured data"),
      ADMIN("Administrator");

      private final String label;



      /*******************************************************************************
       ** Constructor.
       *******************************************************************************/
      RuleSource(String label)
      {
         this.label = label;
      }



      /*******************************************************************************
       ** Getter for label
       *******************************************************************************/
      public String getLabel()
      {
         return (this.label);
      }
   }



   /*******************************************************************************
    ** Lifecycle of a QA report.
    *******************************************************************************/
   public enum ReportStatus
   {
      OPEN("Open"),
      CAPTURED("Captured"),
      FILED("Filed"),
      CLOSED("Closed");

      private final String label;



      /*******************************************************************************
       ** Constructor.
       *******************************************************************************/
      ReportStatus(String label)
      {
         this.label = label;
      }



      /*******************************************************************************
       ** Getter for label
       *******************************************************************************/
      public String getLabel()
      {
         return (this.label);
      }
   }
}
