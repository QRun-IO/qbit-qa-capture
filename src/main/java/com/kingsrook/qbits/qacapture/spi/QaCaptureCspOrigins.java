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

package com.kingsrook.qbits.qacapture.spi;


import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


/*******************************************************************************
 ** Content-Security-Policy origins a provider needs, per directive.
 **
 ** @param scriptSrc  script-src sources
 ** @param frameSrc   frame-src sources
 ** @param connectSrc connect-src sources
 *******************************************************************************/
public record QaCaptureCspOrigins(List<String> scriptSrc, List<String> frameSrc, List<String> connectSrc)
{
   /*******************************************************************************
    ** Immutable copies (null becomes empty).
    *******************************************************************************/
   public QaCaptureCspOrigins
   {
      scriptSrc = scriptSrc == null ? List.of() : List.copyOf(scriptSrc);
      frameSrc = frameSrc == null ? List.of() : List.copyOf(frameSrc);
      connectSrc = connectSrc == null ? List.of() : List.copyOf(connectSrc);
   }



   /*******************************************************************************
    ** No origins.
    *******************************************************************************/
   public static QaCaptureCspOrigins none()
   {
      return (new QaCaptureCspOrigins(List.of(), List.of(), List.of()));
   }



   /*******************************************************************************
    ** The contract's cspOrigins object (scriptSrc, frameSrc, connectSrc).
    *******************************************************************************/
   public Map<String, Object> toMap()
   {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("scriptSrc", scriptSrc);
      map.put("frameSrc", frameSrc);
      map.put("connectSrc", connectSrc);
      return (map);
   }
}
