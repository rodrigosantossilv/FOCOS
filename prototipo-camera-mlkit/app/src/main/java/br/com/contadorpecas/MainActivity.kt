package br.com.contadorpecas

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : ComponentActivity() {
 private val worker = Executors.newSingleThreadExecutor()
 private val busy = AtomicBoolean(false)
 private lateinit var preview: PreviewView
 private lateinit var overlay: DetectionOverlay
 private lateinit var count: TextView
 private lateinit var status: TextView
 private val detector = ObjectDetection.getClient(ObjectDetectorOptions.Builder()
   .setDetectorMode(ObjectDetectorOptions.STREAM_MODE).enableMultipleObjects().build())
 private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
   if (granted) startCamera() else status.text = "Permissão da câmera negada"
 }
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val root = FrameLayout(this)
  preview = PreviewView(this).apply { scaleType = PreviewView.ScaleType.FIT_CENTER }
  overlay = DetectionOverlay(this)
  val header = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28,24,28,24); setBackgroundColor(0xCC101820.toInt()) }
  count = TextView(this).apply { text="0 objetos"; textSize=28f; setTextColor(-1) }
  status = TextView(this).apply { text="Iniciando câmera..."; textSize=13f; setTextColor(0xFFBBDEFB.toInt()) }
  header.addView(count); header.addView(status)
  root.addView(preview, FrameLayout.LayoutParams(-1,-1))
  root.addView(overlay, FrameLayout.LayoutParams(-1,-1))
  root.addView(header, FrameLayout.LayoutParams(-1,-2, android.view.Gravity.TOP))
  setContentView(root)
  if (ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) startCamera()
  else requestPermission.launch(Manifest.permission.CAMERA)
 }
 private fun startCamera() {
  val future=ProcessCameraProvider.getInstance(this)
  future.addListener({
   try {
    val provider=future.get()
    val p=Preview.Builder().build().also { it.surfaceProvider=preview.surfaceProvider }
    val analysis=ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
    analysis.setAnalyzer(worker) { proxy -> analyze(proxy) }
    provider.unbindAll()
    provider.bindToLifecycle(this,CameraSelector.DEFAULT_BACK_CAMERA,p,analysis)
    status.text="Detecção genérica ativa • máximo de 5 objetos • não identifica peças"
   } catch(e:Exception) { status.text="Erro ao iniciar câmera: ${e.message}" }
  },ContextCompat.getMainExecutor(this))
 }
 private fun analyze(proxy:ImageProxy) {
  if (!busy.compareAndSet(false,true)) { proxy.close(); return }
  val media=proxy.image
  if(media==null){ busy.set(false);proxy.close();return }
  val image=InputImage.fromMediaImage(media,proxy.imageInfo.rotationDegrees)
  detector.process(image).addOnSuccessListener { objects ->
   val bounds=objects.map { Rect(it.boundingBox) }
   runOnUiThread {
    overlay.update(bounds,image.width,image.height)
    count.text="${bounds.size} objeto${if(bounds.size==1) "" else "s"}"
   }
  }.addOnFailureListener { error -> runOnUiThread { status.text="Falha na detecção: ${error.localizedMessage}" } }
   .addOnCompleteListener { busy.set(false);proxy.close() }
 }
 override fun onDestroy() { super.onDestroy(); detector.close(); worker.shutdown() }
}

class DetectionOverlay(context:android.content.Context): View(context) {
 private var boxes:List<Rect> = emptyList()
 private var imageW=1; private var imageH=1
 private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=0xFF4ADE80.toInt(); style=Paint.Style.STROKE; strokeWidth=5f }
 private val label=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.WHITE; textSize=34f; typeface=Typeface.DEFAULT_BOLD }
 fun update(rects:List<Rect>,w:Int,h:Int){boxes=rects;imageW=w;imageH=h;invalidate()}
 override fun onDraw(canvas:Canvas){ super.onDraw(canvas)
  val scale=minOf(width.toFloat()/imageW,height.toFloat()/imageH)
  val dx=(width-imageW*scale)/2f; val dy=(height-imageH*scale)/2f
  boxes.forEachIndexed { i,r ->
   val rf=RectF(dx+r.left*scale,dy+r.top*scale,dx+r.right*scale,dy+r.bottom*scale)
   canvas.drawRoundRect(rf,12f,12f,paint)
   canvas.drawText("${i+1}",rf.left+8,rf.top+36,label)
  }
 }
}
