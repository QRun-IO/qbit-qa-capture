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


import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import com.kingsrook.qbits.qacapture.service.SanitizerRules;
import com.kingsrook.qbits.qacapture.web.SanitizerScript;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Rule validation, grouping, URL stripping, and the generated JavaScript
 ** sanitizers executed in Node (skipped when node is not installed).
 *******************************************************************************/
class SanitizerRulesTest
{
   /*******************************************************************************
    **
    *******************************************************************************/
   private static QRecord rule(String type, String value, boolean active)
   {
      return (new QRecord().withValue("id", 1).withValue("ruleType", type).withValue("value", value).withValue("active", active));
   }



   /*******************************************************************************
    ** What is valid.
    *******************************************************************************/
   @Test
   void validate()
   {
      assertThat(SanitizerRules.validate("VALUE_PATTERN", "eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+")).isNull();
      assertThat(SanitizerRules.validate("VALUE_PATTERN", "(?<word>abc)")).isNull();
      assertThat(SanitizerRules.validate("VALUE_PATTERN", "abc(?=def)")).isNull();
      assertThat(SanitizerRules.validate("VALUE_PATTERN", "a++")).contains("JavaScript");
      assertThat(SanitizerRules.validate("VALUE_PATTERN", "(?i)secret")).contains("JavaScript");
      assertThat(SanitizerRules.validate("VALUE_PATTERN", "\\Asecret")).contains("JavaScript");
      assertThat(SanitizerRules.validate("VALUE_PATTERN", "[a-z&&[^b]]")).contains("JavaScript");
      assertThat(SanitizerRules.validate("VALUE_PATTERN", "(unclosed")).contains("not a valid");
      assertThat(SanitizerRules.validate("VALUE_PATTERN", "x*")).contains("empty");
      assertThat(SanitizerRules.validate("DROP_PATH", "qqq/v1")).contains("starts with /");
      assertThat(SanitizerRules.validate("DROP_PATH", "/qqq/v1/secret")).isNull();
      assertThat(SanitizerRules.validate("HEADER", "x api key")).contains("header");
      assertThat(SanitizerRules.validate("BODY_FIELD", "anything goes")).isNull();
      assertThat(SanitizerRules.validate("BODY_FIELD", " ")).contains("required");
      assertThat(SanitizerRules.validate("NOPE", "x")).contains("Unknown");
      assertThat(SanitizerRules.validate(null, "x")).contains("Unknown");
   }



   /*******************************************************************************
    ** Grouping skips inactive, invalid and duplicate rows; headers lowercase.
    *******************************************************************************/
   @Test
   void groupingAndStripping()
   {
      SanitizerRules rules = SanitizerRules.fromRecords(List.of(
         rule("HEADER", "X-Api-Key", true), rule("HEADER", "x-api-key", true), rule("HEADER", "cookie", false),
         rule("BODY_FIELD", "password", true), rule("QUERY_PARAM", "code", true), rule("QUERY_PARAM", "Token", true),
         rule("VALUE_PATTERN", "a++", true), rule("VALUE_PATTERN", "secret-[0-9]+", true),
         rule("DROP_PATH", "/qqq/v1/vault", true), rule("BLUR_SELECTOR", ".ssn", true), rule("BODY_FIELD", null, true),
         new QRecord().withValue("ruleType", null).withValue("value", "x").withValue("active", true)));
      assertThat(rules.getHeaderNames()).containsExactly("x-api-key");
      assertThat(rules.getBodyFieldNames()).containsExactly("password");
      assertThat(rules.getQueryParamNames()).containsExactly("code", "Token");
      assertThat(rules.getValuePatterns()).containsExactly("secret-[0-9]+");
      assertThat(rules.getDropPathPrefixes()).containsExactly("/qqq/v1/vault");
      assertThat(rules.withExtraBlurSelectors(".ssn\n.card\r\n").getBlurSelectors()).containsExactly(".ssn", ".card");
      assertThat(rules.withExtraBlurSelectors(null).getBlurSelectors()).hasSize(2);

      assertThat(rules.stripQueryParams("https://a.example.com/p?code=1&keep=2&TOKEN=3&token%3D=4&&x#frag", 2000))
         .isEqualTo("https://a.example.com/p?keep=2&token%3D=4&x#frag");
      assertThat(rules.stripQueryParams("https://a.example.com/p?code=1", 2000)).isEqualTo("https://a.example.com/p");
      assertThat(rules.stripQueryParams("https://a.example.com/very/long", 10)).isEqualTo("https://a.");
      assertThat(rules.stripQueryParams(" ", 10)).isNull();
      assertThat(rules.stripQueryParams(null, 10)).isNull();
   }



