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
import java.util.Map;
import com.kingsrook.qbits.qacapture.service.QaModePreference;
import com.kingsrook.qbits.qacapture.spi.JamProvider;
import com.kingsrook.qbits.qacapture.spi.QaCaptureCspOrigins;
import com.kingsrook.qbits.qacapture.spi.QaCaptureProvider;
import com.kingsrook.qbits.qacapture.spi.QaCaptureWebhookRequest;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.preferences.UserPreferencesMetaData;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** Production, config validation and the provider registry.
 *******************************************************************************/
class QaCaptureProducerTest
{
   /*******************************************************************************
    ** A provider that has no webhook.
    *******************************************************************************/
   static class OtherProvider implements QaCaptureProvider
   {
      /***************************************************************************
       **
       ***************************************************************************/
      @Override
      public String getName()
      {
         return ("other");
      }



      /***************************************************************************
       **
       ***************************************************************************/
      @Override
      public String getLabel()
      {
         return ("Other");
      }



      /***************************************************************************
       **
       ***************************************************************************/
      @Override
      public List<String> getScriptUrls()
      {
         return (List.of());
      }



      /***************************************************************************
       **
       ***************************************************************************/
      @Override
      public QaCaptureCspOrigins getCspOrigins()
      {
         return (QaCaptureCspOrigins.none());
      }



      /***************************************************************************
       **
       ***************************************************************************/
      @Override
      public Map<String, Object> providerConfig(QRecord settings)
      {
         return (Map.of());
      }



      /***************************************************************************
       **
       ***************************************************************************/
      @Override
      public Map<String, String> recordingParams(QRecord report, QRecord settings)
      {
         return (null);
      }



      /***************************************************************************
       **
       ***************************************************************************/
      @Override
      public String recorderHead(QRecord settings, String sanitizersExpression)
      {
         return ("");
      }
   }



   /*******************************************************************************
    ** Invalid configs fail production with every problem listed.
    *******************************************************************************/
   @Test
   void validation()
   {
      QInstance instance = BaseQaCaptureTest.newInstance();
      assertThatThrownBy(() -> new QaCaptureQBitProducer().produce(instance)).isInstanceOf(QException.class).hasMessageContaining("no config");
      assertThat(new QaCaptureQBitProducer().isEnabled()).isFalse();

      assertThatThrownBy(() -> new QaCaptureQBitProducer().withQBitConfig(new QaCaptureQBitConfig().withBackendName("nope").withEnvironment("bad env!")
         .withAppName("1bad").withParentAppName("missing").withProviders(List.of())).produce(instance))
         .isInstanceOf(QException.class)
         .hasMessageContaining("backend nope is not defined").hasMessageContaining("environment is required")
         .hasMessageContaining("appName").hasMessageContaining("parent app missing").hasMessageContaining("at least one provider");
      assertThatThrownBy(() -> new QaCaptureQBitProducer().withQBitConfig(new QaCaptureQBitConfig().withEnvironment("prod")).produce(instance))
         .hasMessageContaining("backendName is required");

      QaCaptureQBitConfig config = new QaCaptureQBitConfig().withBackendName(BaseQaCaptureTest.BACKEND_NAME).withEnvironment("prod");
      config.withProvider(new OtherProvider());
      assertThat(config.getProviders()).containsKeys("jam", "other");
   }



   /*******************************************************************************
    ** Producing twice is refused; metadata and identity are complete.
    *******************************************************************************/
   @Test
   void produce() throws Exception
   {
      QInstance           instance = BaseQaCaptureTest.newInstance();
      QaCaptureQBitConfig config   = new QaCaptureQBitConfig().withBackendName(BaseQaCaptureTest.BACKEND_NAME).withEnvironment("prod").withAppLabel("QA")
         .withJamWebhookSecret("whsec_c2VjcmV0").withNode(null);
      QaCaptureQBitProducer producer = new QaCaptureQBitProducer().withQBitConfig(config).withNamespace("qa");
      producer.produce(instance);
      assertThat(producer.getNamespace()).isEqualTo("qa");
      assertThat(producer.getQBitMetaData().getArtifactId()).isEqualTo("qbit-qa-capture");
      assertThat(instance.getApp("qaCapture").getLabel()).isEqualTo("QA");
      assertThat(QaModePreference.isRegistered(instance)).isTrue();
      assertThat(UserPreferencesMetaData.of(instance).getSections()).singleElement().satisfies(section ->
         assertThat(section.getPermissionName()).isEqualTo(QaCaptureSchema.PERMISSION_USE_QA_MODE));
      QaModePreference.register(instance);
      assertThat(UserPreferencesMetaData.of(instance).getSections()).hasSize(1);
      assertThat(QaCaptureRuntime.getCurrentInstance()).isSameAs(instance);
      assertThat(config.toString()).contains("jam").contains("<redacted>").doesNotContain("c2VjcmV0");
      assertThat(config.getEffectiveNode()).isEqualTo(System.getenv("HOSTNAME"));
      assertThat(config.getDefaultBackendNameForTables()).isEqualTo(BaseQaCaptureTest.BACKEND_NAME);

      assertThatThrownBy(() -> new QaCaptureQBitProducer().withQBitConfig(new QaCaptureQBitConfig().withBackendName(BaseQaCaptureTest.BACKEND_NAME).withEnvironment("prod")).produce(instance))
         .hasMessageContaining("already exists");
      assertThat(QaCaptureRuntime.find(BaseQaCaptureTest.newInstance())).isNull();
      assertThatThrownBy(() -> QaCaptureRuntime.of(new QInstance())).isInstanceOf(IllegalStateException.class);
   }



   /*******************************************************************************
    ** The default webhook hook answers 404.
    *******************************************************************************/
   @Test
   void defaultWebhookIsNotFound() throws Exception
   {
      assertThat(new OtherProvider().handleWebhook(new QaCaptureWebhookRequest(null, null, null), null).status()).isEqualTo(404);
      assertThat(new JamProvider().getLabel()).isEqualTo("Jam");
   }
}
