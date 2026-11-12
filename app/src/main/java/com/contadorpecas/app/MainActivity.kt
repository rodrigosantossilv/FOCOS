package com.contadorpecas.app

import android.content.ContentValues
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.*
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import java.util.concurrent.Executors
import kotlin.math.*

class MainActivity:ComponentActivity(){
 private val worker=Executors.newSingleThreadExecutor()
 private var photo:Bitmap?=null
 private var marks=mutableListOf<Counter.Mark>()
 private val history=ArrayDeque<List<Counter.Mark>>()
 private var dark=false
 private var sensitivity=55
 private var minArea=35
 private var detector:YoloDetector?=null
 private var detections=listOf<YoloDetector.Detection>()
 private var cameraUri:Uri?=null
 private var generation=0
 private lateinit var total:TextView
 private lateinit var warning:TextView
 private lateinit var overlay:PhotoOverlay
 private lateinit var root:LinearLayout
 private val take=registerForActivityResult(ActivityResultContracts.TakePicture()){ok->
  val uri=cameraUri
  if(ok&&uri!=null)load(uri)
  else if(uri!=null)contentResolver.delete(uri,null,null)
  cameraUri=null
 }
 private val gallery=registerForActivityResult(ActivityResultContracts.GetContent()){uri->if(uri!=null)load(uri)}
 private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
 private fun fg()=if(dark)Color.WHITE else Color.rgb(20,30,49)
 private fun bg()=if(dark)Color.rgb(15,23,42) else Color.rgb(246,248,252)
 private fun label(s:String,size:Float,bold:Boolean=false)=TextView(this).apply{
  text=s;textSize=size;setTextColor(fg());setPadding(dp(3),dp(5),dp(3),dp(5))
  if(bold)setTypeface(null,Typeface.BOLD)
 }
 private fun button(s:String,click:()->Unit)=Button(this).apply{text=s;isAllCaps=false;setOnClickListener{click()}}
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  window.statusBarColor=Color.rgb(15,23,42);window.navigationBarColor=Color.rgb(15,23,42)
  window.decorView.systemUiVisibility=0
  try{detector=YoloDetector(this)}catch(e:Exception){android.util.Log.w("FOCOS","Modelo YOLO indisponível",e)}
  buildUi();showExample()
 }
 private fun buildUi(){
  root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(8),dp(14),dp(8));setBackgroundColor(bg())}
  val heading=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
  heading.addView(label("FOCOS 2.0",22f,true),LinearLayout.LayoutParams(0,-2,1f))
  heading.addView(button(if(dark)"Tema claro" else "Tema escuro"){dark=!dark;buildUi();refreshMarks()},LinearLayout.LayoutParams(dp(128),dp(48)))
  root.addView(heading)
  total=label("0 objetos",36f,true);root.addView(total)
  warning=label("Processamento local • Sem envio de fotos",13f);root.addView(warning)
  overlay=PhotoOverlay()
  root.addView(overlay,LinearLayout.LayoutParams(-1,0,1f))
  val scroller=ScrollView(this)
  val controls=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
  val buttons=LinearLayout(this)
  buttons.addView(button("Tirar foto"){capture()},LinearLayout.LayoutParams(0,dp(54),1f))
  buttons.addView(button("Galeria"){gallery.launch("image/*")},LinearLayout.LayoutParams(0,dp(54),1f))
  controls.addView(buttons)
  val edits=LinearLayout(this)
  edits.addView(button("Desfazer"){if(history.isNotEmpty()){marks=history.removeLast().toMutableList();refreshMarks()}},LinearLayout.LayoutParams(0,dp(50),1f))
  edits.addView(button("Recontar"){analyze()},LinearLayout.LayoutParams(0,dp(50),1f))
  controls.addView(edits)
  controls.addView(label("Confiança da detecção",15f,true))
  controls.addView(SeekBar(this).apply{
   max=100;progress=sensitivity
   setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
    override fun onProgressChanged(s:SeekBar?,p:Int,u:Boolean){sensitivity=p;if(u)analyze()}
    override fun onStartTrackingTouch(s:SeekBar?){}
    override fun onStopTrackingTouch(s:SeekBar?){}
   })
  })
  controls.addView(label("Tamanho mínimo (reservado)",15f,true))
  controls.addView(SeekBar(this).apply{
   max=600;progress=minArea
   setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
    override fun onProgressChanged(s:SeekBar?,p:Int,u:Boolean){minArea=p.coerceAtLeast(1);if(u)analyze()}
    override fun onStartTrackingTouch(s:SeekBar?){}
    override fun onStopTrackingTouch(s:SeekBar?){}
   })
  })
  controls.addView(label("Toque em um ponto para remover. Toque em um espaço vazio para adicionar. Marcas tracejadas são estimadas.",12f))
  scroller.addView(controls)
  root.addView(scroller,LinearLayout.LayoutParams(-1,dp(255)))
  setContentView(root)
 }
 private fun capture(){
  val values=ContentValues().apply{
   put(MediaStore.Images.Media.DISPLAY_NAME,"FOCOS_"+System.currentTimeMillis()+".jpg")
   put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg")
  }
  val uri=contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)
  if(uri==null){Toast.makeText(this,"Não foi possível abrir a câmera",Toast.LENGTH_LONG).show();return}
  cameraUri=uri;take.launch(uri)
 }
 private fun load(uri:Uri){
  try{
   val bmp=contentResolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it)}
   if(bmp==null){warning.text="Foto inválida";return}
   val size=640f/max(bmp.width,bmp.height)
   photo=if(size<1f)Bitmap.createScaledBitmap(bmp,max(1,(bmp.width*size).toInt()),max(1,(bmp.height*size).toInt()),true)else bmp
   overlay.invalidate();analyze()
  }catch(e:Exception){warning.text="Não foi possível abrir esta foto."}
 }
 private fun showExample(){
  val b=Bitmap.createBitmap(480,640,Bitmap.Config.ARGB_8888)
  val c=Canvas(b);c.drawColor(Color.rgb(236,240,244))
  val p=Paint(3).apply{color=Color.rgb(63,87,110)}
  val coords=listOf(90f to 120f,230f to 115f,360f to 145f,120f to 280f,270f to 300f,380f to 330f,140f to 470f,300f to 490f)
  for((x,y) in coords){c.drawCircle(x,y,24f,p)}
  photo=b;analyze()
 }
 private fun analyze(){
  val b=photo?:return;val id=++generation
  warning.text="Analisando imagem..."
  worker.execute{
   val r=detector?.detect(b,(sensitivity.coerceIn(0,100)/100f).coerceAtLeast(.05f))
   runOnUiThread{
    if(id==generation&&photo===b){
     detections=r?:emptyList()
     marks=detections.map{Counter.Mark(it.box.centerX(),it.box.centerY(),false)}.toMutableList();history.clear()
     warning.text=if(r==null)"Modelo YOLOv8 não instalado. Contagem automática indisponível." else if(r.isEmpty())"Nenhum objeto reconhecido. Verifique a foto e a confiança." else "YOLOv8 offline • ${r.size} detecções. Revise os resultados."
     refreshMarks()
    }
   }
  }
 }
 private fun refreshMarks(){
  total.text="${marks.size} objetos"
  overlay.invalidate()
 }
 private fun edit(x:Float,y:Float){
  val b=photo?:return
  val rect=overlay.imageRect(b)
  if(!rect.contains(x,y))return
  val bx=(x-rect.left)*b.width/rect.width()
  val by=(y-rect.top)*b.height/rect.height()
  val index=marks.indexOfFirst{hypot((it.x-bx).toDouble(),(it.y-by).toDouble())<max(18.0,b.width*.035)}
  history.addLast(marks.toList());if(history.size>30)history.removeFirst()
  if(index>=0){marks.removeAt(index);detections=emptyList()}else{marks.add(Counter.Mark(bx,by,false));detections=emptyList()}
  refreshMarks()
 }
 override fun onDestroy(){super.onDestroy();worker.shutdown();detector?.close()}
 inner class PhotoOverlay:View(this@MainActivity){
  private val pen=Paint(3)
  fun imageRect(b:Bitmap):RectF{
   val s=min(width.toFloat()/b.width,height.toFloat()/b.height)
   val rw=b.width*s;val rh=b.height*s
   return RectF((width-rw)/2,(height-rh)/2,(width+rw)/2,(height+rh)/2)
  }
  override fun onDraw(c:Canvas){
   super.onDraw(c);c.drawColor(if(dark)Color.rgb(30,41,59)else Color.rgb(229,235,241))
   val b=photo?:return;val r=imageRect(b)
   c.drawBitmap(b,null,r,Paint(3))
   for((idx,m) in marks.withIndex()){
    if(idx<detections.size && idx<marks.size){val d=detections[idx];val box=d.box;pen.color=Color.rgb(16,185,129);pen.style=Paint.Style.STROKE;pen.strokeWidth=dp(2).toFloat();pen.pathEffect=null;c.drawRect(r.left+box.left*r.width()/b.width,r.top+box.top*r.height()/b.height,r.left+box.right*r.width()/b.width,r.top+box.bottom*r.height()/b.height,pen)}
    val x=r.left+m.x*r.width()/b.width;val y=r.top+m.y*r.height()/b.height
    pen.color=if(m.estimated)Color.rgb(245,158,11)else Color.rgb(16,185,129)
    pen.style=Paint.Style.STROKE;pen.strokeWidth=dp(3).toFloat()
    pen.pathEffect=if(m.estimated)DashPathEffect(floatArrayOf(9f,7f),0f)else null
    c.drawCircle(x,y,dp(12).toFloat(),pen)
    pen.pathEffect=null;pen.style=Paint.Style.FILL;c.drawCircle(x,y,dp(3).toFloat(),pen)
   }
  }
  override fun onTouchEvent(e:MotionEvent):Boolean{
   if(e.action==MotionEvent.ACTION_UP){edit(e.x,e.y);performClick()}
   return true
  }
  override fun performClick():Boolean{super.performClick();return true}
 }
}
