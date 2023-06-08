package com.example.barkers

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import com.beust.klaxon.Klaxon
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var webSocket: WebSocket
    private lateinit var username: String
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    override fun onStart() {
        super.onStart()
        val input = EditText(this);
        AlertDialog.Builder(this)
            .setTitle("Barkers")
            .setView(input)
            .setMessage("Masukkan username untuk join!")
            .setCancelable(false)
            .setNegativeButton("Keluar", { dialog, which -> android.os.Process.killProcess(android.os.Process.myPid())})
            .setPositiveButton("Join") { dialog, which ->
                run {
                    username = input.text.toString()
                    webSocket = ChatWebSocket(this, username).run()
                }
            }
            .show()
    }
    fun onSend(v: View){
        try {
            val input = findViewById<EditText>(R.id.main_input)
            if(input.length() < 1) return
            val msg = Klaxon().toJsonString(object {
                val type = "message"
                val name = username
                val chat = input.text.toString()
            })
            webSocket.send(msg)

            val layout = findViewById<LinearLayout>(R.id.main_content)
            val card = LayoutInflater.from(this).inflate(R.layout.main_me, layout, false)

            card.findViewById<TextView>(R.id.main_me_username).text = username
            card.findViewById<TextView>(R.id.main_me_chat).text = input.text.toString()
            layout.addView(card)
            input.text.clear()
        } catch (e: Exception){
            e.printStackTrace()
        }
    }
}
class ChatWebSocket(val activity: MainActivity, val username: String) : WebSocketListener(){
    private lateinit var pb: ProgressBar
    private var connected = false
    fun run(): WebSocket {
        pb = activity.findViewById(R.id.main_loading)
        pb.visibility = View.VISIBLE

        val client = OkHttpClient.Builder()
            .readTimeout(3, TimeUnit.SECONDS).build()
        val request = Request.Builder()
            .url("wss://ancritbat.my.id:8443").build()
        val webSocket = client.newWebSocket(request, this);
        client.dispatcher.executorService.shutdown();
        return webSocket
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        Log.d("Websocket", "Connected!")
        val msg = Klaxon().toJsonString(object {
            val type = "connect"
            val username = this@ChatWebSocket.username
        })
        webSocket.send(msg)
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        val data = JSONObject(text)
        when(data.getString("type")){
            "connect" -> onConnect(this@ChatWebSocket.activity, data.getJSONArray("data"))
            "join" -> onJoin(this@ChatWebSocket.activity, data)
            "message" -> onMessaged(this@ChatWebSocket.activity, data)
        }
    }
    private fun onMessaged(activity: MainActivity, data: JSONObject){
        val layout = activity.findViewById<LinearLayout>(R.id.main_content)
        val card = LayoutInflater.from(activity).inflate(R.layout.main_you, layout, false)

        card.findViewById<TextView>(R.id.main_you_username).text = data.getString("name")
        card.findViewById<TextView>(R.id.main_you_chat).text = data.getString("chat")
        activity.runOnUiThread { layout.addView(card) }
    }
    private fun onJoin(activity: MainActivity, data: JSONObject){
        val layout = activity.findViewById<LinearLayout>(R.id.main_content)
        val card = LayoutInflater.from(activity).inflate(R.layout.main_join, layout, false)
        card.findViewById<TextView>(R.id.main_join_text).text = data.getString("username") + " joined the chat"
        activity.runOnUiThread { layout.addView(card) }
    }
    private fun onConnect(activity: MainActivity, data: JSONArray){
        try {
            val layout = activity.findViewById<LinearLayout>(R.id.main_content)
            for(i in 0 until data.length()){
                val dat = data.getJSONObject(i)
                val fromMe = dat.getString("name").equals(this@ChatWebSocket.username)
                val card: CardView = LayoutInflater.from(activity).inflate(
                    if(fromMe) R.layout.main_me else R.layout.main_you,
                    layout, false) as CardView

                card.findViewById<TextView>(if(fromMe) R.id.main_me_username else R.id.main_you_username).text =
                    dat.getString("name")
                card.findViewById<TextView>(if(fromMe) R.id.main_me_chat else R.id.main_you_chat).text =
                    dat.getString("chat")
                activity.runOnUiThread { layout.addView(card) }
            }
            fun enable(){
                pb.visibility = View.GONE
                activity.findViewById<EditText>(R.id.main_input).isFocusableInTouchMode = true
            }
            activity.runOnUiThread { enable() }
        } catch (e: Exception){
            e.printStackTrace()
        }
    }
}