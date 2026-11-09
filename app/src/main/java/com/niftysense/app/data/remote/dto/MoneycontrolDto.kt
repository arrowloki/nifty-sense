package com.niftysense.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class MoneycontrolResponse(
    val code: String? = null,
    val message: String? = null,
    val data: MoneycontrolData? = null
)

@Serializable
data class MoneycontrolData(
    val pricecurrent: String? = null,
    val priceprevclose: String? = null,
    val pricechange: String? = null,
    val pricepercentchange: String? = null,
    val HIGH: String? = null,
    val LOW: String? = null,
    val OPEN: String? = null,
    val lastupd: String? = null,
    val HN: String? = null
)
