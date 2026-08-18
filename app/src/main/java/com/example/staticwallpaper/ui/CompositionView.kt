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
    var config=WallpaperConfig();set(value){field=value;invalidate()}
    var canvasSize=PixelSize(1,1);set(value){field=value;invalidate()}
    var transform=CompositionTransform();private set
    var onTransformChanged:((CompositionTransform)->Unit)?=null
    var onGestureStarted:(()->Unit)?=null
    var onGestureFinished:((CompositionTransform)->Unit)?=null
    private var gestureActive=false
    private var lastX=0f;private var lastY=0f
    private val frameRect=RectF()
    private var snapped=false
    private val imageMatrix=Matrix()
    private val imagePaint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
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
            val result=TransformCalculator.calculate(b.width.toFloat(),b.height.toFloat(),frameRect.width(),frameRect.height(),transform)
            val fx=(detector.focusX-frameRect.left-result.translateX)/result.scale
            val fy=(detector.focusY-frameRect.top-result.translateY)/result.scale
            transform=TransformCalculator.zoomAround(transform,b.width.toFloat(),b.height.toFloat(),fx,fy,detector.scaleFactor)
            invalidate();onTransformChanged?.invoke(transform);return true
        }
    })

    fun setExternalTransform(value: CompositionTransform){if(!gestureActive){transform=value;invalidate()}}

    override fun onDraw(canvas: Canvas){
        super.onDraw(canvas);canvas.drawColor(Color.rgb(28,28,30));val b=bitmap?:return
        val ratio=canvasSize.width.toFloat()/canvasSize.height.toFloat()
        val frameW=min(width*.92f,height*.82f*ratio);val frameH=frameW/ratio
        frameRect.set((width-frameW)/2f,(height-frameH)/2f,(width+frameW)/2f,(height+frameH)/2f)
        val result=TransformCalculator.calculate(b.width.toFloat(),b.height.toFloat(),frameW,frameH,transform)
        imageMatrix.reset();imageMatrix.setScale(result.scale,result.scale);imageMatrix.postTranslate(frameRect.left+result.translateX,frameRect.top+result.translateY)
        // The dimmed area keeps the surrounding image visible while the fixed frame is the exact wallpaper result.
        canvas.drawBitmap(b,imageMatrix,imagePaint)
        canvas.save();canvas.clipRect(frameRect);canvas.translate(frameRect.left,frameRect.top)
        WallpaperRenderer.draw(canvas,b,config,transform,frameW.toInt(),frameH.toInt());canvas.restore()
        canvas.save();canvas.clipOutRect(frameRect);canvas.drawColor(0x88000000.toInt());canvas.restore()
        borderPaint.strokeWidth=2f*resources.displayMetrics.density;guidePaint.strokeWidth=2f*resources.displayMetrics.density;canvas.drawRect(frameRect,borderPaint)
        canvas.drawLine(frameRect.centerX(),frameRect.top,frameRect.centerX(),frameRect.bottom,guidePaint);canvas.drawLine(frameRect.left,frameRect.centerY(),frameRect.right,frameRect.centerY(),guidePaint)
        canvas.drawLine(frameRect.left+frameRect.width()/3,frameRect.top,frameRect.left+frameRect.width()/3,frameRect.bottom,guidePaint);canvas.drawLine(frameRect.left+frameRect.width()*2/3,frameRect.top,frameRect.left+frameRect.width()*2/3,frameRect.bottom,guidePaint)
        canvas.drawLine(frameRect.left,frameRect.top+frameRect.height()/3,frameRect.right,frameRect.top+frameRect.height()/3,guidePaint);canvas.drawLine(frameRect.left,frameRect.top+frameRect.height()*2/3,frameRect.right,frameRect.top+frameRect.height()*2/3,guidePaint)
    }

    override fun onTouchEvent(event: MotionEvent):Boolean{
        gestureDetector.onTouchEvent(event);scaleDetector.onTouchEvent(event)
        when(event.actionMasked){
            MotionEvent.ACTION_DOWN->{gestureActive=true;onGestureStarted?.invoke();lastX=event.x;lastY=event.y;snapped=false;parent.requestDisallowInterceptTouchEvent(true)}
            MotionEvent.ACTION_POINTER_DOWN->{lastX=scaleDetector.focusX;lastY=scaleDetector.focusY}
            MotionEvent.ACTION_MOVE->if(!scaleDetector.isInProgress&&event.pointerCount==1){
                val b=bitmap;if(b!=null){transform=TransformCalculator.moveImageByCanvasPixels(transform,b.width.toFloat(),b.height.toFloat(),frameRect.width(),frameRect.height(),event.x-lastX,event.y-lastY);transform=snap(transform,b);invalidate();onTransformChanged?.invoke(transform)};lastX=event.x;lastY=event.y
            }
            MotionEvent.ACTION_POINTER_UP->{val remaining=if(event.actionIndex==0)1 else 0;if(remaining<event.pointerCount){lastX=event.getX(remaining);lastY=event.getY(remaining)}}
            MotionEvent.ACTION_UP->{performClick();parent.requestDisallowInterceptTouchEvent(false);settle()}
            MotionEvent.ACTION_CANCEL->{parent.requestDisallowInterceptTouchEvent(false);settle()}
        };return true
    }
    override fun performClick():Boolean{super.performClick();return true}

    private fun snap(value:CompositionTransform,b:Bitmap):CompositionTransform{
        val scale=TransformCalculator.calculate(b.width.toFloat(),b.height.toFloat(),frameRect.width(),frameRect.height(),value).scale
        val threshold=8f*resources.displayMetrics.density/scale.coerceAtLeast(.0001f)
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
    override fun onDraw(canvas:Canvas){super.onDraw(canvas);canvas.drawColor(Color.BLACK);val b=bitmap?:return
        val ratio=canvasSize.width.toFloat()/canvasSize.height
        if(fillBounds)previewRect.set(0f,0f,width.toFloat(),height.toFloat())else{val w=min(width*.94f,height*.94f*ratio);val h=w/ratio;previewRect.set((width-w)/2,(height-h)/2,(width+w)/2,(height+h)/2)}
        canvas.save();canvas.clipRect(previewRect);canvas.translate(previewRect.left,previewRect.top);WallpaperRenderer.draw(canvas,b,config,transform,previewRect.width().toInt(),previewRect.height().toInt());canvas.restore()
        if(!fillBounds){borderPaint.strokeWidth=2f*resources.displayMetrics.density;canvas.drawRect(previewRect,borderPaint)}
    }
}
