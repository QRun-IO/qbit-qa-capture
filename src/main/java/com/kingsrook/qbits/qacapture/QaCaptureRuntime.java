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


import java.time.Clock;
import java.util.concurrent.atomic.AtomicReference;
import com.kingsrook.qqq.backend.core.context.CapturedContext;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.QSupplementalInstanceMetaData;
import com.kingsrook.qqq.backend.core.model.session.QSystemUserSession;


/*******************************************************************************
 ** The QBit's state on a QInstance (supplemental meta-data "qaCapture"): its
 ** config and clock. It also remembers the most recent instance the QBit was
 ** produced into or served from, for host hooks that are not handed one (the
 ** Next dashboard security headers customizer).
 *******************************************************************************/
public class QaCaptureRuntime implements QSupplementalInstanceMetaData
{
   public static final String NAME = "qaCapture";

   private static final AtomicReference<QInstance> CURRENT = new AtomicReference<>();

   private final QaCaptureQBitConfig config;
   private       Clock               clock = Clock.systemUTC();



   /*******************************************************************************
    ** A unit of work run in the system context.
    *******************************************************************************/
   @FunctionalInterface
   public interface SystemWork<R>
   {
      /*******************************************************************************
       ** Do the work.
       *******************************************************************************/
      R run() throws QException;
   }



   /*******************************************************************************
    ** Constructor.
    *******************************************************************************/
   public QaCaptureRuntime(QaCaptureQBitConfig config)
   {
      this.config = config;
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public String getName()
   {
      return (NAME);
   }



   /*******************************************************************************
    ** The runtime on an instance, or null when the QBit is not produced there.
    *******************************************************************************/
   public static QaCaptureRuntime find(QInstance qInstance)
   {
      return (qInstance == null ? null : QSupplementalInstanceMetaData.of(qInstance, NAME));
   }



   /*******************************************************************************
    ** The runtime on an instance.
    **
    ** @throws IllegalStateException when the QBit is not produced there
    *******************************************************************************/
   public static QaCaptureRuntime of(QInstance qInstance)
   {
      QaCaptureRuntime runtime = find(qInstance);
      if(runtime == null)
      {
         throw (new IllegalStateException("qbit-qa-capture is not registered on this QInstance"));
      }
      return (runtime);
   }



   /*******************************************************************************
    ** The runtime on the current context's instance.
    *******************************************************************************/
   public static QaCaptureRuntime current()
   {
      return (of(QContext.getQInstance()));
   }



   /*******************************************************************************
    ** Remember the instance the QBit lives in now (production, server start and
    ** hot swaps).
    *******************************************************************************/
   public static void setCurrentInstance(QInstance qInstance)
   {
      if(find(qInstance) != null)
      {
         CURRENT.set(qInstance);
      }
   }



   /*******************************************************************************
    ** The most recent instance the QBit lives in, or null.
    *******************************************************************************/
   public static QInstance getCurrentInstance()
   {
      return (CURRENT.get());
   }



   /*******************************************************************************
    ** Run work in the system context of an instance, restoring the caller's
    ** context afterwards.
    *******************************************************************************/
   public static <R> R asSystem(QInstance qInstance, SystemWork<R> work) throws QException
   {
      Object[] result = new Object[1];
      QContext.withTemporaryContext(new CapturedContext(qInstance, new QSystemUserSession(qInstance)), () ->
      {
         result[0] = work.run();
      });
      @SuppressWarnings("unchecked")
      R typed = (R) result[0];
      return (typed);
   }



   /*******************************************************************************
    ** Getter for config
    *******************************************************************************/
   public QaCaptureQBitConfig getConfig()
   {
      return (this.config);
   }



   /*******************************************************************************
    ** Getter for clock
    *******************************************************************************/
   public Clock getClock()
   {
      return (this.clock);
   }



   /*******************************************************************************
    ** Setter for clock (tests).
    *******************************************************************************/
   public void setClock(Clock clock)
   {
      this.clock = clock == null ? Clock.systemUTC() : clock;
   }
}
