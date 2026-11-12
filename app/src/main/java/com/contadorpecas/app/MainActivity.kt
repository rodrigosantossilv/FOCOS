package com.contadorpecas.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity:ComponentActivity(){
 private val executor=Executors.newSingleThreadExecutor()
 private val busy=AtomicBoolean(false)
 private var detector:YoloDetector?=null
 private lateinit var preview:PreviewView
 private lateinit var count:TextView
 private lateinit var status:TextView
 private var cameraProvider:ProcessCameraProvider?=null
 private var analysis:ImageAnalysis?=null
 private val permission=registerForActivityResult(ActivityResultContracts.RequestPermission()){ok->if(ok)startCamera() else status.text="Permita acesso à câmera nas configurações."}
 private val gallery=registerForActivityResult(ActivityResultContracts.GetContent()){uri->
  if(uri!=null)try{
   val b=contentResolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it)}
   if(b!=null)runDetection(b)else status.text="Foto inválida."
  }catch(e:Exception){status.text="Não foi possível abrir a foto."}
 }
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  window.statusBarColor=Color.rgb(15,23,42)
  window.navigationBarColor=Color.rgb(15,23,42)
  window.decorView.systemUiVisibility=0
  try{detector=YoloDetector(this)}catch(e:Exception){android.util.Log.e("FOCOS","Modelo indisponível",e)}
  val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.rgb(15,23,42));setPadding(16,16,16,16)}
  val title=TextView(this).apply{text="FOCOS • Câmera ao vivo";textSize=20f;setTextColor(Color.WHITE)}
  root.addView(title)
  count=TextView(this).apply{text="— objetos";textSize=42f;setTextColor(Color.WHITE);gravity=Gravity.CENTER_HORIZONTAL}
  root.addView(count)
  status=TextView(this).apply{text=if(detector==null)"Modelo YOLOv8 não instalado. A câmera funciona, mas a contagem está desativada." else "Aponte para os objetos sobre o chão ou mesa.";textSize=14f;setTextColor(Color.LTGRAY)}
  root.addView(status)
  preview=PreviewView(this).apply{scaleType=PreviewView.ScaleType.FIT_CENTER}
  root.addView(preview,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f))
  val buttons=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  val camera=Button(this).apply{text="Câmera ao vivo";setOnClickListener{checkCamera()}}
  val photos=Button(this).apply{text="Galeria";setOnClickListener{gallery.launch("image/*")}}
  buttons.addView(camera,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f))
  buttons.addView(photos,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f))
  root.addView(buttons)
  setContentView(root)
  checkCamera()
 }
 private fun checkCamera(){
  if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)startCamera()
  else permission.launch(Manifest.permission.CAMERA)
 }
 private fun startCamera(){
  val future=ProcessCameraProvider.getInstance(this)
  future.addListener({
   try{
    val provider=future.get();cameraProvider=provider
    val p=Preview.Builder().build().also{it.setSurfaceProvider(preview.surfaceProvider)}
    val a=ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
    analysis=a
    a.setAnalyzer(executor){frame->
     try{
      if(detector!=null && busy.compareAndSet(false,true)){
       try{
        val bmp=frame.toBitmap()
        val result=detector!!.detect(bmp,.5f)
        runOnUiThread{count.text="${result.size} objetos";status.text="YOLOv8 offline • Contagem estimada"}
       }catch(e:Exception){runOnUiThread{status.text="Erro na inferência YOLO: ${e.message}"}}
       finally{busy.set(false)}
      }
     }finally{frame.close()}
    }
    provider.unbindAll()
    provider.bindToLifecycle(this,CameraSelector.DEFAULT_BACK_CAMERA,p,a)
    if(detector==null)status.text="Câmera ativa. Modelo YOLOv8 ainda não instalado; sem contagem automática."
   }catch(e:Exception){status.text="Falha ao abrir câmera: ${e.message}"}
  },ContextCompat.getMainExecutor(this))
 }
 private fun runDetection(bitmap:Bitmap){
  val d=detector
  if(d==null){status.text="Modelo YOLOv8 não instalado; não é possível contar esta foto.";return}
  if(!busy.compareAndSet(false,true))return
  executor.execute{
   try{
    val r=d.detect(bitmap,.5f)
    runOnUiThread{count.text="${r.size} objetos";status.text="Foto analisada com YOLOv8 offline"}
   }catch(e:Exception){runOnUiThread{status.text="Falha na análise: ${e.message}"}}
   finally{busy.set(false)}
  }
 }
 override fun onDestroy(){super.onDestroy();analysis?.clearAnalyzer();cameraProvider?.unbindAll();executor.shutdown();detector?.close()}
}
