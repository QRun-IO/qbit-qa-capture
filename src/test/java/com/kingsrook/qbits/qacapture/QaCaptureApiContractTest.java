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


import java.nio.file.Files;
import java.nio.file.Path;
import com.kingsrook.qbits.qacapture.api.QaCaptureUiApi;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.middleware.javalin.QJavalinMetaData;
import com.kingsrook.qqq.middleware.javalin.specs.extensions.MiddlewareSpecExtensionsMetaData;
import com.kingsrook.qqq.middleware.javalin.specs.v1.MiddlewareVersionV1;
import com.kingsrook.qqq.middleware.javalin.tools.UiApiContract;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** The committed extension fragment (src/main/resources/openapi/qqq-v1/
 ** qa-capture.yaml) is exactly what the code generates, and describes the
 ** two contract operations.
 *******************************************************************************/
class QaCaptureApiContractTest extends BaseQaCaptureTest
{
   public static final Path FRAGMENT = Path.of("src/main/resources/openapi/qqq-v1/qa-capture.yaml");



   /*******************************************************************************
    ** Regenerate only by explicit build property, then check the fragment.
    *******************************************************************************/
   @Test
   void contract() throws Exception
   {
      var    extension = QaCaptureUiApi.extension();
      var    version   = new MiddlewareVersionV1();
      String generated = UiApiContract.generateExtensionYaml(version, extension);
      if(Boolean.getBoolean("qacapture.openapi.update"))
      {
         Files.createDirectories(FRAGMENT.getParent());
         Files.writeString(FRAGMENT, generated);
      }
      assertThat(UiApiContract.check(generated, Files.readString(FRAGMENT), null, 1,
         "./mvnw -Dtest=QaCaptureApiContractTest -Dqacapture.openapi.update=true test")).isEmpty();

      var model = version.generateExtensionOpenAPIModel("qqq", extension);
      assertThat(model.getComponents().getSchemas().keySet()).allMatch(name -> name.startsWith("QaCapture"));
      assertThat(model.getPaths().keySet()).containsExactlyInAnyOrder("/qqq/v1/qbit/qa-capture/capability", "/qqq/v1/qbit/qa-capture/report");
      assertThat(extension.getSemanticVersion()).isEqualTo("1.0.0");
      assertThat(extension.getEffectivePathPrefix()).isEqualTo("/qbit/qa-capture");
      assertThat(generated).contains("qaCaptureCapability", "qaCaptureReportCreate", "providerConfig", "recordingLinkId", "scriptUrls",
         "preferenceSection", "reportsHref", "bodyFieldNames", "dropPathPrefixes", "blurSelectors", "cspOrigins", "requestIdHeader",
         "recordingParams", "frontendVersion", "QA_MODE_OFF", "NOT_CONFIGURED");
      assertThat(generated).doesNotContain("/hooks/", "/qa/recorder");
   }



   /*******************************************************************************
    ** Production registers the extension and the route provider once; the
    ** routes can be switched off.
    *******************************************************************************/
   @Test
   void registration() throws Exception
   {
      assertThat(MiddlewareSpecExtensionsMetaData.of(qInstance).getExtensions()).containsOnlyKeys("qa-capture");
      QaCaptureUiApi.register(qInstance);
      com.kingsrook.qbits.qacapture.web.QaCaptureRouteProvider.register(qInstance);
      assertThat(MiddlewareSpecExtensionsMetaData.of(qInstance).getExtensions()).hasSize(1);
      assertThat(QJavalinMetaData.of(qInstance).getAdditionalRouteProviderReferences()).hasSize(1);

      resetDatabase();
      QInstance disabled = newInstance();
      new QaCaptureQBitProducer().withQBitConfig(new QaCaptureQBitConfig().withBackendName(BACKEND_NAME).withEnvironment("prod").withRegisterRoutes(false)).produce(disabled);
      assertThat(MiddlewareSpecExtensionsMetaData.of(disabled)).isNull();
      assertThat(QJavalinMetaData.of(disabled)).isNull();
   }
}
