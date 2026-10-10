package com.motionforge.editor

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Transformer
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@UnstableApi
class MainActivity : AppCompatActivity() {
    private val bg = Color.rgb(11, 13, 20)
    private val panel = Color.rgb(22, 25, 37)
    private val purple = Color.rgb(139, 92, 246)
    private val white = Color.rgb(245, 247, 255)
    private val muted = Color.rgb(151, 158, 180)
    private var videoUri: Uri? = null
    private var videoName = "Belum ada media"
    private var durationMs = 0L
    private var trimStartMs = 0L
    private var trimEndMs = 0L
    private var projectName = "Proyek Tanpa Judul"
    private val layers = mutableListOf<String>()
    private lateinit var preview: VideoView
    private lateinit var timeline: LinearLayout
    private lateinit var status: TextView
    private lateinit var startSeek: SeekBar
    private lateinit var endSeek: SeekBar
    private lateinit var durationLabel: TextView

    private val pickVideo = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            videoUri = uri
            videoName = queryName(uri)
            preview.setVideoURI(uri)
            preview.setOnPreparedListener { mp ->
                durationMs = mp.duration.toLong().coerceAtLeast(1L)
                trimStartMs = 0L
                trimEndMs = durationMs
                startSeek.max = durationMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                endSeek.max = startSeek.max
                startSeek.progress = 0
                endSeek.progress = endSeek.max
                durationLabel.text = "Durasi ${time(durationMs)} • Rentang ${time(trimEndMs - trimStartMs)}"
                mp.isLooping = true
                mp.start()
                updateStatus("Video siap diedit")
            }
            updateStatus("Memuat $videoName")
            rebuildTimeline()
        }
    }

    private val pickAudio = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            layers.add("Audio • ${queryName(uri)}")
            rebuildTimeline()
            updateStatus("Layer audio ditambahkan (penempatan audio lanjutan segera tersedia)")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg); setPadding(dp(16), dp(10), dp(16), dp(12)) }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; orientation = LinearLayout.HORIZONTAL }
        val logo = TextView(this).apply { text = "M"; textSize = 22f; setTextColor(white); gravity = Gravity.CENTER; setBackgroundColor(purple); width = dp(42); height = dp(42) }
        top.addView(logo)
        val titleCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), 0, 0, 0) }
        titleCol.addView(text("MOTIONFORGE", 17f, white, true))
        titleCol.addView(text("MOTION STUDIO  •  v1.0", 10f, muted))
        top.addView(titleCol, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(button("Profil", panel) { showProfile() })
        top.addView(button("Simpan", panel) { saveProject() }.apply { setPadding(dp(8), 0, dp(8), 0) })
        root.addView(top)
        val projectRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(14), 0, dp(10)) }
        projectRow.addView(text(projectName, 15f, white, true), LinearLayout.LayoutParams(0, -2, 1f))
        projectRow.addView(button("⋯", panel) { showProjectMenu() })
        root.addView(projectRow)

        val previewCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setBackgroundColor(Color.rgb(4, 5, 9))
            background = rounded(Color.rgb(4,5,9), 18)
        }
        preview = VideoView(this).apply { setBackgroundColor(Color.rgb(4,5,9)) }
        previewCard.addView(preview, LinearLayout.LayoutParams(-1, 0, 1f))
        val placeholder = text("Pilih video untuk memulai\nPreview akan tampil di sini", 14f, muted)
        // VideoView remains visible; hint is shown in the status below until media is selected.
        root.addView(previewCard, LinearLayout.LayoutParams(-1, 0, 1.1f).apply { bottomMargin = dp(12) })

        val tools = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, dp(2), 0, dp(12)) }
        tools.addView(button("＋ Video", purple) { pickVideo.launch(arrayOf("video/*")) }, weightParams())
        tools.addView(button("Teks", panel) { addTextLayer() }, weightParams())
        tools.addView(button("♪ Audio", panel) { pickAudio.launch(arrayOf("audio/*")) }, weightParams())
        tools.addView(button("Efek", panel) { showEffects() }, weightParams())
        root.addView(tools)

        val timelineHeader = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; orientation = LinearLayout.HORIZONTAL }
        timelineHeader.addView(text("TIMELINE", 12f, white, true), LinearLayout.LayoutParams(0, -2, 1f))
        timelineHeader.addView(text("00:00:00", 11f, muted))
        root.addView(timelineHeader)
        timeline = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(8), dp(8), dp(8)); background = rounded(panel, 14) }
        root.addView(timeline, LinearLayout.LayoutParams(-1, dp(110)).apply { topMargin = dp(8); bottomMargin = dp(10) })

        val trimTitle = text("POTONG VIDEO", 11f, muted, true)
        root.addView(trimTitle)
        startSeek = SeekBar(this).apply { progressTintList = android.content.res.ColorStateList.valueOf(purple); thumbTintList = android.content.res.ColorStateList.valueOf(white); max = 1000 }
        endSeek = SeekBar(this).apply { progressTintList = android.content.res.ColorStateList.valueOf(purple); thumbTintList = android.content.res.ColorStateList.valueOf(white); max = 1000; progress = 1000 }
        root.addView(startSeek)
        root.addView(endSeek)
        durationLabel = text("Pilih video untuk mengatur rentang potong", 11f, muted)
        root.addView(durationLabel)
        startSeek.setOnSeekBarChangeListener(seekListener(true))
        endSeek.setOnSeekBarChangeListener(seekListener(false))

        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, dp(12), 0, 0) }
        bottom.addView(button("▶ Preview", panel) { if (preview.isPlaying) preview.pause() else preview.start() }, weightParams())
        bottom.addView(button("Ekspor video", purple) { exportVideo() }, LinearLayout.LayoutParams(0, dp(48), 1.5f).apply { leftMargin = dp(8) })
        root.addView(bottom)
        status = text("Siap • Impor video untuk mulai", 11f, muted)
        status.gravity = Gravity.CENTER
        root.addView(status, LinearLayout.LayoutParams(-1, dp(28)))
        setContentView(root)
        rebuildTimeline()
    }

    private fun seekListener(isStart: Boolean) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seek: SeekBar?, progress: Int, fromUser: Boolean) {
            if (!fromUser || durationMs <= 0) return
            if (isStart) trimStartMs = progress.toLong().coerceAtMost((trimEndMs - 500).coerceAtLeast(0))
            else trimEndMs = progress.toLong().coerceAtLeast(trimStartMs + 500).coerceAtMost(durationMs)
            durationLabel.text = "Mulai ${time(trimStartMs)}  •  Akhir ${time(trimEndMs)}  •  Durasi ${time(trimEndMs-trimStartMs)}"
        }
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    }

    private fun addTextLayer() {
        val input = EditText(this).apply { hint = "Tulis teks layer"; setTextColor(white); setHintTextColor(muted) }
        android.app.AlertDialog.Builder(this).setTitle("Tambah layer teks").setView(input)
            .setPositiveButton("Tambah") { _, _ ->
                val value = input.text.toString().trim()
                if (value.isNotEmpty()) { layers.add("Teks • $value"); rebuildTimeline(); updateStatus("Layer teks ditambahkan") }
            }.setNegativeButton("Batal", null).show()
    }

    private fun showEffects() {
        val effects = arrayOf("Kecerahan", "Kontras", "Saturasi", "Blur", "Glow", "Bayangan", "Kecepatan")
        android.app.AlertDialog.Builder(this).setTitle("Efek & penyesuaian")
            .setItems(effects) { _, which ->
                layers.add("Efek • ${effects[which]}")
                rebuildTimeline()
                updateStatus("Preset ${effects[which]} masuk daftar layer. Rendering efek lanjutan perlu diaktifkan pada tahap berikutnya.")
            }.show()
    }

    private fun rebuildTimeline() {
        if (!::timeline.isInitialized) return
        timeline.removeAllViews()
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(text("00:00", 10f, muted), LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(text("00:05", 10f, muted), LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(text("00:10", 10f, muted), LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(text("00:15", 10f, muted), LinearLayout.LayoutParams(0, -2, 1f))
        timeline.addView(header)
        if (videoUri != null) addLayerRow("VIDEO", videoName, purple)
        layers.forEachIndexed { i, layer -> addLayerRow("L${i+1}", layer, if (layer.startsWith("Teks")) Color.rgb(32, 160, 180) else Color.rgb(70, 100, 160)) }
        if (videoUri == null && layers.isEmpty()) timeline.addView(text("Belum ada layer — tekan + Video atau Teks", 12f, muted).apply { gravity = Gravity.CENTER; setPadding(0, dp(18), 0, 0) })
    }

    private fun addLayerRow(tag: String, name: String, color: Int) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(4), 0, dp(4)) }
        row.addView(text(tag, 10f, muted, true), LinearLayout.LayoutParams(dp(34), -2))
        val clip = TextView(this).apply {
            text = name.take(22); textSize = 11f; setTextColor(white); gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, dp(8), 0); background = rounded(color, 7)
        }
        row.addView(clip, LinearLayout.LayoutParams(0, dp(27), 1f))
        row.addView(button("×", panel) {
            if (tag == "VIDEO") { videoUri = null; videoName = "Belum ada media"; preview.stopPlayback() }
            else { val index = tag.removePrefix("L").toIntOrNull()?.minus(1); if (index != null && index in layers.indices) layers.removeAt(index) }
            rebuildTimeline()
        })
        timeline.addView(row)
    }

    private fun exportVideo() {
        val uri = videoUri
        if (uri == null) { updateStatus("Impor video terlebih dahulu"); return }
        if (trimEndMs <= trimStartMs) { updateStatus("Rentang potong tidak valid"); return }
        val output = File(getExternalFilesDir(null), "MotionForge_${System.currentTimeMillis()}.mp4")
        updateStatus("Mengekspor video…")
        try {
            val clipping = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(trimStartMs)
                .setEndPositionMs(trimEndMs)
                .build()
            val mediaItem = MediaItem.Builder().setUri(uri).setClippingConfiguration(clipping).build()
            val edited = EditedMediaItem.Builder(mediaItem).build()
            val transformer = Transformer.Builder(this)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: androidx.media3.transformer.Composition, exportResult: androidx.media3.transformer.ExportResult) {
                        runOnUiThread {
                            updateStatus("Ekspor selesai: ${output.name}")
                            android.app.AlertDialog.Builder(this@MainActivity)
                                .setTitle("Ekspor berhasil")
                                .setMessage("Video tersimpan di:\n${output.absolutePath}\n\nBuka pengelola file untuk menyalinnya ke Galeri.")
                                .setPositiveButton("OK", null).show()
                        }
                    }
                    override fun onError(composition: androidx.media3.transformer.Composition, exportResult: androidx.media3.transformer.ExportResult, exportException: androidx.media3.transformer.ExportException) {
                        runOnUiThread { updateStatus("Ekspor gagal: ${exportException.message ?: "kesalahan media"}") }
                    }
                }).build()
            transformer.start(edited, output.absolutePath)
        } catch (e: Exception) { updateStatus("Gagal mengekspor: ${e.message}") }
    }

    private fun saveProject() {
        try {
            val json = JSONObject()
            json.put("app", "MotionForge")
            json.put("name", projectName)
            json.put("videoUri", videoUri?.toString() ?: "")
            json.put("videoName", videoName)
            json.put("trimStartMs", trimStartMs)
            json.put("trimEndMs", trimEndMs)
            val array = JSONArray()
            layers.forEach { array.put(it) }
            json.put("layers", array)
            val folder = File(getExternalFilesDir(null), "projects").apply { mkdirs() }
            val file = File(folder, "motionforge_project.json")
            file.writeText(json.toString(2))
            updateStatus("Proyek disimpan: ${file.name}")
            android.app.AlertDialog.Builder(this).setTitle("Proyek tersimpan").setMessage(file.absolutePath).setPositiveButton("OK", null).show()
        } catch (e: Exception) { updateStatus("Gagal menyimpan proyek: ${e.message}") }
    }

    private fun showProjectMenu() {
        val options = arrayOf("Ganti nama proyek", "Simpan proyek", "Profil & akun", "Pengaturan", "Tentang MotionForge")
        android.app.AlertDialog.Builder(this).setTitle("Menu proyek").setItems(options) { _, which ->
            when (which) {
                0 -> {
                    val input = EditText(this).apply { setText(projectName); setTextColor(white); setHintTextColor(muted); hint = "Nama proyek" }
                    android.app.AlertDialog.Builder(this).setTitle("Nama proyek").setView(input)
                        .setPositiveButton("Simpan") { _, _ -> projectName = input.text.toString().ifBlank { "Proyek Tanpa Judul" }; saveProject() }
                        .setNegativeButton("Batal", null).show()
                }
                1 -> saveProject()
                2 -> showProfile()
                3 -> showSettings()
                4 -> android.app.AlertDialog.Builder(this).setTitle("MotionForge Studio")
                    .setMessage("Editor video Android dengan akun opsional. Impor, preview, potong video, simpan proyek, dan ekspor potongan MP4 tersedia.\n\nCatatan: teks/audio/efek belum dikomposisikan ke ekspor; keyframe dan compositing lanjutan belum tersedia.")
                    .setPositiveButton("OK", null).show()
            }
        }.setNegativeButton("Tutup", null).show()
    }

    private fun showProfile() {
        val prefs = getSharedPreferences("motionforge_profile", MODE_PRIVATE)
        val savedName = prefs.getString("display_name", "") ?: ""
        val name = EditText(this).apply {
            hint = "Nama tampilan (opsional)"; setText(savedName); setTextColor(white); setHintTextColor(muted)
        }
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        val avatar = TextView(this).apply {
            text = if (savedName.isBlank()) "👤" else savedName.take(1).uppercase(Locale.getDefault())
            textSize = 28f; gravity = Gravity.CENTER; setTextColor(white); background = rounded(purple, 48)
        }
        info.addView(avatar, LinearLayout.LayoutParams(dp(68), dp(68)).apply { gravity = Gravity.CENTER; bottomMargin = dp(12) })
        info.addView(text("Profil lokal • Login tidak wajib", 13f, muted).apply { gravity = Gravity.CENTER })
        info.addView(name, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(10) })
        android.app.AlertDialog.Builder(this).setTitle("Profil saya").setView(info)
            .setPositiveButton("Simpan profil") { _, _ ->
                prefs.edit().putString("display_name", name.text.toString().trim()).apply()
                updateStatus("Profil lokal disimpan")
            }
            .setNeutralButton("Login / Buat akun") { _, _ -> showAccountInfo() }
            .setNegativeButton("Tutup", null).show()
    }

    private fun showAccountInfo() {
        android.app.AlertDialog.Builder(this).setTitle("Akun MotionForge")
            .setMessage("Kamu tetap bisa mengedit dan mengekspor tanpa login. Login dan pembuatan akun online belum aktif karena aplikasi ini belum terhubung ke server autentikasi. Profil lokal tetap bisa digunakan di perangkat ini.")
            .setPositiveButton("Mengerti", null).show()
    }

    private fun showSettings() {
        val options = arrayOf("Tema gelap (aktif)", "Informasi ekspor", "Lokasi penyimpanan")
        android.app.AlertDialog.Builder(this).setTitle("Pengaturan").setItems(options) { _, which ->
            when (which) {
                0 -> android.app.AlertDialog.Builder(this).setMessage("Tema gelap digunakan sebagai tema utama MotionForge.").setPositiveButton("OK", null).show()
                1 -> android.app.AlertDialog.Builder(this).setTitle("Ekspor video")
                    .setMessage("Ekspor saat ini menghasilkan MP4 dari rentang video yang dipilih. Pengaturan resolusi/FPS dan komposisi layer masih perlu ditambahkan dan diuji.")
                    .setPositiveButton("OK", null).show()
                2 -> android.app.AlertDialog.Builder(this).setTitle("Penyimpanan")
                    .setMessage("File ekspor tersimpan di folder privat aplikasi Android/data/com.motionforge.editor/files. Akses dapat dibatasi Android.")
                    .setPositiveButton("OK", null).show()
            }
        }.setNegativeButton("Tutup", null).show()
    }

    private fun queryName(uri: Uri): String {
        var name = uri.lastPathSegment ?: "media"
        try {
            contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) name = it.getString(0) ?: name
            }
        } catch (_: Exception) {}
        return name
    }

    private fun time(ms: Long): String {
        val seconds = (ms / 1000).coerceAtLeast(0)
        return String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60)
    }

    private fun updateStatus(message: String) { if (::status.isInitialized) status.text = message }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }
    private fun rounded(color: Int, radius: Int) = android.graphics.drawable.GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }
    private fun button(label: String, color: Int, action: () -> Unit) = TextView(this).apply {
        text = label; textSize = 12f; setTextColor(white); gravity = Gravity.CENTER
        setPadding(dp(10), 0, dp(10), 0); background = rounded(color, 11)
        minHeight = dp(38); setOnClickListener { action() }
    }
    private fun weightParams() = LinearLayout.LayoutParams(0, dp(42), 1f).apply { rightMargin = dp(6) }
}
