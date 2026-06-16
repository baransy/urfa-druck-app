package de.myurfa.druck

data class Order(
    val orderId: Int,
    val type: String,
    val total: String,
    val name: String,
    val time: String,
    var status: String   // "neu" | "gedruckt" | "fehler"
)
