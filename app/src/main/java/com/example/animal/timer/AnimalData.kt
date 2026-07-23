import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class AnimalRecord(
    var 编号: Int,
    var 动物: String,
    var 阶段: String,
    var 饲料: Int = 100,
    var 清洁度: Int = 100,
    var 阶段开始: Long = System.currentTimeMillis(),
    var 繁殖开始: Long? = null,
    var 繁殖已通知次数: Int = 0,
    var 售出开始: Long? = null,
    var 售出已通知: Boolean = false,
    var 已通知: Boolean = false
)

data class AnimalType(
    val 成长时长: Int,
    val 繁殖期时长: Int,
    val 繁殖次数: Int,
    val 繁殖冷却: Int,
    val 售出时长: Int
)

data class FlowerType(
    val 阶段列表: List<String>,
    val 阶段时长: Map<String, Int>
)

object AnimalConfig {
    const val STORAGE_KEY = "动物计时数据_v2"

    val 编号列表 = (1..10).toList()
    val 阶段列表 = listOf("成长期", "繁殖期", "老年期")

    val 动物配置 = mapOf(
        "鸡" to AnimalType(成长时长 = 48, 繁殖期时长 = 24, 繁殖次数 = 1, 繁殖冷却 = 0, 售出时长 = 12),
        "猪" to AnimalType(成长时长 = 72, 繁殖期时长 = 72, 繁殖次数 = 2, 繁殖冷却 = 48, 售出时长 = 24),
        "鸵鸟" to AnimalType(成长时长 = 72, 繁殖期时长 = 72, 繁殖次数 = 2, 繁殖冷却 = 48, 售出时长 = 24),
        "马" to AnimalType(成长时长 = 144, 繁殖期时长 = 168, 繁殖次数 = 3, 繁殖冷却 = 72, 售出时长 = 48),
        "种花" to FlowerType(
            阶段列表 = listOf("2小时", "8小时", "12小时"),
            阶段时长 = mapOf("2小时" to 2, "8小时" to 8, "12小时" to 12)
        )
    )

    fun getNextStageDuration(type: Any?, stage: String): Int? {
        if (type is FlowerType) return type.阶段时长[stage]
        if (type is AnimalType) {
            return when (stage) {
                "成长期" -> type.成长时长
                "繁殖期" -> type.繁殖期时长
                else -> null
            }
        }
        return null
    }

    fun getStageList(animalName: String): List<String> {
        val type = 动物配置[animalName]
        return if (type is FlowerType) type.阶段列表 else 阶段列表
    }

    fun isFlower(animalName: String): Boolean = 动物配置[animalName] is FlowerType
}

object DataManager {
    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    fun init(context: Context) {
        prefs = context.getSharedPreferences("animal_timer_prefs", Context.MODE_PRIVATE)
    }

    fun loadRows(): MutableList<AnimalRecord> {
        val json = prefs.getString(AnimalConfig.STORAGE_KEY, null)
        return try {
            val type = object : TypeToken<MutableList<AnimalRecord>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            getDefaultData()
        }
    }

    fun saveRows(rows: List<AnimalRecord>) {
        prefs.edit().putString(AnimalConfig.STORAGE_KEY, gson.toJson(rows)).apply()
    }

    private fun getDefaultData(): MutableList<AnimalRecord> {
        val now = System.currentTimeMillis()
        return mutableListOf(
            AnimalRecord(编号 = 1, 动物 = "鸡", 阶段 = "成长期", 阶段开始 = now),
            AnimalRecord(编号 = 2, 动物 = "鸡", 阶段 = "成长期", 阶段开始 = now),
            AnimalRecord(编号 = 3, 动物 = "鸡", 阶段 = "繁殖期", 阶段开始 = now, 繁殖开始 = now),
            AnimalRecord(编号 = 4, 动物 = "猪", 阶段 = "繁殖期", 阶段开始 = now, 繁殖开始 = now),
            AnimalRecord(编号 = 5, 动物 = "鸵鸟", 阶段 = "繁殖期", 阶段开始 = now, 繁殖开始 = now),
            AnimalRecord(编号 = 6, 动物 = "马", 阶段 = "繁殖期", 阶段开始 = now, 繁殖开始 = now)
        )
    }
}
