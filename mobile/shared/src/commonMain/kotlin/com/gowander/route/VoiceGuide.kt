package com.gowander.route

import com.gowander.Prefs

/** Speaker button of the navigation screen (plan §8.3): everything, only warnings, or silent. Saved on the device. */
enum class VoiceMode { On, AlertsOnly, Off;
    fun next() = entries[(ordinal + 1) % entries.size]

    companion object {
        private const val OFF = "voice_off"
        private const val ALERTS = "voice_alerts_only"
        fun load() = when { Prefs.getBool(OFF) -> Off; Prefs.getBool(ALERTS) -> AlertsOnly; else -> On }
        fun save(m: VoiceMode) { Prefs.setBool(OFF, m == Off); Prefs.setBool(ALERTS, m == AlertsOnly) }
    }
}

/** One sentence to say; [alert] ones (off route, get off soon, arrived) are also said in "Chỉ cảnh báo". */
data class Cue(val text: String, val alert: Boolean)

private const val FAR_M = 200.0
private const val NEAR_M = 30.0
private const val FAR_MIN_M = 60.0 // closer than this the "far" sentence would come right before the "near" one
// shortcut: the API has no stop list on a leg, so "1 stop before" is approximated by ~one stop spacing; use real stops when the API sends them.
private const val ALIGHT_M = 400.0

/**
 * Decides what to say on each GPS fix: about 200 m before a turn, again at about 30 m, before getting off a bus or
 * metro, when leaving the route and on arrival. Each sentence is said once; leaving the route is said again only after
 * the user came back to it.
 */
class VoiceCues {
    private val said = mutableSetOf<String>()
    private var wasOff = false

    fun next(progress: NavProgress?, steps: List<NavStep>, arrived: Boolean, offRoute: Boolean, destination: String): Cue? {
        if (progress == null) return null
        if (arrived) return once("arrive", Cue("Bạn đã tới $destination", alert = true))
        if (offRoute != wasOff) { wasOff = offRoute; if (offRoute) return Cue("Bạn đã đi lệch tuyến", alert = true) }
        if (offRoute) return null
        val i = progress.nextStep
        val step = steps.getOrNull(i) ?: return null
        val d = progress.toNextM
        if (step.transit && step.arrow == "⇩") {
            return if (d <= ALIGHT_M) once("alight-$i", Cue("Sắp tới trạm, ${step.text.lowercaseFirst()}, chuẩn bị xuống", alert = true)) else null
        }
        if (step.transit) return null // boarding: the banner says it, no countdown for a stop the user walks to
        return when {
            d <= NEAR_M -> once("near-$i", Cue(step.text, alert = false))
            d in FAR_MIN_M..FAR_M -> once("far-$i", Cue("Đi thêm ${spokenDistance(d)}, ${step.text.lowercaseFirst()}", alert = false))
            else -> null
        }
    }

    private fun once(key: String, cue: Cue): Cue? = if (said.add(key)) cue else null
}

/** Should [cue] be spoken in [mode]? */
fun audible(cue: Cue, mode: VoiceMode) = when (mode) { VoiceMode.On -> true; VoiceMode.AlertsOnly -> cue.alert; VoiceMode.Off -> false }

/** "120 m" -> "120 mét", "1,2 km" -> "1,2 ki lô mét": units read out the same on every voice. */
fun spokenDistance(m: Double) = distanceLabel(m).let { if (it.endsWith(" km")) it.removeSuffix(" km") + " ki lô mét" else it.removeSuffix(" m") + " mét" }

private fun String.lowercaseFirst() = replaceFirstChar { it.lowercase() }
