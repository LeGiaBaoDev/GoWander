package com.gowander.mock

import kotlinx.serialization.Serializable
import kotlin.math.abs

/*
 * SAMPLE DATA for the UX-2 canvas screens (owner, 14-11-2026: "cái nào chưa có thì cứ mockup data").
 * Every value here is made up or a placeholder, listed in the mock data register of PIPELINE.md §5c.
 * Replace each block with its real source and delete its register row; nothing here may be shown as fact in the pilot.
 * Coordinates are real places in HCMC so "Bắt đầu" can ask the real router.
 */

// ---------- Ratings and reviews (real ones need accounts: COM-1) ----------

/** Sample star rating 4,0–4,9, stable per place. */
fun mockRating(id: String): Double = 4.0 + abs(id.hashCode()) % 10 / 10.0
fun mockReviewCount(id: String): Int = 20 + abs(id.hashCode()) % 180

/** Share of 5..1 star reviews, percent. */
val MOCK_RATING_SPLIT = listOf(68, 20, 7, 3, 2)

data class MockReview(val initial: String, val stars: Int, val ago: String, val text: String)

val MOCK_REVIEWS = listOf(
    MockReview("M", 5, "2 tuần trước", "[Bình luận mẫu] Đi buýt xuống ngay trạm gần đây, rất tiện. Nên ghé buổi sáng cho đỡ đông."),
    MockReview("T", 4, "1 tháng trước", "[Bình luận mẫu] Không gian đẹp, giá hơi cao so với xung quanh."),
)

// ---------- Suggested trips (Journey, region guide) ----------

/** How to reach a stop from the previous one; [kind] picks the colour: walk, bus or metro. */
/** Travel to a stop. [from] is the position ("lat,lon") of the stop it was measured from by the router; a leg whose previous stop is another one is stale. */
@Serializable data class Move(val label: String, val kind: MoveKind, val minutes: Int, val walkM: Int = 0, val from: String? = null)
@Serializable enum class MoveKind { Walk, Bus, Metro }


// ---------- Regions (Discover › Điểm đến Việt Nam) ----------

/** Where the card sits relative to the region's point on the map, so neighbouring cards do not cover each other. */
enum class CardSide { Left, Right, Above, Below, AboveRight, BelowRight }

/** A destination region at its real coordinates; the list of regions and their guides are still sample content. */
/** [photoCredit]: author and licence of the bundled photo `drawable/region_<id>.jpg` (Wikimedia Commons, docs/photo-credits.md). */
data class Region(val id: String, val name: String, val area: String, val lat: Double, val lon: Double, val side: CardSide, val photoCredit: String, val routing: Boolean = false)

val MOCK_REGIONS = listOf(
    Region("ha-noi", "Hà Nội", "Miền Bắc", 21.028, 105.854, CardSide.Left, "Bex Walton, CC BY 2.0"),
    Region("ha-long", "Hạ Long", "Quảng Ninh", 20.951, 107.080, CardSide.AboveRight, "Steffen Schmitz, CC BY-SA 4.0"),
    Region("mien-trung", "Huế · Đà Nẵng · Hội An", "Miền Trung", 16.054, 108.202, CardSide.AboveRight, "Steffen Schmitz, CC BY-SA 4.0"),
    Region("da-lat", "Đà Lạt", "Lâm Đồng", 11.940, 108.438, CardSide.Right, "Diane Selwyn, phạm vi công cộng"),
    Region("tp-hcm", "TP.HCM", "Có tìm tuyến", 10.776, 106.701, CardSide.Below, "Tran Van Ngoc, CC0", routing = true),
    Region("phu-quoc", "Phú Quốc", "Kiên Giang", 10.227, 103.964, CardSide.Above, "Elmschrat, CC0"),
)

fun mockRegion(id: String) = MOCK_REGIONS.firstOrNull { it.id == id } ?: MOCK_REGIONS.first { it.routing }


const val MOCK_OTHER_REGION_INTRO = "[Cẩm nang mẫu.] Nội dung vùng này chưa có. GoWander mới tìm tuyến và dẫn đường ở TP.HCM."

// ---------- Profile portfolio ----------

data class PortfolioSection(val title: String, val visibility: String)

