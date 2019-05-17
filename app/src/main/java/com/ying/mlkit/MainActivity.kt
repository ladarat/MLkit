package com.ying.mlkit

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.hardware.Camera
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.exifinterface.media.ExifInterface
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.text.FirebaseVisionText
import io.fotoapparat.Fotoapparat
import io.fotoapparat.preview.Frame
import io.fotoapparat.util.FrameProcessor
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jmrtd.lds.icao.MRZInfo
import java.io.IOException
import java.util.regex.Pattern


class MainActivity : AppCompatActivity(), CameraMLKItListener, VisionProcessorBase.OcrListener {

    override fun onMRZRead(mrzInfo: MRZInfo, timeRequired: Long) {
        mHandler.post {
            try {
                if (cameraMLKitFragmentListener != null) {
                    cameraMLKitFragmentListener!!.onPassportRead(mrzInfo)
                }

            } catch (e: IllegalStateException) {
                //The fragment is destroyed
            }
        }
    }

    override fun onMRZReadFailure(timeRequired: Long) {
        mHandler.post {
        }

        isDecoding = false
    }

    override fun onFailure(e: Exception, timeRequired: Long) {
        isDecoding = false
        e.printStackTrace()
        mHandler.post {
            if (cameraMLKitFragmentListener != null) {
                cameraMLKitFragmentListener!!.onError()
            }
        }
    }

    private val GALLERY_REQUEST_CODE = 1
    private var fotoapparat: Fotoapparat? = null

    private var mCamera: Camera? = null
    private var mPreview: CameraPreview? = null
    private var frameProcessor: OcrMrzDetectorProcessor? = null
    private var cameraMLKitFragmentListener = this
    private val mHandler = Handler(Looper.getMainLooper())
    private var isDecoding = false
    lateinit var uiScope: CoroutineScope
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        uiScope = CoroutineScope(Dispatchers.IO)

        frameProcessor = OcrMrzDetectorProcessor()
        val callbackFrameProcessor = object : FrameProcessor {
            override fun invoke(frame: Frame) {
                if (!isDecoding) {
                    isDecoding = true
                    getProccessScanRx(frame)
                }
            }

        }

        fotoapparat = Fotoapparat
            .with(this)
            .into(camera_view)
            .frameProcessor(
                callbackFrameProcessor
            )
            .build()

        galleryButton.setOnClickListener {
            pickFromGallery()
        }
    }


    private fun getProccessScanRx(frame: Frame) {
        uiScope.launch {
            frameProcessor?.process(frame, this@MainActivity)
        }
    }

    override fun onResume() {
        super.onResume()

        fotoapparat?.start()
    }

    override fun onDestroy() {
        frameProcessor!!.stop()
        super.onDestroy()
    }


    override fun onPause() {
        fotoapparat?.stop()

        super.onPause()
    }

    override fun onPassportRead(mrzInfo: MRZInfo) {
        val intent = Intent(this, SecondActivity::class.java)
        intent.putExtra("MRZInfo", mrzInfo)
        startActivity(intent)
//        documentText.text = mrzInfo.documentNumber
//        birthDate.text = mrzInfo.dateOfBirth
//        expiryDate.text = mrzInfo.dateOfExpiry
    }

    override fun onError() {

    }

    private fun getCameraInstance(): Camera? {
        return try {
            Camera.open() // attempt to get a Camera instance
        } catch (e: Exception) {
            // Camera is not available (in use or does not exist)
            null // returns null if camera is unavailable
        }
    }

    private fun checkCameraHardware(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA)
    }

    private fun pickFromGallery() {
        //Create an Intent with action as ACTION_PICK
        val intent = Intent(Intent.ACTION_PICK)
        // Sets the type as image/*. This ensures only components of type image are selected
        intent.type = "image/*"
        //We pass an extra array with the accepted mime types. This will ensure only components with these MIME types as targeted.
        val mimeTypes = arrayOf("image/jpeg", "image/png")
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
        // Launching the Intent
        startActivityForResult(intent, GALLERY_REQUEST_CODE)
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {

        // Result code is RESULT_OK only if the user selects an Image
        if (resultCode == AppCompatActivity.RESULT_OK)
            when (requestCode) {
                GALLERY_REQUEST_CODE -> {
                    //data.getData returns the content URI for the selected Image
                    val selectedImage = data!!.data
                    var bitMap = MediaStore.Images.Media.getBitmap(contentResolver, selectedImage)
                    val path = getRealPathFromURI(selectedImage)
//                    bitMap = modifyOrientation(bitMap,path)
                    analyzeImage(bitMap)
                    imageView.setImageBitmap(bitMap)
                }
            }

    }

    fun getRealPathFromURI(uri: Uri?): String {
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor!!.moveToFirst()
        val idx = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA)
        return cursor.getString(idx)
    }

    private fun analyzeImage(imageBitMap: Bitmap) {
        val firebaseVisionImage = FirebaseVisionImage.fromBitmap(imageBitMap)
        val textRecognizer = FirebaseVision.getInstance().onDeviceTextRecognizer
        textRecognizer.processImage(firebaseVisionImage)
            .addOnSuccessListener {
                clearText()
                val result = it
                abc(it)
                val mutableImage = imageBitMap.copy(Bitmap.Config.ARGB_8888, true)
                imageView.setImageBitmap(mutableImage)

                showText(it)

            }
            .addOnFailureListener {
                Toast.makeText(this, "There was some error", Toast.LENGTH_SHORT).show()
            }
    }

    private fun clearText() {
//        documentText.text = ""
//        birthDate.text = ""
//        expiryDate.text = ""
    }

    private fun showText(result: FirebaseVisionText) {
        for (block in result.textBlocks) {
            for (line in block.lines) {
                Log.d("HELLO", "Text = ${line.text}")
            }
        }
    }

    fun abc(results: FirebaseVisionText) {
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
            temp = temp.replace("\r".toRegex(), "").replace("\n".toRegex(), "")
                .replace("\t".toRegex(), "")
            fullRead += "$temp-"
        }
