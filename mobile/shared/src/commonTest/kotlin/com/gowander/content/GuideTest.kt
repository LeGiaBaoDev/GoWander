package com.gowander.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GuideTest {
    @Test
    fun serverListsReplaceTheBundledCopyAndDishLookupNeverFails() {
        try {
            assertEquals(GUIDE_DISHES, Guide.dishes) // before the API answers
            val pho = Dish("hcm-food-pho", "Phở", "S", "D", listOf(Shop("Quán A", "1 Lê Lợi")), "Nguồn")
            Guide.use(GuideContent(dishes = listOf(pho), stories = emptyList()))
            assertEquals(listOf(pho), Guide.dishes)
            assertTrue(Guide.stories.isEmpty(), "an empty server list wins: the Admin hid everything")
            assertEquals(pho, guideDish("hcm-food-pho"))
            assertEquals("hcm-food-com-tam", guideDish("hcm-food-com-tam").id, "an id only the bundle knows still opens")
            assertEquals(GUIDE_DISHES.first(), guideDish("gone"))
        } finally {
            Guide.use(GuideContent(GUIDE_DISHES, GUIDE_STORIES))
        }
    }

    @Test
    fun bundledDishesKeepTheirSource() {
        assertTrue(GUIDE_DISHES.all { it.sources == GUIDE_FOOD_SOURCE })
    }
}
