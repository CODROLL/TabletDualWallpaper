package com.example.staticwallpaper.render

import com.example.staticwallpaper.data.*
import com.example.staticwallpaper.ui.EditHistory
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.max

class TransformCalculatorTest {
    @Test fun pixelEditorOnlyProducesEvenCoordinates(){
        assertEquals(100,TransformCalculator.nearestEvenPixel(99f))
        assertEquals(102,TransformCalculator.nearestEvenPixel(101f))
        assertEquals(-2,TransformCalculator.nearestEvenPixel(-3f))
    }

    @Test fun bitmapSamplingStrictlyCapsDecodedLongEdge(){
        assertEquals(1,BitmapDecoder.calculateSampleSize(4000,2250,4096))
        assertEquals(2,BitmapDecoder.calculateSampleSize(4000,2250,2560))
        assertEquals(4,BitmapDecoder.calculateSampleSize(6000,3375,1600))
    }

    @Test fun physicalProfilesCoverCommonTabletRatios(){
        listOf(2560 to 1600,2000 to 1200,2160 to 1440,1920 to 1080,1601 to 1201).forEach{(w,h)->
            val p=DisplayProfile(w,h,320);assertEquals(max(w,h),p.landscape.width);assertTrue(p.landscape.width>p.landscape.height);assertEquals(p.landscape.width,p.portrait.height)
        }
    }

    @Test fun centerCropAndFitCenter(){
        assertEquals(1.6f,TransformCalculator.centerCropScale(1000f,1000f,1600f,1000f),.0001f)
        assertEquals(1f,TransformCalculator.fitCenterScale(1000f,1000f,1600f,1000f),.0001f)
        assertFalse(TransformCalculator.centerCrop(1000f,1000f,1600f,1000f).allowBackground)
        assertTrue(TransformCalculator.fitCenter().allowBackground)
    }

    @Test fun focalPointRemainsStationaryWhileZooming(){
        val iw=4000f;val ih=3000f;val vw=2000f;val vh=1200f;val focusX=3100f;val focusY=800f
        val old=CompositionTransform(1.4f,.55f,.45f,true);val before=TransformCalculator.calculate(iw,ih,vw,vh,old)
        val next=TransformCalculator.zoomAround(old,iw,ih,focusX,focusY,1.7f);val after=TransformCalculator.calculate(iw,ih,vw,vh,next)
        assertEquals(focusX*before.scale+before.translateX,focusX*after.scale+after.translateX,.01f)
        assertEquals(focusY*before.scale+before.translateY,focusY*after.scale+after.translateY,.01f)
    }

    @Test fun panWorksOnBothAxesEvenWhenImageFits(){
        val start=CompositionTransform(1f,.5f,.5f,true);val moved=TransformCalculator.panBySource(start,1000f,1000f,50f,-25f)
        assertEquals(.55f,moved.centerX,.0001f);assertEquals(.475f,moved.centerY,.0001f)
    }

    @Test fun draggingImageMovesRenderedPixelsWithFinger(){
        val iw=4000f;val ih=2250f;val vw=1600f;val vh=1000f
        val start=TransformCalculator.centerCrop(iw,ih,vw,vh)
        val before=TransformCalculator.calculate(iw,ih,vw,vh,start)
        val moved=TransformCalculator.moveImageByCanvasPixels(start,iw,ih,vw,vh,120f,-45f)
        val after=TransformCalculator.calculate(iw,ih,vw,vh,moved)
        assertEquals(before.translateX+120f,after.translateX,.01f)
        assertEquals(before.translateY-45f,after.translateY,.01f)
    }

    @Test fun lockOutputIsCappedWithoutChangingAspectRatio(){
        assertEquals(2560 to 1600,LockScreenSetter.safeOutputSize(2560,1600))
        val (w,h)=LockScreenSetter.safeOutputSize(8000,5000)
        assertTrue(w.toLong()*h<=4_194_304L)
        assertEquals(1.6f,w.toFloat()/h,.002f)
    }

    @Test fun fillClampNeverExposesBackground(){
        val iw=4000f;val ih=2000f;val vw=1000f;val vh=1600f
        val t=TransformCalculator.clamp(iw,ih,vw,vh,CompositionTransform(.1f,-5f,8f,false));val r=TransformCalculator.calculate(iw,ih,vw,vh,t)
        assertTrue(r.translateX<=.001f);assertTrue(r.translateY<=.001f)
        assertTrue(r.translateX+iw*r.scale>=vw-.001f);assertTrue(r.translateY+ih*r.scale>=vh-.001f)
    }

    @Test fun exactFitNeverCreatesAnEmptyCenterRange(){
        listOf(
            floatArrayOf(2560f,1600f,2560f,1600f),
            floatArrayOf(1600f,2560f,1600f,2560f),
            floatArrayOf(4000f,2500f,2560f,1600f),
            floatArrayOf(2500f,4000f,1600f,2560f)
        ).forEach{v->
            val fill=TransformCalculator.centerCrop(v[0],v[1],v[2],v[3]).copy(centerX=2f,centerY=-1f)
            val clamped=TransformCalculator.clamp(v[0],v[1],v[2],v[3],fill)
            assertEquals(.5f,clamped.centerX,.0001f)
            assertEquals(.5f,clamped.centerY,.0001f)
        }
    }

