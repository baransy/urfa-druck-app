package de.myurfa.druck

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class OrderAdapter : RecyclerView.Adapter<OrderAdapter.VH>() {

    private val items = ArrayList<Order>()

    /** Neue Bestellung oben einfügen oder bestehende (per orderId) aktualisieren. */
    fun addOrUpdate(o: Order) {
        val idx = items.indexOfFirst { it.orderId == o.orderId }
        if (idx >= 0) {
            items[idx] = o
            notifyItemChanged(idx)
        } else {
            items.add(0, o)
            notifyItemInserted(0)
        }
    }

    fun isEmpty(): Boolean = items.isEmpty()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_order, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    override fun getItemCount(): Int = items.size

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        private val orderNo: TextView = v.findViewById(R.id.orderNo)
        private val meta: TextView = v.findViewById(R.id.orderMeta)
        private val name: TextView = v.findViewById(R.id.orderName)
        private val time: TextView = v.findViewById(R.id.orderTime)
        private val chip: TextView = v.findViewById(R.id.statusChip)

        fun bind(o: Order) {
            orderNo.text = "#${o.orderId}"
            meta.text = "${o.type} · ${o.total}"
            name.text = o.name
            time.text = o.time
            when (o.status) {
                "gedruckt" -> { chip.text = "✓ Gedruckt"; tint("#16A34A") }
                "fehler"   -> { chip.text = "✗ Fehler";   tint("#DC2626") }
                else       -> { chip.text = "🔔 Neu";      tint("#FF8000") }
            }
        }

        private fun tint(hex: String) {
            chip.background?.setTint(Color.parseColor(hex))
        }
    }
}
