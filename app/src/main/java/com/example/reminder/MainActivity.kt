package com.example.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private var hour = -1
    private var minute = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val editMessage = findViewById<EditText>(R.id.editMessage)
        val btnPickTime = findViewById<Button>(R.id.btnPickTime)
        val txtTime = findViewById<TextView>(R.id.txtTime)
        val btnSet = findViewById<Button>(R.id.btnSet)
        val btnTest = findViewById<Button>(R.id.btnTest)
        val txtStatus = findViewById<TextView>(R.id.txtStatus)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }

        btnPickTime.setOnClickListener {
            val now = Calendar.getInstance()
            TimePickerDialog(this, { _, h, m ->
                hour = h; minute = m
                txtTime.text = String.format(Locale.getDefault(), "%02d:%02d", h, m)
            }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true).show()
        }

        btnSet.setOnClickListener {
            val msg = editMessage.text.toString().ifBlank { "อย่าลืมทำสิ่งที่ตั้งใจไว้" }
            if (hour < 0) {
                Toast.makeText(this, "กรุณาเลือกเวลา", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                return@setOnClickListener
            }
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
            }
            val intent = Intent(this, ReminderReceiver::class.java).apply {
                putExtra("message", msg)
            }
            val pi = PendingIntent.getBroadcast(this, 100, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
            val fmt = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
            txtStatus.text = "ตั้งเตือนไว้ ${fmt.format(cal.time)}\nข้อความ: $msg"
            Toast.makeText(this, "ตั้งเตือนเรียบร้อย", Toast.LENGTH_SHORT).show()
        }

        btnTest.setOnClickListener {
            val msg = editMessage.text.toString().ifBlank { "อย่าลืมทำสิ่งที่ตั้งใจไว้" }
            val intent = Intent(this, ReminderReceiver::class.java).apply {
                putExtra("message", msg)
            }
            sendBroadcast(intent)
        }
    }
}
