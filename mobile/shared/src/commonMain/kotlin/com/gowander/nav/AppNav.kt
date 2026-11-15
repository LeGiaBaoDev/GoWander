package com.gowander.nav

/** The four tabs of the bottom bar (ADR-034). "Đi đâu?" in the middle is an action, not a tab. */
enum class Tab { Map, Discover, Journey, Profile }

/** Screens pushed over a tab. The tab's own root (map, list…) is the empty stack. */
sealed interface Screen {
    data object Search : Screen
    data object PickOrigin : Screen
    data object PickDestination : Screen
    data object Results : Screen
    data object Detail : Screen
    data object Steps : Screen
    data object Navigation : Screen
    data class Preview(val start: Int) : Screen
    data class Place(val id: String) : Screen
    /** Place search from the map (screen 1); with [planId] the chosen place is added to that plan instead. */
    data class FindPlace(val planId: String? = null) : Screen
    data class Region(val id: String) : Screen
    data class Dish(val id: String) : Screen
    data class Trip(val id: String) : Screen
    data class PlanEdit(val id: String) : Screen
    data class PlanMap(val id: String) : Screen
    data object PortfolioEdit : Screen
    data object Settings : Screen
}

private val ROUTE_FORM = setOf(Screen.Search, Screen.PickOrigin, Screen.PickDestination)
private val ROUTE_SCREENS = ROUTE_FORM + setOf(Screen.Results, Screen.Detail, Screen.Steps, Screen.Navigation)

/**
 * One back stack per tab (UX-2, ADR-034). Immutable: every move returns a new value, so the UI state is one
 * `rememberSaveable` and Back is always "drop the top screen". Same code on Android and iOS.
 */
data class AppNav(val tab: Tab = Tab.Map, val stacks: Map<Tab, List<Screen>> = emptyMap()) {
    val stack: List<Screen> get() = stacks[tab].orEmpty()
    val top: Screen? get() = stack.lastOrNull()
    val canPop: Boolean get() = stack.isNotEmpty()

    private fun with(stack: List<Screen>) = copy(stacks = stacks + (tab to stack))

    fun push(s: Screen) = with(stack + s)
    fun pop() = if (canPop) with(stack.dropLast(1)) else this

    /** Another tab keeps its stack; tapping the active tab again goes back to its root (platform convention). */
    fun select(t: Tab) = if (t == tab) with(emptyList()) else copy(tab = t)

    /** Back to the nearest screen matching [match]; unchanged when there is none. */
    fun popTo(match: (Screen) -> Boolean): AppNav {
        val i = stack.indexOfLast(match)
        return if (i < 0) this else with(stack.take(i + 1))
    }

    /** The trip form screens (search, pick on map) give way to the results; an open results screen is reused. */
    fun showResults(): AppNav {
        val kept = stack.dropLastWhile { it in ROUTE_FORM }
        return with(if (kept.lastOrNull() == Screen.Results) kept else kept + Screen.Results)
    }

    /** A place already open in this tab is brought back instead of stacking a second copy. */
    fun openPlace(id: String) = Screen.Place(id).let { p -> if (p in stack) popTo { it == p } else push(p) }

    /** After process death the route is gone: route screens would be empty, so only place pages are kept. */
    fun dropRouteScreens() = copy(stacks = stacks.mapValues { (_, s) -> s.takeWhile { it !in ROUTE_SCREENS && it !is Screen.Preview } })

    /** Strings for `rememberSaveable`: the active tab, then one entry per tab with its screens. */
    fun save(): List<String> = listOf(tab.name) + stacks.map { (t, s) -> (listOf(t.name) + s.map(::encode)).joinToString(SEP) }

    companion object {
        private const val SEP = "\u001F" // unit separator: never part of a place id

        fun restore(saved: List<String>): AppNav = try {
            val tab = Tab.valueOf(saved.first())
            val stacks = saved.drop(1).associate { line ->
                val parts = line.split(SEP)
                Tab.valueOf(parts.first()) to parts.drop(1).map(::decode)
            }
            AppNav(tab, stacks)
        } catch (e: IllegalArgumentException) {
            AppNav() // unknown name from an older or newer app version
        } catch (e: NoSuchElementException) {
            AppNav() // empty bundle
        }

        private fun encode(s: Screen): String = when (s) {
            Screen.Search -> "search"
            Screen.PickOrigin -> "pick_origin"
            Screen.PickDestination -> "pick_destination"
            Screen.Results -> "results"
            Screen.Detail -> "detail"
            Screen.Steps -> "steps"
            Screen.Navigation -> "navigation"
            is Screen.Preview -> "preview:${s.start}"
            is Screen.Place -> "place:${s.id}"
            is Screen.FindPlace -> "find:${s.planId ?: ""}"
            is Screen.Region -> "region:${s.id}"
            is Screen.Dish -> "dish:${s.id}"
            is Screen.Trip -> "trip:${s.id}"
            is Screen.PlanEdit -> "plan:${s.id}"
            is Screen.PlanMap -> "plan_map:${s.id}"
            Screen.PortfolioEdit -> "portfolio_edit"
            Screen.Settings -> "settings"
        }

        private fun decode(v: String): Screen = when {
            v == "search" -> Screen.Search
            v == "pick_origin" -> Screen.PickOrigin
            v == "pick_destination" -> Screen.PickDestination
            v == "results" -> Screen.Results
            v == "detail" -> Screen.Detail
            v == "steps" -> Screen.Steps
            v == "navigation" -> Screen.Navigation
            v.startsWith("preview:") -> Screen.Preview(v.removePrefix("preview:").toIntOrNull() ?: throw IllegalArgumentException(v))
            v.startsWith("place:") -> Screen.Place(v.removePrefix("place:"))
            v.startsWith("find:") -> Screen.FindPlace(v.removePrefix("find:").ifEmpty { null })
            v.startsWith("region:") -> Screen.Region(v.removePrefix("region:"))
            v.startsWith("dish:") -> Screen.Dish(v.removePrefix("dish:"))
            v.startsWith("trip:") -> Screen.Trip(v.removePrefix("trip:"))
            v.startsWith("plan:") -> Screen.PlanEdit(v.removePrefix("plan:"))
            v.startsWith("plan_map:") -> Screen.PlanMap(v.removePrefix("plan_map:"))
            v == "portfolio_edit" -> Screen.PortfolioEdit
            v == "settings" -> Screen.Settings
            else -> throw IllegalArgumentException(v)
        }
    }
}
