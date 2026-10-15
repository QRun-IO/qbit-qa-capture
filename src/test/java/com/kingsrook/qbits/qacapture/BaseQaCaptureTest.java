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


import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.qacapture.service.QaCaptureDb;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.instances.QInstanceValidator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QAuthenticationType;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.authentication.QAuthenticationMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QAppMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.model.session.QUser;
import com.kingsrook.qqq.backend.core.modules.authentication.QAuthenticationModuleCustomizerInterface;
import com.kingsrook.qqq.backend.module.rdbms.model.metadata.RDBMSBackendMetaData;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;


/*******************************************************************************
 ** Test base: an H2 database (PostgreSQL mode) created by running the
 ** shipped changelog through real Liquibase, an "admin" host app and a
 ** "patient" table with secure fields, the QBit produced into a validated
 ** QInstance, and sessions by user id prefix:
 ** admin-* (every qa_capture permission), tester-* (use_qa_mode),
 ** viewer-* (view_reports), anyone else (no permissions).
 *******************************************************************************/
public class BaseQaCaptureTest
{
   public static final String BACKEND_NAME = "rdbms";
   public static final String JDBC_URL     = "jdbc:h2:mem:qa_capture_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE";
   public static final String TEAM_ID      = "0815d765-0953-4f79-91a3-f5ed435abbdd";
   public static final String ADMIN        = "admin-1";
   public static final String TESTER       = "tester-1";
   public static final String VIEWER       = "viewer-1";
   public static final String NOBODY       = "nobody-1";

   protected QInstance           qInstance;
   protected QaCaptureQBitConfig config;



   /*******************************************************************************
    ** Fresh database and instance; signed in as the admin.
    *******************************************************************************/
   @BeforeEach
   void baseSetUp() throws Exception
   {
      resetDatabase();
      qInstance = newInstance();
      config = new QaCaptureQBitConfig().withBackendName(BACKEND_NAME).withEnvironment("prod")
         .withBackendVersion("0.1.110").withNode("node-a").withParentAppName("admin")
         .withJamWebhookSecret("whsec_" + java.util.Base64.getEncoder().encodeToString("test-secret-key-0123456789".getBytes()));
      customizeConfig(config);
      new QaCaptureQBitProducer().withQBitConfig(config).produce(qInstance);
      new QInstanceValidator().validate(qInstance);
      loginAs(ADMIN);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @AfterEach
   void baseTearDown()
   {
      QContext.clear();
   }



   /*******************************************************************************
    ** Hook: change the config before production.
    *******************************************************************************/
   protected void customizeConfig(QaCaptureQBitConfig config)
   {
   }



   /*******************************************************************************
    ** A host instance: backend, mock authentication, an admin app and a
    ** patient table with a sensitive and a PHI-classified field.
    *******************************************************************************/
   public static QInstance newInstance()
   {
      QInstance instance = new QInstance();
      instance.setEnforcePermissionsInCoreActions(true);
      RDBMSBackendMetaData backend = new RDBMSBackendMetaData().withName(BACKEND_NAME).withVendor("h2").withHostName("mem").withDatabaseName("qa_capture_test").withUsername("sa");
      backend.setJdbcUrl(JDBC_URL);
      instance.addBackend(backend);
      instance.withInstanceDefaultAuthentication(new QAuthenticationMetaData().withName("mock").withType(QAuthenticationType.MOCK)
         .withCustomizer(new QCodeReference(TestSessionCustomizer.class)));
      instance.addTable(new QTableMetaData().withName("patient").withBackendName(BACKEND_NAME).withPrimaryKeyField("id")
         .withField(new QFieldMetaData("id", QFieldType.INTEGER))
         .withField(new QFieldMetaData("name", QFieldType.STRING))
         .withField(new QFieldMetaData("ssn", QFieldType.STRING).withIsSensitive(true))
         .withField(new QFieldMetaData("diagnosisCode", QFieldType.STRING).withSupplementalMetaData(
            new com.kingsrook.qqq.backend.core.actions.ai.AiFieldMetaData().withClassification(com.kingsrook.qqq.backend.core.actions.ai.DataClassification.PHI))));
      instance.addApp(new QAppMetaData().withName("admin").withLabel("Admin"));
      return (instance);
   }



   /*******************************************************************************
    ** Drop everything and run the shipped changelog.
    *******************************************************************************/
   public static void resetDatabase() throws Exception
   {
      try(Connection connection = DriverManager.getConnection(JDBC_URL, "sa", ""); Statement statement = connection.createStatement())
      {
         statement.execute("DROP ALL OBJECTS");
      }
      Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(DriverManager.getConnection(JDBC_URL, "sa", "")));
      try(Liquibase liquibase = new Liquibase(QaCaptureSchema.CHANGELOG, new ClassLoaderResourceAccessor(), database))
      {
         liquibase.update("");
      }
   }



