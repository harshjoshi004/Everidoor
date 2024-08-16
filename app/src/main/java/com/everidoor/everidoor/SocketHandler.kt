import android.content.Context
import android.content.SharedPreferences
import io.socket.client.IO
import io.socket.client.Socket
import java.net.URISyntaxException
import android.util.Log
import io.socket.emitter.Emitter

import org.json.JSONObject

object SocketHandler {
    private const val SERVER_URL = "https://vkvp4qqj-4000.inc1.devtunnels.ms/"
    private lateinit var msocket: Socket
    private var isConnected = false
   /* private lateinit var sharedPreferences: SharedPreferences*/

    @Synchronized
    fun setSocket(/*context: Context*/) {
        try {
            if(!::msocket.isInitialized){
                val options = IO.Options()
                options.forceNew = true
                msocket = IO.socket(SERVER_URL, options)
                msocket.on(Socket.EVENT_CONNECT, onConnect)
            }

        } catch (e: URISyntaxException) {
            Log.e("SocketHandler", "Error creating socket: $e")
        }
    }

    @Synchronized
    fun establishConnection() {
        if(!isConnected){
            msocket.connect()
            Log.e("SocketHandler", "Connection Established")
            isConnected = true
        }
        else{
            Log.e("SocketHandler","Connection already established")
        }

    }

    @Synchronized
    fun closeConnection() {
        if(isConnected){
            msocket.disconnect()
            Log.e("SocketHandler", "Connection DISCONNECT")
            isConnected = false
        }
        else{
            Log.e("SocketHandler","Connection already disconnected")
        }

    }

    @Synchronized
    fun getSocket(): Socket {
        return msocket
    }

    private val onConnect = Emitter.Listener {
        Log.d("SocketHandler", "Socket connected with ID: ${msocket.id()}")
    }
    /*private fun screeninactive(){
        val data = JSONObject()
        data.put("screenId",getscreenId())
        data.put("status","inactive")
        msocket.emit("screen-inactive",data)
    }*/

  /*  private fun getscreenId(): String {
        return sharedPreferences.getString("ACTIVATION_KEY","default_screen_id")?:"default-screen_id"
    }*/

}
