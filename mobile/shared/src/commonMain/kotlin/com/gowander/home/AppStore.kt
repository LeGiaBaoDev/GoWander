package com.gowander.home

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateListOf
import com.gowander.mock.MOCK_PLANS
import com.gowander.mock.Plan
import com.gowander.mock.PlanStatus

/** A place the user bookmarked from its sheet ("Đã lưu"). */
data class SavedPlace(val id: String, val name: String, val category: String?)

/**
 * What the user makes in this session: plans and saved places.
 * shortcut: memory only, lost when the app closes; upgrade to on-device storage with the P5 storage ADR.
 */
class AppStore {
    val plans = mutableStateListOf<Plan>().apply { addAll(MOCK_PLANS) }
    val saved = mutableStateListOf<SavedPlace>()

    fun plan(id: String): Plan? = plans.firstOrNull { it.id == id }

    fun put(plan: Plan) {
        val i = plans.indexOfFirst { it.id == plan.id }
        if (i >= 0) plans[i] = plan else plans.add(0, plan)
    }

    fun newPlan(): Plan = Plan("p${plans.size + 1}-${plans.sumOf { it.items.size }}", "Kế hoạch mới", "Chưa chọn ngày", "08:00", "Buýt + đi bộ", emptyList(), PlanStatus.Draft)

    fun toggleSaved(p: SavedPlace) { if (saved.any { it.id == p.id }) saved.removeAll { it.id == p.id } else saved.add(0, p) }
}

/** Shows a short message at the bottom of the screen (things that only explain themselves, e.g. Vệ tinh, Chia sẻ). */
val LocalNotice = compositionLocalOf<(String) -> Unit> { {} }
