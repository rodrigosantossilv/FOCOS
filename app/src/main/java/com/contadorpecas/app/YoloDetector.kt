package com.contadorpecas.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import kotlin.math.*

/**
 * YOLOv8 detection head export: [1,4+C,N], float32, xywh in input pixels.
 * This is NOT compatible with end-to-end/NMS-embedded exports.
 * Training classes: screw, nut, washer, automotive_part.
 */
class YoloDetector(context:Context):AutoCloseable {
 data class Detection(val box:RectF,val label:String,val score:Float)
 private val names=listOf("Parafuso","Porca","Arruela","Peça automotiva")
 private val env=OrtEnvironment.getEnvironment()
 private val session:OrtSession
 private val size=640
 init {
  val bytes=context.assets.open("parts_yolov8.onnx").use{it.readBytes()}
  session=env.createSession(bytes,OrtSession.SessionOptions())
  val shape=session.inputInfo.values.first().info as ai.onnxruntime.TensorInfo
  require(shape.shape.size==4 && shape.shape[1]==3L && shape.shape[2]==size.toLong() && shape.shape[3]==size.toLong()){"Modelo precisa ter entrada [1,3,640,640]"}
 }
 fun detect(bitmap:Bitmap,confidence:Float=.45f,iou:Float=.45f):List<Detection>{
  val w=bitmap.width;val h=bitmap.height
  val scale=min(size.toFloat()/w,size.toFloat()/h)
  val rw=(w*scale).roundToInt();val rh=(h*scale).roundToInt()
  val left=(size-rw)/2;val top=(size-rh)/2
  val canvasBitmap=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888)
  val canvas=android.graphics.Canvas(canvasBitmap);canvas.drawColor(android.graphics.Color.rgb(114,114,114))
  canvas.drawBitmap(Bitmap.createScaledBitmap(bitmap,rw,rh,true),left.toFloat(),top.toFloat(),null)
  val px=IntArray(size*size);canvasBitmap.getPixels(px,0,size,0,0,size,size)
  val input=FloatArray(3*size*size)
  for(i in px.indices){val p=px[i];input[i]=(p shr 16 and 255)/255f;input[size*size+i]=(p shr 8 and 255)/255f;input[2*size*size+i]=(p and 255)/255f}
  val tensor=OnnxTensor.createTensor(env,FloatBuffer.wrap(input),longArrayOf(1,3,size.toLong(),size.toLong()))
  val candidates=ArrayList<Detection>()
  tensor.use { t ->
   session.run(mapOf(session.inputNames.first() to t)).use { result ->
    val out=result[0].value as Array<*>
    val rows=(out[0] as Array<*>)
    require(rows.size==4+names.size){"Saída incompatível: esperado [1,8,N]"}
    val channels=rows.map{it as FloatArray}
    for(i in channels[0].indices){
     var cls=0;var score=0f
     for(c in names.indices)if(channels[4+c][i]>score){score=channels[4+c][i];cls=c}
     if(score<confidence)continue
     val cx=channels[0][i];val cy=channels[1][i];val bw=channels[2][i];val bh=channels[3][i]
     val box=RectF(((cx-bw/2-left)/scale).coerceIn(0f,w.toFloat()),((cy-bh/2-top)/scale).coerceIn(0f,h.toFloat()),((cx+bw/2-left)/scale).coerceIn(0f,w.toFloat()),((cy+bh/2-top)/scale).coerceIn(0f,h.toFloat()))
     if(box.width()>2&&box.height()>2)candidates.add(Detection(box,names[cls],score))
    }
   }
  }
  val kept=ArrayList<Detection>()
  for(d in candidates.sortedByDescending{it.score}){
   if(kept.size>=300)break
   if(kept.none{it.label==d.label&&overlap(it.box,d.box)>iou})kept.add(d)
  }
  return kept
 }
 private fun overlap(a:RectF,b:RectF):Float{
  val x=max(0f,min(a.right,b.right)-max(a.left,b.left));val y=max(0f,min(a.bottom,b.bottom)-max(a.top,b.top))
  val intersection=x*y;return intersection/(a.width()*a.height()+b.width()*b.height()-intersection+1e-6f)
 }
 override fun close(){session.close()}
}
