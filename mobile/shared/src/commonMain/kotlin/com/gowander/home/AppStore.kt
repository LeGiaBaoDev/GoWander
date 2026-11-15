package com.gowander.home

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateListOf
import com.gowander.mock.MOCK_PLANS
import com.gowander.mock.Plan
import com.gowander.mock.PlanStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A place the user bookmarked from its sheet ("Đã lưu"). */
@Serializable
data class SavedPlace(val id: String, val name: String, val category: String?)

@Serializable
private data class Stored(val plans: List<Plan> = emptyList(), val saved: List<SavedPlace> = emptyList())

private val storeJson = Json { ignoreUnknownKeys = true }

/**
 * What the user makes: plans and saved places, kept on the device as one JSON string (ADR-036, no account needed).
 * [stored] is what was saved last time; [persist] is called with the new string after every change.
 * First run, or data this version cannot read: the sample plans, so the Plans tab is not empty.
 */
class AppStore(stored: String? = null, private val persist: (String) -> Unit = {}) {
    private val loaded = stored?.let {
        try { storeJson.decodeFromString<Stored>(it) } catch (e: Exception) { println("GoWander: saved plans unreadable, starting from samples: ${e.message}"); null }
    }
    val plans = mutableStateListOf<Plan>().apply { addAll(loaded?.plans ?: MOCK_PLANS) }
    val saved = mutableStateListOf<SavedPlace>().apply { addAll(loaded?.saved.orEmpty()) }

    fun plan(id: String): Plan? = plans.firstOrNull { it.id == id }

    fun put(plan: Plan) {
        val i = plans.indexOfFirst { it.id == plan.id }
        if (i >= 0) plans[i] = plan else plans.add(0, plan)
        save()
    }

    fun newPlan(): Plan = Plan("p${plans.size + 1}-${plans.sumOf { it.items.size }}", "Kế hoạch mới", "Chưa chọn ngày", "08:00", "Buýt + đi bộ", emptyList(), PlanStatus.Draft)

    fun toggleSaved(p: SavedPlace) {
        if (saved.any { it.id == p.id }) saved.removeAll { it.id == p.id } else saved.add(0, p)
        save()
    }

    private fun save() = persist(storeJson.encodeToString(Stored.serializer(), Stored(plans.toList(), saved.toList())))
}

/** Shows a short message at the bottom of the screen (things that only explain themselves, e.g. Vệ tinh, Chia sẻ). */
val LocalNotice = compositionLocalOf<(String) -> Unit> { {} }
