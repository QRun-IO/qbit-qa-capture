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


import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import com.kingsrook.qbits.qacapture.spi.JamProvider;
import com.kingsrook.qbits.qacapture.spi.QaCaptureProvider;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitConfig;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.utils.StringUtils;


/*******************************************************************************
 ** Host configuration of the QA capture QBit.
 **
 ** Required: the backend holding the qa_capture_ tables and the environment
 ** name (it selects the qa_capture_settings row, for example "prod"). The
 ** host supplies its build version and node name (for example from its
 ** manifest and the HOSTNAME variable) and the provider webhook secrets from
 ** its environment; secrets are never stored in tables or logged.
 **
 ** Placement: the QBit adds one app (appName, default "qaCapture") holding
 ** its four tables. Set parentAppName to the host's Admin (system tooling) app
 ** to nest it there; otherwise the host places the app itself.
 *******************************************************************************/
public class QaCaptureQBitConfig implements QBitConfig
{
   public static final String DEFAULT_APP_NAME = "qaCapture";
   public static final String HOSTNAME_ENV     = "HOSTNAME";

   private static final Pattern NAME        = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");
   private static final Pattern ENVIRONMENT = Pattern.compile("[A-Za-z0-9._-]{1,50}");
   private static final Pattern PROVIDER    = Pattern.compile("[a-z0-9][a-z0-9-]{0,49}");

   private String  backendName;
   private String  environment;
   private String  backendVersion;
   private String  node;
   private String  appName        = DEFAULT_APP_NAME;
   private String  appLabel       = "QA Capture";
   private String  parentAppName;
   private Boolean registerRoutes = true;
   private Boolean syncSecuredDataRules = true;

   ///////////////////////////////////////////////////////////////////////////
   // code and secrets: never serialized with the instance's metadata       //
   ///////////////////////////////////////////////////////////////////////////
   private transient Map<String, QaCaptureProvider>             providers;
   private transient Map<String, String>                        webhookSecrets;
   private transient Function<QSession, Collection<String>>     roleResolver;



   /*******************************************************************************
    ** Validate the config against the instance it is produced into.
    *******************************************************************************/
   @Override
   public void validate(QInstance qInstance, List<String> errors)
   {
      assertCondition(StringUtils.hasContent(backendName), "backendName is required", errors);
      assertCondition(!StringUtils.hasContent(backendName) || qInstance.getBackend(backendName) != null, "backend " + backendName + " is not defined", errors);
      assertCondition(environment != null && ENVIRONMENT.matcher(environment).matches(), "environment is required (letters, digits, '.', '_' or '-', at most 50; got " + environment + ")", errors);
      assertCondition(appName != null && NAME.matcher(appName).matches(), "appName must be alphanumeric (got " + appName + ")", errors);
      assertCondition(parentAppName == null || qInstance.getApp(parentAppName) != null, "parent app " + parentAppName + " is not defined (define it before producing the QBit)", errors);
      assertCondition(!getProviders().isEmpty(), "at least one provider is required", errors);
      for(String name : getProviders().keySet())
      {
         assertCondition(PROVIDER.matcher(name).matches(), "provider name " + name + " must be lowercase letters, digits or '-'", errors);
      }
   }



   /*******************************************************************************
    ** The registered providers by name (Jam unless the host replaced them).
    *******************************************************************************/
   public Map<String, QaCaptureProvider> getProviders()
   {
      if(providers == null)
      {
         providers = new LinkedHashMap<>();
         providers.put(JamProvider.NAME, new JamProvider());
      }
      return (providers);
   }



   /*******************************************************************************
    ** A provider by name, or null.
    *******************************************************************************/
   public QaCaptureProvider getProvider(String name)
   {
      return (name == null ? null : getProviders().get(name));
   }



   /*******************************************************************************
    ** Register (or replace) a provider.
    *******************************************************************************/
   public QaCaptureQBitConfig withProvider(QaCaptureProvider provider)
   {
      getProviders().put(provider.getName(), provider);
      return (this);
   }



   /*******************************************************************************
    ** Replace the whole provider registry (for example to drop Jam).
    *******************************************************************************/
   public QaCaptureQBitConfig withProviders(List<QaCaptureProvider> providers)
   {
      this.providers = new LinkedHashMap<>();
      if(providers != null)
      {
         providers.forEach(provider -> this.providers.put(provider.getName(), provider));
      }
      return (this);
   }



