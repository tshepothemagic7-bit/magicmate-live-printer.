package com.magicmate.printer

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import com.bumptech.glide.Glide
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {
 private data class Style(val name:String,val line:String,val accent:Int,val second:Int,val machine:String)
 private val styles=listOf(
  Style("THE VIEWER FACTORY","COMMENT YOUR NAME TO ENTER THE FACTORY!  •  SEND A GIFT TO ACTIVATE THE MACHINE!",Color.rgb(190,75,255),Color.rgb(255,184,64),"⚙  VIEWER IN  •  RECEIPT OUT  ⚙"),
  Style("THE MYSTERY PRINTER","SEND A GIFT TO UNLOCK THE PRINTER!  •  COMMENT TO MAKE THE MACHINE REACT!",Color.rgb(165,83,255),Color.rgb(255,205,75),"✦  A SURPRISE IS WAITING  ✦"),
  Style("THE VIP EXPERIENCE","SEND A GIFT TO JOIN THE VIP WALL!  •  COMMENT AND GET NOTICED!",Color.rgb(255,190,65),Color.rgb(190,90,255),"✧  VIP LOUNGE  ✧"),
  Style("THE DIGITAL ARENA","COMMENT TO JOIN THE ARENA!  •  SEND A GIFT TO POWER UP!",Color.rgb(60,210,255),Color.rgb(190,75,255),"⚡  ARENA OPEN  ⚡")
 )
 private var selected=0
 private var socket:Socket?=null
 private var status:TextView?=null
 private fun bg(c:Int,s:Int=0)=GradientDrawable().apply{setColor(c);cornerRadius=18f;if(s!=0)setStroke(2,s)}
 private fun text(t:String,n:Float,c:Int=Color.WHITE,b:Boolean=false)=TextView(this).apply{text=t;textSize=n;setTextColor(c);if(b)typeface=Typeface.DEFAULT_BOLD}
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);selected=getPreferences(0).getInt("style",0).coerceIn(0,3);picker()}
 private fun picker(){
  requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
  val root=LinearLayout(this).apply{orientation=1;setPadding(22,24,22,24);gravity=Gravity.CENTER;setBackgroundColor(Color.rgb(13,9,22))}
  root.addView(text("CHOOSE YOUR LIVE EXPERIENCE",23f,Color.WHITE,true))
  root.addView(text("Four ways to turn viewers into participants",14f,Color.LTGRAY).apply{setPadding(0,8,0,18)})
  styles.forEachIndexed{i,s->
   val card=LinearLayout(this).apply{orientation=1;setPadding(16,13,16,13);background=bg(Color.rgb(34,23,49),s.accent)}
   card.addView(text(s.name,18f,s.accent,true))
   card.addView(text(s.machine,13f,Color.WHITE,true).apply{setPadding(0,6,0,4)})
   card.addView(text(s.line,12f,Color.LTGRAY))
   root.addView(card,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=11})
   card.setOnClickListener{selected=i;getPreferences(0).edit().putInt("style",i).apply();dashboard()}
  }
  setContentView(ScrollView(this).apply{addView(root)})
 }
 private fun panel(title:String,accent:Int):LinearLayout{
  val box=LinearLayout(this).apply{orientation=1;setPadding(9,8,9,8);background=bg(Color.rgb(29,20,42),accent)}
  box.addView(text(title,13f,accent,true))
  val list=LinearLayout(this).apply{orientation=1;tag=title}
  box.addView(ScrollView(this).apply{addView(list)},LinearLayout.LayoutParams(-1,0,1f))
  return box
 }
 private fun dashboard(){
  requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
  val s=styles[selected]
  val root=LinearLayout(this).apply{orientation=1;setPadding(14,10,14,10);setBackgroundColor(Color.rgb(10,8,17))}
  val header=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
  header.addView(text(s.name,21f,s.accent,true),LinearLayout.LayoutParams(0,-2,1f))
  header.addView(text("● LIVE",14f,Color.rgb(255,70,90),true))
  val body=LinearLayout(this).apply{orientation=0}
  val left=panel("NEW VIEWERS",s.accent)
  val centre=LinearLayout(this).apply{orientation=1;gravity=Gravity.CENTER;setPadding(12,8,12,8);background=bg(Color.rgb(30,19,43),s.accent)}
  centre.addView(text(s.machine,17f,s.accent,true))
  centre.addView(text(if(selected==0)"THE FACTORY IS READY" else if(selected==1)"WHAT WILL THE MACHINE PRINT NEXT?" else s.name,12f,Color.WHITE,true).apply{setPadding(0,8,0,8)})
  val machine=text(if(selected==0)"▤  PROCESSING BELT  ▤" else if(selected==1)"▤  MYSTERY PRINTER  ▤" else "✧  "+s.name+"  ✧",23f,Color.WHITE,true).apply{gravity=Gravity.CENTER;background=bg(Color.rgb(48,29,66),s.second)}
  centre.addView(machine,LinearLayout.LayoutParams(-1,0,1f))
  centre.addView(text(s.line,13f,s.second,true).apply{gravity=Gravity.CENTER;setPadding(4,10,4,2)})
  val right=panel("LIVE ACTIVITY",s.second)
  body.addView(left,LinearLayout.LayoutParams(0,-1,0.9f).apply{rightMargin=8})
  body.addView(centre,LinearLayout.LayoutParams(0,-1,1.5f).apply{rightMargin=8})
  body.addView(right,LinearLayout.LayoutParams(0,-1,0.9f))
  status=text("Connecting to LIVE events…",11f,Color.LTGRAY)
  root.addView(header);root.addView(body,LinearLayout.LayoutParams(-1,0,1f));root.addView(status)
  setContentView(root)
  connect()
 }
 private fun connect(){
  try{
   socket=IO.socket("https://magicmate-live-printer-bmmj.onrender.com")
   socket?.on("connect"){runOnUiThread{status?.text="Connected • waiting for viewer activity"}}
   socket?.on("disconnect"){runOnUiThread{status?.text="Connection lost • reconnecting…"}}
   socket?.on("live:status"){a->val j=a.firstOrNull() as? JSONObject;runOnUiThread{status?.text=j?.optString("message")?:"LIVE status updated"}}
   socket?.on("live:history"){a->val arr=a.firstOrNull() as? JSONArray?:return@on;runOnUiThread{for(i in 0 until arr.length())arr.optJSONObject(i)?.let{addEvent(it)}}}
   socket?.on("live:event"){a->(a.firstOrNull() as? JSONObject)?.let{j->runOnUiThread{addEvent(j)}}}
   socket?.connect()
  }catch(_:Exception){status?.text="Could not connect to event server"}
 }
 private fun addEvent(j:JSONObject){
  val name=j.optString("displayName",j.optString("username","Viewer"))
  val detail=j.optString("detail","Interacted with the LIVE")
  val avatar=j.optString("avatar","")
  val kind=(j.optString("event",j.optString("type",""))+" "+detail).lowercase()
  val content=findViewById<FrameLayout>(android.R.id.content)
  val root=content.getChildAt(0) as? LinearLayout?:return
  val body=root.getChildAt(1) as? LinearLayout?:return
  val box=body.getChildAt(if(kind.contains("gift"))2 else 0) as? LinearLayout?:return
  val list=box.findViewWithTag<LinearLayout>(if(kind.contains("gift"))"LIVE ACTIVITY" else "NEW VIEWERS")?:return
  val row=LinearLayout(this).apply{orientation=0;gravity=Gravity.CENTER_VERTICAL;setPadding(6,7,6,7);background=bg(Color.rgb(55,39,69))}
  val pic=ImageView(this).apply{layoutParams=LinearLayout.LayoutParams(36,36).apply{rightMargin=7};scaleType=ImageView.ScaleType.CENTER_CROP}
  if(avatar.isNotBlank())Glide.with(this).load(avatar).circleCrop().into(pic)
  val words=LinearLayout(this).apply{orientation=1}
  words.addView(text(name,12f,Color.WHITE,true));words.addView(text(detail,10f,Color.LTGRAY))
  row.addView(pic);row.addView(words,LinearLayout.LayoutParams(0,-2,1f))
  list.addView(row,0,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=5})
  while(list.childCount>5)list.removeViewAt(list.childCount-1)
 }
 override fun onDestroy(){try{socket?.disconnect();socket?.off()}catch(_:Exception){};super.onDestroy()}
}
