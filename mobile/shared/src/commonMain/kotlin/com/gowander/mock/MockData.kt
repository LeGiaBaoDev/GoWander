package com.gowander.mock

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
data class Move(val label: String, val kind: MoveKind, val minutes: Int)
enum class MoveKind { Walk, Bus, Metro }

data class Stop(val name: String, val note: String, val lat: Double, val lon: Double, val stayMin: Int, val move: Move? = null)

data class SuggestedTrip(val id: String, val title: String, val summary: String, val minutes: Int, val stops: List<Stop>, val editorsPick: Boolean = false)

private val OPERA = Stop("Nhà hát Thành phố", "Tham quan khoảng 25 phút", 10.77667, 106.70305, 25)
private val POST = Stop("Bưu điện Trung tâm Sài Gòn", "Tham quan khoảng 30 phút", 10.77984, 106.69995, 30, Move("Đi bộ 6 phút", MoveKind.Walk, 6))
private val MARKET = Stop("Chợ Bến Thành", "Tham quan khoảng 20 phút", 10.77254, 106.69797, 20, Move("Metro 1 ga", MoveKind.Metro, 8))

val MOCK_TRIPS = listOf(
    SuggestedTrip("kien-truc", "Kiến trúc quanh ga Nhà Hát", "3 điểm · khoảng 90 phút · đi bộ ít · có metro", 90, listOf(OPERA, POST, MARKET), editorsPick = true),
    SuggestedTrip(
        "ben-thanh", "Một vòng quanh Bến Thành", "2 điểm · khoảng 60 phút · đi bộ vừa", 60,
        listOf(MARKET.copy(move = null), Stop("Bảo tàng Mỹ thuật", "Tham quan khoảng 35 phút", 10.76962, 106.69928, 35, Move("Đi bộ 8 phút", MoveKind.Walk, 8))),
    ),
    SuggestedTrip(
        "sai-gon-xua", "Sài Gòn xưa qua ảnh tư liệu", "4 điểm · khoảng 90 phút · kèm câu chuyện", 90,
        listOf(
            Stop("Dinh Độc Lập", "Tham quan khoảng 30 phút", 10.77703, 106.69531, 30),
            Stop("Nhà thờ Đức Bà", "Tham quan khoảng 15 phút", 10.77980, 106.69902, 15, Move("Đi bộ 7 phút", MoveKind.Walk, 7)),
            POST.copy(move = Move("Đi bộ 2 phút", MoveKind.Walk, 2)),
            Stop("Bảo tàng TP. Hồ Chí Minh", "Tham quan khoảng 30 phút", 10.77607, 106.69998, 30, Move("Đi bộ 6 phút", MoveKind.Walk, 6)),
        ),
    ),
    SuggestedTrip(
        "an-ca-ngay", "Một ngày ăn sáng tới tối", "5 điểm · cả ngày · xe buýt + đi bộ", 600,
        listOf(MARKET.copy(move = null), OPERA.copy(move = Move("Đi bộ 12 phút", MoveKind.Walk, 12)), POST.copy(move = Move("Đi bộ 6 phút", MoveKind.Walk, 6))),
    ),
)

fun mockTrip(id: String) = MOCK_TRIPS.firstOrNull { it.id == id } ?: MOCK_TRIPS.first()

// ---------- Regions (Discover › Điểm đến Việt Nam) ----------

/** [x], [y], [w]: the region's card on the drawn map, in canvas units (393 wide, 574 tall). */
data class Region(val id: String, val name: String, val area: String, val x: Float, val y: Float, val w: Float, val routing: Boolean = false)

val MOCK_REGIONS = listOf(
    Region("ha-noi", "Hà Nội", "Miền Bắc", 12f, 48f, 152f),
    Region("ha-long", "Hạ Long", "Quảng Ninh", 254f, 32f, 130f),
    Region("mien-trung", "Huế · Đà Nẵng · Hội An", "Miền Trung", 70f, 230f, 214f),
    Region("da-lat", "Đà Lạt", "Lâm Đồng", 140f, 378f, 138f),
    Region("tp-hcm", "TP.HCM", "Có tìm tuyến", 244f, 440f, 140f, routing = true),
    Region("phu-quoc", "Phú Quốc", "Kiên Giang", 12f, 476f, 140f),
)

fun mockRegion(id: String) = MOCK_REGIONS.firstOrNull { it.id == id } ?: MOCK_REGIONS.first { it.routing }

data class Dish(val id: String, val name: String, val description: String, val shops: List<Shop>)
data class Shop(val name: String, val rating: Double, val detail: String)

