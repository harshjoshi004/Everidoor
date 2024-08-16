package com.everidoor.everidoor

import android.app.ProgressDialog
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import android.widget.VideoView
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.provider.Settings.System
import android.widget.ImageView
import android.widget.MediaController
import androidx.annotation.RequiresApi
import androidx.fragment.app.FragmentActivity
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import crocodile8008.videoviewcache.lib.playUrl
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.LinkedList
import java.util.Locale
import java.util.Queue

class Display : FragmentActivity() {
    // for new setupPlayer
    private var playerRunnable: Runnable? = null
    private var handler: Handler? = null
    private var SlotQueue: Queue<Runnable> = LinkedList()

    private val videosJSONArray = JSONArray()
    private lateinit var audioManager: AudioManager
    private lateinit var context: Context
    lateinit var loadingvideo: VideoView
    private var totalPlayCount = 0
    private var totalPlaylistDuration = 0L
    private lateinit var mediaController: MediaController
    private var progressDialog: ProgressDialog? = null
    var msocket = SocketHandler.getSocket()
    private var updatedContentIdArrayForIb = ArrayList<String>()
    private var updatedDurationArrayForIb = ArrayList<Int>()
    private var advertiserArray = ArrayList<String>()

    companion object {
        var contentId: String = ""

        // current Video id that Runner() is playing
        var currentVideoBeingPlayed = ""
        // for video-loop-count
        var globalSlotsArray = JSONArray()

        lateinit var videoview: VideoView
        lateinit var logo: ImageView
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_display)
        SocketHandler.setSocket()
        SocketHandler.establishConnection()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            val decorView = window.decorView
            val flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            decorView.systemUiVisibility = flags
        }

        actionBar?.hide()

        logo = findViewById<ImageView>(R.id.logo)
        logo.visibility = View.GONE

        videoview = findViewById(R.id.videoView)
        loadingvideo = findViewById(R.id.videoView)

        val prevId = intent.getStringExtra("prevId") // New Addition

        val change = intent.getBooleanExtra("change", false)
        println(change)
        val pl = intent.getStringArrayListExtra("PlayList")
        val duration = intent.getIntegerArrayListExtra("Duration")
        val advertiser = intent.getStringArrayListExtra("Advertiser")
        if (advertiser != null) {
            advertiserArray = advertiser
        }
        context = applicationContext
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        setDefaultVolume()
        setVolumeToZero()

        mediaController = MediaController(this)

        // This block sets default video path
        val videoPath = "android.resource://" + packageName + "/" + R.raw.everidoor
        loadingvideo.setVideoPath(videoPath)
        println("Harsh: Default loading video set")
        loadingvideo.setMediaController(null)

        // This block calls setup player for our array
        println("harsh: playlist before default video set ${pl.toString()}")
        if (change) {
            if (pl != null && duration != null && prevId != null) {
                val i = 0
                setupPlayer(videoview, pl, duration, i, "-1")
                println("Harsh: Initital video paths set")
            }
        }


        setMaxVolume(this)

        controlSockets()


        //this block sets orientation of screen
        val change2 = intent.getBooleanExtra("orientation2", true)
        if (change2) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun controlSockets() {
        msocket.off("updatePlayPause")
        msocket.off("updateVolume")
        msocket.off("updateBrightness")
        msocket.off("stopDevice")
        msocket.off("edit-campaign-autoupdation-for-android")
        msocket.off("resetcampaign-defaultData") //if campaign is reset then we emit this to recieve 0000 content id

        msocket.on("updatePlayPause") {
            if (videoview.isPlaying) {
                videoview.pause()
                Log.e("play/pause", "received")
            } else {
                videoview.start()
            }
            val data = JSONObject().put("username", getUserName()).put("screenId", getScreenId())
            msocket.emit("ack-playPause", data)
        }

        msocket.on("updateVolume") { args ->
            Log.e("get volume", "get volume socket")
            Log.e("get volume", getVolume().toString())
            Log.e("args", args[0].toString())
            val jsonString = args[0].toString()
            val jsonObject = JSONObject(jsonString)
            val desiredVolume = jsonObject.getString("desired_volume")
            val userName = jsonObject.getString("username")
            Log.e("args vol", desiredVolume.toString())
            val currvolume = getVolume().toString()
            volUpdateWithValue(desiredVolume.toInt())
            val data = JSONObject().put("screenId", getScreenId()).put("username", userName)
                .put("volumeVal", currvolume)
            msocket.emit("ack-updateVol", data)
        }

        msocket.on("updateBrightness") { args ->
            Log.e("set brightness", "get brightness socket")
            Log.e("set brightness", getVolume().toString())
            Log.e("args", args[0].toString())
            val jsonString = args[0].toString()
            val jsonObject = JSONObject(jsonString)
            val userName = jsonObject.getString("username")
            val desiredBrightness = jsonObject.getString("desired_Brightness")
            Log.e("args vol", desiredBrightness.toString())
            val data = JSONObject().put("brightnessVal", getBrightness()).put("username", userName)
                .put("screenId", getScreenId())
            if (desiredBrightness.toInt() > 50) {
                maxBrightness()
            } else {
                minBrightness()
            }
            msocket.emit("ack-updateBrightness", data)
        }

        msocket.on("stopDevice") {
            videoview.stopPlayback()
            val intent = Intent(this, ConvertToScreen::class.java)
            intent.putExtra("orientation", requestedOrientation)
            startActivity(intent)
            finish()
        }

        msocket.on("edit-campaign-autoupdation-for-android"){
            println("Display.kt: Campaign is edited.. getPlaylistFromApi() called")
            getPlaylistFromApi()
        }

        //socket event to play default video if campaigns are reset

        msocket.on("resetcampaign-defaultData"){
            Log.d("Socket Event","Reset Campaign recieved")
            getPlaylistFromApi()
        }

        //socket event to play default video if campaigns are reset
        msocket.on("get-todays-slots") { args ->
            Log.d("Socket event", "Socket Received get-todays-slots")
            Log.d("Socket Response", args[0].toString())
            getPlaylistFromApi()
        }

        msocket.on("get-todays-slots --prev") { args ->
            Log.d("Socket event", "Socket Received get-todays-slots")
            Log.d("Socket Response", args[0].toString())
            val jsonData = JSONObject(args[0].toString())
            val slotsArray = jsonData.getJSONArray("slots")
            globalSlotsArray = slotsArray
            println("Slots Array: $slotsArray")
            val updatedContentIdArray = ArrayList<String>()
            val updatedDurationArray = ArrayList<Int>()
            val updatedAdvertiserArray = ArrayList<String>()
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val todayDate = sdf.format(Calendar.getInstance().time)

            val totalSlots = 12
            var slotIndex = 0

            val contentIdCountMap = mutableMapOf<String, Int>()

            for (i in 0 until slotsArray.length()) {
                val slotArray = slotsArray.getJSONArray(i)
                for (j in 0 until slotArray.length()) {
                    val slot = slotArray.getJSONObject(j)
                    val contentId = slot.getString("contentId")
                    val advertiser = slot.getString("advertiser")
                    val fromDate = slot.getString("fromDate")
                    val toDate = slot.getString("toDate")
                    val status = slot.getString("booked")
                    val sameAdvertisementForNextnSlots = slot.optInt("sameAdvertisementForNextnSlots", 1)

                    if (todayDate >= fromDate && todayDate <= toDate && status == "Booked" && slotIndex < totalSlots) {
                        var slotsToAdd = sameAdvertisementForNextnSlots
                        contentIdCountMap[contentId] = contentIdCountMap.getOrDefault(contentId, 0)

                        while (slotsToAdd > 0 && slotIndex < totalSlots && contentIdCountMap[contentId]!! < sameAdvertisementForNextnSlots) {
                            println("Adding contentId: $contentId")
                            updatedContentIdArray.add(contentId)
                            updatedDurationArray.add(1) // setting duration to 1 for each slot
                            updatedAdvertiserArray.add(advertiser)
                            slotIndex++
                            slotsToAdd--
                            contentIdCountMap[contentId] = contentIdCountMap[contentId]!! + 1
                        }
                    }
                }
            }

            while (updatedContentIdArray.size < totalSlots) {
                Log.d("Loop", "While loop called")
                updatedContentIdArray.add("000000000") // Default content ID
                updatedDurationArray.add(1) // Default duration
                updatedAdvertiserArray.add("Default Advertiser") // Default advertiser
                slotIndex++
            }

            println("Updated ContentId Array: $updatedContentIdArray")
            println("Updated Duration Array: $updatedDurationArray")
            println("Updated Advertiser Array: $updatedAdvertiserArray")

            updatedContentIdArrayForIb = updatedContentIdArray
            updatedDurationArrayForIb = updatedDurationArray
            advertiserArray = updatedAdvertiserArray

            // Restart player with updated playlist
            if (updatedContentIdArrayForIb.isNotEmpty() && updatedDurationArrayForIb.isNotEmpty()) {
                // setupPlayer(videoview, updatedContentIdArrayForIb, updatedDurationArrayForIb, 0)
                // Definition of setup player is changed!
            }
        }
    }

    private fun getPlaylistFromApi() {
        val url = "https://vkvp4qqj-4000.inc1.devtunnels.ms/getMissedSlotsForToday"
            //"https://www.everidoorbackend.com/getMissedSlotsForToday"
        val requestQueue = Volley.newRequestQueue(this@Display)

        val jsonData = JSONObject().put("screenId", getScreenId()).put("username", getUserName())

        val stringRequest = JsonObjectRequest(Request.Method.POST, url, jsonData,
            { response ->
                Log.d("GetPlayListFromAPI: API Response", response.toString())
                val jsonData = JSONObject(response.toString())
                val slotsArray = jsonData.getJSONArray("slots")
                val defContentId = jsonData.getString("default_contentId")

                println("GetPlayListFromAPI: Slots Array: $slotsArray and defaultContentId: $defContentId")

                val updatedContentIdArray = ArrayList<String>()
                val updatedDurationArray = ArrayList<Int>()
                val updatedAdvertiserArray = ArrayList<String>()
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val todayDate = sdf.format(Calendar.getInstance().time)

                println("GetPlaylist from API: Length of SlotsArray is ${slotsArray.length()}")

                val totalSlots = slotsArray.length()
                var slotIndex = 0

                val contentIdCountMap = mutableMapOf<String, Int>()

                for (i in 0 until slotsArray.length()) {
                    println("GetPlayListFromAPI: Slot under Iteration: $i")
                    val slotArray = slotsArray.getJSONArray(i)
                    for (j in 0 until slotArray.length()) {
                        val slot = slotArray.getJSONObject(j)
                        val contentId = slot.getString("contentId")
                        val advertiser = slot.getString("advertiser")
                        val fromDate = slot.getString("fromDate")
                        val toDate = slot.getString("toDate")
                        val status = slot.getString("booked")
                        val sameAdvertisementForNextnSlots = slot.getString("sameAdvertisementForNextnSlots").toInt()

                        if (todayDate >= fromDate && todayDate <= toDate && status == "Booked" && slotIndex < totalSlots) {
                            if (!contentIdCountMap.containsKey(contentId)) {
                                contentIdCountMap[contentId] = 0
                            }
                            var slotsToAdd = sameAdvertisementForNextnSlots
                            while (slotsToAdd > 0 && slotIndex < totalSlots && contentIdCountMap[contentId]!! < sameAdvertisementForNextnSlots) {
                                updatedContentIdArray.add(contentId)
                                updatedDurationArray.add(1) // setting duration to 1 for each slot
                                updatedAdvertiserArray.add(advertiser)
                                slotIndex++
                                slotsToAdd--
                                contentIdCountMap[contentId] = contentIdCountMap[contentId]!! + 1
                            }
                        }
                    }
                }

                // Fill remaining slots with default content
                while (updatedContentIdArray.size < totalSlots) {
                    updatedContentIdArray.add(defContentId) // Default content ID
                    updatedDurationArray.add(1) // Default duration
                    updatedAdvertiserArray.add("Default Advertiser") // Default advertiser
                }

                println("Updated ContentId Array: $updatedContentIdArray")
                println("Updated Duration Array: $updatedDurationArray")
                println("Updated Advertiser Array: $updatedAdvertiserArray")

                updatedContentIdArrayForIb = updatedContentIdArray
                updatedDurationArrayForIb = updatedDurationArray
                advertiserArray = updatedAdvertiserArray

                // Restart player with updated playlist
                if (updatedContentIdArrayForIb.isNotEmpty() && updatedDurationArrayForIb.isNotEmpty()) {
                    setupPlayer(videoview, updatedContentIdArrayForIb, updatedDurationArrayForIb, 0, "-1")
                }
            },
            { error ->
                Log.e("API Error", error.toString())
            })
        requestQueue.add(stringRequest)
    }

    private fun volUpdateWithValue(n: Int) {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, n, 0)
    }

    private fun increaseVolume() {
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_RAISE,
            AudioManager.FLAG_SHOW_UI
        )
    }

    private fun getVolume(): Int {
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        return currentVolume
    }

    private fun decreaseVolume() {
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_LOWER,
            AudioManager.FLAG_SHOW_UI
        )
    }

    private fun setVolumeToZero() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
    }

    private fun maxBrightness() {
        runOnUiThread {
            val layoutParams = window.attributes
            layoutParams.screenBrightness = 1.0f // set brightness level between 0.0f and 1.0f
            window.attributes = layoutParams
        }
    }

    private fun minBrightness() {
        runOnUiThread {
            val layoutParams = window.attributes
            layoutParams.screenBrightness = 0.2f // set brightness level between 0.0f and 1.0f
            window.attributes = layoutParams
        }
    }

    private fun getBrightness(): Int {
        return try {
            System.getInt(contentResolver, System.SCREEN_BRIGHTNESS)
        } catch (e: Exception) {
            0
        }
    }

    fun setMaxVolume(context: Context) {
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0)
    }

    private fun setupPlayer(
        videoview: VideoView,
        contentIdArray: ArrayList<String>,
        durationArray: ArrayList<Int>,
        index: Int,
        prevId: String
    ) {
        handler = Handler(Looper.getMainLooper())
        println("Setup_Player: Setup player called!")
        var previousId = "-1"
        var ind = 0
        // Define the runnable
        println("Setup_Player: Building our Runnable Block")
        playerRunnable = object : Runnable {
            override fun run() {
                val handler2 = Handler(Looper.getMainLooper())
                if (ind < contentIdArray.size) {
                    val contentId = contentIdArray[ind]
                    val url = "https://s3.ap-south-1.amazonaws.com/everidoor2.0/Videos/$contentId.mp4"
                    println("Player_Runnable: ind = $ind URL set to VideoView: $url")

                    if (previousId != contentId) {
                        // regular mode
                        println("Player_Runnable: VideoView prevId ($previousId) != contentId ($contentId), ie regular-mode")
                        runOnUiThread {
                            println("Player_Runnable: setting video attributes in ui thread")
                            videoview.setVideoPath(url)
                            videoview.setOnCompletionListener {
                                videoview.start()
                            }
                            videoview.start()
                            logo.visibility = View.VISIBLE

                            currentVideoBeingPlayed = contentId
                            // Socket Emmit Here

                            val dataForAck = JSONObject()
                            dataForAck.put("username", getUserName())
                            dataForAck.put("screenId", getScreenId())
                            dataForAck.put("contentId", currentVideoBeingPlayed)
                            dataForAck.put("play_pause_status", Display.videoview.isPlaying)
                            dataForAck.put("seekTo", Display.videoview.currentPosition)
                            println("Harsh:22-7: Data to send to server: $dataForAck")
                            msocket.emit("ack-send-current-video", dataForAck)
                        }
                        previousId = contentId
                    } else {
                        println("Player_Runnable: VideoView prevId ($previousId) == contentId ($contentId), ie persist-mode")
                        previousId = contentId
                    }

                    val sleepDuration = 10000 * durationArray[ind].toLong()
                    println("Player_Runnable: Sleeping for $sleepDuration milliseconds")
                    handler2?.postDelayed(this, sleepDuration)

                    println("Player_Runnable: Runnable has woken up")
                    ind++
                } else {
                    ind = 0
                    println("Player_Runnable: Loop Has Ended, (One Runnable Block is Ended)")
                }
            }
        }

        if(SlotQueue.isEmpty()){
            println("Setup_Player: SlotQueue is Empty, initialising queueRunner and enqueueing first runnable")
            SlotQueue.offer(playerRunnable)
            Thread {
                while(true){
                    println("Runner: While loop Runs, size of slotQueue is ${SlotQueue.size}")
                    SlotQueue.forEachIndexed { index, it ->
                        println("Runner: SlotQueue under iteration $index")

                        runOnUiThread(it)

                        println("Runner: Runner went to sleep")
                        Thread.sleep(contentIdArray.size.toLong()*10*1000)
//                        Thread.sleep(5*1000)
                        println("Runner: Runner is back from sleep")
                        println("Runner: SlotQueue iteration $index is done")

                        sendVideoLoopCount(advertiserArray, contentIdArray, globalSlotsArray)
                    }
                }
            }.start()
        } else {
            println("Setup_Player: SlotQueue enqueued with playerRunnable")

            SlotQueue.offer(playerRunnable)

            if(SlotQueue.size > 1) {
                SlotQueue.poll()
                println("Setup_Player: SlotQueue dequeued extra playerRunnable")
            }
        }
    }
