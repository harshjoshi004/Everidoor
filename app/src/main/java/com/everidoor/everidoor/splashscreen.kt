@file:Suppress("DEPRECATION")

package com.everidoor.everidoor

import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

class splashscreen : FragmentActivity() {

    private val REQUEST_OVERLAY_PERMISSION = 123
    private lateinit var imageView: ImageView

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splashscreen)


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

        imageView = findViewById(R.id.imageView)
        imageView.setImageResource(R.drawable.img)
        GlobalScope.launch(Dispatchers.Main) {
            checkRequirementsAndProceed()
        }

    }

    private suspend fun checkRequirementsAndProceed() {
        if (!isConnectedToInternet(this)) {
            playSplashAnimation()

            // Register a network callback to listen for changes in connectivity
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    // If the device connects to Wi-Fi, stop the delay and proceed
                    if (isConnectedToInternet(this@splashscreen)) {
                        connectivityManager.unregisterNetworkCallback(this)
                        imageView.clearAnimation()
                        imageView.visibility = View.VISIBLE
                        checkLocationAndOverlayPermission()
                    }
                }
            }
            connectivityManager.registerNetworkCallback(NetworkRequest.Builder().build(), networkCallback)

            delay(2 * 60 * 1000)

            // If the device is still not connected to the internet after 2 minutes, show the Wi-Fi settings
            if (!isConnectedToInternet(this)) {
                checkWifi()
                return
            }
        } else {
            imageView.clearAnimation()
            imageView.visibility = View.VISIBLE
            checkLocationAndOverlayPermission()
        }
    }
    private fun checkLocationAndOverlayPermission() {
        if (!isLocationEnabled(this)) {
            checkLocation()
            return
        }
        if (!isDisplayOverOtherAppsEnabled(this)) {
            requestOverlayPermission()
            return
        }
        nextActivity()
    }

    private fun playSplashAnimation() {
        if (imageView.animation != null && imageView.animation.hasStarted()) {
            return
        }
        imageView.alpha = 0f
        imageView.animate().setDuration(3000).alpha(1f).withEndAction {

        }
    }
    override fun onBackPressed() {
        super.onBackPressed()
        finishAffinity()
        exitProcess(0)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onRestart() {
        super.onRestart()
        if (!isConnectedToInternet(this)) {
            checkWifi()
        }
        if (!isLocationEnabled(this)) {
            checkLocation()
        }
        if (isConnectedToInternet(this) && isLocationEnabled(this) && isDisplayOverOtherAppsEnabled(
                this
            )
        ) {
// Next Activity
            nextActivity()
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun isConnectedToInternet(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val networkCapabilities =
            connectivityManager.getNetworkCapabilities(network) ?: return false
        return networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }


    private fun isLocationEnabled(context: Context): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER
        )
    }

    private fun isDisplayOverOtherAppsEnabled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(context)
        } else {
            // On older Android versions, overlay permissions are not required
            return true
        }
    }


    private fun nextActivity() {
        Toast.makeText(this, "Online", Toast.LENGTH_LONG).show()
        Toast.makeText(this, "Location and Google Location Accuracy are Enabled", Toast.LENGTH_LONG)
            .show()
        val imageView = findViewById<ImageView>(R.id.imageView)
        imageView.setImageResource(R.drawable.img)
        imageView.alpha = 0f
        imageView.animate().setDuration(3000).alpha(1f).withEndAction {
            val i = Intent(this, MainActivity::class.java)
            startActivity(i)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    private fun checkWifi() {
        Toast.makeText(
            this,
            "Offline, Please Connect Wifi or Enable Mobile data!",
            Toast.LENGTH_LONG
        ).show()
        startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
    }

    private fun checkLocation() {
        Toast.makeText(
            this,
            "Location and Google Location Accuracy are not Enabled, Please Enable them!",
            Toast.LENGTH_LONG
        ).show()
        startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION)
        Toast.makeText(
            this,
            "Display Over Other Apps are not Enabled, Please Enable it!",
            Toast.LENGTH_LONG
        ).show()
    }

}
