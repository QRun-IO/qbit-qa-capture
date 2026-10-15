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


import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.data.QRecord;


/*******************************************************************************
 ** What a provider's webhook handler may use: its configured secret, the
 ** environment's settings row and the QA reports (read and written in the
 ** system context; field names are the qa_capture_report QQQ field names).
 *******************************************************************************/
public interface QaCaptureWebhookContext
{
   /*******************************************************************************
    ** The provider's webhook signing secret from the QBit config, or null when
    ** none is configured (the webhook then answers 404).
    *******************************************************************************/
   String getWebhookSecret();

   /*******************************************************************************
    ** The settings row of the configured environment, or null.
    *******************************************************************************/
   QRecord getSettings() throws QException;

   /*******************************************************************************
    ** The configured environment name.
    *******************************************************************************/
   String getEnvironment();

   /*******************************************************************************
    ** A URL with the active sanitizer rules' query parameters redacted (and
    ** capped in length), for storing page URLs a provider reports.
    *******************************************************************************/
   String sanitizeUrl(String url) throws QException;

   /*******************************************************************************
    ** The report with this reference, or null.
    *******************************************************************************/
   QRecord findReportByReference(String reference) throws QException;

   /*******************************************************************************
    ** The report holding this provider capture id (jamId), or null.
    *******************************************************************************/
   QRecord findReportByCaptureId(String captureId) throws QException;

   /*******************************************************************************
    ** Insert a report (reference, environment and status are filled when
    ** missing); returns the stored row.
    *******************************************************************************/
   QRecord insertReport(QRecord report) throws QException;

   /*******************************************************************************
    ** Update a report by id; returns the stored row.
    *******************************************************************************/
   QRecord updateReport(QRecord report) throws QException;
}
