import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat

class TimerService : Service() {
    private val serviceChannelId = "animal_timer_service"
    private val remindChannelId = "animal_remind"
    private val foregroundNotifyId = 1001

    private lateinit var wakeLock: PowerManager.WakeLock
    private var tickThread: Thread? = null
    private var isRunning = false

    override fun onCreate() {
        super.onCreate()
        DataManager.init(this)
        createNotificationChannels()

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AnimalTimer:WakeLock")
        wakeLock.setReferenceCounted(false)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(foregroundNotifyId, buildServiceNotification("动物计时运行中"))
        if (!isRunning) {
            isRunning = true
            wakeLock.acquire()
            startTickLoop()
        }
        return START_STICKY
    }

    private fun startTickLoop() {
        tickThread = Thread {
            while (isRunning) {
                try {
                    tick()
                    Thread.sleep(1000)
                } catch (e: InterruptedException) {
                    break
                }
            }
        }
        tickThread?.start()
    }

    private fun tick() {
        val now = System.currentTimeMillis()
        val rows = DataManager.loadRows()
        var changed = false

        rows.forEach { row ->
            val type = AnimalConfig.动物配置[row.动物] ?: return@forEach
            var stage = row.阶段
            var stageStart = row.阶段开始
            var reproduceStart = row.繁殖开始
            var sellStart = row.售出开始

            while (true) {
                val dur = AnimalConfig.getNextStageDuration(type, stage) ?: break
                val end = stageStart + dur * 3600_000L
                if (now >= end) {
                    val stageList = AnimalConfig.getStageList(row.动物)
                    val idx = stageList.indexOf(stage)
                    if (idx >= 0 && idx < stageList.size - 1) {
                        stage = stageList[idx + 1]
                        stageStart = end
                        if (stage == "繁殖期") reproduceStart = stageStart
                        if (stage == "老年期") sellStart = stageStart
                    } else break
                } else break
            }

            if (stage != row.阶段) {
                row.阶段 = stage
                row.阶段开始 = stageStart
                row.繁殖开始 = reproduceStart
                row.售出开始 = sellStart
                row.繁殖已通知次数 = if (stage == "繁殖期") 0 else row.繁殖已通知次数
                row.售出已通知 = false
                changed = true
            } else if (stage == "繁殖期" && row.繁殖开始 != null && type is AnimalType) {
                val totalBreeds = type.繁殖次数
                val breedInterval = type.繁殖冷却 * 3600_000L
                val currentCount = row.繁殖已通知次数
                var newCount = currentCount

                for (i in currentCount until totalBreeds) {
                    val breedTime = row.繁殖开始!! + i * breedInterval
                    if (now >= breedTime) {
                        newCount++
                        sendRemind("繁殖倒计时", "编号${row.编号}的${row.动物}已到第${i + 1}次繁殖时间！")
                    } else break
                }

                if (newCount > currentCount) {
                    row.繁殖已通知次数 = newCount
                    changed = true
                }
            } else if (stage == "老年期" && row.售出开始 != null && type is AnimalType) {
                val end = row.售出开始!! + type.售出时长 * 3600_000L
                if (now >= end && !row.售出已通知) {
                    row.售出已通知 = true
                    sendRemind("售出倒计时", "编号${row.编号}的${row.动物}已到售出时间！")
                    changed = true
                }
            } else if (type is FlowerType) {
                val dur = AnimalConfig.getNextStageDuration(type, stage)
                if (dur != null) {
                    val end = stageStart + dur * 3600_000L
                    if (now >= end && !row.已通知) {
                        row.已通知 = true
                        sendRemind("种花提醒", "编号${row.编号}的${row.动物}${row.阶段}时间到！")
                        changed = true
                    }
                }
            }
        }

        if (changed) {
            DataManager.saveRows(rows)
            sendBroadcast(Intent("com.example.animal.timer.UPDATE_UI"))
        }
    }

    private fun buildServiceNotification(content: String): Notification {
        return NotificationCompat.Builder(this, serviceChannelId)
            .setContentTitle("动物计时器")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun sendRemind(title: String, content: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, remindChannelId)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            .setAutoCancel(true)
            .build()
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                serviceChannelId, "后台计时服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "APP后台运行常驻通知" }

            val remindChannel = NotificationChannel(
                remindChannelId, "到期提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "繁殖、售出、种花到期提醒"
                enableVibration(true)
                enableLights(true)
            }

            getSystemService(NotificationManager::class.java).apply {
                createNotificationChannel(serviceChannel)
                createNotificationChannel(remindChannel)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        tickThread?.interrupt()
        if (wakeLock.isHeld) wakeLock.release()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
