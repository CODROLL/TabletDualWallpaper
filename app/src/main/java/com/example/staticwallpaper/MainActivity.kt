package com.example.staticwallpaper

import android.app.WallpaperManager
import android.content.*
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.example.staticwallpaper.data.*
import com.example.staticwallpaper.render.BitmapCache
import com.example.staticwallpaper.render.LockScreenSetter
import com.example.staticwallpaper.render.TransformCalculator
import com.example.staticwallpaper.service.StaticWallpaperService
import com.example.staticwallpaper.ui.CropEditorView
import com.example.staticwallpaper.ui.WallpaperPreviewView
import kotlinx.coroutines.launch

private enum class Page { HOME, EDIT, PREVIEW, SETTINGS }
private enum class ApplyTarget { DESKTOP, LOCK, BOTH }

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity:ComponentActivity(){
    private val repo by lazy{ConfigRepository(applicationContext)}
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{MaterialTheme{App()}}}

    @Composable private fun App(){
        val stored by repo.config.collectAsState(initial=WallpaperConfig())
        val configuration=LocalConfiguration.current
        val profile=remember(configuration.orientation,configuration.densityDpi){DisplayProfile.from(this)}
        var page by remember{mutableStateOf(Page.HOME)}
        var selectedTarget by remember{mutableStateOf(WallpaperTarget.DESKTOP)}
        var original by remember{mutableStateOf(stored)}
        var draft by remember{mutableStateOf(stored)}
        var sessionId by remember{mutableIntStateOf(0)}
        var pickerTarget by remember{mutableStateOf(WallpaperTarget.DESKTOP)}
        var pickerBase by remember{mutableStateOf(stored)}

        val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null){
            runCatching{contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)}
            lifecycleScope.launch{
                val lease=BitmapCache.acquire(contentResolver,uri.toString(),pickerBase.memoryMode)
                if(lease==null){Toast.makeText(this@MainActivity,"无法读取图片，请换一张重试",Toast.LENGTH_LONG).show();return@launch}
                val b=lease.bitmap;val l=profile.landscape;val p=profile.portrait
                val source=WallpaperSourceConfig(uri.toString(),TransformCalculator.centerCrop(b.width.toFloat(),b.height.toFloat(),l.width.toFloat(),l.height.toFloat()),TransformCalculator.centerCrop(b.width.toFloat(),b.height.toFloat(),p.width.toFloat(),p.height.toFloat()))
                lease.close();original=pickerBase;draft=pickerBase.withSource(pickerTarget,source);selectedTarget=pickerTarget;sessionId++;page=Page.EDIT
            }
        }}

        val desktopBitmap=rememberBitmap(stored.desktop.imageUri,stored.memoryMode)
        LaunchedEffect(stored.legacy,desktopBitmap){if(stored.legacy!=null&&desktopBitmap!=null)repo.migrateLegacy(stored,desktopBitmap.width,desktopBitmap.height,profile)}

        when(page){
            Page.HOME->Home(stored,profile,onEdit={target->val source=stored.source(target);if(source.imageUri==null){pickerTarget=target;pickerBase=stored;picker.launch(imageTypes)}else{selectedTarget=target;original=stored;draft=stored;sessionId++;page=Page.EDIT}},onReplace={target->pickerTarget=target;pickerBase=stored;picker.launch(imageTypes)},onPreview={if(stored.source(it).imageUri==null)toast("请先选择图片")else{selectedTarget=it;page=Page.PREVIEW}},onSettings={page=Page.SETTINGS})
            Page.EDIT->key(sessionId){Editor(original,draft,selectedTarget,profile,onSave={lifecycleScope.launch{repo.save(it)};page=Page.HOME},onDiscard={page=Page.HOME})}
            Page.PREVIEW->FullPreview(stored,selectedTarget,profile){page=Page.HOME}
            Page.SETTINGS->Settings(stored,{lifecycleScope.launch{repo.save(it)}},{page=Page.HOME})
        }
    }

    @Composable private fun rememberBitmap(uri:String?,mode:MemoryMode):Bitmap?{
        val lease=remember(uri,mode){mutableStateOf<BitmapCache.Lease?>(null)}
        LaunchedEffect(uri,mode){lease.value=if(uri==null)null else BitmapCache.acquire(contentResolver,uri,mode)}
        DisposableEffect(uri,mode){onDispose{lease.value?.close();lease.value=null}}
        return lease.value?.bitmap
    }

    @Composable private fun Home(config:WallpaperConfig,profile:DisplayProfile,onEdit:(WallpaperTarget)->Unit,onReplace:(WallpaperTarget)->Unit,onPreview:(WallpaperTarget)->Unit,onSettings:()->Unit){
        val orientation=LocalConfiguration.current.orientation
        val desktopBitmap=rememberBitmap(config.desktop.imageUri,config.memoryMode);val lockBitmap=rememberBitmap(config.lock.imageUri,config.memoryMode)
        var applyDialog by remember{mutableStateOf(false)};var compatibility by remember{mutableStateOf<ApplyTarget?>(null)};var copyMissing by remember{mutableStateOf<ApplyTarget?>(null)};var pendingCompatibilityConfig by remember{mutableStateOf<WallpaperConfig?>(null)}
        fun openDynamic(target:ApplyTarget,c:WallpaperConfig=config){val legacyTarget=if(target==ApplyTarget.LOCK)WallpaperTarget.LOCK else WallpaperTarget.DESKTOP;lifecycleScope.launch{if(Build.VERSION.SDK_INT<34&&c.legacyDynamicTarget!=legacyTarget)repo.save(c.copy(legacyDynamicTarget=legacyTarget));toast("请在系统预览页确认应用目标");openWallpaperPreview()}}
        fun apply(target:ApplyTarget){
            val d=config.desktop.imageUri;val l=config.lock.imageUri
            if(target==ApplyTarget.BOTH&&(d==null||l==null)){if(d==null&&l==null)toast("请先选择图片")else copyMissing=target;return}
            if(target==ApplyTarget.DESKTOP&&d==null){toast("请先选择桌面图片");return}
            if(target==ApplyTarget.LOCK&&l==null){copyMissing=target;return}
            if((target!=ApplyTarget.DESKTOP)&&Build.VERSION.SDK_INT<34)compatibility=target else openDynamic(target)
        }
        Scaffold(topBar={TopAppBar(title={Text("TabletDualWallpaper")},actions={IconButton(onClick=onSettings){Icon(Icons.Default.Settings,"设置")}})},bottomBar={Surface(shadowElevation=8.dp){Button(onClick={applyDialog=true},Modifier.fillMaxWidth().padding(16.dp)){Text("应用壁纸")}}}){padding->
            BoxWithConstraints(Modifier.padding(padding).padding(16.dp).fillMaxSize()){
                val wide=maxWidth>=720.dp
                if(wide)Row(Modifier.fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(16.dp)){WallpaperCard("桌面壁纸",WallpaperTarget.DESKTOP,config,desktopBitmap,profile,Modifier.weight(1f),onEdit,onReplace,onPreview);WallpaperCard("锁屏壁纸",WallpaperTarget.LOCK,config,lockBitmap,profile,Modifier.weight(1f),onEdit,onReplace,onPreview)}
                else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)){WallpaperCard("桌面壁纸",WallpaperTarget.DESKTOP,config,desktopBitmap,profile,Modifier.fillMaxWidth(),onEdit,onReplace,onPreview);WallpaperCard("锁屏壁纸",WallpaperTarget.LOCK,config,lockBitmap,profile,Modifier.fillMaxWidth(),onEdit,onReplace,onPreview)}
            }
        }
        if(applyDialog)AlertDialog(onDismissRequest={applyDialog=false},title={Text("应用到哪里？")},text={Column{TextButton({applyDialog=false;apply(ApplyTarget.DESKTOP)}){Text("桌面")};TextButton({applyDialog=false;apply(ApplyTarget.LOCK)}){Text("锁屏")};TextButton({applyDialog=false;apply(ApplyTarget.BOTH)}){Text("桌面和锁屏")}}},confirmButton={TextButton({applyDialog=false}){Text("取消")}})
        if(copyMissing!=null)AlertDialog(onDismissRequest={copyMissing=null},title={Text("复用现有图片")},text={Text("当前只有一张图片。是否复制为另一处的独立配置并继续？")},confirmButton={TextButton({val requested=copyMissing?:ApplyTarget.BOTH;val existing=if(config.desktop.imageUri!=null)config.desktop else config.lock;val updated=if(config.desktop.imageUri==null)config.copy(desktop=existing)else config.copy(lock=existing);copyMissing=null;pendingCompatibilityConfig=updated;lifecycleScope.launch{repo.save(updated)};if(requested==ApplyTarget.DESKTOP)openDynamic(requested,updated)else if(Build.VERSION.SDK_INT<34)compatibility=requested else openDynamic(requested,updated)}){Text("复制并继续")}},dismissButton={TextButton({copyMissing=null}){Text("取消")}})
        if(compatibility!=null)AlertDialog(onDismissRequest={compatibility=null},title={Text("锁屏兼容方式")},text={Text("Android 13及以下无法可靠区分桌面与锁屏动态壁纸。建议使用桌面动态壁纸 + 静态锁屏；也可以尝试厂商提供的动态锁屏入口。")},confirmButton={TextButton({val requested=compatibility;val c=pendingCompatibilityConfig?:config;compatibility=null;pendingCompatibilityConfig=null;lifecycleScope.launch{setStaticLock(c,profile.canvas(orientation==Configuration.ORIENTATION_LANDSCAPE));if(requested==ApplyTarget.BOTH)openDynamic(ApplyTarget.DESKTOP,c)}}){Text(if(compatibility==ApplyTarget.LOCK)"设置静态锁屏" else "兼容方式")}},dismissButton={TextButton({val requested=compatibility?:ApplyTarget.LOCK;val c=pendingCompatibilityConfig?:config;compatibility=null;pendingCompatibilityConfig=null;openDynamic(requested,c)}){Text("尝试动态锁屏")}})
    }

    @Composable private fun WallpaperCard(title:String,target:WallpaperTarget,config:WallpaperConfig,bitmap:Bitmap?,profile:DisplayProfile,modifier:Modifier,onEdit:(WallpaperTarget)->Unit,onReplace:(WallpaperTarget)->Unit,onPreview:(WallpaperTarget)->Unit){
        Card(modifier.height(300.dp).clickable{onEdit(target)}){Box(Modifier.fillMaxSize()){
            if(bitmap!=null)AndroidView(factory={WallpaperPreviewView(it)},update={v->v.bitmap=bitmap;v.config=config;v.canvasSize=profile.landscape;v.transform=config.transform(target,true)},modifier=Modifier.fillMaxSize())else Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text("尚未选择图片")}
            Surface(Modifier.align(Alignment.TopStart).padding(12.dp),color=MaterialTheme.colorScheme.surface.copy(alpha=.86f),shape=MaterialTheme.shapes.small){Text(title,Modifier.padding(horizontal=12.dp,vertical=8.dp),style=MaterialTheme.typography.titleMedium)}
            IconButton(onClick={onPreview(target)},Modifier.align(Alignment.TopEnd).padding(8.dp)){Icon(Icons.Default.Fullscreen,"全屏预览")}
            FilledTonalButton(onClick={onReplace(target)},Modifier.align(Alignment.BottomEnd).padding(12.dp)){Text(if(bitmap==null)"选择图片" else "更换图片")}
        }}
    }

    @Composable private fun FullPreview(config:WallpaperConfig,target:WallpaperTarget,profile:DisplayProfile,onClose:()->Unit){
        val orientation=LocalConfiguration.current.orientation;val landscape=orientation==Configuration.ORIENTATION_LANDSCAPE;val source=config.source(target);val bitmap=rememberBitmap(source.imageUri,config.memoryMode)
        DisposableEffect(Unit){val controller=WindowInsetsControllerCompat(window,window.decorView);WindowCompat.setDecorFitsSystemWindows(window,false);controller.hide(WindowInsetsCompat.Type.systemBars());controller.systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE;onDispose{WindowCompat.setDecorFitsSystemWindows(window,true);controller.show(WindowInsetsCompat.Type.systemBars())}}
        BackHandler(onBack=onClose)
        Box(Modifier.fillMaxSize()){AndroidView(factory={WallpaperPreviewView(it)},update={v->v.bitmap=bitmap;v.config=config;v.canvasSize=profile.canvas(landscape);v.transform=config.transform(target,landscape);v.fillBounds=true},modifier=Modifier.fillMaxSize());FilledTonalButton(onClick=onClose,Modifier.align(Alignment.TopStart).padding(16.dp)){Text("退出预览")}}
    }

    @Composable private fun Editor(original:WallpaperConfig,initial:WallpaperConfig,target:WallpaperTarget,profile:DisplayProfile,onSave:(WallpaperConfig)->Unit,onDiscard:()->Unit){
        var local by remember{mutableStateOf(initial)};var landscape by remember{mutableStateOf(true)};var numeric by remember{mutableStateOf(false)};var exitDialog by remember{mutableStateOf(false)}
        val undo=remember{mutableStateListOf<WallpaperConfig>().apply{if(initial!=original)add(original)}};val redo=remember{mutableStateListOf<WallpaperConfig>()};var gestureStart by remember{mutableStateOf<WallpaperConfig?>(null)}
        val bitmap=rememberBitmap(local.source(target).imageUri,local.memoryMode);val size=profile.canvas(landscape);val t=local.transform(target,landscape);val physicalLandscape=LocalConfiguration.current.orientation==Configuration.ORIENTATION_LANDSCAPE
        fun commit(next:WallpaperConfig){if(next==local)return;if(undo.size==50)undo.removeAt(0);undo.add(local);redo.clear();local=next}
        fun setTransform(value:CompositionTransform)=local.withTransform(target,landscape,if(bitmap==null)value else TransformCalculator.clamp(bitmap.width.toFloat(),bitmap.height.toFloat(),size.width.toFloat(),size.height.toFloat(),value))
        fun requestExit(){if(local!=original)exitDialog=true else onDiscard()}
        BackHandler{requestExit()}
        Scaffold(topBar={TopAppBar(title={Text(if(target==WallpaperTarget.DESKTOP)"编辑桌面壁纸" else "编辑锁屏壁纸")},navigationIcon={TextButton(onClick={requestExit()}){Text("返回")}},actions={TextButton(enabled=undo.isNotEmpty(),onClick={redo.add(local);local=undo.removeAt(undo.lastIndex)}){Text("撤销")};TextButton(enabled=redo.isNotEmpty(),onClick={undo.add(local);local=redo.removeAt(redo.lastIndex)}){Text("重做")};TextButton(onClick={onSave(local)}){Text("完成")}})}){padding->
            Column(Modifier.padding(padding).fillMaxSize()){
                TabRow(if(landscape)0 else 1){Tab(landscape,{landscape=true},text={Text("横屏 ${profile.landscape.width}×${profile.landscape.height}",Modifier.padding(10.dp))});Tab(!landscape,{landscape=false},text={Text("竖屏 ${profile.portrait.width}×${profile.portrait.height}",Modifier.padding(10.dp))})}
                val editor:@Composable (Modifier)->Unit={m->AndroidView(factory={CropEditorView(it)},update={v->v.bitmap=bitmap;v.canvasSize=size;v.setExternalTransform(t);v.onGestureStarted={gestureStart=local};v.onTransformChanged={local=local.withTransform(target,landscape,it)};v.onGestureFinished={end->local=local.withTransform(target,landscape,end);gestureStart?.let{before->if(before!=local){if(undo.size==50)undo.removeAt(0);undo.add(before);redo.clear()}};gestureStart=null}},modifier=m)}
                val preview:@Composable (Modifier)->Unit={m->AndroidView(factory={WallpaperPreviewView(it)},update={v->v.bitmap=bitmap;v.config=local;v.canvasSize=size;v.transform=t},modifier=m)}
                if(physicalLandscape)Row(Modifier.weight(1f).fillMaxWidth()){editor(Modifier.weight(1f).fillMaxHeight());preview(Modifier.weight(1f).fillMaxHeight())}else Column(Modifier.weight(1f).fillMaxWidth()){editor(Modifier.weight(1f).fillMaxWidth());preview(Modifier.weight(1f).fillMaxWidth())}
                EditorTools(local,target,landscape,bitmap,size,numeric,{numeric=!numeric},{commit(setTransform(it))},{next->commit(next)})
            }
        }
        if(exitDialog)AlertDialog(onDismissRequest={exitDialog=false},title={Text("保存修改？")},text={Text("当前构图尚未保存。")},confirmButton={TextButton({exitDialog=false;onSave(local)}){Text("保存")}},dismissButton={Row{TextButton({exitDialog=false;onDiscard()}){Text("放弃")};TextButton({exitDialog=false}){Text("继续编辑")}}})
    }

    @Composable private fun EditorTools(config:WallpaperConfig,target:WallpaperTarget,landscape:Boolean,bitmap:Bitmap?,size:PixelSize,numeric:Boolean,toggleNumeric:()->Unit,setTransform:(CompositionTransform)->Unit,setConfig:(WallpaperConfig)->Unit){
        val t=config.transform(target,landscape);val iw=bitmap?.width?.toFloat()?:1f;val ih=bitmap?.height?.toFloat()?:1f
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(6.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)){
            TextButton({setTransform(t.copy(centerX=.5f,centerY=.5f))}){Text("居中")};TextButton({setTransform(TransformCalculator.centerCrop(iw,ih,size.width.toFloat(),size.height.toFloat()))}){Text("填满")};TextButton({setTransform(TransformCalculator.fitCenter())}){Text("完整显示")};TextButton({setTransform(TransformCalculator.centerCrop(iw,ih,size.width.toFloat(),size.height.toFloat()))}){Text("重置")};TextButton(toggleNumeric){Text(if(numeric)"收起数值" else "数值微调")}
            TextButton({val s=config.source(target);setConfig(config.withSource(target,if(landscape)s.copy(portrait=s.landscape)else s.copy(landscape=s.portrait)))}){Text("复制到${if(landscape)"竖屏" else "横屏"}")}
            TextButton({val other=if(target==WallpaperTarget.DESKTOP)WallpaperTarget.LOCK else WallpaperTarget.DESKTOP;setConfig(config.withTransform(other,landscape,t))}){Text("复制到${if(target==WallpaperTarget.DESKTOP)"锁屏" else "桌面"}")}
        }
        if(numeric){var x by remember(t.centerX){mutableStateOf("%.1f".format(t.centerX*iw))};var y by remember(t.centerY){mutableStateOf("%.1f".format(t.centerY*ih))};var z by remember(t.zoom){mutableStateOf("%.1f".format(t.zoom*100))}
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                OutlinedTextField(x,{x=it},label={Text("中心 X px")},modifier=Modifier.width(145.dp),singleLine=true);OutlinedTextField(y,{y=it},label={Text("中心 Y px")},modifier=Modifier.width(145.dp),singleLine=true);OutlinedTextField(z,{z=it},label={Text("缩放 %")},modifier=Modifier.width(130.dp),singleLine=true)
                Button({val next=t.copy(centerX=(x.toFloatOrNull()?:t.centerX*iw)/iw,centerY=(y.toFloatOrNull()?:t.centerY*ih)/ih,zoom=(z.toFloatOrNull()?:t.zoom*100)/100);setTransform(next)}){Text("应用")}
                for(step in listOf(-10f,-1f,1f,10f))OutlinedButton({setTransform(t.copy(centerX=t.centerX+step/iw))}){Text("X ${if(step>0)"+" else ""}${step.toInt()}")}
                for(step in listOf(-10f,-1f,1f,10f))OutlinedButton({setTransform(t.copy(centerY=t.centerY+step/ih))}){Text("Y ${if(step>0)"+" else ""}${step.toInt()}")}
                Text("画布 ${size.width}×${size.height}")
            }
        }
    }

    @Composable private fun Settings(config:WallpaperConfig,save:(WallpaperConfig)->Unit,back:()->Unit){Scaffold(topBar={TopAppBar(title={Text("设置")},navigationIcon={TextButton(onClick=back){Text("返回")}})}){p->Column(Modifier.padding(p).padding(20.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)){Text("背景填充方式",style=MaterialTheme.typography.titleMedium);BackgroundMode.entries.forEach{m->Row{RadioButton(config.backgroundMode==m,{save(config.copy(backgroundMode=m))});Text(when(m){BackgroundMode.BLACK->"黑色背景";BackgroundMode.COLOR->"自定义纯色";BackgroundMode.EDGE->"图片边缘颜色";BackgroundMode.BLUR->"放大柔化背景"},Modifier.padding(top=12.dp))}};HorizontalDivider();Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("跟随桌面滑动");Switch(config.parallaxEnabled,{save(config.copy(parallaxEnabled=it))})};Text("图片质量 / 内存模式",style=MaterialTheme.typography.titleMedium);MemoryMode.entries.forEach{m->Row{RadioButton(config.memoryMode==m,{save(config.copy(memoryMode=m))});Text("${m.name}（最长边 ${m.maxLongEdge}）",Modifier.padding(top=12.dp))}}}}}

    private fun openWallpaperPreview(){val component=ComponentName(this,StaticWallpaperService::class.java);try{startActivity(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,component))}catch(_:Exception){try{startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))}catch(_:Exception){toast("系统未提供动态壁纸选择器")}}}
    private suspend fun setStaticLock(config:WallpaperConfig,size:PixelSize){val result=LockScreenSetter.apply(applicationContext,config,size.width,size.height);toast(result.fold({"锁屏画面设置成功"},{"锁屏设置失败：${it.message}"}))}
    private fun toast(message:String)=Toast.makeText(this,message,Toast.LENGTH_LONG).show()
    companion object{private val imageTypes=arrayOf("image/png","image/jpeg","image/webp")}
}
