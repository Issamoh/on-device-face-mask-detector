package com.issamoh.facemaskdetector

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.Bitmap.createBitmap
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.common.math.Quantiles.scale
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.issamoh.facemaskdetector.ml.MaskDetector
import org.tensorflow.lite.support.image.TensorImage
import java.io.*
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.Timer
import kotlin.concurrent.schedule
import kotlin.collections.AbstractCollection


class MainActivity : AppCompatActivity() {
    private lateinit var b2:Bitmap
    private lateinit var b1:Bitmap
    private lateinit var maskDetector: MaskDetector
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var graphicOverlay: GraphicOverlay
    private lateinit var viewFinder: PreviewView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //Request Camera persmissions :
        if(allPermissionsGranted()){
            startCamera()
        }else{
            ActivityCompat.requestPermissions(
                this,REQUIRED_PERMISSIONS,REQUEST_CODE_PERMISSIONS
            )
        }
        cameraExecutor = Executors.newSingleThreadExecutor()

        //an overlay in top of the camera preview in order to draw on it boxes and labels
        graphicOverlay = findViewById(R.id.graphicOverlay)
        viewFinder = findViewById(R.id.viewFinder)

         maskDetector = MaskDetector.newInstance(this)

        var istr1: InputStream? = null
        var istr2: InputStream? = null
        try {
            istr1 = application.assets.open("1.png")
            istr2 = application.assets.open("2.png")
        } catch (e: IOException) {
            e.printStackTrace()
        }
         b1 = BitmapFactory.decodeStream(istr1)
         b2 = BitmapFactory.decodeStream(istr2)

    }


    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(Runnable {
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }
            //ImageAnalyzer
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)// in order to only one image will be delivered for analysis at a time(from docs)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, FaceAnalyzer(graphicOverlay, lensFacing,maskDetector,b1,b2,this,viewFinder))
                }

            // Select back camera as a default
            val cameraSelector = lensFacing

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview,imageAnalyzer)

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraXBasic"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private var lensFacing = CameraSelector.DEFAULT_BACK_CAMERA

    }
}
private class FaceAnalyzer(
    val graphicOverlay: GraphicOverlay,
    val lensFacing: CameraSelector,
    val maskDetector: MaskDetector,
    val b1: Bitmap,
    val b2: Bitmap,
    val context: Context,
    val previewView:PreviewView
):ImageAnalysis.Analyzer{
    private val detector: FaceDetector
    val overlay = graphicOverlay
    lateinit var correctionMatrix: Matrix
   init{
       val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .build()
     detector = FaceDetection.getClient(options)
   }
    @RequiresApi(Build.VERSION_CODES.N)
    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageP: ImageProxy) {
        correctionMatrix = getCorrectionMatrix(imageP,previewView)
        val mediaImage = imageP.getImage()
        if (mediaImage != null) {
            val isImageFlipped = lensFacing.equals(CameraSelector.LENS_FACING_FRONT)
            val rotationDegrees = imageP.imageInfo.rotationDegrees
            overlay.clear()
            val image = InputImage.fromMediaImage(mediaImage, imageP.imageInfo.rotationDegrees)
            var flip =  Matrix()
            if (rotationDegrees == 0 || rotationDegrees == 180) {
                overlay.setImageSourceInfo(
                    imageP.width, imageP.height, isImageFlipped
                )
               // flip.postScale(1F, -1F, previewView.width / 2.0f, previewView.height / 2.0f)
            } else {
                overlay.setImageSourceInfo(
                    imageP.height, imageP.width, isImageFlipped
                )
               //flip.postScale(-1F, 1F, previewView.width / 2.0f, previewView.height / 2.0f)
            }


            val bitmapImage = imageP.toBitmap()
            var done = false
            val result = detector.process(image)
                .addOnSuccessListener { faces ->
                    for (face in faces) {
                        Log.d("Face mask detector", "one face detected")

                       val bounding = face.boundingBox
                        val faceBmp = cropBitmap(bitmapImage, bounding)
                        val correctedBmp = Bitmap.createBitmap(faceBmp, 0, 0, faceBmp.getWidth(), faceBmp.getHeight(), correctionMatrix, true);
                         val tfImage = TensorImage.fromBitmap(correctedBmp)
                       // val tfImage = TensorImage.fromBitmap(b2) was for test purposes
                        val output = maskDetector.process(tfImage)
                        .probabilityAsCategoryList.apply {
                                    sortByDescending { it.score } // Sort with highest confidence first
                                }.take(3).get(0) // take the top results*/
                        //           Log.d("Face mask detector", outputs.get(0).label+" "+outputs.get(0).score)
                        Log.d("Face mask detector", output.toString())
                        if(output.score >0.5){
                        if(output.label.equals("WithMask")){
                            overlay.add(FaceGraphic(overlay,face,"with mask",9))
                        }
                        else{
                            overlay.add(FaceGraphic(overlay,face,"without mask",3))
                        }
                        }
                    }
                    overlay.postInvalidate()
                    Timer("SettingUp", false).schedule(600) {
                        imageP.close()
                    }
                }
                .addOnFailureListener { e ->
                    imageP.close()

                }
        }

    }
    private fun ImageProxy.toBitmap(): Bitmap {
        val yBuffer = planes[0].buffer // Y
        val uBuffer = planes[1].buffer // U
        val vBuffer = planes[2].buffer // V

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, this.width, this.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 100, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }
    private fun cropBitmap(bitmap :Bitmap, rect:Rect):Bitmap {
        val w = rect.right - rect.left
        val h = rect.bottom - rect.top;
        val ret = createBitmap(w.toInt(), h.toInt(), bitmap.getConfig())
        val canvas = Canvas(ret)
        val left = (-rect.left).toFloat()
        val top = -rect.top.toFloat()
        canvas.drawBitmap(bitmap, left , top , null);
        return ret
}
    fun getResizedBitmap(image: Bitmap?, bitmapWidth: Int, bitmapHeight: Int): Bitmap? {
        return Bitmap.createScaledBitmap(image!!, bitmapWidth, bitmapHeight, true)
    }

    fun getCorrectionMatrix(imageProxy: ImageProxy, previewView: PreviewView) : Matrix {
        val cropRect = imageProxy.cropRect
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val matrix = Matrix()

        // A float array of the source vertices (crop rect) in clockwise order.
        val source = floatArrayOf(
            cropRect.left.toFloat(),
            cropRect.top.toFloat(),
            cropRect.right.toFloat(),
            cropRect.top.toFloat(),
            cropRect.right.toFloat(),
            cropRect.bottom.toFloat(),
            cropRect.left.toFloat(),
            cropRect.bottom.toFloat()
        )

        // A float array of the destination vertices in clockwise order.
        val destination = floatArrayOf(
            0f,
            0f,
            previewView.width.toFloat(),
            0f,
            previewView.width.toFloat(),
            previewView.height.toFloat(),
            0f,
            previewView.height.toFloat()
        )

        // The destination vertexes need to be shifted based on rotation degrees. The
        // rotation degree represents the clockwise rotation needed to correct the image.

        // Each vertex is represented by 2 float numbers in the vertices array.
        val vertexSize = 2
        // The destination needs to be shifted 1 vertex for every 90° rotation.
        val shiftOffset = rotationDegrees / 90 * vertexSize;
        val tempArray = destination.clone()
        for (toIndex in source.indices) {
            val fromIndex = (toIndex + shiftOffset) % source.size
            destination[toIndex] = tempArray[fromIndex]
        }
        matrix.setPolyToPoly(source, 0, destination, 0, 4)
        return matrix
    }

}