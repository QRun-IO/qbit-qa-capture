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
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.joins.QJoinMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** The changelog on PostgreSQL (the production database), run by Liquibase
 ** as an ordinary application user (no superuser, no extensions):
 ** - every table possible-value field and join of the QBit has a database
 **   foreign key (or an exemption with a reason);
 ** - the built-in rules are seeded;
 ** - the foreign key changeset is a no-op on a database that already has the
 **   constraint.
 ** Skipped when Docker is not available.
 *******************************************************************************/
@Testcontainers(disabledWithoutDocker = true)
class QaCaptureForeignKeyTest
{
   //////////////////////////////////////////////////////////////////////////
   // references that are deliberately not foreign keys: none              //
   //////////////////////////////////////////////////////////////////////////
   private static final Map<String, String> EXEMPT = Map.of();

   @Container
   private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void everyReferenceHasAForeignKey() throws Exception
   {
      try(Connection admin = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
         Statement statement = admin.createStatement())
      {
         statement.execute("DROP SCHEMA public CASCADE");
         statement.execute("CREATE SCHEMA public");
         statement.execute("CREATE ROLE qa_app LOGIN PASSWORD 'qa_app' NOSUPERUSER NOCREATEDB NOCREATEROLE");
         statement.execute("GRANT USAGE, CREATE ON SCHEMA public TO qa_app");
      }

      try(Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "qa_app", "qa_app");
         Statement statement = connection.createStatement())
      {
         migrate(connection);

         Map<String, String> foreignKeys = foreignKeys(statement);
         List<String>        missing     = new ArrayList<>();
         for(String reference : references())
         {
            if(!foreignKeys.containsKey(reference) && !EXEMPT.containsKey(reference))
            {
               missing.add(reference);
            }
         }
         assertThat(missing).as("references without a foreign key (add one to the changelog, or an exemption with a reason)").isEmpty();
         assertThat(foreignKeys).containsExactlyEntriesOf(Map.of("qa_capture_audience.settings_id", "qa_capture_settings CASCADE"));
         assertThat(rows(statement, "SELECT CAST(COUNT(*) AS TEXT) FROM qa_capture_sanitizer_rule WHERE source = 'BUILT_IN'")).containsExactly("50");

         statement.execute("DELETE FROM databasechangelog WHERE id = 'qbit-qa-capture:6'");
         migrate(connection);
         assertThat(foreignKeys(statement)).isEqualTo(foreignKeys);
      }
   }



   /*******************************************************************************
    ** Run the whole changelog.
    *******************************************************************************/
   private static void migrate(Connection connection) throws Exception
   {
      Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
      Liquibase liquibase = new Liquibase(QaCaptureSchema.CHANGELOG, new ClassLoaderResourceAccessor(), database);
      liquibase.update("");
   }



   /*******************************************************************************
    ** "child.column" for every table possible-value field and join of the QBit.
    *******************************************************************************/
   private static List<String> references() throws Exception
   {
      QInstance qInstance = BaseQaCaptureTest.newInstance();
      new QaCaptureQBitProducer().withQBitConfig(new QaCaptureQBitConfig().withBackendName(BaseQaCaptureTest.BACKEND_NAME).withEnvironment("prod")).produce(qInstance);
      List<String> references = new ArrayList<>();
      for(String tableName : QaCaptureSchema.TABLES)
      {
         QTableMetaData table = qInstance.getTable(tableName);
         for(QFieldMetaData field : table.getFields().values())
         {
            if(field.getPossibleValueSourceName() != null && qInstance.getTable(field.getPossibleValueSourceName()) != null)
            {
               references.add(table.getName() + "." + field.getBackendName());
            }
         }
      }
      for(QJoinMetaData join : qInstance.getJoins().values())
      {
         if(join.getLeftTable().startsWith("qa_capture_"))
         {
            QTableMetaData right = qInstance.getTable(join.getRightTable());
            join.getJoinOns().forEach(on -> references.add(right.getName() + "." + right.getField(on.getRightField()).getBackendName()));
         }
      }
      assertThat(references).as("the QBit's references").isNotEmpty();
      return (references);
   }



   /*******************************************************************************
    ** "child.column" to "parent RULE" for every foreign key.
    *******************************************************************************/
   private static Map<String, String> foreignKeys(Statement statement) throws Exception
   {
      Map<String, String> foreignKeys = new HashMap<>();
      try(ResultSet resultSet = statement.executeQuery("""
         SELECT tc.table_name || '.' || kcu.column_name, ccu.table_name || ' ' || rc.delete_rule
         FROM information_schema.table_constraints tc
         JOIN information_schema.key_column_usage kcu ON kcu.constraint_name = tc.constraint_name
         JOIN information_schema.referential_constraints rc ON rc.constraint_name = tc.constraint_name
         JOIN information_schema.constraint_column_usage ccu ON ccu.constraint_name = tc.constraint_name
         WHERE tc.constraint_type = 'FOREIGN KEY'"""))
      {
         while(resultSet.next())
         {
            foreignKeys.put(resultSet.getString(1), resultSet.getString(2));
         }
      }
      return (foreignKeys);
   }



   /*******************************************************************************
    ** The first column of each row, as text.
    *******************************************************************************/
   private static List<String> rows(Statement statement, String sql) throws Exception
   {
      List<String> rows = new ArrayList<>();
      try(ResultSet resultSet = statement.executeQuery(sql))
      {
         while(resultSet.next())
         {
            rows.add(resultSet.getString(1));
         }
      }
      return (rows);
   }
}
