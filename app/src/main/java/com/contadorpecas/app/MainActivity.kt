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
 private lateinit var subtitle:TextView
 private lateinit var modeButton:Button
 private var selected:Bitmap?=null
 private var threshold=125
 private var minArea=70
 private var dark=true
 private var manual=false
 private var last=0L
 private val taps=mutableListOf<PointF>()
 private val permission=registerForActivityResult(ActivityResultContracts.RequestPermission()){if(it)startCamera()}
 private val picker=registerForActivityResult(ActivityResultContracts.GetContent()){uri->
  if(uri!=null) try {
   contentResolver.openInputStream(uri)?.use {stream->
    val bmp=BitmapFactory.decodeStream(stream)
    if(bmp!=null) {
     selected=scale(bmp);photo.setImageBitmap(selected);photo.visibility=View.VISIBLE
     preview.visibility=View.GONE;taps.clear();refresh()
    }
   }
  }catch(_:Exception){Toast.makeText(this,"Não foi possível abrir a imagem",Toast.LENGTH_LONG).show()}
 }
 private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
 private fun label(s:String,size:Float=16f,bold:Boolean=false)=TextView(this).apply{
  text=s;textSize=size;setTextColor(Color.WHITE)
  if(bold)setTypeface(null,android.graphics.Typeface.BOLD)
  setPadding(dp(3),dp(5),dp(3),dp(5))
 }
 private fun button(s:String,click:()->Unit)=Button(this).apply{
  text=s;isAllCaps=false;setOnClickListener{click()}
 }
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  window.statusBarColor=Color.rgb(15,23,42)
  window.navigationBarColor=Color.rgb(15,23,42)
  window.decorView.systemUiVisibility=0
  val root=LinearLayout(this).apply{
   orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(8),dp(12),dp(8))
   setBackgroundColor(Color.rgb(15,23,42))
  }
  root.addView(label("FOCOS  |  Contador de Peças",21f,true))
  count=label("0 peças",34f,true);root.addView(count)
  subtitle=label("Contagem automática estimada",13f);root.addView(subtitle)
  val frame=FrameLayout(this).apply{setBackgroundColor(Color.BLACK)}
  preview=PreviewView(this).apply{scaleType=PreviewView.ScaleType.FIT_CENTER}
  photo=ImageView(this).apply{scaleType=ImageView.ScaleType.FIT_CENTER;visibility=View.GONE}
  markers=Markers()
  frame.addView(preview,FrameLayout.LayoutParams(-1,-1))
  frame.addView(photo,FrameLayout.LayoutParams(-1,-1))
  frame.addView(markers,FrameLayout.LayoutParams(-1,-1))
  root.addView(frame,LinearLayout.LayoutParams(-1,0,1f))
  val controls=ScrollView(this).apply{isFillViewport=false}
  val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
  val sources=LinearLayout(this)
  sources.addView(button("Câmera"){selected=null;taps.clear();photo.visibility=View.GONE;preview.visibility=View.VISIBLE;markers.clear();refresh();ensureCamera()},LinearLayout.LayoutParams(0,dp(52),1f))
  sources.addView(button("Abrir foto"){picker.launch("image/*")},LinearLayout.LayoutParams(0,dp(52),1f))
  panel.addView(sources)
  modeButton=button("Modo: Automático (estimativa)"){manual=!manual;taps.clear();refresh()}
  panel.addView(modeButton,LinearLayout.LayoutParams(-1,dp(52)))
  val actions=LinearLayout(this)
  actions.addView(button("Desfazer"){if(manual&&taps.isNotEmpty()){taps.removeAt(taps.lastIndex);refresh()}},LinearLayout.LayoutParams(0,dp(48),1f))
  actions.addView(button("Zerar"){taps.clear();if(manual)refresh() else {selected=null;photo.visibility=View.GONE;preview.visibility=View.VISIBLE;markers.clear();refresh()}},LinearLayout.LayoutParams(0,dp(48),1f))
  panel.addView(actions)
  panel.addView(label("Ajustes do modo automático",14f,true))
  panel.addView(CheckBox(this).apply{
   text="Peças escuras em fundo claro";setTextColor(Color.WHITE);isChecked=true
   setOnCheckedChangeListener{_,v->dark=v;refresh()}
  })
  panel.addView(label("Contraste",13f))
  panel.addView(SeekBar(this).apply{
   max=255;progress=threshold
   setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
    override fun onProgressChanged(s:SeekBar?,p:Int,u:Boolean){threshold=p;if(u)refresh()}
    override fun onStartTrackingTouch(s:SeekBar?){}
    override fun onStopTrackingTouch(s:SeekBar?){}
   })
  })
  panel.addView(label("Tamanho mínimo detectado",13f))
  panel.addView(SeekBar(this).apply{
   max=800;progress=minArea
   setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
    override fun onProgressChanged(s:SeekBar?,p:Int,u:Boolean){minArea=p.coerceAtLeast(1);if(u)refresh()}
    override fun onStartTrackingTouch(s:SeekBar?){}
    override fun onStopTrackingTouch(s:SeekBar?){}
   })
  })
  panel.addView(label("Dica: no modo manual, toque uma vez em cada peça. Para remover a última marcação, use Desfazer.",12f))
  controls.addView(panel)
  root.addView(controls,LinearLayout.LayoutParams(-1,dp(252)))
  setContentView(root);ensureCamera()
 }
 private fun scale(b:Bitmap):Bitmap{
  val f=minOf(1f,480f/maxOf(b.width,b.height))
  return if(f==1f)b else Bitmap.createScaledBitmap(b,(b.width*f).toInt().coerceAtLeast(1),(b.height*f).toInt().coerceAtLeast(1),true)
 }
 private fun refresh(){
  modeButton.text=if(manual)"Modo: Manual por toque" else "Modo: Automático (estimativa)"
  subtitle.text=if(manual)"Toque em cada peça para contar" else "Estimativa por contraste — pode contar detalhes como peças"
  if(manual){count.text="${taps.size} peças";markers.manual(taps)}
  else {markers.clear();processPhoto()}
 }
 private fun processPhoto(){
  val b=selected?:return
  worker.execute{
   val r=Counter.analyze(b,threshold,minArea,dark)
   runOnUiThread{if(selected===b&&!manual)display(r)}
  }
 }
 private fun display(r:Counter.Result){count.text="${r.count} peças (estimativa)";markers.show(r)}
 private fun ensureCamera(){
  if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)startCamera()
  else permission.launch(Manifest.permission.CAMERA)
 }
 private fun startCamera(){
  val future=ProcessCameraProvider.getInstance(this)
  future.addListener({
   try{
    val provider=future.get()
    val p=Preview.Builder().build().also{it.surfaceProvider=preview.surfaceProvider}
    val a=ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
     .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888).build()
    a.setAnalyzer(worker){image->
     try{
      if(!manual&&selected==null&&System.currentTimeMillis()-last>400){
       last=System.currentTimeMillis()
       val plane=image.planes[0];val buf=plane.buffer
       val bytes=ByteArray(image.width*image.height*4)
       for(y in 0 until image.height){
        buf.position(y*plane.rowStride)
        buf.get(bytes,y*image.width*4,image.width*4)
       }
       val b=Bitmap.createBitmap(image.width,image.height,Bitmap.Config.ARGB_8888)
       b.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(bytes))
       val m=Matrix().apply{postRotate(image.imageInfo.rotationDegrees.toFloat())}
       val rotated=Bitmap.createBitmap(b,0,0,b.width,b.height,m,true)
       val r=Counter.analyze(scale(rotated),threshold,minArea,dark)
       runOnUiThread{if(!manual&&selected==null)display(r)}
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
  private val pen=Paint(3).apply{color=Color.rgb(52,211,153);style=Paint.Style.STROKE;strokeWidth=dp(3).toFloat()}
  fun show(r:Counter.Result){result=r;invalidate()}
  fun manual(points:List<PointF>){result=null;invalidate()}
  fun clear(){result=null;invalidate()}
  override fun onTouchEvent(event:android.view.MotionEvent):Boolean{
   if(!manual)return false
   if(event.action==MotionEvent.ACTION_UP){
    taps.add(PointF(event.x,event.y));refresh();performClick()
   }
   return true
  }
  override fun performClick():Boolean{super.performClick();return true}
  override fun onDraw(c:Canvas){
   super.onDraw(c)
   if(manual){for((i,p) in taps.withIndex()){
    c.drawCircle(p.x,p.y,dp(16).toFloat(),pen)
    val t=Paint(3).apply{color=Color.WHITE;textSize=dp(13).toFloat();typeface=Typeface.DEFAULT_BOLD}
    c.drawText((i+1).toString(),p.x-dp(4),p.y+dp(5),t)
   };return}
   val r=result?:return
   val s=minOf(width.toFloat()/r.width,height.toFloat()/r.height)
   val ox=(width-r.width*s)/2;val oy=(height-r.height*s)/2
   for(p in r.centers)c.drawCircle(ox+p.x*s,oy+p.y*s,dp(10).toFloat(),pen)
  }
 }
}