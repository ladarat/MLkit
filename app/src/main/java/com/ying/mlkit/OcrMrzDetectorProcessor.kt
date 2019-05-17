/*
 * Copyright (C) The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ying.mlkit

import android.util.Log

import com.google.android.gms.tasks.Task
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.text.FirebaseVisionText
import com.google.firebase.ml.vision.text.FirebaseVisionTextRecognizer

import net.sf.scuba.data.Gender


import org.jmrtd.lds.icao.MRZInfo

import java.io.IOException
import java.util.regex.Matcher
import java.util.regex.Pattern


/**
 * A very simple Processor which receives detected TextBlocks and adds them to the overlay
 * as OcrGraphics.
 */
class OcrMrzDetectorProcessor : VisionProcessorBase<FirebaseVisionText>() {

    private val detector: FirebaseVisionTextRecognizer

    init {
        detector = FirebaseVision.getInstance().onDeviceTextRecognizer
    }


    override fun stop() {
        try {
            detector.close()
        } catch (e: IOException) {
            Log.e(TAG, "Exception thrown while trying to close Text Detector: $e")
        }

    }

    override fun detectInImage(image: FirebaseVisionImage): Task<FirebaseVisionText> {
        return detector.processImage(image)
    }


    override fun onSuccess(
            results: FirebaseVisionText,
            frameMetadata: FrameMetadata?,
            timeRequired: Long,
            ocrListener: VisionProcessorBase.OcrListener) {

        var fullRead = ""
        val blocks = results.textBlocks
        for (i in blocks.indices) {
            var temp = ""
            val lines = blocks[i].lines
            for (j in lines.indices) {
                //extract scanned text lines here
                //temp+=lines.get(j).getText().trim()+"-";
                temp += lines[j].text + "-"
            }
            temp = temp.replace("\r".toRegex(), "").replace("\n".toRegex(), "").replace("\t".toRegex(), "")
            fullRead += "$temp-"
        }
        Log.d(TAG, "Read: $fullRead")
        val patternLineOldPassportType = Pattern.compile(REGEX_OLD_PASSPORT)
        val matcherLineOldPassportType = patternLineOldPassportType.matcher(fullRead)



        if (matcherLineOldPassportType.find()) {
            //Old passport format
            val line2 = matcherLineOldPassportType.group(0)
            var documentNumber = line2.substring(0, 9)
            val dateOfBirthDay = line2.substring(13, 19)
            val expirationDate = line2.substring(21, 27)

            //As O and 0 and really similar most of the countries just removed them from the passport, so for accuracy I am formatting it
            documentNumber = documentNumber.replace("O".toRegex(), "0")


            val mrzInfo = createDummyMrz(documentNumber, dateOfBirthDay, expirationDate)
            ocrListener.onMRZRead(mrzInfo, timeRequired)
        } else {
            //Try with the new IP passport type
            val patternLineIPassportTypeLine1 = Pattern.compile(REGEX_IP_PASSPORT_LINE_1)
            val matcherLineIPassportTypeLine1 = patternLineIPassportTypeLine1.matcher(fullRead)
            val patternLineIPassportTypeLine2 = Pattern.compile(REGEX_IP_PASSPORT_LINE_2)
            val matcherLineIPassportTypeLine2 = patternLineIPassportTypeLine2.matcher(fullRead)
            if (matcherLineIPassportTypeLine1.find() && matcherLineIPassportTypeLine2.find()) {
                val line1 = matcherLineIPassportTypeLine1.group(0)
                val line2 = matcherLineIPassportTypeLine2.group(0)
                var documentNumber = line1.substring(5, 14)
                val dateOfBirthDay = line2.substring(0, 6)
                val expirationDate = line2.substring(8, 14)

                //As O and 0 and really similar most of the countries just removed them from the passport, so for accuracy I am formatting it
                documentNumber = documentNumber.replace("O".toRegex(), "0")

                val mrzInfo = createDummyMrz(documentNumber, dateOfBirthDay, expirationDate)
                ocrListener.onMRZRead(mrzInfo, timeRequired)
            } else {
                //No success
                ocrListener.onMRZReadFailure(timeRequired)
            }
        }


    }


    protected fun createDummyMrz(documentNumber: String, dateOfBirthDay: String, expirationDate: String): MRZInfo {
        return MRZInfo(
                "P",
                "ESP",
                "DUMMY",
                "DUMMY",
                documentNumber,
                "ESP",
                dateOfBirthDay,
                Gender.MALE,
                expirationDate,
                ""
        )
    }

    override fun onFailure(e: Exception, timeRequired: Long, ocrListener: VisionProcessorBase.OcrListener) {
        Log.w(TAG, "Text detection failed.$e")
        ocrListener.onFailure(e, timeRequired)
    }

    companion object {

        private val TAG = OcrMrzDetectorProcessor::class.java.simpleName

        private val REGEX_OLD_PASSPORT = "[A-Z0-9<]{9}[0-9]{1}[A-Z<]{3}[0-9]{6}[0-9]{1}[FM<]{1}[0-9]{6}[0-9]{1}"
        private val REGEX_IP_PASSPORT_LINE_1 = "\\bIP[A-Z<]{3}[A-Z0-9<]{9}[0-9]{1}"
        private val REGEX_IP_PASSPORT_LINE_2 = "[0-9]{6}[0-9]{1}[FM<]{1}[0-9]{6}[0-9]{1}[A-Z<]{3}"
    }
}
