package com.magicmate.printer

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.view.animation.DecelerateInterpolator
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
  Style("LIVE RECEIPT PRESS","JOIN THE LIVE AND GET PRINTED!  •  SEND A GIFT TO MAKE THE PRINTER ROLL!",Color.rgb(83,220,64),Color.rgb(245,65,35),"YOUR NAME COULD BE NEXT ON THE PAPER"),
  Style("THE MYSTERY PRINTER","SEND A GIFT TO UNLOCK THE PRINTER!  •  COMMENT TO MAKE THE MACHINE REACT!",Color.rgb(165,83,255),Color.rgb(255,205,75),"✦  A SURPRISE IS WAITING  ✦"),
  Style("THE VIP EXPERIENCE","SEND A GIFT TO JOIN THE VIP WALL!  •  COMMENT AND GET NOTICED!",Color.rgb(255,190,65),Color.rgb(190,90,255),"✧  VIP LOUNGE  ✧"),
  Style("THE DIGITAL ARENA","COMMENT TO JOIN THE ARENA!  •  SEND A GIFT TO POWER UP!",Color.rgb(60,210,255),Color.rgb(190,75,255),"⚡  ARENA OPEN  ⚡"),
  Style("COSMIC CONSTELLATION","DROP A COMMENT AND BECOME A STAR!  •  SEND A GIFT TO LIGHT UP THE GALAXY!",Color.rgb(126,190,255),Color.rgb(208,157,255),"✦  EVERY VIEWER ADDS A STAR  ✦"),
  Style("DEEP SEA AQUARIUM","SAY HELLO TO SWIM INTO THE AQUARIUM!  •  SEND A GIFT TO FEED THE DEEP!",Color.rgb(62,225,210),Color.rgb(85,170,255),"◉  THE OCEAN IS ALIVE  ◉")
 )
 private var selected=0
 private var socket:Socket?=null
 private var status:TextView?=null
 private var viewerGrid:LinearLayout?=null
 private var viewerScroll:ScrollView?=null
 private var currentViewerRow:LinearLayout?=null
 private var printerView:LinearLayout?=null
 private var paperView:LinearLayout?=null
 private var printerSound:ToneGenerator?=null
 private val soundHandler=Handler(Looper.getMainLooper())
 private var tts:TextToSpeech?=null
 private var ttsReady=false
 private val pendingSpeech=mutableListOf<String>()

 private fun bg(c:Int,s:Int=0)=GradientDrawable().apply{setColor(c);cornerRadius=18f;if(s!=0)setStroke(2,s)}
 private fun text(t:String,n:Float,c:Int=Color.WHITE,b:Boolean=false)=TextView(this).apply{text=t;textSize=n;setTextColor(c);if(b)typeface=Typeface.DEFAULT_BOLD}

 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  selected=getPreferences(0).getInt("style",0).coerceIn(0,5)
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
  viewerGrid=null;viewerScroll=null;currentViewerRow=null;printerView=null;paperView=null
  val root=LinearLayout(this).apply{orientation=1;setPadding(12,8,12,8);setBackgroundColor(Color.rgb(10,8,17))}
  val header=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
  header.addView(text(s.name,20f,s.accent,true),LinearLayout.LayoutParams(0,-2,1f))
  header.addView(text("● LIVE",14f,Color.rgb(255,70,90),true))
  val body=LinearLayout(this).apply{orientation=0}
  val centre=LinearLayout(this).apply{orientation=1;setPadding(10,8,10,8);gravity=Gravity.CENTER_HORIZONTAL
   setBackground(bg(when(selected){0->Color.rgb(50,65,44);2->Color.rgb(39,29,12);3->Color.rgb(7,22,38);4->Color.rgb(8,13,38);5->Color.rgb(3,31,43);else->Color.rgb(30,19,43)},s.accent))}
  when(selected){
   0->{
    centre.addView(text("LIVE RECEIPT PRESS  •  READY",20f,Color.WHITE,true).apply{gravity=Gravity.CENTER})
    centre.addView(text("EVERY NEW VIEWER IS PRINTED ON THE ROLL",10f,Color.rgb(220,255,210),true).apply{gravity=Gravity.CENTER;setPadding(0,3,0,7)})
    val machine=LinearLayout(this).apply{orientation=0;gravity=Gravity.CENTER;setPadding(7,6,7,6);background=bg(Color.rgb(42,180,47),Color.rgb(24,105,28))}
    printerView=machine
    val leftRail=LinearLayout(this).apply{orientation=1;gravity=Gravity.CENTER;setPadding(3,4,3,4);background=bg(Color.rgb(77,232,70),Color.rgb(30,130,34))}
    leftRail.addView(text("●",13f,Color.BLACK,true))
    leftRail.addView(text("┃\n┃\n┃\n┃\n┃\n┃",16f,Color.rgb(15,45,15),true).apply{gravity=Gravity.CENTER})
    leftRail.addView(text("●",13f,Color.BLACK,true))
    val rightRail=LinearLayout(this).apply{orientation=1;gravity=Gravity.CENTER;setPadding(3,4,3,4);background=bg(Color.rgb(77,232,70),Color.rgb(30,130,34))}
    rightRail.addView(text("●",13f,Color.BLACK,true))
    rightRail.addView(text("┃\n┃\n┃\n┃\n┃\n┃",16f,Color.rgb(15,45,15),true).apply{gravity=Gravity.CENTER})
    rightRail.addView(text("●",13f,Color.BLACK,true))
    val paper=LinearLayout(this).apply{orientation=1;setPadding(7,5,7,5);background=bg(Color.rgb(250,249,241),Color.rgb(190,190,180))}
    paperView=paper
    val head=LinearLayout(this).apply{orientation=0;gravity=Gravity.CENTER_VERTICAL;setPadding(4,3,4,5);background=bg(Color.rgb(220,220,215),Color.rgb(120,120,115))}
    head.addView(text("▰",18f,Color.rgb(30,65,210),true))
    head.addView(text("  PRINT HEAD  ",10f,Color.DKGRAY,true))
    head.addView(text("▰",18f,Color.rgb(30,65,210),true))
    paper.addView(head,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=5})
    paper.addView(text("LIVE VIEWER ROLL  •  AUTO PRINT",9f,Color.DKGRAY,true).apply{gravity=Gravity.CENTER;setPadding(0,2,0,5)})
    machine.addView(leftRail,LinearLayout.LayoutParams(24,-1))
    machine.addView(paper,LinearLayout.LayoutParams(0,-1,1f).apply{leftMargin=4;rightMargin=4})
    machine.addView(rightRail,LinearLayout.LayoutParams(24,-1))
    centre.addView(machine,LinearLayout.LayoutParams(-1,0,1f).apply{bottomMargin=6})
   }
   1->{
    centre.addView(text("🖨️ READY TO PRINT",22f,Color.rgb(255,210,90),true).apply{gravity=Gravity.CENTER})
    centre.addView(text("MYSTERY PRINT LAB",11f,Color.WHITE,true).apply{gravity=Gravity.CENTER;setPadding(0,3,0,6)})
    val mystery=LinearLayout(this).apply{orientation=1;gravity=Gravity.CENTER;setPadding(10,7,10,7);background=bg(Color.rgb(47,31,68),s.second)}
    printerView=mystery
    mystery.addView(text("╔══════════════════════╗",14f,s.second,true).apply{gravity=Gravity.CENTER})
    mystery.addView(text("🖨️",48f,Color.WHITE,true).apply{gravity=Gravity.CENTER})
    mystery.addView(text("MYSTERY PAPER LOADING…",12f,Color.WHITE,true).apply{gravity=Gravity.CENTER})
    mystery.addView(text("╚══════ ▤ ▤ ▤ ═══════╝",14f,s.second,true).apply{gravity=Gravity.CENTER})
    centre.addView(mystery,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=7})
   }
   2->{
    centre.addView(text("✦ THE VIP WALL ✦",22f,Color.rgb(255,207,92),true).apply{gravity=Gravity.CENTER})
    centre.addView(text("EXCLUSIVE GUEST DISPLAY",11f,Color.rgb(255,232,180),true).apply{gravity=Gravity.CENTER;setPadding(0,5,0,8)})
    val plaque=text("♛   VIP MEMBERS   ♛",20f,Color.rgb(255,224,150),true).apply{gravity=Gravity.CENTER;background=bg(Color.rgb(83,58,17),Color.rgb(255,207,92))}
    centre.addView(plaque,LinearLayout.LayoutParams(-1,0,1f))
   }
   4->{
    centre.addView(text("✦ COSMIC CONSTELLATION ✦",22f,Color.rgb(155,195,255),true).apply{gravity=Gravity.CENTER})
    centre.addView(text("YOUR AUDIENCE BECOMES THE GALAXY",11f,Color.WHITE,true).apply{gravity=Gravity.CENTER;setPadding(0,5,0,8)})
    val galaxy=text("✧       ·       ✦       ·       ✧\n\n       ✦       ◌       ✦\n\n✧       ·       ✦       ·       ✧",22f,Color.rgb(180,195,255),true).apply{gravity=Gravity.CENTER;background=bg(Color.rgb(14,20,57),Color.rgb(126,190,255))}
    centre.addView(galaxy,LinearLayout.LayoutParams(-1,0,1f))
   }
   5->{
    centre.addView(text("◉ DEEP SEA AQUARIUM ◉",22f,Color.rgb(90,235,220),true).apply{gravity=Gravity.CENTER})
    centre.addView(text("EVERY VIEWER SWIMS INTO YOUR WORLD",11f,Color.WHITE,true).apply{gravity=Gravity.CENTER;setPadding(0,5,0,8)})
    val ocean=text("  〰       🫧       〰\n\n       🪸       🐠\n\n  🫧       〰       🐟",22f,Color.rgb(110,225,235),true).apply{gravity=Gravity.CENTER;background=bg(Color.rgb(4,53,70),Color.rgb(62,225,210))}
    centre.addView(ocean,LinearLayout.LayoutParams(-1,0,1f))
   }
   else->{
    centre.addView(text("⚡ DIGITAL ARENA ⚡",22f,Color.rgb(70,220,255),true).apply{gravity=Gravity.CENTER})
    centre.addView(text("PLAYER ENTRY SYSTEM",11f,Color.WHITE,true).apply{gravity=Gravity.CENTER;setPadding(0,5,0,8)})
    val arena=text("◈  PLAYER SPAWN ZONE  ◈\n\n⚡  READY  ⚡",20f,Color.rgb(110,235,255),true).apply{gravity=Gravity.CENTER;background=bg(Color.rgb(7,39,58),Color.rgb(60,210,255))}
    centre.addView(arena,LinearLayout.LayoutParams(-1,0,1f))
   }
  }
  viewerGrid=LinearLayout(this).apply{orientation=1;setPadding(4,4,4,4)}
  viewerScroll=ScrollView(this).apply{isFillViewport=true;addView(viewerGrid)}
  if(selected==0){paperView?.addView(viewerScroll,LinearLayout.LayoutParams(-1,0,1f))}
  else{
   centre.addView(text(when(selected){2->"NEW VIP ARRIVALS";3->"NEW PLAYERS JOINING";4->"NEW STARS IN THE GALAXY";5->"NEW CREATURES IN THE AQUARIUM";else->"LIVE PRINT QUEUE"},12f,s.accent,true).apply{gravity=Gravity.CENTER;setPadding(0,5,0,3)})
   centre.addView(viewerScroll,LinearLayout.LayoutParams(-1,0,1f))
  }
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
  val columns=if(selected==0)3 else 2
  val target=if(row==null || row.childCount>=columns){
   LinearLayout(this).apply{orientation=0;gravity=Gravity.TOP;weightSum=2f}
    .also{currentViewerRow=it;grid.addView(it,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=7})}
  }else row
  val card=LinearLayout(this).apply{orientation=1;gravity=Gravity.CENTER;setPadding(if(selected==0)3 else 6,if(selected==0)5 else 8,if(selected==0)3 else 6,if(selected==0)5 else 8);background=bg(when(selected){0->Color.rgb(250,249,241);4->Color.rgb(27,32,77);5->Color.rgb(8,76,88);else->Color.rgb(247,243,250)},if(selected==0)Color.rgb(205,205,195) else styles[selected].second)}
  if(selected==4)card.addView(text("✦  STAR DISCOVERED  ✦",9f,Color.rgb(190,205,255),true).apply{gravity=Gravity.CENTER})
  if(selected==5)card.addView(text("🫧  NEW ARRIVAL  🫧",9f,Color.rgb(160,255,240),true).apply{gravity=Gravity.CENTER})
  val pic=ImageView(this).apply{layoutParams=LinearLayout.LayoutParams(if(selected==0)43 else if(selected>=4)64 else 54,if(selected==0)43 else if(selected>=4)64 else 54).apply{bottomMargin=4};scaleType=ImageView.ScaleType.CENTER_CROP}
  if(avatar.isNotBlank()){
   if(selected==0)pic.colorFilter=ColorMatrixColorFilter(ColorMatrix().apply{setSaturation(0f)})
   Glide.with(this).load(avatar).circleCrop().into(pic)
  }
  card.addView(pic)
  card.addView(text(name,if(selected==0)10f else 12f,if(selected>=4)Color.WHITE else Color.rgb(40,40,40),true).apply{gravity=Gravity.CENTER;maxLines=1})
  if(selected==0)card.addView(text("Thank Like…",8f,Color.GRAY).apply{gravity=Gravity.CENTER;maxLines=1})
  else if(username.isNotBlank())card.addView(text(username,9f,Color.DKGRAY).apply{gravity=Gravity.CENTER;maxLines=1})
  target.addView(card,LinearLayout.LayoutParams(0,-2,1f).apply{leftMargin=if(selected==0)2 else 4;rightMargin=if(selected==0)2 else 4})
  if(selected==0 || selected==1){
   playPrinterSound()
   card.alpha=0f
   card.translationY=-115f
   card.scaleX=0.92f
   card.scaleY=0.92f
   card.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
    .setDuration(720).setInterpolator(DecelerateInterpolator()).start()
   printerView?.animate()?.scaleX(0.97f)?.scaleY(0.94f)?.setDuration(90)?.withEndAction{
    printerView?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(160)?.start()
   }?.start()
  }else if(selected==4){
   card.alpha=0f;card.scaleX=0.25f;card.scaleY=0.25f
   card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(850).setInterpolator(DecelerateInterpolator()).start()
  }else if(selected==5){
   card.alpha=0f;card.translationY=85f
   card.animate().alpha(1f).translationY(0f).setDuration(950).setInterpolator(DecelerateInterpolator()).start()
  }else{
   card.alpha=0f
   card.animate().alpha(1f).setDuration(450).start()
  }
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

 private fun playPrinterSound(){
  try{
   if(printerSound==null)printerSound=ToneGenerator(AudioManager.STREAM_MUSIC,75)
   val tg=printerSound?:return
   tg.startTone(ToneGenerator.TONE_PROP_BEEP,75)
   soundHandler.postDelayed({tg.startTone(ToneGenerator.TONE_PROP_BEEP,55)},95)
   soundHandler.postDelayed({tg.startTone(ToneGenerator.TONE_PROP_BEEP,75)},180)
   soundHandler.postDelayed({tg.startTone(ToneGenerator.TONE_PROP_ACK,110)},285)
  }catch(_:Exception){}
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
  soundHandler.removeCallbacksAndMessages(null)
  try{printerSound?.release()}catch(_:Exception){}
  printerSound=null
  super.onDestroy()
 }
}
