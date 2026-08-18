package com.example.staticwallpaper

import android.app.WallpaperManager
import android.content.*
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.example.staticwallpaper.data.*
import com.example.staticwallpaper.render.BitmapDecoder
import com.example.staticwallpaper.render.TransformCalculator
import com.example.staticwallpaper.service.StaticWallpaperService
import com.example.staticwallpaper.ui.CompositionView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private val repo by lazy { ConfigRepository(applicationContext) }
    override fun onCreate(s: Bundle?) { super.onCreate(s); setContent { MaterialTheme { App() } } }

    @Composable private fun App() {
        val config by repo.config.collectAsState(initial=WallpaperConfig())
        var page by remember { mutableStateOf("home") }
        var bitmap by remember { mutableStateOf<Bitmap?>(null) }
        LaunchedEffect(config.imageUri,config.memoryMode) {
            val old=bitmap
            bitmap=if(config.imageUri==null) null else withContext(Dispatchers.IO) { runCatching { BitmapDecoder.decode(contentResolver,Uri.parse(config.imageUri),config.memoryMode.maxLongEdge) }.getOrNull() }
            if(old!==bitmap) old?.recycle()
        }
        DisposableEffect(Unit) { onDispose { bitmap?.recycle() } }
        when(page) {
            "edit" -> Editor(config,bitmap,{ lifecycleScope.launch { repo.save(it) } },{page="home"})
            "settings" -> Settings(config,{ lifecycleScope.launch { repo.save(it) } },{page="home"})
            else -> Home(config,bitmap,{page=it})
        }
    }

    @Composable private fun Home(config:WallpaperConfig,b:Bitmap?,open:(String)->Unit) {
        val ctx=LocalContext.current
        val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let {
            runCatching { contentResolver.takePersistableUriPermission(it,Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            lifecycleScope.launch {
                val selected=withContext(Dispatchers.IO) { runCatching { BitmapDecoder.decode(contentResolver,it,config.memoryMode.maxLongEdge) }.getOrNull() }
                if(selected==null) Toast.makeText(ctx,"无法读取此图片，请换一张图片重试。",Toast.LENGTH_LONG).show()
                else {
                    val iw=selected.width.toFloat(); val ih=selected.height.toFloat()
                    repo.save(config.copy(imageUri=it.toString(),landscape=TransformCalculator.defaultFor(iw,ih,1600f,1000f,true),portrait=TransformCalculator.defaultFor(iw,ih,1000f,1600f,true)))
                    selected.recycle()
                }
            }
        } }
        Scaffold(topBar={TopAppBar(title={Text("静态动态壁纸")},actions={TextButton(onClick={open("settings")}){Text("设置")}})}) { pad ->
            Column(Modifier.padding(pad).padding(20.dp).fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                if(b==null) Card(Modifier.fillMaxWidth().height(260.dp)){Box(Modifier.fillMaxSize().padding(24.dp)){Text("请选择一张 PNG、JPG、JPEG 或 WEBP 图片")}}
                else Image(b.asImageBitmap(),null,Modifier.fillMaxWidth().height(280.dp),contentScale=ContentScale.Fit)
                Button({picker.launch(arrayOf("image/png","image/jpeg","image/webp"))},Modifier.fillMaxWidth()){Text("选择图片")}
                Button({if(b!=null) open("edit") else Toast.makeText(ctx,"请先选择图片",Toast.LENGTH_SHORT).show()},Modifier.fillMaxWidth()){Text("调整显示区域")}
                OutlinedButton({if(b!=null) openWallpaperPreview() else Toast.makeText(ctx,"请先选择图片",Toast.LENGTH_SHORT).show()},Modifier.fillMaxWidth()){Text("预览并设置为系统壁纸")}
                Text("壁纸仅在创建、尺寸变化、变为可见或设置变化时绘制，不运行持续帧循环。",style=MaterialTheme.typography.bodySmall)
            }
        }
    }

    @Composable private fun Editor(config:WallpaperConfig,b:Bitmap?,save:(WallpaperConfig)->Unit,back:()->Unit) {
        var landscape by remember { mutableStateOf(true) }
        var fullscreen by remember { mutableStateOf(false) }
        // Initialize once when entering the editor. DataStore emissions must not
        // replace a transform that is currently being manipulated.
        var local by remember { mutableStateOf(config) }
        fun current()=if(landscape)local.landscape else local.portrait
        fun updateInMemory(t:OrientationTransform){ local=if(landscape)local.copy(landscape=t) else local.copy(portrait=t) }
        fun commit(t:OrientationTransform=current()){ updateInMemory(t);save(local) }
        DisposableEffect(fullscreen) {
            val controller=WindowInsetsControllerCompat(window,window.decorView)
            if(fullscreen) {
                WindowCompat.setDecorFitsSystemWindows(window,false)
                controller.systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                WindowCompat.setDecorFitsSystemWindows(window,true)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
            onDispose {
                WindowCompat.setDecorFitsSystemWindows(window,true)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        Scaffold(topBar={if(!fullscreen) TopAppBar(title={Text("构图编辑")},navigationIcon={TextButton(onClick={commit();back()}){Text("返回")}},actions={TextButton(onClick={fullscreen=true}){Text("全屏")}})}) { pad ->
            Column(Modifier.padding(pad).fillMaxSize()) {
                if(!fullscreen) TabRow(if(landscape)0 else 1) {
                    Tab(selected=landscape,onClick={commit();landscape=true},text={Text("横屏区域 16:10",Modifier.padding(12.dp))})
                    Tab(selected=!landscape,onClick={commit();landscape=false},text={Text("竖屏区域 10:16",Modifier.padding(12.dp))})
                }
                val iw=b?.width?.toFloat()?:1f;val ih=b?.height?.toFloat()?:1f
                val vw=if(landscape)2560f else 1600f;val vh=if(landscape)1600f else 2560f
                val result=TransformCalculator.calculate(iw,ih,vw,vh,current())
                val sourceX=((vw/2f-result.translateX)/result.scale).coerceIn(0f,iw)
                val sourceY=((vh/2f-result.translateY)/result.scale).coerceIn(0f,ih)
                val offsetX=result.translateX+(iw*result.scale-vw)/2f
                val offsetY=result.translateY+(ih*result.scale-vh)/2f
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    AndroidView(factory={CompositionView(it)},update={v->
                        v.bitmap=b;v.landscape=landscape;v.fullscreenPreview=fullscreen;v.setExternalTransform(current())
                        v.onTransformChanged={updateInMemory(it)}
                        v.onGestureFinished={commit(it)}
                    },modifier=Modifier.fillMaxSize())
                    if(fullscreen) Row(Modifier.fillMaxWidth().padding(8.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                        FilledTonalButton(onClick={commit();fullscreen=false}){Text("退出全屏")}
                        FilledTonalButton(onClick={commit();landscape=!landscape}){Text(if(landscape)"切到竖屏" else "切到横屏")}
                    }
                    if(fullscreen) Surface(Modifier.align(Alignment.BottomCenter).padding(8.dp),color=MaterialTheme.colorScheme.surface.copy(alpha=.82f),shape=MaterialTheme.shapes.small) {
                        Text("中心 (${sourceX.toInt()}, ${sourceY.toInt()}) px  ·  位移 (${offsetX.toInt()}, ${offsetY.toInt()}) px  ·  ${"%.1f".format(result.scale*100)}%",Modifier.padding(horizontal=10.dp,vertical=6.dp),style=MaterialTheme.typography.labelMedium)
                    }
                }
                if(!fullscreen) {
                    Text("像素位：原图中心 (${sourceX.toInt()}, ${sourceY.toInt()}) px  ·  位移 (${offsetX.toInt()}, ${offsetY.toInt()}) px  ·  缩放 ${"%.1f".format(result.scale*100)}%",Modifier.padding(horizontal=12.dp,vertical=4.dp),style=MaterialTheme.typography.bodySmall)
                    Text("按 ${vw.toInt()} × ${vh.toInt()} 预估；实际壁纸以系统 Surface 为准",Modifier.padding(horizontal=12.dp),style=MaterialTheme.typography.labelSmall)
                    Row(Modifier.fillMaxWidth().padding(6.dp),horizontalArrangement=Arrangement.SpaceEvenly) {
                        TextButton({commit(current().copy(normalizedOffsetX=0f,normalizedOffsetY=0f))}){Text("居中")}
                        TextButton({commit(TransformCalculator.defaultFor(iw,ih,vw,vh,true))}){Text("填满")}
                        TextButton({commit(TransformCalculator.defaultFor(iw,ih,vw,vh,false))}){Text("完整显示")}
                        TextButton({commit(TransformCalculator.defaultFor(iw,ih,vw,vh,true))}){Text("恢复默认")}
                    }
                    Row(Modifier.fillMaxWidth().padding(bottom=8.dp),horizontalArrangement=Arrangement.SpaceEvenly) {
                        OutlinedButton({local=local.copy(portrait=local.landscape);save(local)}){Text("横屏复制到竖屏")}
                        OutlinedButton({local=local.copy(landscape=local.portrait);save(local)}){Text("竖屏复制到横屏")}
                    }
                }
            }
        }
    }

    @Composable private fun Settings(c:WallpaperConfig,save:(WallpaperConfig)->Unit,back:()->Unit) {
        Scaffold(topBar={TopAppBar(title={Text("设置")},navigationIcon={TextButton(onClick=back){Text("返回")}})}) { p -> Column(Modifier.padding(p).padding(20.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text("背景填充方式",style=MaterialTheme.typography.titleMedium)
            BackgroundMode.entries.forEach { mode -> Row(Modifier.fillMaxWidth()) { RadioButton(c.backgroundMode==mode,{save(c.copy(backgroundMode=mode))});Text(when(mode){BackgroundMode.BLACK->"黑色背景";BackgroundMode.COLOR->"自定义纯色（深灰）";BackgroundMode.EDGE->"图片边缘颜色";BackgroundMode.BLUR->"放大柔化背景"},Modifier.padding(top=12.dp)) } }
            HorizontalDivider();Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("跟随桌面滑动（有限视差）");Switch(c.parallaxEnabled,{save(c.copy(parallaxEnabled=it))})}
            Text("图片质量 / 内存模式",style=MaterialTheme.typography.titleMedium)
            MemoryMode.entries.forEach { m->Row{RadioButton(c.memoryMode==m,{save(c.copy(memoryMode=m))});Text(when(m){MemoryMode.SAVING->"节省内存（最长边 3072）";MemoryMode.BALANCED->"均衡（最长边 4096）";MemoryMode.HIGH->"高画质（最长边 6144）"},Modifier.padding(top=12.dp))} }
        } }
    }

    private fun openWallpaperPreview() {
        val component=ComponentName(this,StaticWallpaperService::class.java)
        val direct=Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,component)
        try { startActivity(direct) } catch (_:Exception) { try { startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)) } catch (e:Exception) { Toast.makeText(this,"系统未提供动态壁纸选择器，请在系统设置的壁纸页面手动选择。",Toast.LENGTH_LONG).show() } }
    }
}
