@file:Suppress("DEPRECATION")

package com.everidoor.everidoor

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.everidoor.everidoor.model.LocationPost
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Task
import com.google.android.material.snackbar.Snackbar
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.InstallState
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import timber.log.Timber
import java.util.concurrent.Executors

class MainActivity : FragmentActivity() {
//Updating the app version
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private val appUpdateManager: AppUpdateManager by lazy { AppUpdateManagerFactory.create(this) }
    private val appUpdatedListener: InstallStateUpdatedListener by lazy {
        object : InstallStateUpdatedListener {
            override fun onStateUpdate(installState: InstallState) {
                when {
                    installState.installStatus() == InstallStatus.DOWNLOADED -> popupSnackbarForCompleteUpdate()
                    installState.installStatus() == InstallStatus.INSTALLED -> appUpdateManager.unregisterListener(this)
                    else -> Timber.d("InstallStateUpdatedListener: state: %s", installState.installStatus())
                }
            }
        }
    }
//on create
    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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

        val imageView = findViewById<ImageView>(R.id.imageView2)
        imageView.setImageResource(R.drawable.img)

        val pradio = findViewById<RadioButton>(R.id.pradio)
        val hradio = findViewById<RadioButton>(R.id.hradio)

        // Change 1
        // Commenting the following two lines of code is crashing the app
        SocketHandler.setSocket()
        SocketHandler.establishConnection()

//go to convert to screen
        if (validateOrientation() == "null") {
            pradio.setOnClickListener {
                val intent = Intent(this, ConvertToScreen::class.java)
                intent.putExtra("orientation", true)
                intent.putExtra("SOCKET", true)
                saveOrientation("true")
                startActivity(intent)
                finish()
            }
            hradio.setOnClickListener {
                val intent = Intent(this, ConvertToScreen::class.java)
                intent.putExtra("orientation", false)
                intent.putExtra("SOCKET", true)
                saveOrientation("false")
                startActivity(intent)
                finish()
            }
        } else {
            val intent = Intent(this, ConvertToScreen::class.java)
            if (validateOrientation() == "true") {
                intent.putExtra("orientation", true)
            } else {
                intent.putExtra("orientation", false)
            }
            intent.putExtra("SOCKET", true)
            startActivity(intent)
            finish()
        }

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        checkLocationPermissionAndFetchLocation()

        checkForAppUpdate()
    }

    private fun saveOrientation(orientation: String) {
        val sharedPreferences = this.getSharedPreferences("Everidoor", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString("ORIENTATION", orientation)
        editor.apply()
    }

    private fun validateOrientation(): String {
        val sharedPreferences = this.getSharedPreferences("Everidoor", Context.MODE_PRIVATE)
        return when (sharedPreferences.getString("ORIENTATION", "null")) {
            "true" -> "true"
            "false" -> "false"
            else -> "null"
        }
    }

    private fun validateForOrientation(): Boolean? {
        val sharedPreferences = this.getSharedPreferences("Everidoor", Context.MODE_PRIVATE)
        val orientation = sharedPreferences?.getBoolean("ORIENTATION", false)
        val firsttime = sharedPreferences?.getString("FIRSTTIMESET", "null")

        return firsttime != "null" && orientation == true
    }

    private fun saveForOrientation(orientation: Boolean, firstTime: String) {
        val sharedPreferences = this.getSharedPreferences("Everidoor", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putBoolean("ORIENTATION", orientation)
        editor.putString("FIRSTTIMESET", firstTime)
        editor.apply()
    }

    private fun checkLocationPermissionAndFetchLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
        } else {
            getUserLocation()
        }
    }

    @SuppressLint("MissingPermission")
    private fun getUserLocation() {
        val task: Task<Location> = fusedLocationProviderClient.lastLocation
        task.addOnSuccessListener {
            if (it != null) {
                val latitude = it.latitude.toString()
                val longitude = it.longitude.toString()
                Log.d("Location", "Latitude: $latitude, Longitude: $longitude")
                postVolley(latitude, longitude)
            } else {
                useDefaultLocation()
            }
        }.addOnFailureListener {
            useDefaultLocation()
        }
    }

    private fun useDefaultLocation() {
        val defaultLatitude = "28.498540"
        val defaultLongitude = "77.088620"
        Log.d("Default Location", "Latitude: $defaultLatitude, Longitude: $defaultLongitude")
        postVolley(defaultLatitude, defaultLongitude)
    }

    private fun postVolley(lat: String, long: String) {
        val url = "http://ec2-65-0-74-168.ap-south-1.compute.amazonaws.com:4001/"
        val retrofitBuilder = Retrofit.Builder().addConverterFactory(GsonConverterFactory.create())
            .baseUrl(url).build()

        val api = retrofitBuilder.create(ApiInterface::class.java)
        val loc = LocationPost(lat, long)
        val call = api.postLocation(loc)
        call.enqueue(object : Callback<LocationPost> {
            override fun onResponse(call: Call<LocationPost>, response: Response<LocationPost>) {
                Timber.tag("api").d("POSTED")
            }

            override fun onFailure(call: Call<LocationPost>, t: Throwable) {
                Timber.tag("api").d("FAILED %s", t.message.toString())
            }
        })
    }

    private fun getVolley(lat: String, long: String) {
        val queue = Volley.newRequestQueue(this)
        val url = "https://vkvp4qqj-4000.inc1.devtunnels.ms/location_details?lat=$lat&long=$long"

        val stringReq = StringRequest(Request.Method.GET, url,
            { response ->
                val strResp = response.toString()
                Timber.tag("API").d(strResp)
            },
            { Timber.tag("API").d("that didn't work") })
        queue.add(stringReq)
    }

    private fun checkForAppUpdate() {
        val appUpdateInfoTask = appUpdateManager.appUpdateInfo

        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                try {
                    val installType = when {
                        appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) -> AppUpdateType.IMMEDIATE
                        else -> null
                    }
                    appUpdateManager.startUpdateFlowForResult(
                        appUpdateInfo,
                        installType!!,
                        this,
                        APP_UPDATE_REQUEST_CODE
                    )
                } catch (e: IntentSender.SendIntentException) {
                    e.printStackTrace()
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == APP_UPDATE_REQUEST_CODE) {
            if (resultCode != Activity.RESULT_OK) {
                Toast.makeText(this, "App Update failed, please try again on the next app launch.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun popupSnackbarForCompleteUpdate() {
        val snackbar = Snackbar.make(
            findViewById(R.id.linearLayout5),
            "An update has just been downloaded.",
            Snackbar.LENGTH_INDEFINITE
        )
        snackbar.setAction("RESTART") { appUpdateManager.completeUpdate() }
        snackbar.setActionTextColor(ContextCompat.getColor(this, android.R.color.holo_blue_bright))
        snackbar.show()
    }

    override fun onResume() {
        super.onResume()
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED) {
                popupSnackbarForCompleteUpdate()
            }
            try {
                if (appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                    appUpdateManager.startUpdateFlowForResult(
                        appUpdateInfo,
                        AppUpdateType.IMMEDIATE,
                        this,
                        APP_UPDATE_REQUEST_CODE
                    )
                }
            } catch (e: IntentSender.SendIntentException) {
                e.printStackTrace()
            }
        }
    }

    companion object {
        private const val APP_UPDATE_REQUEST_CODE = 1991
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
    }

   /* override fun onDestroy() {
        super.onDestroy()
        SocketHandler.closeConnection()
    }*/


}
