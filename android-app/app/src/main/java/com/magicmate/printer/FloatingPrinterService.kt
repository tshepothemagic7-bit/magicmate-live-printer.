package com.magicmate.printer
import android.app.*
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat
import com.bumptech.glide.Glide
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject

class FloatingPrinterService : Service() {
 private val channel="magicmate_printer"
 private var wm:WindowManager?=null
 private var panel:LinearLayout?=null
 private var feed:LinearLayout?=null
 private var socket:Socket?=null
 private var auto=true
 override fun onCreate() {
  super.onCreate()
  if(Build.VERSION.SDK_INT>=26) getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(channel,"MagicMate floating printer",NotificationManager.IMPORTANCE_LOW))
  val n=NotificationCompat.Builder(this,channel).setSmallIcon(android.R.drawable.ic_menu_print).setContentTitle("MagicMate printer running").setContentText("Watching @tshepothemagician").setOngoing(true).build()
  if(Build.VERSION.SDK_INT>=34) startForeground(41,n,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(41,n)
  showOverlay(); connect()
 }
 private fun showOverlay() {
  wm=getSystemService(WINDOW_SERVICE) as WindowManager
  val box=LinearLayout(this).apply { orientation=1; background=android.graphics.drawable.GradientDrawable().apply { setColor(Color.rgb(22,14,32)); cornerRadius=25f; setStroke(2,Color.rgb(166,75,238)) }; elevation=16f }
  val head=LinearLayout(this).apply { orientation=0; gravity=Gravity.CENTER_VERTICAL; setPadding(14,8,8,8); setBackgroundColor(Color.rgb(52,25,73)) }
  head.addView(TextView(this).apply { text="🎩 MAGICMATE LIVE"; textSize=13f; setTextColor(Color.WHITE); typeface=Typeface.DEFAULT_BOLD },LinearLayout.LayoutParams(0,-2,1f))
  head.addView(TextView(this).apply { text="×"; textSize=24f; setTextColor(Color.WHITE); setPadding(12,0,12,0); setOnClickListener { stopSelf() } })
  val status=TextView(this).apply { text="Connecting to LIVE…"; tag="status"; textSize=11f; setTextColor(Color.LTGRAY); setPadding(12,7,12,5) }
  val check=CheckBox(this).apply { text="Auto-print events"; isChecked=true; setTextColor(Color.WHITE); setOnCheckedChangeListener { _,v -> auto=v } }
  feed=LinearLayout(this).apply { orientation=1; setPadding(8,6,8,8) }
  box.addView(head); box.addView(status); box.addView(check); box.addView(ScrollView(this).apply { addView(feed) },LinearLayout.LayoutParams(-1,330))
  val p=WindowManager.LayoutParams((resources.displayMetrics.widthPixels*.88f).toInt(),-2,if(Build.VERSION.SDK_INT>=26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT).apply { gravity=Gravity.TOP or Gravity.CENTER_HORIZONTAL; y=90 }
  head.setOnTouchListener(object:View.OnTouchListener {
   var x=0; var y=0; var ox=0; var oy=0
   override fun onTouch(v:View,e:MotionEvent):Boolean { when(e.action) { MotionEvent.ACTION_DOWN->{x=e.rawX.toInt();y=e.rawY.toInt();ox=p.x;oy=p.y;return true}; MotionEvent.ACTION_MOVE->{p.x=ox+e.rawX.toInt()-x;p.y=oy+e.rawY.toInt()-y;wm?.updateViewLayout(box,p);return true} };return false }
  })
  panel=box; wm?.addView(box,p)
 }
 private fun connect() {
  try {
   socket=IO.socket("https://magicmate-live-printer-bmmj.onrender.com")
   socket?.on("connect") { status("Dashboard connected; waiting for LIVE events") }
   socket?.on("disconnect") { status("Connection lost; reconnecting…") }
   socket?.on("live:status") { a -> val j=a.firstOrNull() as? JSONObject; status(j?.optString("message") ?: "TikTok status") }
   socket?.on("live:history") { a -> val arr=a.firstOrNull() as? org.json.JSONArray ?: return@on; for(i in 0 until arr.length()) arr.optJSONObject(i)?.let { add(it) } }
   socket?.on("live:event") { a -> (a.firstOrNull() as? JSONObject)?.let { add(it) } }
   socket?.connect()
  } catch(_:Exception) { status("Could not connect to printer server") }
 }
 private fun status(s:String) { android.os.Handler(mainLooper).post { val root=panel?:return@post; for(i in 0 until root.childCount) { val v=root.getChildAt(i); if(v.tag=="status" && v is TextView) v.text=s } } }
 private fun add(j:JSONObject) {
  if(!auto) return
  val name=j.optString("displayName",j.optString("username","Viewer")); val user=j.optString("username",""); val detail=j.optString("detail",""); val avatar=j.optString("avatar","")
  android.os.Handler(mainLooper).post {
   val row=LinearLayout(this).apply { orientation=0; gravity=Gravity.CENTER_VERTICAL; setPadding(9,9,9,9); background=android.graphics.drawable.GradientDrawable().apply { setColor(Color.WHITE);cornerRadius=14f } }
   val pic=ImageView(this).apply { layoutParams=LinearLayout.LayoutParams(44,44).apply { rightMargin=9 }; setBackgroundColor(Color.LTGRAY); scaleType=ImageView.ScaleType.CENTER_CROP }
   if(avatar.isNotBlank()) Glide.with(this).load(avatar).circleCrop().into(pic)
   val words=LinearLayout(this).apply { orientation=1 }
   words.addView(TextView(this).apply { text=name;setTextColor(Color.rgb(43,24,55));textSize=14f;typeface=Typeface.DEFAULT_BOLD })
   words.addView(TextView(this).apply { text=user;setTextColor(Color.DKGRAY);textSize=10f })
   words.addView(TextView(this).apply { text=detail;setTextColor(Color.BLACK);textSize=13f;maxLines=3 })
   row.addView(pic);row.addView(words,LinearLayout.LayoutParams(0,-2,1f));feed?.addView(row,0)
   while((feed?.childCount?:0)>10) feed?.removeViewAt((feed?.childCount?:1)-1)
  }
 }
 override fun onDestroy() { try { socket?.disconnect();socket?.off() }catch(_:Exception){};try{panel?.let{wm?.removeView(it)}}catch(_:Exception){};super.onDestroy() }
 override fun onBind(i:Intent?):IBinder?=null
}
