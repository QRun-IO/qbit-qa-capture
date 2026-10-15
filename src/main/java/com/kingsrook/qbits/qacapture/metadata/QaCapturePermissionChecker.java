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

package com.kingsrook.qbits.qacapture.metadata;


import java.util.Arrays;
import java.util.List;
import java.util.Map;
import com.kingsrook.qqq.backend.core.actions.permissions.AvailablePermission;
import com.kingsrook.qqq.backend.core.actions.permissions.CustomPermissionChecker;
import com.kingsrook.qqq.backend.core.actions.permissions.PermissionSubType;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QPermissionDeniedException;
import com.kingsrook.qqq.backend.core.model.actions.AbstractActionInput;
import com.kingsrook.qqq.backend.core.model.metadata.code.InitializableViaCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReferenceWithProperties;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.MetaDataWithName;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.MetaDataWithPermissionRules;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.PermissionLevel;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.QPermissionRules;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.utils.ValueUtils;


/*******************************************************************************
 ** Gates a QBit table or the app on the QBit's own permission names (any of
 ** the listed ones; fail closed) instead of QQQ's per-table names, and lists
 ** the first one among the instance's available permissions so hosts can
 ** grant it. A QQQ custom checker applies to every operation of the table.
 *******************************************************************************/
public class QaCapturePermissionChecker implements CustomPermissionChecker, InitializableViaCodeReference
{
   public static final String PERMISSIONS_PROPERTY = "permissionNames";

   private List<String> permissionNames = List.of();



   /*******************************************************************************
    ** Rules requiring any of the named permissions.
    *******************************************************************************/
   public static QPermissionRules rules(String... permissionNames)
   {
      return (new QPermissionRules()
         .withLevel(PermissionLevel.HAS_ACCESS_PERMISSION)
         .withCustomPermissionChecker(new QCodeReferenceWithProperties(QaCapturePermissionChecker.class,
            Map.of(PERMISSIONS_PROPERTY, String.join(",", permissionNames)))));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public void initialize(QCodeReference codeReference)
   {
      if(codeReference instanceof QCodeReferenceWithProperties withProperties)
      {
         String names = ValueUtils.getValueAsString(withProperties.getProperties().get(PERMISSIONS_PROPERTY));
         this.permissionNames = names == null ? List.of() : Arrays.stream(names.split(",")).map(String::trim).filter(name -> !name.isEmpty()).toList();
      }
   }



   /*******************************************************************************
    ** Allow when the session holds any of the permissions.
    *******************************************************************************/
   @Override
   public void checkPermissionsThrowing(AbstractActionInput actionInput, MetaDataWithPermissionRules metaDataWithPermissionRules) throws QPermissionDeniedException
   {
      QSession session = QContext.getQSession();
      if(session == null || permissionNames.stream().noneMatch(session::hasPermission))
      {
         throw (new QPermissionDeniedException("Permission denied."));
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public boolean handlesBuildAvailablePermission()
   {
      return (true);
   }



   /*******************************************************************************
    ** The first permission name (the one that governs the object).
    *******************************************************************************/
   @Override
   public AvailablePermission buildAvailablePermission(QPermissionRules rules, PermissionSubType permissionSubType, String baseName, MetaDataWithName metaDataWithName, String objectType)
   {
      AvailablePermission permission = buildBaseAvailablePermission(rules, permissionSubType, metaDataWithName, objectType);
      return (permission == null || permissionNames.isEmpty() ? null : permission.withName(permissionNames.get(0)));
   }
}