object MockPortfolio {
    const val NAME = "[TÊN BẠN]"
    const val INITIAL = "B"
    const val TAGLINE = "Khám phá Sài Gòn bằng metro, xe buýt và đôi chân."
    const val CITY = "TP. Hồ Chí Minh"
    val STATS = listOf("12" to "Chuyến", "38" to "Điểm đã ghé", "21" to "Đóng góp", "4" to "Câu chuyện")
    const val ABOUT = "[Giới thiệu mẫu] Thích đi metro và tìm quán cà phê yên tĩnh. Mỗi cuối tuần chọn một con phố cũ để đi bộ và chụp lại."
    const val FEATURED_TITLE = "Một buổi chiều kiến trúc"
    const val FEATURED_META = "11-11-2026 · 2 điểm · đi metro + đi bộ"
    const val FEATURED_QUOTE = "“Trời vừa tạnh mưa, ánh đèn lên rất đẹp.”"
    const val MAP_SUMMARY = "38 điểm · 3 quận"
    val CONTRIBUTIONS = listOf("9" to "Đánh giá đã đăng", "6" to "Ảnh chia sẻ (CC BY-SA)", "4" to "Sửa thông tin được duyệt", "2" to "Báo lỗi tuyến đã sửa")
    const val RECENT_REVIEW_PLACE = "ĐÁNH GIÁ GẦN ĐÂY · CHỢ BẾN THÀNH"
    const val RECENT_REVIEW = "[Bình luận mẫu] Đi buýt số 18 xuống ngay trạm trước chợ, rất tiện."
    const val MOMENTS = 6
    val SECTIONS = listOf(
        PortfolioSection("Bìa + số liệu", "Công khai"), PortfolioSection("Về tôi", "Công khai"),
        PortfolioSection("Chuyến nổi bật", "Người có link"), PortfolioSection("Bản đồ khám phá", "Chỉ mình tôi"),
        PortfolioSection("Đóng góp cho cộng đồng", "Công khai"), PortfolioSection("Khoảnh khắc (ảnh)", "Người có link"),
    )
}

// ---------- Plans (Hồ sơ › Kế hoạch, Tự lên kế hoạch) ----------

/** One stop of a plan; [lat]/[lon] null for "your starting point" (no fixed place yet). */
/** [memo] is the user's own note for the stop (ticket price, a time to remember…). */
@Serializable data class PlanItem(val title: String, val note: String, val stayMin: Int, val move: Move? = null, val lat: Double? = null, val lon: Double? = null, val memo: String = "")

@Serializable enum class PlanStatus { Upcoming, Draft, Done }

@Serializable data class Plan(val id: String, val name: String, val date: String, val start: String, val mode: String, val items: List<PlanItem>, val status: PlanStatus, val routing: Boolean = true,
    /** Progress when the plan is being followed: stops before [next] are done; [going] = the user is on the way to the next one. */
    val next: Int = 0, val going: Boolean = false,
)

val MOCK_PLANS = listOf(
    Plan(
        "mot-ngay", "Một ngày ở TP.HCM", "Thứ Bảy 21-11", "08:00", "Buýt + đi bộ",
        listOf(
            PlanItem("[Điểm xuất phát của bạn]", "Khách sạn / nhà", 0),
            PlanItem("Ăn sáng: bánh mì", "[Quán mẫu]", 30, null, 10.77310, 106.69840),
            PlanItem("Bưu điện Trung tâm", "Kiến trúc", 45, null, 10.77984, 106.69995),
            PlanItem("Chợ Bến Thành", "Chợ", 40, null, 10.77254, 106.69797),
        ),
        PlanStatus.Upcoming,
    ),
    Plan("hoi-an", "Hội An 2 ngày", "Chưa chọn ngày", "08:00", "Đi bộ", List(5) { PlanItem("[Điểm mẫu ${it + 1}]", "Hội An", 60) }, PlanStatus.Draft, routing = false),
    Plan("chieu-kien-truc", "Một buổi chiều kiến trúc", "11-11-2026", "15:00", "Metro + đi bộ", listOf(PlanItem("Nhà hát Thành phố", "Kiến trúc", 30, null, 10.77667, 106.70305), PlanItem("Bưu điện Trung tâm", "Kiến trúc", 30, null, 10.77984, 106.69995)), PlanStatus.Done),
)

/** Places offered under "Gợi ý thêm" in the planner (sample). */
val MOCK_PLAN_SUGGESTIONS = listOf(
    PlanItem("Nhà hát Thành phố", "Kiến trúc", 25, null, 10.77667, 106.70305),
    PlanItem("Ăn trưa: cơm tấm", "[Quán mẫu]", 40, null, 10.76890, 106.69420),
)
