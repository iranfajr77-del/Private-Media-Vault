package com.myvault.app

import android.app.*
import android.os.Bundle
import android.content.*
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.view.Gravity
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID

data class VaultItem(val id:String,val name:String,val file:String,val video:Boolean,val album:String)

class MainActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("vault", MODE_PRIVATE) }
    private val items= mutableListOf<VaultItem>()
    private val green=0xFF39D98A.toInt()
    private val dark=0xFF101512.toInt()
    private val card=0xFF1D2922.toInt()

    private val importer=registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris?.forEach { importUri(it) }; saveItems(); showVault()
    }

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        loadItems()
        if(prefs.getString("pin_hash",null)==null) setupPin() else unlockScreen()
    }

    private fun setupPin() {
        val input=EditText(this).apply { hint="رمز حداقل ۴ رقمی"; inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD }
        AlertDialog.Builder(this).setTitle("ساخت رمز MY Vault").setMessage("برای اولین ورود یک رمز انتخاب کن.")
            .setView(input).setCancelable(false).setPositiveButton("ثبت") { _,_ ->
                val pin=input.text.toString()
                if(pin.length<4) { toast("رمز باید حداقل ۴ رقم باشد"); setupPin() }
                else { prefs.edit().putString("pin_hash",hash(pin)).apply(); showVault() }
            }.show()
    }

    private fun unlockScreen() {
        val input=EditText(this).apply { hint="رمز ورود"; inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD }
        val dialog=AlertDialog.Builder(this).setTitle("MY Vault 🔐").setMessage("رمز را وارد کن یا از اثر انگشت استفاده کن.")
            .setView(input).setCancelable(false).setPositiveButton("ورود") { _,_ ->
                if(hash(input.text.toString())==prefs.getString("pin_hash","")) showVault()
                else { toast("رمز اشتباه است"); unlockScreen() }
            }.setNeutralButton("اثر انگشت",null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { biometricUnlock() } }
        dialog.show()
    }

    private fun biometricUnlock() {
        if(BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)==BiometricManager.BIOMETRIC_SUCCESS) {
            val prompt=BiometricPrompt(this,ContextCompat.getMainExecutor(this),object:BiometricPrompt.AuthenticationCallback(){
                override fun onAuthenticationSucceeded(result:BiometricPrompt.AuthenticationResult) { showVault() }
                override fun onAuthenticationError(code:Int,message:CharSequence) { toast(message.toString()) }
            })
            prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("بازکردن MY Vault").setSubtitle("احراز هویت با اثر انگشت").setNegativeButtonText("لغو").build())
        } else toast("اثر انگشت روی این دستگاه در دسترس نیست")
    }

    private fun showVault() {
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(dark); setPadding(16,22,16,10) }
        root.addView(ImageView(this).apply { setImageResource(R.drawable.my_logo); adjustViewBounds=true; scaleType=ImageView.ScaleType.FIT_CENTER },LinearLayout.LayoutParams(104,76).apply { gravity=Gravity.CENTER_HORIZONTAL })
        root.addView(TextView(this).apply { text="MY Vault"; textSize=29f; setTextColor(green); gravity=Gravity.CENTER; setPadding(8,8,8,12) })
        root.addView(TextView(this).apply { text="فضای خصوصی عکس‌ها و ویدئوهای تو"; setTextColor(-1); gravity=Gravity.CENTER; setPadding(4,0,4,14) })
        val actions=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        actions.addView(button("＋ افزودن رسانه") { importer.launch(arrayOf("image/*","video/*")) },LinearLayout.LayoutParams(0,52,1f))
        actions.addView(button("＋ آلبوم") { createAlbum() },LinearLayout.LayoutParams(0,52,1f))
        root.addView(actions)
        root.addView(button("⚙ تنظیمات و تغییر رمز") { settingsDialog() })
        val scroll=ScrollView(this)
        val list=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(0,10,0,24) }
        val albums=items.map{it.album}.filter{it.isNotBlank()}.distinct()
        if(albums.isNotEmpty()) {
            list.addView(section("آلبوم‌ها"))
            albums.forEach { album -> list.addView(button("📁 "+album+" · "+items.count{it.album==album}+" مورد") { showAlbum(album) }) }
        }
        list.addView(section("همه فایل‌ها"))
        if(items.isEmpty()) list.addView(section("هنوز فایلی اضافه نکردی. از دکمه بالا شروع کن."))
        items.toList().forEach { item -> list.addView(itemRow(item)) }
        scroll.addView(list); root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root)
    }

    private fun button(text:String,click:()->Unit)=Button(this).apply {
        this.text=text; isAllCaps=false; textSize=13f; setTextColor(green)
        backgroundTintList=android.content.res.ColorStateList.valueOf(card); setOnClickListener{click()}
    }
    private fun section(text:String)=TextView(this).apply { this.text=text; textSize=16f; setTextColor(green); setPadding(4,16,4,8) }

    private fun itemRow(item:VaultItem)=LinearLayout(this).apply {
        orientation=LinearLayout.VERTICAL; setPadding(10,8,10,8); setBackgroundColor(card)
        addView(TextView(this@MainActivity).apply { text=(if(item.video)"🎬 " else "🖼 ")+item.name; setTextColor(-1); textSize=14f })
        val row=LinearLayout(this@MainActivity).apply { orientation=LinearLayout.HORIZONTAL }
        row.addView(button(if(item.video)"▶ پخش" else "نمایش"){ if(item.video) playVideo(item) else showImage(item) },LinearLayout.LayoutParams(0,44,1f))
        row.addView(button("آلبوم"){ chooseAlbum(item) },LinearLayout.LayoutParams(0,44,1f))
        row.addView(button("بازگردانی"){ restoreItem(item) },LinearLayout.LayoutParams(0,44,1f))
        row.addView(button("حذف"){ deleteItem(item) },LinearLayout.LayoutParams(0,44,1f))
        addView(row)
        val lp=LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,5,0,5); layoutParams=lp
    }

    private fun importUri(uri:Uri) {
        try {
            val mime=contentResolver.getType(uri) ?: "image/jpeg"
            val video=mime.startsWith("video/")
            val file=File(filesDir,"media/"+UUID.randomUUID().toString()+(if(video)".mp4" else ".jpg"))
            file.parentFile?.mkdirs()
            contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } } ?: return
            if(file.length()==0L) { file.delete(); return }
            val name=contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { c ->
                if(c.moveToFirst()) c.getString(0) else null
            } ?: file.name
            items.add(VaultItem(UUID.randomUUID().toString(),name,file.absolutePath,video,""))
        } catch(e:Exception) { toast("واردکردن فایل ناموفق بود: "+e.message) }
    }

    private fun createAlbum() {
        val input=EditText(this).apply { hint="نام آلبوم" }
        AlertDialog.Builder(this).setTitle("آلبوم جدید").setView(input).setNegativeButton("لغو",null)
            .setPositiveButton("ساخت"){_,_-> val name=input.text.toString().trim(); if(name.isNotEmpty()) { toast("آلبوم ساخته شد؛ فایل‌ها را با گزینه آلبوم به آن منتقل کن."); showVault() } }.show()
    }
    private fun chooseAlbum(item:VaultItem) {
        val names=(items.map{it.album}.filter{it.isNotBlank()}.distinct()+listOf("بدون آلبوم","＋ آلبوم جدید")).distinct()
        AlertDialog.Builder(this).setTitle("انتقال به آلبوم").setItems(names.toTypedArray()){_,which->
            val name=names[which]
            if(name=="＋ آلبوم جدید") {
                val input=EditText(this)
                AlertDialog.Builder(this).setTitle("نام آلبوم").setView(input).setPositiveButton("ذخیره"){_,_-> updateAlbum(item,input.text.toString().trim()) }.setNegativeButton("لغو",null).show()
            } else updateAlbum(item,if(name=="بدون آلبوم")"" else name)
        }.show()
    }
    private fun updateAlbum(item:VaultItem,name:String) {
        val i=items.indexOfFirst{it.id==item.id}; if(i>=0) items[i]=item.copy(album=name)
        saveItems(); showVault()
    }
    private fun showAlbum(name:String) {
        val found=items.filter{it.album==name}
        AlertDialog.Builder(this).setTitle(name).setMessage(found.joinToString("\n"){it.name}.ifBlank{"این آلبوم خالی است"}).setPositiveButton("باشه",null).show()
    }

    private fun playVideo(item:VaultItem) {
        val video=VideoView(this)
        val controls=MediaController(this); controls.setAnchorView(video); video.setMediaController(controls)
        video.setVideoURI(Uri.fromFile(File(item.file)))
        val panel=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(8,8,8,8); setBackgroundColor(dark) }
        panel.addView(video,LinearLayout.LayoutParams(-1,250))
        val skips=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        skips.addView(button("↶ ۱۰ ثانیه"){video.seekTo((video.currentPosition-10000).coerceAtLeast(0))},LinearLayout.LayoutParams(0,48,1f))
        skips.addView(button("۱۰ ثانیه ↷"){video.seekTo((video.currentPosition+10000).coerceAtMost(video.duration))},LinearLayout.LayoutParams(0,48,1f))
        panel.addView(skips)
        AlertDialog.Builder(this).setTitle(item.name).setView(panel).setPositiveButton("بستن",null).show()
        video.start()
    }
    private fun showImage(item:VaultItem) {
        val image=ImageView(this).apply { setImageURI(Uri.fromFile(File(item.file))); adjustViewBounds=true; scaleType=ImageView.ScaleType.FIT_CENTER }
        AlertDialog.Builder(this).setTitle(item.name).setView(image).setPositiveButton("بستن",null).show()
    }

    private fun restoreItem(item:VaultItem) {
        try {
            val source=File(item.file)
            val collection=if(item.video)MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val values=android.content.ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME,item.name)
                put(MediaStore.MediaColumns.MIME_TYPE,if(item.video)"video/mp4" else "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH,if(item.video)"Movies/MY Vault" else "Pictures/MY Vault")
            }
            val uri=contentResolver.insert(collection,values) ?: throw Exception("ساخت مقصد ناموفق بود")
            contentResolver.openOutputStream(uri)?.use { out->source.inputStream().use{it.copyTo(out)} } ?: throw Exception("نوشتن فایل ناموفق بود")
            if(source.length()==0L) throw Exception("فایل خالی است")
            source.delete(); items.removeAll{it.id==item.id}; saveItems(); showVault(); toast("فایل بازگردانده شد")
        } catch(e:Exception) { toast("بازگردانی ناموفق بود: "+e.message) }
    }
    private fun deleteItem(item:VaultItem) {
        AlertDialog.Builder(this).setTitle("حذف فایل").setMessage("این فایل برای همیشه از MY Vault حذف شود؟")
            .setNegativeButton("لغو",null).setPositiveButton("حذف"){_,_-> File(item.file).delete(); items.removeAll{it.id==item.id}; saveItems(); showVault() }.show()
    }
    private fun settingsDialog() {
        AlertDialog.Builder(this).setTitle("تنظیمات").setItems(arrayOf("تغییر رمز","قفل‌کردن برنامه")){_,i->if(i==0)changePin() else unlockScreen()}.show()
    }
    private fun changePin() {
        val input=EditText(this).apply { hint="رمز جدید حداقل ۴ رقم"; inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD }
        AlertDialog.Builder(this).setTitle("تغییر رمز").setView(input).setNegativeButton("لغو",null)
            .setPositiveButton("ذخیره"){_,_-> val p=input.text.toString(); if(p.length>=4){prefs.edit().putString("pin_hash",hash(p)).apply();toast("رمز تغییر کرد")}else toast("رمز باید حداقل ۴ رقم باشد")}.show()
    }
    private fun loadItems() {
        try {
            val a=JSONArray(prefs.getString("items","[]"))
            for(i in 0 until a.length()) {
                val o=a.getJSONObject(i); val path=o.getString("file")
                if(File(path).exists()) items.add(VaultItem(o.getString("id"),o.getString("name"),path,o.getBoolean("video"),o.optString("album","")))
            }
        } catch(_:Exception) {}
    }
    private fun saveItems() {
        val a=JSONArray()
        items.forEach { v->a.put(JSONObject().put("id",v.id).put("name",v.name).put("file",v.file).put("video",v.video).put("album",v.album)) }
        prefs.edit().putString("items",a.toString()).apply()
    }
    private fun hash(value:String)=MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString(""){"%02x".format(it)}
    private fun toast(message:String)=Toast.makeText(this,message,Toast.LENGTH_LONG).show()
}