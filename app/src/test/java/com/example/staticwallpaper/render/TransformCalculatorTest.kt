package com.example.staticwallpaper.render

import com.example.staticwallpaper.data.OrientationTransform
import org.junit.Assert.*
import org.junit.Test

class TransformCalculatorTest {
    @Test fun centerCropWideIntoPortrait() { assertEquals(2560f/2258f,TransformCalculator.centerCropScale(4000f,2258f,1600f,2560f),.0001f) }
    @Test fun fitCenterWideIntoPortrait() { assertEquals(.4f,TransformCalculator.fitCenterScale(4000f,2258f,1600f,2560f),.0001f) }
    @Test fun squareIntoLandscapeAndPortrait() {
        assertEquals(1.6f,TransformCalculator.centerCropScale(1000f,1000f,1600f,1000f),.0001f)
        assertEquals(1.6f,TransformCalculator.centerCropScale(1000f,1000f,1000f,1600f),.0001f)
    }
    @Test fun extremeTall() { assertEquals(16f,TransformCalculator.centerCropScale(100f,1000f,1600f,1000f),.0001f) }
    @Test fun normalizedOffsetConvertsToPixels() {
        val r=TransformCalculator.calculate(4000f,2000f,1000f,1000f,OrientationTransform(1f,1f,0f))
        assertEquals(0.25f,r.scale,.0001f);assertEquals(0f,r.translateX,.0001f)
    }
    @Test fun orientationSelectionIsIndependent() {
        val l=OrientationTransform(2f,-1f,.2f); val p=OrientationTransform(3f,1f,-.4f)
        assertNotEquals(l,p);assertEquals(-1f,l.normalizedOffsetX);assertEquals(1f,p.normalizedOffsetX)
    }
    @Test fun surfaceSizeChangeRecalculates() {
        val t=OrientationTransform(2f,.5f,0f)
        val a=TransformCalculator.calculate(4000f,2000f,2560f,1600f,t)
        val b=TransformCalculator.calculate(4000f,2000f,1600f,2560f,t)
        assertNotEquals(a.scale,b.scale);assertNotEquals(a.translateX,b.translateX)
    }
    @Test fun clampFillPreventsBlankSpace() {
        val t=TransformCalculator.clamp(4000f,2000f,1000f,1600f,OrientationTransform(.1f,9f,-9f),true)
        assertTrue(t.scale>1f);assertEquals(1f,t.normalizedOffsetX);assertEquals(-1f,t.normalizedOffsetY)
    }
}
