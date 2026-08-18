package com.example.staticwallpaper.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** iOS/iPadOS reference colors published by Apple in 2025, mapped to Android semantic roles. */
object ApplePalette {
    val PurpleLight=Color(0xFFCB30E0)
    val PurpleDark=Color(0xFFDB34F2)
    val RedLight=Color(0xFFFF383C)
    val RedDark=Color(0xFFFF4245)
    val Gray2Light=Color(0xFFAEAEB2)
    val Gray2Dark=Color(0xFF636366)
    val Gray3Light=Color(0xFFC7C7CC)
    val Gray3Dark=Color(0xFF48484A)
    val Gray4Light=Color(0xFFD1D1D6)
    val Gray4Dark=Color(0xFF3A3A3C)
    val Gray5Light=Color(0xFFE5E5EA)
    val Gray5Dark=Color(0xFF2C2C2E)
    val Gray6Light=Color(0xFFF2F2F7)
    val Gray6Dark=Color(0xFF1C1C1E)
}

object AppleMetrics {
    val Hairline=2.dp
    val Small=8.dp
    val Medium=12.dp
    val Page=16.dp
    val ControlHeight=44.dp
}

private val LightColors=lightColorScheme(
    primary=ApplePalette.PurpleLight,onPrimary=Color.White,
    primaryContainer=ApplePalette.Gray6Light,onPrimaryContainer=Color.Black,
    secondary=ApplePalette.PurpleLight,onSecondary=Color.White,
    background=ApplePalette.Gray6Light,onBackground=Color.Black,
    surface=Color.White,onSurface=Color.Black,
    surfaceVariant=ApplePalette.Gray5Light,onSurfaceVariant=Color.Black,
    outline=ApplePalette.Gray3Light,outlineVariant=ApplePalette.Gray4Light,
    error=ApplePalette.RedLight,onError=Color.White
)

private val DarkColors=darkColorScheme(
    primary=ApplePalette.PurpleDark,onPrimary=Color.White,
    primaryContainer=ApplePalette.Gray5Dark,onPrimaryContainer=Color.White,
    secondary=ApplePalette.PurpleDark,onSecondary=Color.White,
    background=Color.Black,onBackground=Color.White,
    surface=ApplePalette.Gray6Dark,onSurface=Color.White,
    surfaceVariant=ApplePalette.Gray5Dark,onSurfaceVariant=Color.White,
    outline=ApplePalette.Gray3Dark,outlineVariant=ApplePalette.Gray4Dark,
    error=ApplePalette.RedDark,onError=Color.White
)

private val AppleTypography=Typography(
    headlineSmall=TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.SemiBold,fontSize=22.sp,lineHeight=28.sp),
    titleLarge=TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.SemiBold,fontSize=20.sp,lineHeight=24.sp),
    titleMedium=TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.SemiBold,fontSize=16.sp,lineHeight=20.sp),
    bodyLarge=TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Normal,fontSize=16.sp,lineHeight=22.sp),
    bodyMedium=TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Normal,fontSize=14.sp,lineHeight=20.sp),
    labelLarge=TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.SemiBold,fontSize=16.sp,lineHeight=20.sp),
    labelMedium=TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Medium,fontSize=14.sp,lineHeight=18.sp),
    labelSmall=TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Normal,fontSize=12.sp,lineHeight=16.sp)
)

@Composable
fun AppleTabletTheme(content:@Composable ()->Unit){
    MaterialTheme(
        colorScheme=if(isSystemInDarkTheme())DarkColors else LightColors,
        typography=AppleTypography,
        shapes=Shapes(small=androidx.compose.foundation.shape.RoundedCornerShape(8.dp),medium=androidx.compose.foundation.shape.RoundedCornerShape(12.dp),large=androidx.compose.foundation.shape.RoundedCornerShape(16.dp)),
        content=content
    )
}
