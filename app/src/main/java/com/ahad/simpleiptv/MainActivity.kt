package com.ahad.simpleiptv

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {

    data class Channel(val name: String, val url: String)

    private val channels = mutableListOf<Channel>()
    private lateinit var adapter: ArrayAdapter<Channel>
    private lateinit var listView: ListView
    private lateinit var playerView: PlayerView
    private var player: ExoPlayer? = null

    private val prefs by lazy { getSharedPreferences("iptv", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        loadChannels()
        buildUi()

        player = ExoPlayer.Builder(this).build().also { p ->
            playerView.player = p
            p.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    Toast.makeText(
                        this@MainActivity,
                        "Can't play this channel: ${error.errorCodeName}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            })
        }

        if (channels.isNotEmpty()) {
            val last = prefs.getInt("last", 0).coerceIn(0, channels.size - 1)
            listView.setSelection(last)
            play(last)
        }
    }

    // ---------- UI ----------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#12171F"))
        }

        val left = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        root.addView(left, LinearLayout.LayoutParams(dp(320), ViewGroup.LayoutParams.MATCH_PARENT))

        left.addView(TextView(this).apply {
            text = "Channels"
            setTextColor(Color.WHITE)
            textSize = 22f
            setPadding(dp(4), 0, 0, dp(8))
        })

        adapter = object : ArrayAdapter<Channel>(this, android.R.layout.simple_list_item_1, channels) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent) as TextView
                v.text = getItem(position)?.name
                v.setTextColor(Color.WHITE)
                v.textSize = 18f
                return v
            }
        }
        listView = ListView(this).apply {
            adapter = this@MainActivity.adapter
            selector = ColorDrawable(Color.parseColor("#553B82F6"))
            divider = null
            isFocusable = true
            setOnItemClickListener { _, _, pos, _ -> play(pos) }
            setOnItemLongClickListener { _, _, pos, _ ->
                confirmDelete(pos)
                true
            }
        }
        left.addView(listView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        left.addView(Button(this).apply {
            text = "+ Add channel"
            setOnClickListener { showAddChannel() }
        })
        left.addView(Button(this).apply {
            text = "+ Import m3u playlist"
            setOnClickListener { showImportPlaylist() }
        })
        left.addView(TextView(this).apply {
            text = "Hold OK on a channel to delete it"
            setTextColor(Color.parseColor("#8A94A6"))
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, 0)
        })

        playerView = PlayerView(this).apply {
            setBackgroundColor(Color.BLACK)
            // Keep the controller hidden until the user presses a button.
            controllerAutoShow = false
        }
        root.addView(playerView, LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))

        setContentView(root)
        listView.requestFocus()
    }

    private fun showAddChannel() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(10), dp(20), 0)
        }
        val nameInput = EditText(this).apply {
            hint = "Channel name"
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val urlInput = EditText(this).apply {
            hint = "Stream URL (.m3u8)"
            inputType = InputType.TYPE_TEXT_VARIATION_URI
        }
        box.addView(nameInput)
        box.addView(urlInput)

        AlertDialog.Builder(this)
            .setTitle("Add channel")
            .setView(box)
            .setPositiveButton("Add") { _, _ ->
                val name = nameInput.text.toString().trim()
                val url = urlInput.text.toString().trim()
                if (url.startsWith("http")) {
                    addChannels(listOf(Channel(name.ifEmpty { url }, url)))
                } else {
                    Toast.makeText(this, "URL must start with http", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showImportPlaylist() {
        val urlInput = EditText(this).apply {
            hint = "Playlist URL (.m3u)"
            inputType = InputType.TYPE_TEXT_VARIATION_URI
        }
        val box = LinearLayout(this).apply {
            setPadding(dp(20), dp(10), dp(20), 0)
            addView(urlInput, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        AlertDialog.Builder(this)
            .setTitle("Import playlist")
            .setView(box)
            .setPositiveButton("Import") { _, _ ->
                val url = urlInput.text.toString().trim()
                if (url.startsWith("http")) importPlaylist(url)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDelete(pos: Int) {
        val ch = channels.getOrNull(pos) ?: return
        AlertDialog.Builder(this)
            .setTitle("Delete \"${ch.name}\"?")
            .setPositiveButton("Delete") { _, _ ->
                channels.removeAt(pos)
                saveChannels()
                adapter.notifyDataSetChanged()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------- Playback ----------

    private fun play(index: Int) {
        val ch = channels.getOrNull(index) ?: return
        prefs.edit().putInt("last", index).apply()
        val item = MediaItem.Builder()
            .setUri(ch.url)
            .apply { if (ch.url.contains("m3u", ignoreCase = true)) setMimeType(MimeTypes.APPLICATION_M3U8) }
            .build()
        player?.apply {
            setMediaItem(item)
            prepare()
            playWhenReady = true
        }
        Toast.makeText(this, ch.name, Toast.LENGTH_SHORT).show()
    }

    override fun onStart() {
        super.onStart()
        player?.play()
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }

    // ---------- Storage ----------

    private fun defaultChannels() = listOf(
        Channel("Zee Bangla HD (YuppTV)",
            "https://raw.githubusercontent.com/amazeyourself/adaptive-streams/refs/heads/main/streams/in/YuppTV/ZeeBanglaHD.m3u8"),
        Channel("Zee Bangla HD (Ayna)",
            "https://tvsen6.aynaott.com/ZeeBangla/index.m3u8")
    )

    private fun loadChannels() {
        channels.clear()
        val saved = prefs.getString("channels", null)
        if (saved == null) {
            channels.addAll(defaultChannels())
            saveChannels()
            return
        }
        saved.lines().forEach { line ->
            val parts = line.split("\t")
            if (parts.size == 2 && parts[1].isNotBlank()) channels.add(Channel(parts[0], parts[1]))
        }
    }

    private fun saveChannels() {
        val text = channels.joinToString("\n") {
            it.name.replace("\t", " ").replace("\n", " ") + "\t" + it.url
        }
        prefs.edit().putString("channels", text).apply()
    }

    private fun addChannels(list: List<Channel>) {
        channels.addAll(list)
        saveChannels()
        adapter.notifyDataSetChanged()
        Toast.makeText(this, "Added ${list.size}", Toast.LENGTH_SHORT).show()
    }

    // ---------- Playlist import ----------

    private fun importPlaylist(url: String) {
        Toast.makeText(this, "Importing…", Toast.LENGTH_SHORT).show()
        thread {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val parsed = parseM3u(body)
                runOnUiThread {
                    if (parsed.isEmpty()) {
                        Toast.makeText(this, "No channels found in that playlist", Toast.LENGTH_LONG).show()
                    } else {
                        addChannels(parsed)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun parseM3u(text: String): List<Channel> {
        val out = mutableListOf<Channel>()
        var pendingName: String? = null
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.startsWith("#EXTINF")) {
                pendingName = line.substringAfterLast(",", "").trim()
            } else if (line.startsWith("http")) {
                out.add(Channel(pendingName?.ifEmpty { null } ?: line, line))
                pendingName = null
            }
        }
        return out
    }
}