    @Test fun backgroundModeKeepsTwentyPercentVisible(){
        val iw=1000f;val ih=1000f;val vw=1600f;val vh=1000f
        val t=TransformCalculator.clamp(iw,ih,vw,vh,CompositionTransform(1f,99f,-99f,true));val r=TransformCalculator.calculate(iw,ih,vw,vh,t)
        val left=r.translateX;val top=r.translateY;val right=left+iw*r.scale;val bottom=top+ih*r.scale
        val intersectionW=(minOf(vw,right)-maxOf(0f,left)).coerceAtLeast(0f);val intersectionH=(minOf(vh,bottom)-maxOf(0f,top)).coerceAtLeast(0f)
        assertTrue(intersectionW+1f>=minOf(vw*.2f,iw*r.scale));assertTrue(intersectionH+1f>=minOf(vh*.2f,ih*r.scale))
    }

    @Test fun legacyMigrationProducesIdenticalMatrix(){
        val iw=4000f;val ih=2250f;val vw=2560f;val vh=1600f;val old=LegacyTransform(2f,.65f,-.4f)
        val fit=TransformCalculator.fitCenterScale(iw,ih,vw,vh);val s=fit*old.scale;val oldX=(vw-iw*s)/2+old.offsetX*((iw*s-vw)/2).coerceAtLeast(0f);val oldY=(vh-ih*s)/2+old.offsetY*((ih*s-vh)/2).coerceAtLeast(0f)
        val migrated=TransformCalculator.migrateLegacy(iw,ih,vw,vh,old);val result=TransformCalculator.calculate(iw,ih,vw,vh,migrated)
        assertEquals(s,result.scale,.001f);assertEquals(oldX,result.translateX,.01f);assertEquals(oldY,result.translateY,.01f)
    }

    @Test fun desktopAndLockImagesAndTransformsAreIndependent(){
        val d=WallpaperSourceConfig("desktop",CompositionTransform(2f,.2f,.3f),CompositionTransform(3f,.4f,.5f))
        val l=WallpaperSourceConfig("lock",CompositionTransform(4f,.6f,.7f),CompositionTransform(5f,.8f,.9f))
        val c=WallpaperConfig(desktop=d,lock=l);assertEquals("desktop",c.source(WallpaperTarget.DESKTOP).imageUri);assertEquals("lock",c.source(WallpaperTarget.LOCK).imageUri);assertEquals(5f,c.transform(WallpaperTarget.LOCK,false).zoom)
        val copied=c.copy(lock=d.copy());val changed=copied.copy(lock=copied.lock.copy(imageUri="changed"));assertEquals("desktop",changed.desktop.imageUri);assertEquals("changed",changed.lock.imageUri)
    }

    @Test fun lockRenderSelectionUsesRealSurfaceOrientation(){
        val landscapeTransform=CompositionTransform(2f,.25f,.4f)
        val portraitTransform=CompositionTransform(3f,.75f,.6f)
        val config=WallpaperConfig(lock=WallpaperSourceConfig(
            imageUri="lock-landscape",
            landscape=landscapeTransform,
            portrait=portraitTransform,
            portraitImageUri="lock-portrait"
        ))

        val landscape=config.renderSelection(WallpaperTarget.LOCK,2560,1600)
        assertTrue(landscape.landscape)
        assertEquals("lock-landscape",landscape.imageUri)
        assertEquals(landscapeTransform,landscape.transform)

        val portrait=config.renderSelection(WallpaperTarget.LOCK,1600,2560)
        assertFalse(portrait.landscape)
        assertEquals("lock-portrait",portrait.imageUri)
        assertEquals(portraitTransform,portrait.transform)
    }

    @Test fun lockPortraitFallsBackToSharedImageUntilItIsReplaced(){
        val shared=WallpaperSourceConfig(imageUri="shared")
        assertEquals("shared",shared.imageUri(true))
        assertEquals("shared",shared.imageUri(false))

        val replacedPortrait=shared.withImageUri(false,"portrait")
        assertEquals("shared",replacedPortrait.imageUri(true))
        assertEquals("portrait",replacedPortrait.imageUri(false))

        val replacedLandscape=replacedPortrait.withImageUri(true,"landscape")
        assertEquals("landscape",replacedLandscape.imageUri(true))
        assertEquals("portrait",replacedLandscape.imageUri(false))
    }

    @Test fun physicalOrientationUsesNaturalDeviceShapeAndRejectsDiagonals(){
        assertTrue(PhysicalOrientationResolver.isLandscape(0,true)!!)
        assertTrue(PhysicalOrientationResolver.isLandscape(180,true)!!)
        assertFalse(PhysicalOrientationResolver.isLandscape(90,true)!!)
        assertFalse(PhysicalOrientationResolver.isLandscape(270,true)!!)

        assertFalse(PhysicalOrientationResolver.isLandscape(0,false)!!)
        assertTrue(PhysicalOrientationResolver.isLandscape(90,false)!!)
        assertFalse(PhysicalOrientationResolver.isLandscape(359,false)!!)

        assertNull(PhysicalOrientationResolver.isLandscape(45,true))
        assertNull(PhysicalOrientationResolver.isLandscape(135,true))
        assertNull(PhysicalOrientationResolver.isLandscape(PhysicalOrientationResolver.UNKNOWN,true))
    }

    @Test fun editHistorySupportsReplaceUndoRedoAndDiscard(){
        val original=WallpaperConfig(desktop=WallpaperSourceConfig("old"));val replacement=original.copy(desktop=WallpaperSourceConfig("new"));val h=EditHistory(original)
        h.commit(replacement);assertEquals("new",h.current.desktop.imageUri);assertEquals("old",h.undo().desktop.imageUri);assertEquals("new",h.redo().desktop.imageUri);assertEquals("old",original.desktop.imageUri)
    }
}
