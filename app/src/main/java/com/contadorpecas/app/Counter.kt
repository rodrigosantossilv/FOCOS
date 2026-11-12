package com.contadorpecas.app

import android.graphics.Bitmap
import android.graphics.PointF
import kotlin.math.*

object Counter {
 data class Mark(val x:Float,val y:Float,val estimated:Boolean=false)
 data class Result(val marks:List<Mark>,val warning:String,val typicalArea:Double) {
  val count get()=marks.size
 }
 fun analyze(src:Bitmap,sensitivity:Int,minArea:Int):Result {
  val w=src.width;val h=src.height
  if(w<48||h<48)return Result(emptyList(),"Foto inválida ou pequena demais.",0.0)
  val px=IntArray(w*h);src.getPixels(px,0,w,0,0,w,h)
  val g=IntArray(px.size){i->val c=px[i];((c shr 16 and 255)*30+(c shr 8 and 255)*59+(c and 255)*11)/100}
  val cell=max(32,min(w,h)/7);val nx=(w+cell-1)/cell;val ny=(h+cell-1)/cell
  val bg=FloatArray(nx*ny)
  for(by in 0 until ny)for(bx in 0 until nx){
   val a=ArrayList<Int>()
   for(y in by*cell until min(h,(by+1)*cell) step 5)for(x in bx*cell until min(w,(bx+1)*cell) step 5)a.add(g[y*w+x])
   a.sort();bg[by*nx+bx]=a[a.size/2].toFloat()
  }
  val diff=IntArray(w*h)
  for(y in 0 until h)for(x in 0 until w){
   val fx=(x.toFloat()/cell-.5f).coerceIn(0f,(nx-1).toFloat())
   val fy=(y.toFloat()/cell-.5f).coerceIn(0f,(ny-1).toFloat())
   val x0=fx.toInt();val y0=fy.toInt();val x1=min(nx-1,x0+1);val y1=min(ny-1,y0+1)
   val tx=fx-x0;val ty=fy-y0
   val b=(bg[y0*nx+x0]*(1-tx)+bg[y0*nx+x1]*tx)*(1-ty)+(bg[y1*nx+x0]*(1-tx)+bg[y1*nx+x1]*tx)*ty
   diff[y*w+x]=abs(g[y*w+x]-b.toInt())
  }
  val cut=(42-sensitivity.coerceIn(0,100)*.32).toInt().coerceAtLeast(7)
  val mask=BooleanArray(w*h){diff[it]>=cut}
  val visited=BooleanArray(w*h);val areas=ArrayList<Int>();val blobs=ArrayList<Triple<Int,Float,Float>>()
  val queue=IntArray(w*h)
  for(y in 1 until h-1)for(x in 1 until w-1){
   val i=y*w+x
   if(!mask[i]||visited[i])continue
   var head=0;var tail=1;queue[0]=i;visited[i]=true
   var sumX=0L;var sumY=0L;var edge=false
   while(head<tail){
    val p=queue[head++];val xx=p%w;val yy=p/w
    sumX+=xx;sumY+=yy
    if(xx==0||yy==0||xx==w-1||yy==h-1)edge=true
    val candidates=intArrayOf(if(xx>0)p-1 else -1,if(xx<w-1)p+1 else -1,if(yy>0)p-w else -1,if(yy<h-1)p+w else -1)
    for(n in candidates)if(n>=0&&mask[n]&&!visited[n]){visited[n]=true;queue[tail++]=n}
   }
   if(!edge&&tail>=minArea&&tail<w*h/4){
    areas.add(tail);blobs.add(Triple(tail,sumX.toFloat()/tail,sumY.toFloat()/tail))
   }
  }
  if(blobs.isEmpty())return Result(emptyList(),"Nenhum item encontrado. Ajuste a sensibilidade e use fundo liso.",0.0)
  val sorted=areas.sorted()
  val typical=sorted.take(max(1,(sorted.size*.7).toInt())).let{it[it.size/2].toDouble()}
  val marks=ArrayList<Mark>();var crowded=false
  for((area,x,y) in blobs){
   val ratio=area/typical
   val n=ratio.roundToInt().coerceIn(1,8)
   if(n>1)crowded=true
   for(k in 0 until n){
    val offset=(k-(n-1)/2f)*sqrt(typical).toFloat()*.65f
    marks.add(Mark((x+offset).coerceIn(0f,w.toFloat()),y,n>1))
   }
  }
  val average=diff.average()
  val warning=when{
   average<8->"Pouco contraste: use iluminação uniforme e fundo contrastante."
   crowded->"Itens encostados: pontos tracejados representam estimativas. Confira manualmente."
   blobs.size>120->"Muitos itens: revise a contagem e evite amontoar."
   else->"Contagem estimada. Toque nos pontos para corrigir."
  }
  return Result(marks,warning,typical)
 }
}