val MOCK_DISHES = listOf(
    Dish(
        "com-tam", "Cơm tấm",
        "[Mô tả mẫu, chờ biên tập viên viết và ghi nguồn.] Món cơm quen thuộc của người Sài Gòn, thường ăn kèm sườn nướng, bì, chả và nước mắm.",
        listOf(Shop("[Quán mẫu 1]", 4.6, "400 m · mở tới 21:00"), Shop("[Quán mẫu 2]", 4.4, "1,2 km · đi buýt 8 phút")),
    ),
    Dish(
        "banh-mi", "Bánh mì",
        "[Mô tả mẫu.] Ổ bánh mì giòn kẹp pa-tê, chả, rau và đồ chua; ăn sáng hay ăn vội đều tiện.",
        listOf(Shop("[Quán mẫu 3]", 4.5, "250 m · mở từ 6:00")),
    ),
    Dish(
        "hu-tieu", "Hủ tiếu",
        "[Mô tả mẫu.] Sợi hủ tiếu dai, nước dùng ngọt xương; có loại nước và loại khô.",
        listOf(Shop("[Quán mẫu 4]", 4.3, "800 m · đi bộ 10 phút")),
    ),
)

fun mockDish(id: String) = MOCK_DISHES.firstOrNull { it.id == id } ?: MOCK_DISHES.first()

const val MOCK_GUIDE_INTRO = "[Đoạn giới thiệu mẫu, chờ biên tập viên viết và ghi nguồn.] Thành phố lớn nhất phía Nam, nơi phố cũ thời Pháp nằm cạnh nhà cao tầng, chợ và quán ăn đường phố."
const val MOCK_OTHER_REGION_INTRO = "[Cẩm nang mẫu.] Nội dung vùng này chưa có. GoWander mới tìm tuyến và dẫn đường ở TP.HCM."
const val MOCK_STORY_TITLE = "Sài Gòn năm 1896 qua ảnh tư liệu"
const val MOCK_STORY_META = "Đọc 4 phút · có ghi nguồn · bài mẫu"

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
data class PlanItem(val title: String, val note: String, val stayMin: Int, val move: Move? = null, val lat: Double? = null, val lon: Double? = null)

enum class PlanStatus { Upcoming, Draft, Done }

data class Plan(val id: String, val name: String, val date: String, val start: String, val mode: String, val items: List<PlanItem>, val status: PlanStatus, val routing: Boolean = true)

val MOCK_PLANS = listOf(
    Plan(
        "mot-ngay", "Một ngày ở TP.HCM", "Thứ Bảy 21-11", "08:00", "Buýt + đi bộ",
        listOf(
            PlanItem("[Điểm xuất phát của bạn]", "Khách sạn / nhà", 0),
            PlanItem("Ăn sáng: bánh mì", "[Quán mẫu] · ở 30 phút", 30, Move("Buýt 18 · 12 phút", MoveKind.Bus, 12), 10.77310, 106.69840),
            PlanItem("Bưu điện Trung tâm", "Kiến trúc · ở 45 phút", 45, Move("Đi bộ 6 phút", MoveKind.Walk, 6), 10.77984, 106.69995),
            PlanItem("Chợ Bến Thành", "Chợ · ở 40 phút", 40, Move("Metro 1 ga · 8 phút", MoveKind.Metro, 8), 10.77254, 106.69797),
        ),
        PlanStatus.Upcoming,
    ),
    Plan("hoi-an", "Hội An 2 ngày", "Chưa chọn ngày", "08:00", "Đi bộ", List(5) { PlanItem("[Điểm mẫu ${it + 1}]", "Hội An", 60) }, PlanStatus.Draft, routing = false),
    Plan("chieu-kien-truc", "Một buổi chiều kiến trúc", "11-11-2026", "15:00", "Metro + đi bộ", listOf(PlanItem("Nhà hát Thành phố", "Kiến trúc", 30, null, 10.77667, 106.70305), PlanItem("Bưu điện Trung tâm", "Kiến trúc", 30, Move("Đi bộ 6 phút", MoveKind.Walk, 6), 10.77984, 106.69995)), PlanStatus.Done),
)

/** Places offered under "Gợi ý thêm" in the planner (sample). */
val MOCK_PLAN_SUGGESTIONS = listOf(
    PlanItem("Nhà hát Thành phố", "Kiến trúc · ở 25 phút", 25, Move("Đi bộ 4 phút", MoveKind.Walk, 4), 10.77667, 106.70305),
    PlanItem("Ăn trưa: cơm tấm", "[Quán mẫu] · ở 40 phút", 40, Move("Buýt · 8 phút", MoveKind.Bus, 8), 10.76890, 106.69420),
)
