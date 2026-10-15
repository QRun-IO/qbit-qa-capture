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


import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import com.kingsrook.qbits.qacapture.api.QaCaptureUiApi;
import com.kingsrook.qbits.qacapture.metadata.QaCaptureHelp;
import com.kingsrook.qbits.qacapture.metadata.QaCaptureMetaData;
import com.kingsrook.qbits.qacapture.service.QaModePreference;
import com.kingsrook.qbits.qacapture.web.QaCaptureRouteProvider;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.metadata.MetaDataProducerMultiOutput;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.help.bundles.HelpBundles;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitMetaDataProducer;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** Entry point of the QA capture QBit.
 **
 ** Host usage:
 ** <pre>
 ** new QaCaptureQBitProducer()
 **    .withQBitConfig(new QaCaptureQBitConfig()
 **       .withBackendName("rdbms")
 **       .withEnvironment(System.getenv("APP_ENVIRONMENT"))       // "prod"
 **       .withBackendVersion(buildVersion)
 **       .withParentAppName("admin")
 **       .withJamWebhookSecret(System.getenv("QA_CAPTURE_JAM_WEBHOOK_SECRET"))
 **       .withRoleResolver(session -&gt; myGroups(session)))
 **    .produce(qInstance);
 ** </pre>
 ** The tables come from the host-included changelog
 ** (db/liquibase/qbit-qa-capture/qa_capture.changelog.sql); the QBit never
 ** runs DDL. Producing registers the tables and app, the qa-capture UI API
 ** extension, the route provider (request ids, /qa/recorder, webhooks), the
 ** "qa-capture" preference section and the help bundle.
 *******************************************************************************/
public class QaCaptureQBitProducer implements QBitMetaDataProducer<QaCaptureQBitConfig>
{
   private static final QLogger LOG = QLogger.getLogger(QaCaptureQBitProducer.class);

   public static final String GROUP_ID    = "com.kingsrook.qbits";
   public static final String ARTIFACT_ID = "qbit-qa-capture";
   public static final String VERSION     = loadVersion();

   private QaCaptureQBitConfig qBitConfig;
   private String              namespace;



   /*******************************************************************************
    ** Read the artifact version from the Maven-filtered properties resource.
    *******************************************************************************/
   private static String loadVersion()
   {
      try(InputStream inputStream = QaCaptureQBitProducer.class.getClassLoader().getResourceAsStream("qbit-qa-capture.properties"))
      {
         if(inputStream == null)
         {
            throw (new IllegalStateException("Missing qbit-qa-capture.properties; build with Maven to generate the artifact version"));
         }
         Properties properties = new Properties();
         properties.load(inputStream);
         String version = properties.getProperty("version");
         if(version == null || version.isBlank() || version.contains("${"))
         {
            throw (new IllegalStateException("Missing or unfiltered version in qbit-qa-capture.properties; build with Maven"));
         }
         return (version);
      }
      catch(IOException e)
      {
         throw (new IllegalStateException("Could not read the artifact version from qbit-qa-capture.properties", e));
      }
   }



   /*******************************************************************************
    ** Fluent setter for the config.
    *******************************************************************************/
   public QaCaptureQBitProducer withQBitConfig(QaCaptureQBitConfig config)
   {
      this.qBitConfig = config;
      return (this);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public QaCaptureQBitConfig getQBitConfig()
   {
      return (qBitConfig);
   }



   /*******************************************************************************
    ** Fluent setter for the namespace (recorded only: the table names are fixed).
    *******************************************************************************/
   public QaCaptureQBitProducer withNamespace(String namespace)
   {
      this.namespace = namespace;
      return (this);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public String getNamespace()
   {
      return (namespace);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public QBitMetaData getQBitMetaData()
   {
      return (new QBitMetaData()
         .withGroupId(GROUP_ID)
         .withArtifactId(ARTIFACT_ID)
         .withVersion(VERSION)
         .withNamespace(getNamespace())
         .withConfig(getQBitConfig()));
   }



   /*******************************************************************************
    ** A producer found by package scanning has no config: skip it.
    *******************************************************************************/
   @Override
   public boolean isEnabled()
   {
      return (qBitConfig != null);
   }



   /*******************************************************************************
    ** Validate the config, then add the QBit and its meta-data. Returns an
    ** empty output, so a host that also adds the output adds nothing twice.
    *******************************************************************************/
   @Override
   public MetaDataProducerMultiOutput produce(QInstance qInstance) throws QException
   {
      if(qBitConfig == null)
      {
         throw (new QException("QA capture configuration validation failed: no config was supplied (call withQBitConfig)"));
      }
      List<String> errors = new ArrayList<>();
      qBitConfig.validate(qInstance, errors);
      for(String table : QaCaptureSchema.TABLES)
      {
         if(qInstance.getTable(table) != null)
         {
            errors.add("table " + table + " already exists in the instance");
         }
      }
      if(qInstance.getApp(qBitConfig.getAppName()) != null)
      {
         errors.add("app " + qBitConfig.getAppName() + " already exists in the instance");
      }
      if(!errors.isEmpty())
      {
         throw (new QException("QA capture configuration validation failed: " + String.join("; ", errors)));
      }

      qInstance.addQBit(getQBitMetaData());
      new QaCaptureRuntime(qBitConfig).addSelfToInstance(qInstance);
      new QaCaptureMetaData(qBitConfig).addTo(qInstance);
      QaCaptureHelp.apply(qInstance, qBitConfig);
      HelpBundles.register(qInstance, QaCaptureHelp.RESOURCE);
      QaModePreference.register(qInstance);
      if(Boolean.TRUE.equals(qBitConfig.getRegisterRoutes()))
      {
         QaCaptureUiApi.register(qInstance);
         QaCaptureRouteProvider.register(qInstance);
      }
      QaCaptureRuntime.setCurrentInstance(qInstance);

      LOG.info("QA capture registered", logPair("environment", qBitConfig.getEnvironment()), logPair("app", qBitConfig.getAppName()),
         logPair("parentApp", qBitConfig.getParentAppName()), logPair("providers", qBitConfig.getProviders().keySet()),
         logPair("routes", qBitConfig.getRegisterRoutes()));
      return (new MetaDataProducerMultiOutput());
   }
}
