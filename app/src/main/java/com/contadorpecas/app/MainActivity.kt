package com.contadorpecas.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

class MainActivity:ComponentActivity() {
 private val worker=Executors.newSingleThreadExecutor()
 private lateinit var preview:PreviewView
 private lateinit var photo:ImageView
 private lateinit var markers:Markers
 private lateinit var count:TextView
 private var selected:Bitmap?=null
 private var threshold=125
 private var minArea=40
 private var dark=true
 private var last=0L
 private val permission=registerForActivityResult(ActivityResultContracts.RequestPermission()){if(it) startCamera()}
 private val picker=registerForActivityResult(ActivityResultContracts.GetContent()){uri->
  if(uri!=null) try {
   contentResolver.openInputStream(uri)?.use { stream->
    val bmp=BitmapFactory.decodeStream(stream)
    if(bmp!=null) {
     selected=scale(bmp); photo.setImageBitmap(selected); photo.visibility=View.VISIBLE
     preview.visibility=View.GONE; processPhoto()
    }
   }
  }catch(e:Exception){Toast.makeText(this,"Erro ao abrir imagem",Toast.LENGTH_SHORT).show()}
 }
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  val root=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(12,12,12,12);setBackgroundColor(Color.rgb(15,22,32))}
  fun text(s:String,size:Float=16f)=TextView(this).apply {text=s;textSize=size;setTextColor(Color.WHITE);setPadding(4,6,4,6)}
  root.addView(text("FOCOS • Contador de Peças",22f))
  count=text("Peças: 0",26f);root.addView(count)
  val frame=FrameLayout(this)
  preview=PreviewView(this).apply {scaleType=PreviewView.ScaleType.FIT_CENTER}
  photo=ImageView(this).apply {scaleType=ImageView.ScaleType.FIT_CENTER;visibility=View.GONE}
  markers=Markers()
  frame.addView(preview,FrameLayout.LayoutParams(-1,-1))
  frame.addView(photo,FrameLayout.LayoutParams(-1,-1))
  frame.addView(markers,FrameLayout.LayoutParams(-1,-1))
  root.addView(frame,LinearLayout.LayoutParams(-1,0,1f))
  val buttons=LinearLayout(this)
  buttons.addView(Button(this).apply{text="Câmera ao vivo";setOnClickListener {selected=null;photo.visibility=View.GONE;preview.visibility=View.VISIBLE;markers.clear();ensureCamera()}},LinearLayout.LayoutParams(0,-2,1f))
  buttons.addView(Button(this).apply{text="Abrir foto";setOnClickListener {picker.launch("image/*")}},LinearLayout.LayoutParams(0,-2,1f))
  root.addView(buttons)
  root.addView(CheckBox(this).apply {text="Peças escuras em fundo claro";setTextColor(Color.WHITE);isChecked=true;setOnCheckedChangeListener {_,v->dark=v;processPhoto()}})
  root.addView(text("Contraste"))
  root.addView(SeekBar(this).apply {max=255;progress=threshold;setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
   override fun onProgressChanged(s:SeekBar?,p:Int,u:Boolean){threshold=p;if(u)processPhoto()}
   override fun onStartTrackingTouch(s:SeekBar?){}
   override fun onStopTrackingTouch(s:SeekBar?){}
  })})
  root.addView(text("Tamanho mínimo das peças"))
  root.addView(SeekBar(this).apply {max=500;progress=minArea;setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
   override fun onProgressChanged(s:SeekBar?,p:Int,u:Boolean){minArea=p.coerceAtLeast(1);if(u)processPhoto()}
   override fun onStartTrackingTouch(s:SeekBar?){}
   override fun onStopTrackingTouch(s:SeekBar?){}
  })})
  root.addView(text("Separe as peças e use fundo uniforme.",12f))
  setContentView(root);ensureCamera()
 }
 private fun scale(b:Bitmap):Bitmap {val f=minOf(1f,480f/maxOf(b.width,b.height));return if(f==1f)b else Bitmap.createScaledBitmap(b,(b.width*f).toInt().coerceAtLeast(1),(b.height*f).toInt().coerceAtLeast(1),true)}
 private fun processPhoto(){val b=selected?:return;worker.execute {val r=Counter.analyze(b,threshold,minArea,dark);runOnUiThread {if(selected===b)display(r)}}}
 private fun display(r:Counter.Result){count.text="Peças: ${r.count}";markers.show(r)}
 private fun ensureCamera(){if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)startCamera() else permission.launch(Manifest.permission.CAMERA)}
 private fun startCamera(){
  val future=ProcessCameraProvider.getInstance(this)
  future.addListener({
   try{
    val provider=future.get()
    val p=Preview.Builder().build().also{it.surfaceProvider=preview.surfaceProvider}
    val a=ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888).build()
    a.setAnalyzer(worker){image->
     try {
      if(selected==null && System.currentTimeMillis()-last>300){
       last=System.currentTimeMillis()
       val plane=image.planes[0];val buf=plane.buffer
       val bytes=ByteArray(image.width*image.height*4)
       for(y in 0 until image.height){buf.position(y*plane.rowStride);buf.get(bytes,y*image.width*4,image.width*4)}
       val b=Bitmap.createBitmap(image.width,image.height,Bitmap.Config.ARGB_8888)
       b.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(bytes))
       val m=Matrix().apply{postRotate(image.imageInfo.rotationDegrees.toFloat())}
       val rotated=Bitmap.createBitmap(b,0,0,b.width,b.height,m,true)
       val r=Counter.analyze(scale(rotated),threshold,minArea,dark)
       runOnUiThread {if(selected==null)display(r)}
      }
     }catch(_:Exception){}finally{image.close()}
    }
    provider.unbindAll();provider.bindToLifecycle(this,CameraSelector.DEFAULT_BACK_CAMERA,p,a)
   }catch(e:Exception){Toast.makeText(this,"Erro na câmera: ${e.message}",Toast.LENGTH_LONG).show()}
  },ContextCompat.getMainExecutor(this))
 }
 override fun onDestroy(){super.onDestroy();worker.shutdown()}
 inner class Markers:View(this@MainActivity){
  private var result:Counter.Result?=null
  private val pen=Paint(3).apply{color=Color.GREEN;style=Paint.Style.STROKE;strokeWidth=4f}
  fun show(r:Counter.Result){result=r;invalidate()}
  fun clear(){result=null;invalidate()}
  override fun onDraw(c:Canvas){super.onDraw(c);val r=result?:return;val s=minOf(width.toFloat()/r.width,height.toFloat()/r.height);val ox=(width-r.width*s)/2;val oy=(height-r.height*s)/2;for(p in r.centers)c.drawCircle(ox+p.x*s,oy+p.y*s,12f,pen)}
 }
}