//    var nitin = 0
    fun sendVideoLoopCount(advertiserArray: ArrayList<String>, contentIdArray: ArrayList<String>, globalSlotsArray: JSONArray){
//        This Conversion takes substantial amount of time, hence running on separate thread
//        if(nitin<1)
        Thread {
//            nitin++
            println("VideoLoopCount: Json Array Convertion started!")
            val advertiserJson = JSONArray(advertiserArray)
            val contentIdJson = JSONArray(contentIdArray)
            println("VideoLoopCount: Conversion Finished")

            val data = JSONObject()
            data.put("username", getUserName())
            data.put("screenId", getScreenId())
            data.put("advertiserArray", advertiserJson)
            data.put("slotsArray", globalSlotsArray)
            data.put("contentIdArray", contentIdJson)

            msocket.emit("video-loop-count", data)
            println("Video-Loop-Count Emitted! $data")

//            println("VideoLoopCount: Send-Video-Loop Count Sent")
//            println("VideoLoopCount: advertiserArray: $advertiserJson")
//            println("VideoLoopCount: contentIdArray: $contentIdJson")
//            println("VideoLoopCount: globalSlotsJson: $globalSlotsArray")
        }.start()
    }

    fun updateVideo(pl: ArrayList<String>, duration: ArrayList<Int>, i: Int) {
        var b=i
        b++
        Log.e("completed", "true")
        println(videoview.duration)
        if (updatedContentIdArrayForIb.isEmpty() && updatedDurationArrayForIb.isEmpty()) {
            setupPlayer(videoview, pl, duration, b, "-1")
        } else {
            setupPlayer(videoview, updatedContentIdArrayForIb, updatedDurationArrayForIb, b, "-1")
        }

        // Update play count and playlist duration
        totalPlayCount++
        totalPlaylistDuration += videoview.duration

        // Generate JSON object with playtime and count information
        val jsonObject = JSONObject()
        jsonObject.put("totalPlayCount", totalPlayCount)
        jsonObject.put("totalPlaylistDuration", totalPlaylistDuration)

        // Convert JSON object to string
        val jsonString = jsonObject.toString()
        Log.e("VIDEO METADATA", jsonString)
    }

    fun getUserName(): String? {
        val sharedPreferences = this?.getSharedPreferences("Everidoor", Context.MODE_PRIVATE)
        return sharedPreferences?.getString("Username", "null")
    }

    fun getScreenId(): String? {
        val sharedPreferences = this?.getSharedPreferences("Everidoor", Context.MODE_PRIVATE)
        return sharedPreferences?.getString("ACTIVATION_KEY", "null")
    }

    //default volume 10
    private fun setDefaultVolume() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 10, 0)
    }

    //default volume 10

    private fun updatePlayer(videoview: VideoView, pl: ArrayList<String>, duration: ArrayList<Int>) {
        // Stop current playback
        videoview.stopPlayback()
        // Start the player with the new playlist
        setupPlayer(videoview, pl, duration, 0, "-1")
    }

    override fun onResume() {
        super.onResume()
        val curUrl = "https://s3.ap-south-1.amazonaws.com/everidoor2.0/Videos/${Display.currentVideoBeingPlayed}.mp4"
        println("LifeCycle: OnResume: setting video $curUrl")
        videoview.setVideoPath(curUrl)
        videoview.start()
        logo.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
