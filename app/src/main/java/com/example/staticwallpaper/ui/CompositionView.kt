package com.example.staticwallpaper.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.example.staticwallpaper.data.OrientationTransform
import com.example.staticwallpaper.render.TransformCalculator
import kotlin.math.min

class CompositionView @JvmOverloads constructor(c: Context, a: AttributeSet? = null) : View(c,a) {
    var bitmap: Bitmap? = null; set(v) { field=v; invalidate() }
    var landscape = true; set(v) { field=v; invalidate() }
    var fullscreenPreview = false; set(v) { field=v; invalidate() }
    var transform = OrientationTransform()
        private set
    fun setExternalTransform(v: OrientationTransform) {
        // Compose may re-run AndroidView.update while a finger is still down.
        // Never let an asynchronous state emission replace the in-progress gesture.
        if (!gestureActive) {
            transform=v
            invalidate()
        }
    }
    var onTransformChanged: ((OrientationTransform)->Unit)? = null
    var onGestureFinished: ((OrientationTransform)->Unit)? = null
    private var lastX=0f; private var lastY=0f
    private var gestureActive=false
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val scaleDetector=ScaleGestureDetector(c, object: ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            transform=transform.copy(scale=(transform.scale*d.scaleFactor).coerceIn(.05f,20f))
            invalidate(); onTransformChanged?.invoke(transform); return true
        }
    })
    private fun cropRect(): RectF {
        val ratio=if(landscape) 1.6f else .625f
        val maxW=width*(if(fullscreenPreview) 1f else .88f)
        val maxH=height*(if(fullscreenPreview) 1f else .82f)
        val w=min(maxW,maxH*ratio); val h=w/ratio
        return RectF((width-w)/2,(height-h)/2,(width+w)/2,(height+h)/2)
    }
    override fun onDraw(c: Canvas) {
        super.onDraw(c); c.drawColor(Color.rgb(35,35,39)); val b=bitmap ?: return
        val r=cropRect(); c.save(); c.clipRect(r)
        val tr=TransformCalculator.calculate(b.width.toFloat(),b.height.toFloat(),r.width(),r.height(),transform)
        val m=Matrix().apply { postScale(tr.scale,tr.scale); postTranslate(r.left+tr.translateX,r.top+tr.translateY) }
        c.drawBitmap(b,m,paint); c.restore()
        val shade=Paint().apply { color=0x99000000.toInt() }
        c.drawRect(0f,0f,width.toFloat(),r.top,shade); c.drawRect(0f,r.bottom,width.toFloat(),height.toFloat(),shade)
        c.drawRect(0f,r.top,r.left,r.bottom,shade); c.drawRect(r.right,r.top,width.toFloat(),r.bottom,shade)
        c.drawRect(r,Paint().apply { style=Paint.Style.STROKE; strokeWidth=3f; color=Color.WHITE })
    }
    override fun onTouchEvent(e: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(e)
        when(e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                gestureActive=true; lastX=e.x;lastY=e.y
                parent.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                // A second finger changes the gesture centroid. Reset the one-finger
                // baseline so lifting either finger cannot create a large jump.
                lastX=scaleDetector.focusX; lastY=scaleDetector.focusY
            }
            MotionEvent.ACTION_MOVE -> if(!scaleDetector.isInProgress && e.pointerCount==1) {
                val b=bitmap; if(b!=null) { val r=cropRect(); transform=TransformCalculator.pixelDeltaToNormalized(b.width.toFloat(),b.height.toFloat(),r.width(),r.height(),transform,e.x-lastX,e.y-lastY); invalidate(); onTransformChanged?.invoke(transform) };lastX=e.x;lastY=e.y
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val remaining=if(e.actionIndex==0) 1 else 0
                if(remaining<e.pointerCount) { lastX=e.getX(remaining); lastY=e.getY(remaining) }
            }
            MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL -> {
                gestureActive=false
                parent.requestDisallowInterceptTouchEvent(false)
                onGestureFinished?.invoke(transform)
            }
        }; return true
    }
}