   /*******************************************************************************
    ** Set a provider's webhook signing secret (read by the host from its
    ** environment). Without one, the provider's webhook answers 404.
    *******************************************************************************/
   public QaCaptureQBitConfig withWebhookSecret(String providerName, String secret)
   {
      if(webhookSecrets == null)
      {
         webhookSecrets = new LinkedHashMap<>();
      }
      if(StringUtils.hasContent(secret))
      {
         webhookSecrets.put(providerName, secret.trim());
      }
      else
      {
         webhookSecrets.remove(providerName);
      }
      return (this);
   }



   /*******************************************************************************
    ** Set Jam's webhook signing secret ("whsec_...", from the Jam webhook).
    *******************************************************************************/
   public QaCaptureQBitConfig withJamWebhookSecret(String secret)
   {
      return (withWebhookSecret(JamProvider.NAME, secret));
   }



   /*******************************************************************************
    ** A provider's webhook secret, or null.
    *******************************************************************************/
   public String getWebhookSecret(String providerName)
   {
      return (webhookSecrets == null ? null : webhookSecrets.get(providerName));
   }



   /*******************************************************************************
    ** The names a user's ROLE audience rows match: the host's resolver (for
    ** example the user's identity-provider groups), else the session's
    ** permission names. QQQ 4.1 sessions have no role list of their own.
    *******************************************************************************/
   public Collection<String> resolveRoles(QSession session)
   {
      if(session == null)
      {
         return (List.of());
      }
      if(roleResolver != null)
      {
         Collection<String> roles = roleResolver.apply(session);
         return (roles == null ? List.of() : roles);
      }
      return (session.getPermissions() == null ? List.of() : new ArrayList<>(session.getPermissions()));
   }



   /*******************************************************************************
    ** Fluent setter for roleResolver: the role (group) names of a session's
    ** user, matched case-insensitively by ROLE audience rows.
    *******************************************************************************/
   public QaCaptureQBitConfig withRoleResolver(Function<QSession, Collection<String>> roleResolver)
   {
      this.roleResolver = roleResolver;
      return (this);
   }



   /*******************************************************************************
    ** The node name: the configured one, else the HOSTNAME environment variable.
    *******************************************************************************/
   public String getEffectiveNode()
   {
      return (StringUtils.hasContent(node) ? node : System.getenv(HOSTNAME_ENV));
   }



   /*******************************************************************************
    ** Secrets and code are not printed.
    *******************************************************************************/
   @Override
   public String toString()
   {
      return ("QaCaptureQBitConfig{backendName=" + backendName + ", environment=" + environment + ", backendVersion=" + backendVersion
         + ", appName=" + appName + ", parentAppName=" + parentAppName + ", registerRoutes=" + registerRoutes
         + ", providers=" + getProviders().keySet() + ", webhookSecrets=" + (webhookSecrets == null ? "[]" : webhookSecrets.keySet() + " <redacted>") + "}");
   }



   /*******************************************************************************
    ** Getter for backendName
    *******************************************************************************/
   public String getBackendName()
   {
      return (this.backendName);
   }



   /*******************************************************************************
    ** Setter for backendName
    *******************************************************************************/
   public void setBackendName(String backendName)
   {
      this.backendName = backendName;
   }



   /*******************************************************************************
    ** Fluent setter for backendName
    *******************************************************************************/
   public QaCaptureQBitConfig withBackendName(String backendName)
   {
      this.backendName = backendName;
      return (this);
   }



   /*******************************************************************************
    ** The backend for the QBit's tables.
    *******************************************************************************/
   @Override
   public String getDefaultBackendNameForTables()
   {
      return (backendName);
   }



   /*******************************************************************************
    ** Getter for environment
    *******************************************************************************/
   public String getEnvironment()
   {
      return (this.environment);
   }



   /*******************************************************************************
    ** Setter for environment
    *******************************************************************************/
   public void setEnvironment(String environment)
   {
      this.environment = environment;
   }



   /*******************************************************************************
    ** Fluent setter for environment (selects the qa_capture_settings row).
    *******************************************************************************/
   public QaCaptureQBitConfig withEnvironment(String environment)
   {
      this.environment = environment;
      return (this);
   }



   /*******************************************************************************
    ** Getter for backendVersion
    *******************************************************************************/
   public String getBackendVersion()
   {
      return (this.backendVersion);
   }