   /*******************************************************************************
    ** The generated sanitizers, run in Node against Jam-shaped events.
    *******************************************************************************/
   @Test
   void javascriptSanitizers(@TempDir Path directory) throws Exception
   {
      String node = findNode();
      Assumptions.assumeTrue(node != null, "node is not installed");

      SanitizerRules rules = SanitizerRules.fromRecords(List.of(
         rule("HEADER", "authorization", true), rule("HEADER", "Set-Cookie", true), rule("BODY_FIELD", "password", true), rule("BODY_FIELD", "accessToken", true),
         rule("QUERY_PARAM", "code", true), rule("VALUE_PATTERN", "eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+", true),
         rule("VALUE_PATTERN", "[Bb]earer\\s+[A-Za-z0-9._~+/=-]+", true), rule("DROP_PATH", "/qqq/v1/vault", true)));
      String script = "globalThis.location = { href: 'https://admin.example.com/app/x', origin: 'https://admin.example.com' };\n"
         + "const s = " + SanitizerScript.expression(rules) + ";\n"
         + "const out = [];\n"
         + "const req = s.network.requestSanitizer({ type: 'fetch', url: '/qqq/v1/table?code=abc&x=1', method: 'POST',"
         + " headers: { authorization: 'Bearer abc', 'content-type': 'application/json', 'x-note': 'token eyJa.eyJb.sig' },"
         + " body: JSON.stringify({ user: { Password: 'p', name: 'n' }, list: [{ accessToken: 't' }], note: 'Bearer zzz' }) });\n"
         + "out.push(JSON.stringify(req));\n"
         + "out.push(JSON.stringify(s.network.requestSanitizer({ type: 'xhr', url: '/login', headers: {}, body: 'password=secret&user=u' })));\n"
         + "out.push(JSON.stringify(s.network.requestSanitizer({ type: 'fetch', url: '/qqq/v1/vault/1', headers: {}, body: '{\"a\":1}' })));\n"
         + "out.push(JSON.stringify(s.network.responseSanitizer({ url: 'https://admin.example.com/qqq/v1/vault/1', headers: { 'set-cookie': 'x' }, body: 'secret' })));\n"
         + "out.push(JSON.stringify(s.console.logSanitizer({ level: 'log', args: ['jwt eyJh.eyJi.sig', { password: 'p', ok: 1 }] })));\n"
         + "out.push(JSON.stringify(s.network.webSocketSanitizer({ action: 'send', url: 'wss://x', data: '{\"accessToken\":\"t\"}' })));\n"
         + "console.log(out.join('\\n'));\n";
      Path file = directory.resolve("sanitizers.js");
      Files.writeString(file, script, StandardCharsets.UTF_8);

      Process process = new ProcessBuilder(node, file.toString()).redirectErrorStream(true).start();
      assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
      String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      assertThat(process.exitValue()).as(output).isZero();
      List<String> lines = output.lines().toList();

      assertThat(lines.get(0)).contains("code=%5Bredacted%5D", "x=1").doesNotContain("authorization", "Bearer abc", "eyJa.eyJb.sig")
         .contains("\\\"Password\\\":\\\"[redacted]\\\"", "\\\"name\\\":\\\"n\\\"", "\\\"accessToken\\\":\\\"[redacted]\\\"", "\\\"note\\\":\\\"[redacted]\\\"");
      assertThat(lines.get(1)).contains("password=%5Bredacted%5D", "user=u");
      assertThat(lines.get(2)).doesNotContain("body");
      assertThat(lines.get(3)).doesNotContain("set-cookie", "secret");
      assertThat(lines.get(4)).contains("jwt [redacted]", "\"password\":\"[redacted]\"", "\"ok\":1");
      assertThat(lines.get(5)).contains("[redacted]").doesNotContain(":\\\"t\\\"");
   }



   /*******************************************************************************
    ** The node executable on the PATH, or null.
    *******************************************************************************/
   private static String findNode()
   {
      String path = System.getenv("PATH");
      if(path == null)
      {
         return (null);
      }
      for(String directory : path.split(File.pathSeparator))
      {
         for(String name : List.of("node", "node.exe"))
         {
            File candidate = new File(directory, name);
            if(candidate.isFile() && candidate.canExecute())
            {
               return (candidate.getAbsolutePath());
            }
         }
      }
      return (null);
   }
}
