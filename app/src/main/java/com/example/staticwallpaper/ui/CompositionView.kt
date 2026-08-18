package com.example.staticwallpaper.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.*
import android.view.animation.DecelerateInterpolator
import com.example.staticwallpaper.data.CompositionTransform
import com.example.staticwallpaper.data.PixelSize
import com.example.staticwallpaper.data.WallpaperConfig
import com.example.staticwallpaper.render.TransformCalculator
import com.example.staticwallpaper.render.WallpaperRenderer
import kotlin.math.abs
import kotlin.math.min

class CropEditorView @JvmOverloads constructor(context: Context,attrs: AttributeSet?=null): View(context,attrs) {
    var bitmap: Bitmap?=null;set(value){field=value;invalidate()}
    var canvasSize=PixelSize(1,1);set(value){field=value;invalidate()}
    var transform=CompositionTransform();private set
    var onTransformChanged:((CompositionTransform)->Unit)?=null
    var onGestureStarted:(()->Unit)?=null
    var onGestureFinished:((CompositionTransform)->Unit)?=null
    private var gestureActive=false
    private var lastX=0f;private var lastY=0f
    private var mapScale=1f;private var imageLeft=0f;private var imageTop=0f
    private var snapped=false
    private val imagePaint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val imageRect=RectF();private val cropRect=RectF()
    private val borderPaint=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;color=Color.WHITE}
    private val guidePaint=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;color=0x99FFFFFF.toInt()}
    private val gestureDetector=GestureDetector(context,object:GestureDetector.SimpleOnGestureListener(){
        override fun onDoubleTap(e: MotionEvent): Boolean {
            val b=bitmap?:return false
            transform=if(transform.allowBackground) TransformCalculator.centerCrop(b.width.toFloat(),b.height.toFloat(),canvasSize.width.toFloat(),canvasSize.height.toFloat()) else TransformCalculator.fitCenter()
            invalidate();onTransformChanged?.invoke(transform);return true
        }
    })
    private val scaleDetector=ScaleGestureDetector(context,object:ScaleGestureDetector.SimpleOnScaleGestureListener(){
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val b=bitmap?:return false
            val fx=(detector.focusX-imageLeft)/mapScale;val fy=(detector.focusY-imageTop)/mapScale
            transform=TransformCalculator.zoomAround(transform,b.width.toFloat(),b.height.toFloat(),fx,fy,detector.scaleFactor)
            invalidate();onTransformChanged?.invoke(transform);return true
        }
    })

    fun setExternalTransform(value: CompositionTransform){if(!gestureActive){transform=value;invalidate()}}

    override fun onDraw(canvas: Canvas){
        super.onDraw(canvas);canvas.drawColor(Color.rgb(31,31,35));val b=bitmap?:return
        mapScale=min(width*.78f/b.width,height*.78f/b.height).coerceAtLeast(.0001f)
        val dw=b.width*mapScale;val dh=b.height*mapScale
        imageLeft=(width-dw)/2f;imageTop=(height-dh)/2f
        imageRect.set(imageLeft,imageTop,imageLeft+dw,imageTop+dh);canvas.drawBitmap(b,null,imageRect,imagePaint)
        val viewport=TransformCalculator.viewport(b.width.toFloat(),b.height.toFloat(),canvasSize.width.toFloat(),canvasSize.height.toFloat(),transform)
        cropRect.set(imageLeft+viewport.left*mapScale,imageTop+viewport.top*mapScale,imageLeft+viewport.right*mapScale,imageTop+viewport.bottom*mapScale)
        canvas.save();canvas.clipOutRect(cropRect);canvas.drawColor(0x99000000.toInt());canvas.restore()
        borderPaint.strokeWidth=3f*resources.displayMetrics.density;guidePaint.strokeWidth=resources.displayMetrics.density;canvas.drawRect(cropRect,borderPaint)
        canvas.drawLine(cropRect.centerX(),cropRect.top,cropRect.centerX(),cropRect.bottom,guidePaint);canvas.drawLine(cropRect.left,cropRect.centerY(),cropRect.right,cropRect.centerY(),guidePaint)
        canvas.drawLine(cropRect.left+cropRect.width()/3,cropRect.top,cropRect.left+cropRect.width()/3,cropRect.bottom,guidePaint);canvas.drawLine(cropRect.left+cropRect.width()*2/3,cropRect.top,cropRect.left+cropRect.width()*2/3,cropRect.bottom,guidePaint)
        canvas.drawLine(cropRect.left,cropRect.top+cropRect.height()/3,cropRect.right,cropRect.top+cropRect.height()/3,guidePaint);canvas.drawLine(cropRect.left,cropRect.top+cropRect.height()*2/3,cropRect.right,cropRect.top+cropRect.height()*2/3,guidePaint)
    }

    override fun onTouchEvent(event: MotionEvent):Boolean{
        gestureDetector.onTouchEvent(event);scaleDetector.onTouchEvent(event)
        when(event.actionMasked){
            MotionEvent.ACTION_DOWN->{gestureActive=true;onGestureStarted?.invoke();lastX=event.x;lastY=event.y;snapped=false;parent.requestDisallowInterceptTouchEvent(true)}
            MotionEvent.ACTION_POINTER_DOWN->{lastX=scaleDetector.focusX;lastY=scaleDetector.focusY}
            MotionEvent.ACTION_MOVE->if(!scaleDetector.isInProgress&&event.pointerCount==1){
                val b=bitmap;if(b!=null){transform=TransformCalculator.panBySource(transform,b.width.toFloat(),b.height.toFloat(),(event.x-lastX)/mapScale,(event.y-lastY)/mapScale);transform=snap(transform,b);invalidate();onTransformChanged?.invoke(transform)};lastX=event.x;lastY=event.y
            }
            MotionEvent.ACTION_POINTER_UP->{val remaining=if(event.actionIndex==0)1 else 0;if(remaining<event.pointerCount){lastX=event.getX(remaining);lastY=event.getY(remaining)}}
            MotionEvent.ACTION_UP->{performClick();parent.requestDisallowInterceptTouchEvent(false);settle()}
            MotionEvent.ACTION_CANCEL->{parent.requestDisallowInterceptTouchEvent(false);settle()}
        };return true
    }
    override fun performClick():Boolean{super.performClick();return true}

    private fun snap(value:CompositionTransform,b:Bitmap):CompositionTransform{
        val threshold=8f*resources.displayMetrics.density/mapScale
        fun axis(v:Float,size:Float):Float{val px=v*size;val c=floatArrayOf(size/3f,size/2f,size*2f/3f);return c.minByOrNull{abs(it-px)}?.takeIf{abs(it-px)<=threshold}?.div(size)?:v}
        val result=value.copy(centerX=axis(value.centerX,b.width.toFloat()),centerY=axis(value.centerY,b.height.toFloat()))
        if(result!=value&&!snapped){performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);snapped=true};if(result==value)snapped=false;return result
    }

    private fun settle(){
        val b=bitmap;if(b==null){gestureActive=false;return}
        val start=transform;val end=TransformCalculator.clamp(b.width.toFloat(),b.height.toFloat(),canvasSize.width.toFloat(),canvasSize.height.toFloat(),start)
        if(start==end){gestureActive=false;onGestureFinished?.invoke(end);return}
        ValueAnimator.ofFloat(0f,1f).apply{duration=180;interpolator=DecelerateInterpolator();addUpdateListener{
            val f=it.animatedFraction;transform=CompositionTransform(start.zoom+(end.zoom-start.zoom)*f,start.centerX+(end.centerX-start.centerX)*f,start.centerY+(end.centerY-start.centerY)*f,end.allowBackground);invalidate();onTransformChanged?.invoke(transform)
        };addListener(object:android.animation.AnimatorListenerAdapter(){override fun onAnimationEnd(animation:android.animation.Animator){gestureActive=false;transform=end;onGestureFinished?.invoke(end)}});start()}
    }
}

