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


import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import com.kingsrook.qbits.qacapture.metadata.QaCaptureHelp;
import com.kingsrook.qqq.backend.core.model.metadata.help.QHelpContent;
import com.kingsrook.qqq.backend.core.model.metadata.help.bundles.HelpBundles;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.testing.HelpBundleAssertions;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Owner guardrails for every qa_capture_ table:
 ** - runtime code (outside the metadata package) reads or writes it;
 ** - it is seeded by the changelog or listed in STARTS_EMPTY with where its
 **   rows come from;
 ** - its help says how rows get created; the bundle covers every visible
 **   field, the tables and the app;
 ** - names: lowercase snake_case with the qa_capture_ prefix, QQQ table name
 **   equal to the backend name; permissions qa_capture.*.
 *******************************************************************************/
class QaCaptureGuardrailsTest extends BaseQaCaptureTest
{
   private static final Path MAIN = Path.of("src", "main", "java", "com", "kingsrook", "qbits", "qacapture");

   static final Map<String, String> SEEDED = Map.of(
      QaCaptureSchema.SANITIZER_RULE, "changeset 5: built-in header, body field, query parameter, value pattern and blur selector rules");

   static final Map<String, String> STARTS_EMPTY = Map.of(
      QaCaptureSchema.SETTINGS, "per installation: the host or an administrator adds its environment row (team ids are not ours to seed)",
      QaCaptureSchema.AUDIENCE, "administrators add members; QA mode switches add QA_MODE rows",
      QaCaptureSchema.REPORT, "runtime: Report a bug and provider webhooks");



   /*******************************************************************************
    ** Every table is used by runtime code, not only declared.
    *******************************************************************************/
   @Test
   void everyTableIsUsedAtRuntime() throws Exception
   {
      List<String> runtimeSource;
      try(Stream<Path> files = Files.walk(MAIN))
      {
         runtimeSource = files.filter(path -> path.toString().endsWith(".java"))
            .filter(path -> !path.startsWith(MAIN.resolve("metadata")) && !path.endsWith("QaCaptureSchema.java"))
            .map(QaCaptureGuardrailsTest::read).toList();
      }
      for(String table : QaCaptureSchema.TABLES)
      {
         String  constant = table.substring("qa_capture_".length()).toUpperCase(Locale.ROOT);
         Pattern use      = Pattern.compile("QaCaptureSchema\\." + constant + "\\b");
         assertThat(runtimeSource.stream().anyMatch(source -> use.matcher(source).find())).as(table + " must be read or written by runtime code").isTrue();
      }
   }



   /*******************************************************************************
    ** Every table is seeded or explained; only seeded tables are inserted into
    ** by the changelog.
    *******************************************************************************/
   @Test
   void everyTableIsSeededOrExplained() throws Exception
   {
      String changelog = Files.readString(Path.of("src", "main", "resources", QaCaptureSchema.CHANGELOG), StandardCharsets.UTF_8);
      for(String table : QaCaptureSchema.TABLES)
      {
         boolean seeded = SEEDED.containsKey(table);
         assertThat(seeded ^ STARTS_EMPTY.containsKey(table)).as(table + " must be seeded or listed in STARTS_EMPTY (not both)").isTrue();
         assertThat(changelog.contains("INSERT INTO " + table + " ")).as(table + " seeded iff the changelog inserts it").isEqualTo(seeded);
      }
   }



   /*******************************************************************************
    ** Help for every table (with the empty state), field and the app.
    *******************************************************************************/
   @Test
   void helpCoversEverything() throws Exception
   {
      for(String table : QaCaptureSchema.TABLES)
      {
         List<QHelpContent> help = qInstance.getTable(table).getHelpContent().get(QaCaptureHelp.SLOT);
         assertThat(help).as(table + " help").isNotEmpty();
         assertThat(help.get(0).getContent()).as(table + " says how rows get created").contains("How rows get created");
      }
      assertThat(qInstance.getApp(config.getAppName()).getHelpContent()).containsKey(QaCaptureHelp.SLOT);
      assertThat(QaCaptureHelp.entries()).hasSize(QaCaptureSchema.TABLES.size() + 1);

      assertThat(HelpBundles.of(qInstance).getBundles()).singleElement().satisfies(bundle ->
      {
         assertThat(bundle.getId()).isEqualTo("qbit-qa-capture");
         assertThat(System.getProperty("qbit.artifactVersion")).isNotBlank();
         assertThat(bundle.getVersion()).isEqualTo(System.getProperty("qbit.artifactVersion")).isEqualTo(QaCaptureQBitProducer.VERSION);
      });
      HelpBundleAssertions.forInstance(qInstance)
         .bundles(QaCaptureHelp.RESOURCE)
         .owning(target -> !"process".equals(target.kind()) && (target.name().startsWith("qa_capture_") || target.name().equals(config.getAppName())))
         .assertComplete();
   }



   /*******************************************************************************
    ** A host's own help is kept.
    *******************************************************************************/
   @Test
   void hostHelpIsKept() throws Exception
   {
      QTableMetaData table = qInstance.getTable(QaCaptureSchema.REPORT);
      table.getHelpContent().remove(QaCaptureHelp.SLOT);
      table.withHelpContent(QaCaptureHelp.SLOT, new QHelpContent("host words"));
      QaCaptureHelp.apply(qInstance, config);
      assertThat(table.getHelpContent().get(QaCaptureHelp.SLOT).get(0).getContent()).isEqualTo("host words");
   }



   /*******************************************************************************
    ** Naming rules and placement under the host's Admin app.
    *******************************************************************************/
   @Test
   void namingAndPlacement()
   {
      for(String table : QaCaptureSchema.TABLES)
      {
         assertThat(table).matches("qa_capture_[a-z_]+");
         QTableMetaData metaData = qInstance.getTable(table);
         assertThat(metaData.getBackendDetails()).as(table + " has no backend table name override: QQQ name == SQL name").isNull();
         metaData.getFields().values().forEach(field -> assertThat(field.getBackendName() == null ? field.getName() : field.getBackendName()).matches("[a-z_]+"));
         assertThat(metaData.getIsHidden()).as(table + " is visible").isFalse();
      }
      assertThat(QaCaptureSchema.PERMISSIONS).allMatch(permission -> permission.matches("qa_capture\\.[a-z_]+"));
      assertThat(qInstance.getApp("admin").getChildren()).anyMatch(child -> config.getAppName().equals(child.getName()));
      assertThat(qInstance.getApp(config.getAppName()).getChildren()).extracting(child -> child.getName()).containsExactlyElementsOf(QaCaptureSchema.TABLES);
      assertThat(qInstance.getProcesses().keySet()).as("no QBit processes, only core's generated bulk table actions")
         .filteredOn(name -> name.startsWith("qa")).allMatch(name -> name.matches("qa_capture_[a-z_]+[.]bulk[A-Za-z]+"));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static String read(Path path)
   {
      try
      {
         return (Files.readString(path, StandardCharsets.UTF_8));
      }
      catch(Exception e)
      {
         throw (new IllegalStateException(e));
      }
   }
}
