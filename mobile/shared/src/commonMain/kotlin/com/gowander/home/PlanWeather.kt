package com.gowander.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gowander.api.WeatherReading
import com.gowander.mock.Plan
import com.gowander.mock.PlanItem
import com.gowander.mock.shiftClock
import com.gowander.route.nowMillis
import com.gowander.route.pilotDay
import com.gowander.ui.Button
import com.gowander.ui.ButtonKind
import com.gowander.ui.ExtraIcons
import com.gowander.ui.Icon
import com.gowander.ui.KitIcons
import com.gowander.ui.Palette
import com.gowander.ui.Strings
import com.gowander.ui.Text
import kotlinx.coroutines.CancellationException
import kotlin.math.roundToInt

/** The one API call the weather UI needs; the app passes its shared client in (no second HttpClient). */
typealias WeatherQuery = suspend (lat: Double, lon: Double, at: String) -> WeatherReading

/** Where a plan's forecast is asked from: the first stop with coordinates, on the plan's day and start hour. */
internal data class PlanWeatherSpot(val lat: Double, val lon: Double, val at: String, val key: String)

/**
 * The forecast hour of a plan, or null when there is nothing honest to ask for: no day chosen, no stop with
 * coordinates, or a day beyond the free Open-Meteo window (today + 15 days).
 */
internal fun planWeatherSpot(plan: Plan, now: Long = nowMillis()): PlanWeatherSpot? {
    val day = plan.day ?: return null
    val start = plan.start
    if (start.length != 5 || start[2] != ':') return null
    val hour = start.take(2).toIntOrNull() ?: return null
    val minute = start.takeLast(2).toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    if (day < pilotDay(now, 0).first || day > pilotDay(now, 15).first) return null
    val stop = plan.items.firstOrNull { it.lat != null && it.lon != null } ?: return null
    val lat = stop.lat ?: return null
    val lon = stop.lon ?: return null
    val at = "${day}T${start}:00+07:00"
    return PlanWeatherSpot(lat, lon, at, "$lat,$lon,$at")
}

internal enum class Venue { Indoor, Outdoor, Unknown }

// Keywords of the sample/guide places. A place named after something else stays Unknown and is not counted as
// either, so the advice never guesses about a stop it does not recognise.
private val OUTDOOR_WORDS = listOf(
    "công viên", "phố đi bộ", "bờ sông", "quảng trường", "vườn", "bãi biển", "chợ nổi", "địa đạo",
    "bến phà", "bến tàu", "cầu ", "park", "square", "beach", "riverfront", "promenade",
)
private val INDOOR_WORDS = listOf(
    "bưu điện", "chợ", "nhà hát", "bảo tàng", "quán", "cà phê", "trung tâm thương mại", "siêu thị", "nhà sách",
    "rạp", "khách sạn", "nhà hàng", "chùa", "dinh", "thư viện", "cửa hàng", "tiệm",
    "museum", "market", "mall", "cafe", "coffee", "restaurant", "hotel", "library",
)

internal fun venueOf(item: PlanItem): Venue {
    val text = "${item.title} ${item.note}".lowercase()
    if (OUTDOOR_WORDS.any { text.contains(it) }) return Venue.Outdoor
    if (INDOOR_WORDS.any { text.contains(it) }) return Venue.Indoor
    return Venue.Unknown
}

/** Indoor and outdoor stops of a plan (the starting point without coordinates is not a stop). */
internal fun planVenueCounts(plan: Plan): Pair<Int, Int> {
    var indoor = 0
    var outdoor = 0
    plan.items.forEach { item ->
        if (item.lat == null || item.lon == null) return@forEach
        when (venueOf(item)) {
            Venue.Indoor -> indoor++
            Venue.Outdoor -> outdoor++
            Venue.Unknown -> Unit
        }
    }
    return indoor to outdoor
}

// One reading per exact hour and spot for the process: the plans list and the plan screen ask for the same hours.
private val weatherCache = mutableMapOf<String, WeatherReading>()

@Composable
internal fun rememberPlanWeather(plan: Plan, query: WeatherQuery): WeatherReading? {
    val spot = remember(plan.id, plan.day, plan.start, plan.items) { planWeatherSpot(plan) }
    var reading by remember(spot?.key) { mutableStateOf(spot?.let { weatherCache[it.key] }) }
    LaunchedEffect(spot?.key) {
        val wanted = spot ?: return@LaunchedEffect
        if (reading != null) return@LaunchedEffect
        reading = try {
            query(wanted.lat, wanted.lon, wanted.at)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null // weather is advice only: no forecast, no weather UI
        }
        reading?.let { weatherCache[wanted.key] = it }
    }
    return reading
}

private enum class Tone { Rain, Heat, Clear }

private fun WeatherReading.tone(): Tone = when {
    isRain || precipitationProbability >= 50 -> Tone.Rain
    apparentC >= 35 || temperatureC >= 35 -> Tone.Heat
    else -> Tone.Clear
}

private fun WeatherReading.hour(): String = at.substringAfter('T', at).take(5)