class WallpaperPreviewView @JvmOverloads constructor(context:Context,attrs:AttributeSet?=null):View(context,attrs){
    var bitmap:Bitmap?=null;set(value){field=value;invalidate()}
    var config=WallpaperConfig();set(value){field=value;invalidate()}
    var transform=CompositionTransform();set(value){field=value;invalidate()}
    var canvasSize=PixelSize(1,1);set(value){field=value;invalidate()}
    var fillBounds=false;set(value){field=value;invalidate()}
    private val previewRect=RectF();private val borderPaint=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;color=Color.WHITE}
    override fun onDraw(canvas:Canvas){super.onDraw(canvas);canvas.drawColor(Color.rgb(22,22,25));val b=bitmap?:return
        val ratio=canvasSize.width.toFloat()/canvasSize.height
        if(fillBounds)previewRect.set(0f,0f,width.toFloat(),height.toFloat())else{val w=min(width*.94f,height*.94f*ratio);val h=w/ratio;previewRect.set((width-w)/2,(height-h)/2,(width+w)/2,(height+h)/2)}
        canvas.save();canvas.clipRect(previewRect);canvas.translate(previewRect.left,previewRect.top);WallpaperRenderer.draw(canvas,b,config,transform,previewRect.width().toInt(),previewRect.height().toInt());canvas.restore()
        if(!fillBounds){borderPaint.strokeWidth=2f*resources.displayMetrics.density;canvas.drawRect(previewRect,borderPaint)}
    }
}
