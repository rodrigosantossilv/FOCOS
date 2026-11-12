package com.contadorpecas.app
import android.graphics.Bitmap
import android.graphics.PointF
import java.util.ArrayDeque

object Counter {
 data class Result(val count:Int,val width:Int,val height:Int,val centers:List<PointF>)
 fun analyze(bitmap:Bitmap,threshold:Int,minArea:Int,dark:Boolean):Result {
  val w=bitmap.width; val h=bitmap.height
  val pixels=IntArray(w*h); bitmap.getPixels(pixels,0,w,0,0,w,h)
  val mask=BooleanArray(w*h)
  for(i in pixels.indices) {
   val p=pixels[i]; val gray=((p shr 16 and 255)*30+(p shr 8 and 255)*59+(p and 255)*11)/100
   mask[i]=if(dark) gray<threshold else gray>threshold
  }
  val visited=BooleanArray(w*h); val centers=ArrayList<PointF>(); val q=ArrayDeque<Int>()
  for(y in 1 until h-1) for(x in 1 until w-1) {
   val start=y*w+x
   if(!mask[start]||visited[start]) continue
   visited[start]=true; q.add(start)
   var n=0; var sx=0L; var sy=0L; var border=false
   while(q.isNotEmpty()) {
    val i=q.removeFirst(); val xx=i%w; val yy=i/w
    n++; sx+=xx; sy+=yy
    if(xx==0||yy==0||xx==w-1||yy==h-1) border=true
    val neighbors=intArrayOf(i-1,i+1,i-w,i+w)
    for(j in neighbors) if(j in mask.indices && mask[j] && !visited[j] && (j/w==yy || j%w==xx)) { visited[j]=true; q.add(j) }
   }
   if(n>=minArea && !border && n<w*h/3) centers.add(PointF(sx.toFloat()/n,sy.toFloat()/n))
  }
  return Result(centers.size,w,h,centers)
 }
}