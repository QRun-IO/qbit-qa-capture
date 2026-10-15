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


import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.regex.Pattern;
import com.kingsrook.qbits.qacapture.service.SanitizerRules;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** The shipped changelog through real Liquibase on H2 (PostgreSQL mode): it
 ** creates the four tables and seeds the 50 built-in rules (all valid, the
 ** value patterns valid in Java), a second update runs nothing, and the seed
 ** statement run again inserts nothing and keeps an administrator's edit.
 *******************************************************************************/
class QaCaptureChangelogTest
{
   private static final String URL = "jdbc:h2:mem:qa_capture_changelog;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE";



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void seedsAreInsertOnlyAndIdempotent() throws Exception
   {
      try(Connection connection = DriverManager.getConnection(URL, "sa", ""))
      {
         update();
         assertThat(count(connection, "SELECT COUNT(*) FROM qa_capture_sanitizer_rule WHERE source = 'BUILT_IN' AND active = TRUE")).isEqualTo(50);
         for(String table : QaCaptureSchema.TABLES)
         {
            assertThat(count(connection, "SELECT COUNT(*) FROM " + table)).as(table + " is queryable").isGreaterThanOrEqualTo(0);
         }
         assertThat(count(connection, "SELECT COUNT(*) FROM qa_capture_settings")).as("no host data is seeded").isZero();
         try(Statement statement = connection.createStatement(); ResultSet rules = statement.executeQuery("SELECT rule_type, value FROM qa_capture_sanitizer_rule"))
         {
            while(rules.next())
            {
               assertThat(SanitizerRules.validate(rules.getString(1), rules.getString(2))).as(rules.getString(2)).isNull();
               if("VALUE_PATTERN".equals(rules.getString(1)))
               {
                  assertThat(Pattern.compile(rules.getString(2)).matcher("Authorization: Bearer abc.def-123 eyJhbGciOi.eyJzdWIi.c2lnbmF0dXJl").find()).isTrue();
               }
            }
         }

         int changesets = count(connection, "SELECT COUNT(*) FROM databasechangelog");
         update();
         assertThat(count(connection, "SELECT COUNT(*) FROM databasechangelog")).isEqualTo(changesets);

         try(Statement statement = connection.createStatement())
         {
            statement.execute("UPDATE qa_capture_sanitizer_rule SET active = FALSE, description = 'admin edit' WHERE rule_type = 'QUERY_PARAM' AND value = 'state'");
            statement.execute(seedStatement());
         }
         assertThat(count(connection, "SELECT COUNT(*) FROM qa_capture_sanitizer_rule")).isEqualTo(50);
         assertThat(count(connection, "SELECT COUNT(*) FROM qa_capture_sanitizer_rule WHERE value = 'state' AND active = FALSE AND description = 'admin edit'")).isEqualTo(1);
      }
   }



   /*******************************************************************************
    ** The INSERT of changeset 5.
    *******************************************************************************/
   private static String seedStatement() throws Exception
   {
      String script;
      try(InputStream in = QaCaptureChangelogTest.class.getClassLoader().getResourceAsStream(QaCaptureSchema.CHANGELOG))
      {
         script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      }
      String seed = script.substring(script.indexOf("-- changeset qbit-qa-capture:5"), script.indexOf("-- changeset qbit-qa-capture:6"));
      return (seed.substring(seed.indexOf("INSERT INTO"), seed.indexOf("-- rollback")).trim().replaceAll(";$", ""));
   }



   /*******************************************************************************
    ** One real Liquibase update on its own connection.
    *******************************************************************************/
   private static void update() throws Exception
   {
      Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(DriverManager.getConnection(URL, "sa", "")));
      try(Liquibase liquibase = new Liquibase(QaCaptureSchema.CHANGELOG, new ClassLoaderResourceAccessor(), database))
      {
         liquibase.update("");
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static int count(Connection connection, String sql) throws Exception
   {
      try(Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql))
      {
         resultSet.next();
         return (resultSet.getInt(1));
      }
   }
}