   /*******************************************************************************
    ** Setter for backendVersion
    *******************************************************************************/
   public void setBackendVersion(String backendVersion)
   {
      this.backendVersion = backendVersion;
   }



   /*******************************************************************************
    ** Fluent setter for backendVersion (the host's build version).
    *******************************************************************************/
   public QaCaptureQBitConfig withBackendVersion(String backendVersion)
   {
      this.backendVersion = backendVersion;
      return (this);
   }



   /*******************************************************************************
    ** Getter for node
    *******************************************************************************/
   public String getNode()
   {
      return (this.node);
   }



   /*******************************************************************************
    ** Setter for node
    *******************************************************************************/
   public void setNode(String node)
   {
      this.node = node;
   }



   /*******************************************************************************
    ** Fluent setter for node (defaults to the HOSTNAME environment variable).
    *******************************************************************************/
   public QaCaptureQBitConfig withNode(String node)
   {
      this.node = node;
      return (this);
   }



   /*******************************************************************************
    ** Getter for appName
    *******************************************************************************/
   public String getAppName()
   {
      return (this.appName);
   }



   /*******************************************************************************
    ** Setter for appName
    *******************************************************************************/
   public void setAppName(String appName)
   {
      this.appName = appName;
   }



   /*******************************************************************************
    ** Fluent setter for appName
    *******************************************************************************/
   public QaCaptureQBitConfig withAppName(String appName)
   {
      this.appName = appName;
      return (this);
   }



   /*******************************************************************************
    ** Getter for appLabel
    *******************************************************************************/
   public String getAppLabel()
   {
      return (this.appLabel);
   }



   /*******************************************************************************
    ** Setter for appLabel
    *******************************************************************************/
   public void setAppLabel(String appLabel)
   {
      this.appLabel = appLabel;
   }



   /*******************************************************************************
    ** Fluent setter for appLabel
    *******************************************************************************/
   public QaCaptureQBitConfig withAppLabel(String appLabel)
   {
      this.appLabel = appLabel;
      return (this);
   }



   /*******************************************************************************
    ** Getter for parentAppName
    *******************************************************************************/
   public String getParentAppName()
   {
      return (this.parentAppName);
   }



   /*******************************************************************************
    ** Setter for parentAppName
    *******************************************************************************/
   public void setParentAppName(String parentAppName)
   {
      this.parentAppName = parentAppName;
   }



   /*******************************************************************************
    ** Fluent setter for parentAppName (the host's Admin app; null to place the
    ** app yourself).
    *******************************************************************************/
   public QaCaptureQBitConfig withParentAppName(String parentAppName)
   {
      this.parentAppName = parentAppName;
      return (this);
   }



   /*******************************************************************************
    ** Getter for registerRoutes
    *******************************************************************************/
   public Boolean getRegisterRoutes()
   {
      return (this.registerRoutes);
   }



   /*******************************************************************************
    ** Setter for registerRoutes
    *******************************************************************************/
   public void setRegisterRoutes(Boolean registerRoutes)
   {
      this.registerRoutes = registerRoutes;
   }



   /*******************************************************************************
    ** Fluent setter for registerRoutes: false skips the UI API extension and the
    ** Javalin route provider (request ids, recorder page, webhooks).
    *******************************************************************************/
   public QaCaptureQBitConfig withRegisterRoutes(Boolean registerRoutes)
   {
      this.registerRoutes = registerRoutes;
      return (this);
   }



   /*******************************************************************************
    ** Getter for syncSecuredDataRules
    *******************************************************************************/
   public Boolean getSyncSecuredDataRules()
   {
      return (this.syncSecuredDataRules);
   }



   /*******************************************************************************
    ** Setter for syncSecuredDataRules
    *******************************************************************************/
   public void setSyncSecuredDataRules(Boolean syncSecuredDataRules)
   {
      this.syncSecuredDataRules = syncSecuredDataRules;
   }



   /*******************************************************************************
    ** Fluent setter for syncSecuredDataRules: whether startup adds BODY_FIELD
    ** rules for fields the instance marks secure (default true).
    *******************************************************************************/
   public QaCaptureQBitConfig withSyncSecuredDataRules(Boolean syncSecuredDataRules)
   {
      this.syncSecuredDataRules = syncSecuredDataRules;
      return (this);
   }
}
