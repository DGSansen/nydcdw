import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AnimalAdapter
    private lateinit var data: MutableList<AnimalRecord>

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshAllData()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        DataManager.init(this)
        data = DataManager.loadRows()

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = AnimalAdapter(
            data = data,
            onDelete = { index ->
                data.removeAt(index)
                DataManager.saveRows(data)
                adapter.notifyItemRemoved(index)
            },
            onFieldChange = { index, field, value ->
                val row = data[index]
                when (field) {
                    "编号" -> row.编号 = value as Int
                    "饲料" -> row.饲料 = value as Int
                    "清洁度" -> row.清洁度 = value as Int
                    "动物" -> {
                        row.动物 = value as String
                        val defaultStage = AnimalConfig.getStageList(row.动物)[0]
                        row.阶段 = defaultStage
                        row.阶段开始 = System.currentTimeMillis()
                        row.繁殖开始 = null
                        row.繁殖已通知次数 = 0
                        row.售出开始 = null
                        row.售出已通知 = false
                        row.已通知 = false
                        adapter.notifyItemChanged(index)
                    }
                    "阶段" -> {
                        row.阶段 = value as String
                        val now = System.currentTimeMillis()
                        row.阶段开始 = now
                        val type = AnimalConfig.动物配置[row.动物]
                        if (type is FlowerType) {
                            row.已通知 = false
                            row.繁殖开始 = null
                            row.售出开始 = null
                        } else if (value == "繁殖期") {
                            row.繁殖开始 = now
                            row.繁殖已通知次数 = 0
                            row.售出开始 = null
                        } else if (value == "老年期") {
                            row.售出开始 = now
                            row.售出已通知 = false
                            row.繁殖开始 = null
                        } else {
                            row.繁殖开始 = null
                            row.售出开始 = null
                        }
                    }
                }
                DataManager.saveRows(data)
            }
        )
        recyclerView.adapter = adapter

        findViewById<Button>(R.id.btnAdd).setOnClickListener {
            val firstAnimal = AnimalConfig.动物配置.keys.first()
            val defaultStage = AnimalConfig.getStageList(firstAnimal)[0]
            data.add(
                AnimalRecord(
                    编号 = 1,
                    动物 = firstAnimal,
                    阶段 = defaultStage,
                    阶段开始 = System.currentTimeMillis()
                )
            )
            DataManager.saveRows(data)
            adapter.notifyItemInserted(data.size - 1)
        }

        startTimerService()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(updateReceiver, IntentFilter("com.example.animal.timer.UPDATE_UI"), RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(updateReceiver, IntentFilter("com.example.animal.timer.UPDATE_UI"))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100)
        }
    }

    private fun startTimerService() {
        val intent = Intent(this, TimerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun refreshAllData() {
        data.clear()
        data.addAll(DataManager.loadRows())
        adapter.notifyDataSetChanged()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(updateReceiver)
    }
}
