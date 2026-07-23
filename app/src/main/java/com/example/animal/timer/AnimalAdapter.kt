import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.maxOf

class AnimalAdapter(
    private val data: MutableList<AnimalRecord>,
    private val onDelete: (Int) -> Unit,
    private val onFieldChange: (Int, String, Any) -> Unit
) : RecyclerView.Adapter<AnimalAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val spinnerId: Spinner = view.findViewById(R.id.spinnerId)
        val spinnerAnimal: Spinner = view.findViewById(R.id.spinnerAnimal)
        val spinnerStage: Spinner = view.findViewById(R.id.spinnerStage)
        val etFeed: EditText = view.findViewById(R.id.etFeed)
        val etClean: EditText = view.findViewById(R.id.etClean)
        val btnDelete: Button = view.findViewById(R.id.btnDelete)
        val tvNext: TextView = view.findViewById(R.id.tvNext)
        val tvBreed: TextView = view.findViewById(R.id.tvBreed)
        val tvSell: TextView = view.findViewById(R.id.tvSell)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_animal, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = data[position]
        val context = holder.itemView.context

        val idAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, AnimalConfig.编号列表)
        holder.spinnerId.adapter = idAdapter
        holder.spinnerId.setSelection(AnimalConfig.编号列表.indexOf(item.编号))
        holder.spinnerId.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                onFieldChange(position, "编号", AnimalConfig.编号列表[p2])
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        val animalList = AnimalConfig.动物配置.keys.toList()
        val animalAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, animalList)
        holder.spinnerAnimal.adapter = animalAdapter
        holder.spinnerAnimal.setSelection(animalList.indexOf(item.动物))
        holder.spinnerAnimal.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                onFieldChange(position, "动物", animalList[p2])
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        val stageList = AnimalConfig.getStageList(item.动物)
        val stageAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, stageList)
        holder.spinnerStage.adapter = stageAdapter
        holder.spinnerStage.setSelection(stageList.indexOf(item.阶段))
        holder.spinnerStage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                onFieldChange(position, "阶段", stageList[p2])
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        holder.etFeed.setText(item.饲料.toString())
        holder.etClean.setText(item.清洁度.toString())
        holder.etFeed.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val value = holder.etFeed.text.toString().toIntOrNull() ?: 0
                onFieldChange(position, "饲料", value.coerceIn(0, 100))
            }
        }
        holder.etClean.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val value = holder.etClean.text.toString().toIntOrNull() ?: 0
                onFieldChange(position, "清洁度", value.coerceIn(0, 100))
            }
        }

        holder.btnDelete.setOnClickListener { onDelete(position) }
        updateCountdownText(holder, item)
    }

    fun updateCountdownText(holder: ViewHolder, item: AnimalRecord) {
        val now = System.currentTimeMillis()
        val type = AnimalConfig.动物配置[item.动物]
        val isFlower = AnimalConfig.isFlower(item.动物)

        val nextDur = AnimalConfig.getNextStageDuration(type, item.阶段)
        holder.tvNext.text = if (nextDur == null) {
            "下一阶段: -"
        } else {
            val end = item.阶段开始 + nextDur * 3600_000L
            "下一阶段: ${formatTime(end - now)}"
        }

        holder.tvBreed.text = if (isFlower || item.阶段 != "繁殖期" || item.繁殖开始 == null || type !is AnimalType) {
            "繁殖: -"
        } else {
            val total = type.繁殖次数
            val count = item.繁殖已通知次数
            if (count >= total) {
                "繁殖: 已全部"
            } else {
                val nextTime = item.繁殖开始!! + count * type.繁殖冷却 * 3600_000L
                val left = nextTime - now
                if (left <= 0) "繁殖: 可繁殖 ${count+1}/$total"
                else "繁殖: ${formatTime(left)} ($count/$total)"
            }
        }

        holder.tvSell.text = when {
            isFlower || type !is AnimalType -> "售出: -"
            item.阶段 == "老年期" && item.售出开始 != null -> {
                val end = item.售出开始!! + type.售出时长 * 3600_000L
                if (end - now <= 0) "售出: 可售出" else "售出: ${formatTime(end - now)}"
            }
            item.阶段 == "繁殖期" && item.繁殖开始 != null -> {
                val totalHours = type.成长时长 + type.繁殖期时长
                val startGrowth = item.繁殖开始!! - type.成长时长 * 3600_000L
                val end = startGrowth + totalHours * 3600_000L
                if (end - now <= 0) "售出: 即将老年" else "售出: ${formatTime(end - now)}"
            }
            item.阶段 == "成长期" -> {
                val totalHours = type.成长时长 + type.繁殖期时长
                val end = item.阶段开始 + totalHours * 3600_000L
                if (end - now <= 0) "售出: 即将繁殖" else "售出: ${formatTime(end - now)}"
            }
            else -> "售出: -"
        }
    }

    private fun formatTime(ms: Long): String {
        val total = maxOf(0, ms / 1000)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return "%02d:%02d:%02d".format(h, m, s)
    }

    override fun getItemCount() = data.size
}
