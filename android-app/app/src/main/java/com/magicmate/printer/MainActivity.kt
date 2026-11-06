package com.magicmate.printer

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.*
import com.bumptech.glide.Glide
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

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
 private var viewerGrid:LinearLayout?=null
 private var viewerScroll:ScrollView?=null
 private var currentViewerRow:LinearLayout?=null
 private var tts:TextToSpeech?=null
 private var ttsReady=false
 private val pendingSpeech=mutableListOf<String>()

 private fun bg(c:Int,s:Int=0)=GradientDrawable().apply{setColor(c);cornerRadius=18f;if(s!=0)setStroke(2,s)}
 private fun text(t:String,n:Float,c:Int=Color.WHITE,b:Boolean=false)=TextView(this).apply{text=t;textSize=n;setTextColor(c);if(b)typeface=Typeface.DEFAULT_BOLD}

 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  selected=getPreferences(0).getInt("style",0).coerceIn(0,3)
  tts=TextToSpeech(this){result->
   if(result==TextToSpeech.SUCCESS){
    tts?.language=Locale.UK
    ttsReady=true
    pendingSpeech.toList().forEach{say(it)}
    pendingSpeech.clear()
   }
  }
  if(getPreferences(0).getBoolean("dashboard",false)){dashboard()}else{picker()}
 }

 private fun picker(){
  requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
  val root=LinearLayout(this).apply{orientation=1;setPadding(22,24,22,24);gravity=Gravity.CENTER;setBackgroundColor(Color.rgb(13,9,22))}
  root.addView(text("CHOOSE YOUR LIVE EXPERIENCE",23f,Color.WHITE,true))
  root.addView(text("Viewer profiles print into a live two-column wall",14f,Color.LTGRAY).apply{setPadding(0,8,0,18)})
  styles.forEachIndexed{i,s->
   val card=LinearLayout(this).apply{orientation=1;setPadding(16,13,16,13);background=bg(Color.rgb(34,23,49),s.accent)}
   card.addView(text(s.name,18f,s.accent,true))
   card.addView(text(s.machine,13f,Color.WHITE,true).apply{setPadding(0,6,0,4)})
   card.addView(text(s.line,12f,Color.LTGRAY))
   root.addView(card,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=11})
   card.setOnClickListener{selected=i;getPreferences(0).edit().putInt("style",i).putBoolean("dashboard",true).apply();dashboard()}
  }
  setContentView(ScrollView(this).apply{addView(root)})
 }

 private fun panel(title:String,accent:Int):LinearLayout{
  val box=LinearLayout(this).apply{orientation=1;setPadding(8,8,8,8);background=bg(Color.rgb(29,20,42),accent)}
  box.addView(text(title,13f,accent,true))
  val list=LinearLayout(this).apply{orientation=1;tag=title}
  box.addView(ScrollView(this).apply{addView(list)},LinearLayout.LayoutParams(-1,0,1f))
  return box
 }

 private fun dashboard(){
  getPreferences(0).edit().putBoolean("dashboard",true).apply()
  requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
  val s=styles[selected]
  viewerGrid=null;viewerScroll=null;currentViewerRow=null
  val root=LinearLayout(this).apply{orientation=1;setPadding(12,8,12,8);setBackgroundColor(Color.rgb(10,8,17))}
  val header=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
  header.addView(text(s.name,20f,s.accent,true),LinearLayout.LayoutParams(0,-2,1f))
  header.addView(text("● LIVE",14f,Color.rgb(255,70,90),true))
  val body=LinearLayout(this).apply{orientation=0}
  val centre=LinearLayout(this).apply{orientation=1;setPadding(10,8,10,8);background=bg(Color.rgb(30,19,43),s.accent)}
  centre.addView(text("LIVE VIEWER PRINTER",16f,s.accent,true).apply{gravity=Gravity.CENTER})
  centre.addView(text("Every new viewer gets a printed profile",11f,Color.LTGRAY).apply{gravity=Gravity.CENTER;setPadding(0,3,0,7)})
  viewerGrid=LinearLayout(this).apply{orientation=1;setPadding(4,4,4,4)}
  viewerScroll=ScrollView(this).apply{isFillViewport=true;addView(viewerGrid)}
  centre.addView(viewerScroll,LinearLayout.LayoutParams(-1,0,1f))
  centre.addView(text(s.line,11f,s.second,true).apply{gravity=Gravity.CENTER;setPadding(2,6,2,0)})
  val right=panel("GIFTS & LIVE ACTIVITY",s.second)
  body.addView(centre,LinearLayout.LayoutParams(0,-1,2.25f).apply{rightMargin=8})
  body.addView(right,LinearLayout.LayoutParams(0,-1,0.85f))
  status=text("Connecting to LIVE events…",10f,Color.LTGRAY)
  root.addView(header);root.addView(body,LinearLayout.LayoutParams(-1,0,1f));root.addView(status)
  setContentView(root)
  connect()
 }

 private fun connect(){
  try{
   socket=IO.socket("https://magicmate-live-printer-bmmj.onrender.com")
   socket?.on("connect"){runOnUiThread{status?.text="Connected • waiting for viewers and gifts"}}
   socket?.on("disconnect"){runOnUiThread{status?.text="Connection lost • reconnecting…"}}
   socket?.on("live:status"){a->val j=a.firstOrNull() as? JSONObject;runOnUiThread{status?.text=j?.optString("message")?:"LIVE status updated"}}
   socket?.on("live:history"){a->val arr=a.firstOrNull() as? JSONArray?:return@on;runOnUiThread{for(i in 0 until arr.length())arr.optJSONObject(i)?.let{addEvent(it,false)}}}
   socket?.on("live:event"){a->(a.firstOrNull() as? JSONObject)?.let{j->runOnUiThread{addEvent(j,true)}}}
   socket?.connect()
  }catch(_:Exception){status?.text="Could not connect to event server"}
 }

 private fun addEvent(j:JSONObject,announce:Boolean){
  val name=j.optString("displayName",j.optString("username","Viewer")).ifBlank{"Viewer"}
  val detail=j.optString("detail","Interacted with the LIVE")
  val avatar=j.optString("avatar","")
  val kind=j.optString("type",j.optString("event","")).lowercase()
  if(kind=="join" || kind.contains("join")){
   addViewer(name,j.optString("username",""),avatar)
  }else{
   addActivity(name,detail,avatar,kind)
   if(announce && kind.contains("gift")){
    val giftName=name.replace("@","").trim()
    val spoken="Thank you for the gift, $giftName!"
    if(ttsReady)say(spoken) else pendingSpeech.add(spoken)
   }
  }
 }

 private fun addViewer(name:String,username:String,avatar:String){
  val grid=viewerGrid?:return
  val row=currentViewerRow
  val target=if(row==null || row.childCount>=2){
   LinearLayout(this).apply{orientation=0;gravity=Gravity.TOP;weightSum=2f}
    .also{currentViewerRow=it;grid.addView(it,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=7})}
  }else row
  val card=LinearLayout(this).apply{orientation=1;gravity=Gravity.CENTER;setPadding(6,8,6,8);background=bg(Color.rgb(247,243,250),styles[selected].second)}
  val pic=ImageView(this).apply{layoutParams=LinearLayout.LayoutParams(54,54).apply{bottomMargin=5};scaleType=ImageView.ScaleType.CENTER_CROP}
  if(avatar.isNotBlank())Glide.with(this).load(avatar).circleCrop().into(pic)
  card.addView(pic)
  card.addView(text(name,12f,Color.rgb(40,24,52),true).apply{gravity=Gravity.CENTER;maxLines=2})
  if(username.isNotBlank())card.addView(text(username,9f,Color.DKGRAY).apply{gravity=Gravity.CENTER;maxLines=1})
  target.addView(card,LinearLayout.LayoutParams(0,-2,1f).apply{leftMargin=4;rightMargin=4})
  viewerScroll?.post{viewerScroll?.smoothScrollTo(0,grid.height)}
 }

 private fun addActivity(name:String,detail:String,avatar:String,kind:String){
  val root=findViewById<android.widget.FrameLayout>(android.R.id.content).getChildAt(0) as? LinearLayout?:return
  val body=root.getChildAt(1) as? LinearLayout?:return
  val box=body.getChildAt(1) as? LinearLayout?:return
  val list=box.findViewWithTag<LinearLayout>("GIFTS & LIVE ACTIVITY")?:return
  val row=LinearLayout(this).apply{orientation=0;gravity=Gravity.CENTER_VERTICAL;setPadding(5,7,5,7);background=bg(Color.rgb(55,39,69))}
  val pic=ImageView(this).apply{layoutParams=LinearLayout.LayoutParams(32,32).apply{rightMargin=5};scaleType=ImageView.ScaleType.CENTER_CROP}
  if(avatar.isNotBlank())Glide.with(this).load(avatar).circleCrop().into(pic)
  val words=LinearLayout(this).apply{orientation=1}
  words.addView(text(name,10f,Color.WHITE,true))
  words.addView(text((if(kind.contains("gift"))"GIFT • " else "LIVE • ")+detail,9f,Color.LTGRAY))
  row.addView(pic);row.addView(words,LinearLayout.LayoutParams(0,-2,1f))
  list.addView(row,0,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=5})
  while(list.childCount>20)list.removeViewAt(list.childCount-1)
 }

 private fun say(message:String){
  if(ttsReady)tts?.speak(message,TextToSpeech.QUEUE_ADD,null,"gift-"+System.currentTimeMillis())
 }

 override fun onBackPressed(){
  try{socket?.disconnect();socket?.off()}catch(_:Exception){}
  socket=null
  getPreferences(0).edit().putBoolean("dashboard",false).apply()
  requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
  picker()
 }
 override fun onDestroy(){
  try{socket?.disconnect();socket?.off()}catch(_:Exception){}
  tts?.stop();tts?.shutdown();tts=null
  super.onDestroy()
 }
}
