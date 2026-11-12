package com.apexvpn.client.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 382. Поводы для конфетти: первое подключение, юбилейные подключения, секретная тема. */
object Celebrate {
    data class Event(val id: Long, val text: String?)

    private val _event = MutableStateFlow<Event?>(null)
    val event = _event.asStateFlow()

    fun fire(text: String? = null) {
        _event.value = Event(System.nanoTime(), text)
    }

    fun consume() {
        _event.value = null
    }

    /** Вызывается при каждом успешном подключении. */
    fun onConnected() {
        val n = Prefs.connectCount + 1
        Prefs.connectCount = n
        when {
            n == 1 -> fire("🎉 Первое подключение — вы под защитой!")
            n in setOf(10, 50, 100, 250, 500, 1000, 2000, 5000) -> fire("🎉 Уже $n подключений с APEX CLIENT!")
        }
    }
}
