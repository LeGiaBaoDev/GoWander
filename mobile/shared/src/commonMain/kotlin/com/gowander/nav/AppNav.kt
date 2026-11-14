package com.gowander.nav

/** The four tabs of the bottom bar (ADR-034). "Đi đâu?" in the middle is an action, not a tab. */
enum class Tab { Map, Discover, Journey, Profile }

/** Screens pushed over a tab. The tab's own root (map, list…) is the empty stack. */
sealed interface Screen {
    data object Search : Screen
    data object PickOrigin : Screen
    data object PickDestination : Screen
    /** Temporary trip form (origin, destination, time) until the results header edits in place (UX-2 P2). */
    data object Plan : Screen
    data object Results : Screen
    data object Detail : Screen
    data object Steps : Screen
    data object Navigation : Screen
    data class Preview(val start: Int) : Screen
    data class Place(val id: String) : Screen
}

private val ROUTE_FORM = setOf(Screen.Search, Screen.PickOrigin, Screen.PickDestination, Screen.Plan)

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

    /** The trip form screens (search, pick, edit) give way to the results; an open results screen is reused. */
    fun showResults(): AppNav {
        val kept = stack.dropLastWhile { it in ROUTE_FORM }
        return with(if (kept.lastOrNull() == Screen.Results) kept else kept + Screen.Results)
    }

    /** A place already open in this tab is brought back instead of stacking a second copy. */
    fun openPlace(id: String) = Screen.Place(id).let { p -> if (p in stack) popTo { it == p } else push(p) }

    /** After process death the route is gone: route screens would be empty, so only place pages are kept. */
    fun dropRouteScreens() = copy(stacks = stacks.mapValues { (_, s) -> s.takeWhile { it is Screen.Place } })

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
            Screen.Plan -> "plan"
            Screen.Results -> "results"
            Screen.Detail -> "detail"
            Screen.Steps -> "steps"
            Screen.Navigation -> "navigation"
            is Screen.Preview -> "preview:${s.start}"
            is Screen.Place -> "place:${s.id}"
        }

        private fun decode(v: String): Screen = when {
            v == "search" -> Screen.Search
            v == "pick_origin" -> Screen.PickOrigin
            v == "pick_destination" -> Screen.PickDestination
            v == "plan" -> Screen.Plan
            v == "results" -> Screen.Results
            v == "detail" -> Screen.Detail
            v == "steps" -> Screen.Steps
            v == "navigation" -> Screen.Navigation
            v.startsWith("preview:") -> Screen.Preview(v.removePrefix("preview:").toIntOrNull() ?: throw IllegalArgumentException(v))
            v.startsWith("place:") -> Screen.Place(v.removePrefix("place:"))
            else -> throw IllegalArgumentException(v)
        }
    }
}