//        Log.d(TAG, "Read: $fullRead")
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

            Log.d("HELLO3", "documentNumber = $documentNumber")
            Log.d("HELLO3", "dateOfBirthDay = $dateOfBirthDay")
            Log.d("HELLO3", "expirationDate = $expirationDate")
//            documentText.text = documentNumber
//            birthDate.text = dateOfBirthDay
//            expiryDate.text = expirationDate

//            val mrzInfo = createDummyMrz(documentNumber, dateOfBirthDay, expirationDate)
//            ocrListener.onMRZRead(mrzInfo, timeRequired)
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

//                val mrzInfo = createDummyMrz(documentNumber, dateOfBirthDay, expirationDate)
//                ocrListener.onMRZRead(mrzInfo, timeRequired)
            } else {
                //No success
//                ocrListener.onMRZReadFailure(timeRequired)
            }
        }

    }

    @Throws(IOException::class)
    fun modifyOrientation(bitmap: Bitmap, image_absolute_path: String): Bitmap {
        val ei = ExifInterface(image_absolute_path)
        val orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)

        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> return rotate(bitmap, 90f)

            ExifInterface.ORIENTATION_ROTATE_180 -> return rotate(bitmap, 180f)

            ExifInterface.ORIENTATION_ROTATE_270 -> return rotate(bitmap, 270f)

            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> return flip(bitmap, true, false)

            ExifInterface.ORIENTATION_FLIP_VERTICAL -> return flip(bitmap, false, true)

            else -> return bitmap
        }
    }

    fun rotate(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun flip(bitmap: Bitmap, horizontal: Boolean, vertical: Boolean): Bitmap {
        val matrix = Matrix()
        matrix.preScale(if (horizontal) -1.0f else 1.0f, if (vertical) -1.0f else 1f)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    companion object {

        private val REGEX_OLD_PASSPORT =
            "[A-Z0-9<]{9}[0-9]{1}[A-Z<]{3}[0-9]{6}[0-9]{1}[FM<]{1}[0-9]{6}[0-9]{1}"
        private val REGEX_IP_PASSPORT_LINE_1 = "\\bIP[A-Z<]{3}[A-Z0-9<]{9}[0-9]{1}"
        private val REGEX_IP_PASSPORT_LINE_2 = "[0-9]{6}[0-9]{1}[FM<]{1}[0-9]{6}[0-9]{1}[A-Z<]{3}"
    }
}
