package com.example.protoshuttleapp.ui.schedule

import java.time.DayOfWeek
import java.time.LocalDate

object ScheduleData {

    // TODO: replace with your real weekday times
    private val weekdayTimes = listOf(
        "7:00am","7:15am","7:30am","7:45am",
        "8:00am","8:15am","8:30am","8:45am",
        "9:00am","9:15am","9:30am","9:45am",
        "10:00am","10:15am","10:30am","10:45am",
        "11:00am","11:15am","11:30am","11:45am",
        "12:00pm","12:15pm","12:30pm","12:45pm",
        "1:00pm","1:15pm","1:30pm","1:45pm",
        "2:00pm","2:15pm","2:30pm","2:45pm",
        "3:00pm","3:15pm","3:30pm","3:45pm",
        "4:00pm","4:15pm","4:30pm","4:45pm",
        "5:00pm"
    )

    // TODO: replace with your real weekend times
    private val weekendTimes = listOf(
        "9:00am","9:30am","10:00am","10:30am",
        "11:00am","11:30am","12:00pm","12:30pm",
        "1:00pm","1:30pm","2:00pm","2:30pm",
        "3:00pm","3:30pm","4:00pm"
    )

    private val stopCycle = listOf("Campus", "Panther Bay", "Mary Star")

    private fun buildList(times: List<String>): List<ScheduleStop> =
        times.mapIndexed { i, t -> ScheduleStop(time = t, name = stopCycle[i % stopCycle.size]) }

    private val weekdayList by lazy { buildList(weekdayTimes) }
    private val weekendList by lazy { buildList(weekendTimes) }

    fun getFor(date: LocalDate): List<ScheduleStop> =
        when (date.dayOfWeek) {
            DayOfWeek.SATURDAY, DayOfWeek.SUNDAY -> weekendList
            else -> weekdayList
        }
}