private fun Tone.color(): Color = when (this) {
    Tone.Rain -> Palette.Accent
    Tone.Heat -> Palette.Amber
    Tone.Clear -> Palette.Primary
}

private fun Tone.fill(): Color = when (this) {
    Tone.Rain -> Palette.AccentSoft
    Tone.Heat -> Palette.Warn
    Tone.Clear -> Palette.Soft
}

private fun Tone.icon(): ImageVector = when (this) {
    Tone.Rain -> KitIcons.rain
    else -> ExtraIcons.sun
}

private fun weatherTitle(reading: WeatherReading): String = when (reading.tone()) {
    Tone.Rain -> if (reading.precipitationProbability > 0) Strings.rainAt(reading.precipitationProbability, reading.hour())
    else Strings.rainAtTime(reading.hour())
    Tone.Heat -> Strings.hotAt(reading.apparentC.roundToInt(), reading.hour())
    Tone.Clear -> Strings.dryAt(reading.temperatureC.roundToInt(), reading.hour())
}

private fun weatherTip(tone: Tone, indoor: Int, outdoor: Int): String? = when {
    tone == Tone.Rain && outdoor > 0 -> Strings.WEATHER_RAIN_TIP
    tone == Tone.Rain && indoor > 0 -> Strings.WEATHER_RAIN_ALL_INDOOR
    tone == Tone.Heat && outdoor > 0 -> Strings.WEATHER_HEAT_TIP
    tone == Tone.Heat && indoor > 0 -> Strings.WEATHER_HEAT_INDOOR
    tone == Tone.Clear && outdoor > 0 -> Strings.WEATHER_CLEAR_TIP
    else -> null
}

/** One line inside a saved-plan card: the hour's sky plus how many stops it affects. */
@Composable
internal fun PlanWeatherSummary(plan: Plan, query: WeatherQuery, modifier: Modifier = Modifier) {
    val reading = rememberPlanWeather(plan, query) ?: return
    val tone = reading.tone()
    val (indoor, outdoor) = remember(plan.items) { planVenueCounts(plan) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(tone.icon(), 15.dp, tone.color())
            Spacer(Modifier.width(6.dp))
            Text(weatherTitle(reading), size = 12.sp, color = tone.color(), weight = FontWeight.Bold)
            val count = if (outdoor > 0) Strings.outdoorStops(outdoor) else if (indoor > 0) Strings.indoorStops(indoor) else null
            if (count != null) Text(" · $count", size = 11.5.sp, color = Palette.Muted)
        }
        weatherTip(tone, indoor, outdoor)?.let {
            Text(it, size = 11.sp, color = if (tone == Tone.Rain) Palette.Accent else Palette.Muted)
        }
    }
}

/**
 * The full weather card of the plan screen: the hour, the stops it affects, and — when rain is likely — two ways to
 * move the start. It is a suggestion from one forecast hour, not a promise about the whole trip.
 */
@Composable
internal fun PlanWeatherCard(plan: Plan, query: WeatherQuery, onChangeStart: (String) -> Unit, modifier: Modifier = Modifier) {
    val reading = rememberPlanWeather(plan, query) ?: return
    val tone = reading.tone()
    val (indoor, outdoor) = remember(plan.items) { planVenueCounts(plan) }
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(tone.fill()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Palette.Bg.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
                Icon(tone.icon(), 21.dp, tone.color())
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(Strings.TRIP_WEATHER, size = 11.sp, color = Palette.Muted, weight = FontWeight.Bold)
                Text(weatherTitle(reading), size = 15.sp, color = Palette.Ink, weight = FontWeight.Bold)
                weatherTip(tone, indoor, outdoor)?.let {
                    Text(it, Modifier.padding(top = 2.dp), size = 12.sp, color = Palette.Body)
                }
            }
        }
        if (indoor > 0 || outdoor > 0) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (indoor > 0) WeatherChip(ExtraIcons.home, Strings.indoorStops(indoor))
            if (outdoor > 0) WeatherChip(ExtraIcons.sun, Strings.outdoorStops(outdoor))
        }
        if (tone == Tone.Rain) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val earlier = shiftClock(plan.start, -60)
            if (earlier != plan.start) Button(Strings.WEATHER_EARLIER, Modifier.weight(1f), kind = ButtonKind.Outline, compact = true, pill = true) { onChangeStart(earlier) }
            val later = shiftClock(plan.start, 60)
            if (later != plan.start) Button(Strings.WEATHER_LATER, Modifier.weight(1f), kind = ButtonKind.Outline, compact = true, pill = true) { onChangeStart(later) }
        }
        Text(Strings.WEATHER_SOURCE, size = 10.5.sp, color = Palette.Muted)
    }
}

@Composable
private fun WeatherChip(icon: ImageVector, label: String) {
    Row(
        Modifier.clip(RoundedCornerShape(999.dp)).background(Palette.Bg.copy(alpha = 0.55f)).padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, 13.dp, Palette.Muted)
        Spacer(Modifier.width(5.dp))
        Text(label, size = 11.sp, color = Palette.Body, weight = FontWeight.SemiBold)
    }
}
