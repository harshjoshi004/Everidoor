package com.everidoor.everidoor

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import androidx.core.app.ActivityCompat
import androidx.fragment.app.FragmentActivity
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Task
import crocodile8008.videoviewcache.lib.stop
import io.socket.emitter.Emitter
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Whenever I change a video should I emmit a socket with its video Id to the server?
// This app no feature to notify anyone about the current video. It doesnot even store
// the current video id in global scope in order even try to send it

class ConvertToScreen : FragmentActivity() {
    private val msocket = SocketHandler.getSocket()
    var serverUrl: String = "null"
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    var userEmail: String = "null"
    private var isRoomJoined = false
    private var isConnectToServerEmitted: Boolean = false
    private lateinit var locationTrack: LocationTrack
    private var orientation: Boolean = true
    private val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

    companion object {
        var sleepMode: Boolean = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_convert_to_screen)

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

        serverUrl = resources.getString(R.string.serverurl)
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        val imageView1 = findViewById<ImageView>(R.id.imageView3)
        imageView1.setImageResource(R.drawable.img)
        val imageView2 = findViewById<ImageView>(R.id.imageView5)
        imageView2.setImageResource(R.drawable.wowimage)
        val change = intent.getBooleanExtra("orientation", true)

        orientation = if (change) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
            true
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
            false
        }
        randNumberApiCall()
        askPermission()
    }

    private fun randNumberApiCall() {
        if (!validator()) {
            val url = "$serverUrl/generateNumber"
            val queue = Volley.newRequestQueue(this)
            var rand = ""
            val stringRequest = StringRequest(
                Request.Method.GET, url,
                { response ->
                    Log.d("api", "Random number API call successful!")
                    Log.d("api Response", response)
                    rand = response
                    Log.d("code", rand)
                    val data = JSONObject().put("screenID", rand).put("booleanValue", getOrientation())
                    println(data)
                    msocket.emit("get-screenId", data)
                    displaycode(rand)
                    savePersistenceCode(rand)
                    allSockets(rand)
                },
                { error ->
                    Log.d("api", "Random number API call FAILED! Error: ${error.networkResponse}")
                }
            )
            queue.add(stringRequest)
        } else {
            val sharedPreferences = this?.getSharedPreferences("Everidoor", Context.MODE_PRIVATE)
            val ACTIVATION_CODE = sharedPreferences?.getString("ACTIVATION_KEY", "null")
            if (ACTIVATION_CODE != null) {
                displaycode(ACTIVATION_CODE)
                val data = serverLogs(ACTIVATION_CODE) { data ->
                    if (data != null) {
                        println(data.toString())
                    } else {
                        println("Failed to fetch server logs.")
                    }
                }
                allSockets(ACTIVATION_CODE)
                Log.e("hitserver", ACTIVATION_CODE)
            }
        }
    }

    private fun displaycode(rand: String) {
        val t1 = findViewById<EditText>(R.id.t1)
        val t2 = findViewById<EditText>(R.id.t2)
        val t3 = findViewById<EditText>(R.id.t3)
        val t4 = findViewById<EditText>(R.id.t4)
        val t5 = findViewById<EditText>(R.id.t5)
        val t6 = findViewById<EditText>(R.id.t6)
        t1.setText(rand[0].toString())
        t2.setText(rand[1].toString())
        t3.setText(rand[2].toString())
        t4.setText(rand[3].toString())
        t5.setText(rand[4].toString())
        t6.setText(rand[5].toString())
    }

    fun allSockets(rand: String) {

//removing existing sockets
        msocket.off("$rand-room-joined")
        msocket.off("no-playlist-id-assigned-yet")
        msocket.off("playlist-updated-from-web-app")
        msocket.off("ack-content-for-mobile-app")
        msocket.off("$rand")
        msocket.off("send-current-video")
        msocket.off("edit-campaign-autoupdation-for-android")
        msocket.off("roomJoinedin")

        if (!UserExists()) {
// if user does not exist
    //recieves data from socket and extracts it
            msocket.on("$rand") { args ->
                Log.e("on Rand received ", "successful")
                Log.e("on rand, Received data", args[0].toString())
                Log.e("user not exist", "true")
                val jsonString = args[0].toString()
                val jsonObject = JSONObject(jsonString)
                userEmail = jsonObject.getString("username")
                savePersistenceUsername(userEmail)
                serverLogs(rand) { data ->
                    data?.put("username", getUserName())
                    if (data != null) {
                        Log.e("Send Data ", data.toString())
                        Log.e("Connect to server", "EMIT SUCCESS -connect-to-server(USER NOT EXISTS)")
                        msocket.emit("connect-to-server", data)
                        val jsonObject2 =
                            JSONObject().put("username", getUserName()).put("screenId", rand)
                        println(jsonObject2)

                        msocket.emit("join-room-for-phone", jsonObject2)
                        Log.d("Socket Event","Socket join-room-for-phone emitted")
                        isRoomJoined = true
                        msocket.on("venue-time") { args ->
                            println("venueTime socket has been received")
                            println(args[0].toString())
                            val jsonString2 = args[0].toString()
                            val jsonObject3 = JSONObject(jsonString2)
                            val startinghour = jsonObject3.getInt("startinghour")
                            val startingminute = jsonObject3.getInt("startingminute")
                            val endinghour = jsonObject3.getInt("endinghour")
                            val endingminute = jsonObject3.getInt("endingminute")
                            println("startinghour = $startinghour, startingminute = $startingminute endinghour = $endinghour, endingminute = $endingminute")

                            val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                            val currentMinute = Calendar.getInstance().get(Calendar.MINUTE)

                            if (currentHour > startinghour || (currentHour == startinghour && currentMinute >= startingminute)) {
                                if (currentHour < endinghour || (currentHour == endinghour && currentMinute <= endingminute)) {
                                    getPlaylistFromApi(rand)
                                }
                            }
                        }
                    } else {
                        println("Failed to fetch server logs.")
                    }
                }
            }
        } else if (UserExists() && !isRoomJoined && !isConnectToServerEmitted) {
            isConnectToServerEmitted = true
            Log.e("user exist", "true")
            serverLogs(rand) { data ->
                data?.put("username", getUserName())
                if (data != null) {
                    emitEventOnce("connect-to-server", data)
                    Log.e("Send Data ", data.toString())
                    Log.e("Connect to server", "EMIT SUCCESS -connect-to-server(USER EXISTS)")
                    val jsonObject2 =
                        JSONObject().put("username", getUserName()).put("screenId", rand)
                    println(jsonObject2)
                    msocket.emit("join-room-for-phone", jsonObject2)
                    msocket.on("venue-time") { args ->
                        println("venueTime socket has been received")
                        println(args[0].toString())
                        val jsonString2 = args[0].toString()
                        val jsonObject3 = JSONObject(jsonString2)
                        val startinghour = jsonObject3.getInt("startinghour")
                        val startingminute = jsonObject3.getInt("startingminute")
                        val endinghour = jsonObject3.getInt("endinghour")
                        val endingminute = jsonObject3.getInt("endingminute")
                        println("startinghour = $startinghour, startingminute = $startingminute endinghour = $endinghour, endingminute = $endingminute")

                        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                        val currentMinute = Calendar.getInstance().get(Calendar.MINUTE)

                        if (currentHour > startinghour || (currentHour == startinghour && currentMinute >= startingminute)) {
                            if (currentHour < endinghour || (currentHour == endinghour && currentMinute <= endingminute)) {
                                getPlaylistFromApi(rand)
                            }
                        }
                    }
                } else {
                    println("Failed to fetch server logs.")
                }
            }
        }

        Log.e("User Name", getUserName().toString())

        msocket.on("$rand-room-joined") {
            Log.e("ROOM JOINED", "RECEIVED ROOM JOINED")
            val data = JSONObject()
            data.put("username", getUserName())
            data.put("screenId", rand)
            Log.e("GET CONTENT EMIT DONE", "EMIT SUCCESS -FOR ACK CONTENT")
            msocket.emit("get-content-for-mobile-app", data)
        }

        msocket.on("no-playlist-id-assigned-yet") {
            Log.e("NO PLAYLIST FOUND", "")
        }

        msocket.on("set-sleep-mode") { args ->
            handleSleepMode()
        }

        msocket.on("roomJoinedin") { args ->
            Log.d("Socket Event","recieved roomJoinedin Socket")
            val response = args[0] as JSONObject
            Log.d("Socket Event","roomJoinedin response : $response")
        }
        msocket.on("close-sleep-mode") { args ->
            val response = args[0] as JSONObject

            val startingDate = response.getInt("startingDate")
            val startingMonth = response.getInt("startingMonth")
            val startingYear = response.getInt("startingYear")
            val startingHour = response.getInt("startingHour")
            val startingMinute = response.getInt("startingMinute")
            val endingDate = response.getInt("endingDate")
            val endingMonth = response.getInt("endingMonth")
            val endingYear = response.getInt("endingYear")
            val endingHour = response.getInt("endingHour")
            val endingMinute = response.getInt("endingMinute")

            closeSleepMode(
                startingDate, startingMonth, startingYear, startingHour, startingMinute,
                endingDate, endingMonth, endingYear, endingHour, endingMinute
            )
        }

        msocket.on("send-current-video") {
            val data = JSONObject()
            data.put("username", getUserName())
            data.put("screenId", rand)
            data.put("contentId", Display.currentVideoBeingPlayed)
            data.put("play_pause_status", Display.videoview.isPlaying)
            data.put("seekTo", Display.videoview.currentPosition)
            println("Harsh:22-7: Data to send to server: $data")
            msocket.emit("ack-send-current-video", data)
        }
        /* msocket.on("edit-campaign-autoupdation-for-android"){
             Log.d("Socket Event","Socket edit campaign recieved")
             getPlaylistFromApi(rand)
             Log.d("Socket Event","playlist run")
         }*/
    }

    @SuppressLint("SuspiciousIndentation")
    private fun handleSleepMode() {
        val activityLayout = findViewById<View>(android.R.id.content)

        runOnUiThread {
            activityLayout.setBackgroundColor(Color.BLACK)
            muteVolume()
            minBrightness()
            Display.videoview.stop()
            Display.videoview.visibility = View.GONE
            Display.logo.visibility = View.GONE
            sleepMode = true
        }
    }

    private fun closeSleepMode(
        startingDate: Int,
        startingMonth: Int,
        startingYear: Int,
        startingHour: Int,
        startingMinute: Int,
        endingDate: Int,
        endingMonth: Int,
        endingYear: Int,
        endingHour: Int,
        endingMinute: Int
    ) {
        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH) + 1
        val currentDate = calendar.get(Calendar.DAY_OF_MONTH)
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)

        val activityLayout = findViewById<View>(android.R.id.content)

        val isWithinDateRange =
            (currentYear > startingYear || (currentYear == startingYear && currentMonth > startingMonth) ||
                    (currentYear == startingYear && currentMonth == startingMonth && currentDate >= startingDate)) &&
                    (currentYear < endingYear || (currentYear == endingYear && currentMonth < endingMonth) ||
                            (currentYear == endingYear && currentMonth == endingMonth && currentDate <= endingDate))

        val isWithinTimeRange =
            (currentHour > startingHour || (currentHour == startingHour && currentMinute >= startingMinute))
        (currentHour < endingHour || (currentHour == endingHour && currentMinute <= endingMinute))

        if (isWithinDateRange && isWithinTimeRange) {
            runOnUiThread {
                activityLayout.setBackgroundColor(Color.TRANSPARENT)
                unmuteVolume()
                maxBrightness()
                Display.videoview.start()
                sleepMode = false
            }
        }
    }

    fun emitEventOnce(eventName: String, data: Any) {
        val listener = object : Emitter.Listener {
            override fun call(vararg args: Any?) {
                println("Event received: $args")
                msocket.off(eventName, this)
            }
        }
        msocket.on(eventName, listener)
        msocket.emit(eventName, data)
    }

    fun firePlayer(pl: JSONObject, rand: String) {

        val prevIdForSetUpPlayer = "-1" // New Addition

        val slotsArray = pl.getJSONArray("slots")
        Display.globalSlotsArray = slotsArray
        val defaultContentId = pl.getString("default_contentId")
        println("FirePlayer: Slots Array: $slotsArray and defContentId = $defaultContentId")
        val contentIdArray = ArrayList<String>()
        val durationArray = ArrayList<Int>()
        val advertiserArray = ArrayList<String>()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayDate = sdf.format(Calendar.getInstance().time)

        println("Fireplayer: Length of SlotsArray is ${slotsArray.length()}")

        val totalSlots = slotsArray.length()
        var slotIndex = 0

        val contentIdCountMap = mutableMapOf<String, Int>()

        for (i in 0 until slotsArray.length()) {
            println("FirePlayer: Slot under Iteration: $i")
            val slotArray = slotsArray.getJSONArray(i)
            for (j in 0 until slotArray.length()) {
                val slot = slotArray.getJSONObject(j)
                val contentId = slot.getString("contentId")
                val advertiser = slot.getString("advertiser")
                val fromDate = slot.getString("fromDate")
                val toDate = slot.getString("toDate")
                val status = slot.getString("booked")
                val sameAdvertisementForNextnSlots = slot.getString("sameAdvertisementForNextnSlots").toInt()

                // x
                // 3
                // we have to make: x,x,x,0,0,..
                // and: 1,1,1,1,1...
                // In SetUp player, We can do one thing.

                if (todayDate >= fromDate && todayDate <= toDate && status == "Booked" && slotIndex < totalSlots) {
                    if (!contentIdCountMap.containsKey(contentId)) {
                        contentIdCountMap[contentId] = 0
                    }
                    var slotsToAdd = sameAdvertisementForNextnSlots
                    while (slotsToAdd > 0 && slotIndex < totalSlots && contentIdCountMap[contentId]!! < sameAdvertisementForNextnSlots) {
                        println("Adding contentId: $contentId")
                        contentIdArray.add(contentId)
                        durationArray.add(1) // setting duration to 1 for each slot
                        advertiserArray.add(advertiser)
                        slotIndex++
                        slotsToAdd--
                        contentIdCountMap[contentId] = contentIdCountMap[contentId]!! + 1
                    }
                }
            }
        }

        // Fill remaining slots with default content
        while (contentIdArray.size < totalSlots) {
            contentIdArray.add(defaultContentId) // Default content ID
            durationArray.add(1) // Default duration
            advertiserArray.add("Default Advertiser") // Default advertiser
        }

        println("ContentId Array: $contentIdArray")
        println("Duration Array: $durationArray")
        println("Advertiser Array: $advertiserArray")

        if (contentIdArray.isNotEmpty()) {
            println("The contentIdArray has at least one video.")
            val intent = Intent(this, Display::class.java)
            println("Harsh: Intent for Display is called")
            intent.putExtra("prevId", prevIdForSetUpPlayer) // New Addition
            intent.putExtra("change", true)
            intent.putExtra("PlayList", contentIdArray)
            intent.putExtra("Duration", durationArray)
            intent.putExtra("Advertiser", advertiserArray)
            intent.putExtra("deviceID", rand)
            intent.putExtra("orientation2", orientation)
            startActivity(intent)
            finish()
        } else {
            println("The contentIdArray doesn't have any video.")
            showDialog()
        }
    }

    private fun getPlaylistFromApi(rand: String) {
        val url = "https://vkvp4qqj-4000.inc1.devtunnels.ms/getMissedSlotsForToday"
        val requestQueue = Volley.newRequestQueue(this@ConvertToScreen)

        val jsonData = JSONObject().put("screenId", rand).put("username", getUserName())

        val stringRequest = JsonObjectRequest(Request.Method.POST, url, jsonData,
            { response ->
                Log.d("API Response", response.toString())
                val jsonObject = JSONObject(response.toString())
                firePlayer(jsonObject, rand)
            },
            { error ->
                println("Error is " + error.message)
            })

        requestQueue.add(stringRequest)
    }


    private fun muteVolume() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.adjustVolume(AudioManager.ADJUST_MUTE, AudioManager.FLAG_PLAY_SOUND)
    }

    private fun unmuteVolume() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.adjustVolume(AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_PLAY_SOUND)
    }

    private fun maxBrightness() {
        runOnUiThread {
            val layoutParams = window.attributes
            layoutParams.screenBrightness = 1.0f
            window.attributes = layoutParams
        }
    }

    private fun minBrightness() {
        runOnUiThread {
            val layoutParams = window.attributes
            layoutParams.screenBrightness = 0.2f
            window.attributes = layoutParams
        }
    }

    @SuppressLint("MissingPermission")
    private fun getUserLocation(callback: (Pair<String, String>) -> Unit) {
        val task: Task<Location> = fusedLocationProviderClient.lastLocation

        task.addOnSuccessListener {
            locationTrack = LocationTrack(this@ConvertToScreen)
            val userLocation = Pair(locationTrack.getLatitude(), locationTrack.getLongitude())
            callback(userLocation)
        }
    }

    private fun reverseGeocode(latitude: Double, longitude: Double): String {
        val geocoder = Geocoder(this, Locale.getDefault())

        try {
            if (latitude != -180.0 && longitude != -90.0) {
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                if (addresses != null && addresses.isNotEmpty()) {
                    val address: Address = addresses[0]
                    return address.getAddressLine(0)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return "No Address Found"
    }

    private fun showDialog() {
        runOnUiThread {
            val builder = AlertDialog.Builder(this)
            val inflater = LayoutInflater.from(this)
            val dialogView = inflater.inflate(R.layout.dialogbox, null)
            builder.setView(dialogView)
            val dialog = builder.create()
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.WHITE))
            dialog.show()
        }
    }

    fun askPermission() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this as Activity,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1
            )
        }
    }

    private fun validator(): Boolean {
        val sharedPreferences = this?.getSharedPreferences("Everidoor", Context.MODE_PRIVATE)
        val Activation_CODE = sharedPreferences?.getString("ACTIVATION_KEY", "null")
        return Activation_CODE != "null"
    }

    private fun savePersistenceCode(code: String) {
        val sharedPreferences = this?.getSharedPreferences("Everidoor", Context.MODE_PRIVATE)
        val editor = sharedPreferences?.edit()
        editor?.putString("ACTIVATION_KEY", code)
        editor?.apply()
    }

    private fun savePersistenceUsername(email: String) {
        val sharedPreferences = this?.getSharedPreferences("Everidoor", Context.MODE_PRIVATE)
        val editor = sharedPreferences?.edit()
        editor?.putString("Username", email)
        editor?.apply()
    }

    private fun getOrientation(): String {
        val sharedPreferences = this.getSharedPreferences("Everidoor", Context.MODE_PRIVATE)
        return when (sharedPreferences.getString("ORIENTATION", "null")) {
            "true" -> {
                "true"
            }

            "false" -> {
                "false"
            }

            else -> {
                "null"
            }
        }
    }

    fun getUserName(): String? {
        val sharedPreferences = this?.getSharedPreferences("Everidoor", Context.MODE_PRIVATE)
        return sharedPreferences?.getString("Username", "null")
    }

    fun getActivationCode(): String? {
        val sharedPreferences = this?.getSharedPreferences("Everidoor", Context.MODE_PRIVATE)
        return sharedPreferences?.getString("ACTIVATION_KEY", "null")
    }

    fun UserExists(): Boolean {
        return getUserName() != "null"
    }

    fun JSONArray.toArrayList(): ArrayList<String> {
        val list = arrayListOf<String>()
        for (i in 0 until this.length()) {
            list.add(this.getString(i))
        }
        return list
    }

    private fun serverLogs(activationCode: String, callback: (JSONObject?) -> Unit) {
        getUserLocation { location ->
            val latitude = location.first
            val longitude = location.second
            val manufacturer = Build.MANUFACTURER
            val model = Build.MODEL
            val androidVersion = Build.VERSION.RELEASE
            val screenSize = getScreenSize(this)
            val screenWidth = screenSize.first
            val screenHeight = screenSize.second

            val data = JSONObject()
            data.put("clientID", activationCode)
            data.put("latitude", latitude)
            data.put("longitude", longitude)
            data.put("manufacturer", manufacturer)
            data.put("model", model)
            data.put("screenSize", "$screenHeight * $screenWidth")
            data.put("operSys", "ANDROID$androidVersion")
            data.put("volume", "10")
            data.put("brightness", "100")
            data.put("SCREEN_ADDRESS_FROM_PHONE", reverseGeocode(latitude.toDouble(), longitude.toDouble()))

            Log.e("SERVERLOGS", data.toString())
            callback(data)
        }
    }

    fun getScreenSize(context: Context): Pair<Int, Int> {
        val displayMetrics = DisplayMetrics()
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        return Pair(screenWidth, screenHeight)
    }

//Why is this commented?

  /*  override fun onDestroy() {
        super.onDestroy()
        SocketHandler.closeConnection()
    }*/

}