   /*******************************************************************************
    ** Sign in as a test user.
    *******************************************************************************/
   protected QSession loginAs(String userId)
   {
      QSession session = TestSessionCustomizer.sessionFor(userId);
      QContext.init(qInstance, session);
      return (session);
   }



   /*******************************************************************************
    ** Insert the prod settings row (as the system); returns it.
    *******************************************************************************/
   protected QRecord insertSettings(String mode, String teamId, String recordingLinkId) throws QException
   {
      return (QaCaptureRuntime.asSystem(qInstance, () -> QaCaptureDb.insert(QaCaptureSchema.SETTINGS, new QRecord()
         .withValue("environment", "prod").withValue("provider", "jam").withValue("mode", mode)
         .withValue("teamId", teamId).withValue("recordingLinkId", recordingLinkId))));
   }



   /*******************************************************************************
    ** Insert an audience row (as the system).
    *******************************************************************************/
   protected QRecord insertAudience(QRecord settings, String type, String value, boolean active) throws QException
   {
      return (QaCaptureRuntime.asSystem(qInstance, () -> QaCaptureDb.insert(QaCaptureSchema.AUDIENCE, new QRecord()
         .withValue("settingsId", settings.getValue("id")).withValue("audienceType", type).withValue("value", value)
         .withValue("source", "ADMIN").withValue("active", active))));
   }



   /*******************************************************************************
    ** All rows of a table (as the system).
    *******************************************************************************/
   protected List<QRecord> all(String table) throws QException
   {
      return (QaCaptureRuntime.asSystem(qInstance, () -> QaCaptureDb.query(table, new QQueryFilter())));
   }



   /*******************************************************************************
    ** Mock sessions by user id prefix.
    *******************************************************************************/
   public static class TestSessionCustomizer implements QAuthenticationModuleCustomizerInterface
   {
      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public void customizeSession(QInstance qInstance, QSession qSession, Map<String, Object> context)
      {
         QSession granted = sessionFor(qSession.getUuid());
         qSession.setUser(granted.getUser());
         qSession.setPermissions(granted.getPermissions());
      }



      /*******************************************************************************
       ** A session for a test user.
       *******************************************************************************/
      public static QSession sessionFor(String userId)
      {
         QSession session = new QSession().withUser(new QUser().withIdReference(userId).withFullName("Name of " + userId));
         session.getUser().setEmail(userId + "@example.com");
         session.setPermissions(new HashSet<>());
         if(userId != null && userId.startsWith("admin"))
         {
            session.withPermissions(QaCaptureSchema.PERMISSIONS);
         }
         else if(userId != null && userId.startsWith("tester"))
         {
            session.withPermission(QaCaptureSchema.PERMISSION_USE_QA_MODE);
         }
         else if(userId != null && userId.startsWith("viewer"))
         {
            session.withPermission(QaCaptureSchema.PERMISSION_VIEW_REPORTS);
         }
         return (session);
      }
   }
}
