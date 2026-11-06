package com.magicmate.printer
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.*
import android.graphics.Color
import android.graphics.Typeface

class MainActivity : Activity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val root=LinearLayout(this).apply { orientation=1; gravity=Gravity.CENTER; setPadding(28,36,28,36); setBackgroundColor(Color.rgb(13,9,20)) }
  root.addView(TextView(this).apply { text="🎩 MagicMate LIVE Printer"; textSize=25f; setTextColor(Color.WHITE); typeface=Typeface.DEFAULT_BOLD })
  root.addView(TextView(this).apply { text="Floating viewer cards over TikTok. Allow display-over-other-apps, then start the printer."; textSize=16f; setTextColor(Color.LTGRAY); setPadding(0,18,0,24) })
  fun button(label:String, action:()->Unit) { root.addView(Button(this).apply { text=label; setOnClickListener { action() } }, LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=12 }) }
  button("1. Allow floating window") { if(Build.VERSION.SDK_INT>=23 && !Settings.canDrawOverlays(this)) startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }
  button("2. Start floating printer") {
   if(Build.VERSION.SDK_INT>=23 && !Settings.canDrawOverlays(this)) { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))); return@button }
   val i=Intent(this,FloatingPrinterService::class.java)
   if(Build.VERSION.SDK_INT>=26) startForegroundService(i) else startService(i)
   moveTaskToBack(true)
  }
  button("Stop floating printer") { stopService(Intent(this,FloatingPrinterService::class.java)) }
  setContentView(root)
 }
}
