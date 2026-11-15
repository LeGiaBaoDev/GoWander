package com.gowander.content

import com.gowander.mock.Move

/*
 * The TP.HCM guide, written by the owner: docs/content/cam-nang-tp-hcm.md (content_version 0.1.0-draft, sources
 * reviewed 15-11-2026). Everything here is copied from that file; nothing is added to it. It is still an editorial
 * draft: no licensed photos, opening hours only where the official site states them, suggested shops not visited.
 * Rules taken from the file: no star ratings set by us, no travel minutes that the router has not confirmed.
 * shortcut: bundled in the app, so a text change needs a release; move it behind the API when the admin (M07) exists.
 */

data class Tip(val title: String, val text: String)

/** [hours] only where the file gives them from the place's own site. */
data class Highlight(val id: String, val name: String, val kind: String, val lat: Double, val lon: Double, val summary: String, val hours: String? = null)

/** A shop the editor suggests for a dish; address only, nothing checked on site yet. */
data class Shop(val name: String, val address: String)

data class Dish(val id: String, val name: String, val summary: String, val description: String, val shops: List<Shop>)

data class Story(val id: String, val title: String, val summary: String, val paragraphs: List<String>, val tryThis: String, val sources: String) {
    /** Reading time from the length of the text (about 200 words a minute). */
    val minutes: Int get() = (paragraphs.sumOf { it.split(' ').size } + 199) / 200
}

/** [stayMin] and [move] stay empty until someone has walked the trip; [minutes] until the router has confirmed it. */
data class Stop(val name: String, val note: String, val lat: Double, val lon: Double, val stayMin: Int? = null, val move: Move? = null)

data class SuggestedTrip(
    val id: String, val title: String, val summary: String, val intro: String, val caution: String,
    val stops: List<Stop>, val minutes: Int? = null, val editorsPick: Boolean = false,
)

const val GUIDE_DRAFT_NOTE = "Nội dung đang được biên tập. Giờ mở cửa, giá và quán gợi ý có thể thay đổi: hãy kiểm tra lại trước khi đi."
const val GUIDE_TITLE = "Sài Gòn, thành phố luôn có điều để khám phá"
const val GUIDE_SUMMARY = "Từ công trình lâu đời đến góc ẩm thực ven đường, mỗi hành trình đều mở ra một câu chuyện."
val GUIDE_INTRO = listOf(
    "TP. Hồ Chí Minh là nơi nhịp sống hiện đại gặp gỡ nhiều lớp ký ức đô thị. Bạn có thể bắt đầu một buổi khám phá ở chợ Bến Thành, tìm hiểu dấu ấn kiến trúc tại Dinh Độc Lập và Bưu điện Thành phố, rồi đi bộ xuống phố Nguyễn Huệ và bờ sông Sài Gòn. Xen giữa những điểm dừng là cơm tấm, bánh mì, hủ tiếu và những quán ăn mang hương vị địa phương.",
    "GoWander gợi ý khám phá thành phố theo cách đơn giản: chọn nơi muốn đến, xem thông tin và câu chuyện, tìm đường phù hợp, lưu lại những điều đáng nhớ. Các lịch trình trong cẩm nang là điểm khởi đầu để bạn tùy chỉnh, không bắt buộc phải đi hết trong một ngày.",
)
const val GUIDE_DRY_SEASON = "Thường là mùa khô; thuận tiện hơn cho những chuyến đi bộ ngoài trời."
const val GUIDE_RAIN_SEASON = "Thường có mưa, nhất là buổi chiều; nên linh hoạt điều chỉnh điểm dừng."
const val GUIDE_ALL_YEAR = "Quanh năm: thành phố có hoạt động tham quan và ẩm thực; kiểm tra thời tiết thực tế trước khi đi."
const val GUIDE_INTRO_SOURCE = "Cục Du lịch Quốc gia Việt Nam (vietnam.travel)"

val GUIDE_TIPS = listOf(
    Tip("Ưu tiên lộ trình rõ ràng", "Xem trước điểm đi, điểm đến và những chặng phải chuyển; đừng chỉ nhìn tổng thời gian."),
    Tip("Không nhầm giờ theo lịch với realtime", "Nếu GoWander ghi “theo lịch”, hãy chủ động kiểm tra thông báo vận hành tại nhà ga."),
    Tip("Mang nước và chuẩn bị chống mưa", "Trời nóng, mưa rào có thể làm hành trình đi bộ thay đổi."),
    Tip("Dùng vị trí thủ công khi cần", "Nếu không cấp quyền GPS, bạn vẫn có thể chọn điểm xuất phát hoặc ghim vị trí trên bản đồ."),
    Tip("Xem nguồn trước khi lên đường", "Giờ mở cửa, giá vé và tình trạng trưng bày có thể thay đổi vào ngày lễ hoặc khi bảo trì."),
)

private val BEN_THANH = Highlight("hcm-ben-thanh-market", "Chợ Bến Thành", "Chợ", 10.77257, 106.69802, "Khu chợ biểu tượng với nhiều gian hàng đặc sản, đồ lưu niệm và món ăn địa phương.")
private val PALACE = Highlight("hcm-independence-palace", "Dinh Độc Lập", "Di tích", 10.77702, 106.69540, "Không gian lịch sử và kiến trúc gắn với những bước ngoặt của Sài Gòn – TP.HCM.", "07:00–18:00 hằng ngày (dinhdoclap.gov.vn)")
private val POST_OFFICE = Highlight("hcm-central-post-office", "Bưu điện Trung tâm Sài Gòn", "Kiến trúc", 10.77998, 106.70002, "Một công trình kiến trúc đặc trưng với không gian sảnh lớn và dấu ấn bưu chính xưa.")
private val OPERA = Highlight("hcm-opera-house", "Nhà hát Thành phố", "Kiến trúc", 10.77670, 106.70322, "Công trình kiến trúc nổi bật ở trung tâm, gần khu phố đi bộ Nguyễn Huệ.")
private val FINE_ARTS = Highlight("hcm-fine-arts-museum", "Bảo tàng Mỹ thuật TP.HCM", "Bảo tàng", 10.76965, 106.69925, "Nơi tìm hiểu mỹ thuật Việt Nam trong một không gian kiến trúc giàu cá tính.", "08:00–17:00, thứ Hai đến Chủ nhật (website bảo tàng)")
private val WAR_MUSEUM = Highlight("hcm-war-remnants-museum", "Bảo tàng Chứng tích Chiến tranh", "Bảo tàng", 10.77938, 106.69219, "Không gian trưng bày về chiến tranh và những hệ quả đối với con người.", "07:30–17:30 hằng ngày, nhận khách đến 17:00 (quy định của bảo tàng từ 01-10-2026)")
private val NGUYEN_HUE = Highlight("hcm-nguyen-hue-walking-street", "Phố đi bộ Nguyễn Huệ", "Phố đi bộ", 10.77411, 106.70362, "Tuyến phố rộng nối khu vực trung tâm với hướng bờ sông, thích hợp đi dạo và quan sát đô thị.")
private val BACH_DANG = Highlight("hcm-bach-dang-waterfront", "Khu vực Bến Bạch Đằng", "Bờ sông", 10.77527, 106.70690, "Một góc nhìn thoáng ra sông Sài Gòn và nhịp sống ven sông của trung tâm thành phố.")

val GUIDE_HIGHLIGHTS = listOf(BEN_THANH, PALACE, POST_OFFICE, OPERA, FINE_ARTS, WAR_MUSEUM, NGUYEN_HUE, BACH_DANG)

val GUIDE_DISHES = listOf(
    Dish(
        "hcm-food-com-tam", "Cơm tấm sườn nướng", "Cơm tấm ăn cùng sườn nướng, đồ chua và nước mắm pha là một hương vị quen thuộc của Sài Gòn.",
        "Cơm tấm được làm từ hạt gạo tấm; thường ăn với sườn nướng, bì, chả hoặc trứng tùy cách gọi món. Điểm hấp dẫn nằm ở sự kết hợp giữa hương thơm của sườn, đồ chua và nước mắm pha. Món này phù hợp cho cả bữa sáng lẫn bữa chính.",
        listOf(Shop("Cơm tấm Ba Ghiền", "84 Đặng Văn Ngữ, khu Phú Nhuận")),
    ),
    Dish(
        "hcm-food-banh-mi", "Bánh mì Sài Gòn", "Ổ bánh giòn với nhiều kiểu nhân, rau và nước sốt: lựa chọn nhanh cho một buổi khám phá.",
        "Bánh mì là món ăn tiện mang theo khi di chuyển; nhân có thể gồm thịt, chả, pa-tê, rau, đồ chua và ớt. Mỗi quán có phong cách riêng, vì vậy người dùng có thể chọn loại nhân phù hợp khẩu vị. Nếu không ăn cay, hãy nhờ quán bỏ ớt khi gọi.",
        listOf(Shop("Bánh mì Huỳnh Hoa", "26 Lê Thị Riêng, khu Bến Thành")),
    ),
    Dish(
        "hcm-food-hu-tieu", "Hủ tiếu Nam Vang", "Tô hủ tiếu với nước dùng thanh, tôm, thịt và rau ăn kèm; cũng có phiên bản khô.",
        "Hủ tiếu Nam Vang là một biến thể quen thuộc trong ẩm thực TP.HCM, thường có tôm, thịt và các loại rau tùy quán. Người ăn có thể chọn hủ tiếu nước hoặc hủ tiếu khô; với bản khô, nước dùng thường được phục vụ riêng. Nếu có chế độ ăn đặc biệt, hãy hỏi quán trước khi gọi.",
        listOf(Shop("Hủ tiếu Ba Hoàng", "56 Võ Văn Tần, khu trung tâm")),
    ),
    Dish(
        "hcm-food-goi-cuon", "Gỏi cuốn", "Món cuốn mát với bún, rau, thịt và tôm, thường chấm cùng nước sốt đậm vị.",
        "Gỏi cuốn kết hợp các nguyên liệu tươi, thường gồm bánh tráng, bún, rau thơm, thịt và tôm. Phần nước chấm thay đổi theo hàng quán. Đây là lựa chọn nhẹ nhàng giữa các điểm tham quan; người dị ứng tôm, đậu phộng hoặc thành phần trong sốt cần hỏi rõ quán.",
        listOf(Shop("Gỏi cuốn Minh", "84/33 Bùi Viện")),
    ),
    Dish(
        "hcm-food-banh-xeo", "Bánh xèo", "Chiếc bánh vàng giòn, thường ăn cùng rau sống và nước chấm.",
        "Bánh xèo là món bánh mặn có lớp vỏ giòn, nhân có thể gồm tôm, thịt và giá. Một cách thưởng thức phổ biến là cuốn phần bánh với rau rồi chấm nước mắm pha. Khẩu phần và nguyên liệu thay đổi theo quán; nên hỏi trước về hải sản hoặc thành phần gây dị ứng.",
        listOf(Shop("Bánh xèo 46A", "46A Đinh Công Tráng, khu Tân Định")),
    ),
    Dish(
        "hcm-food-oc-hai-san", "Ốc và hải sản kiểu Sài Gòn", "Một trải nghiệm ẩm thực buổi tối với nhiều cách chế biến như hấp, nướng và xào.",
        "Các quán ốc thường có nhiều lựa chọn hải sản và cách chế biến khác nhau. Khu Vĩnh Khánh được nhắc đến trong các cẩm nang ẩm thực như một khu vực ăn uống sôi động. Hãy hỏi giá theo phần hoặc cân trước khi gọi, và kiểm tra kỹ nguyên liệu nếu có dị ứng hải sản.",
        listOf(Shop("Ốc Vũ", "37 Vĩnh Khánh, khu Quận 4 trước đây")),
    ),
)
const val GUIDE_FOOD_SOURCE = "Cục Du lịch Quốc gia Việt Nam (vietnam.travel)"
const val GUIDE_SHOP_NOTE = "Quán do biên tập gợi ý, chưa kiểm tra tại chỗ. Hãy hỏi giá và giờ mở cửa trước khi đi; người dị ứng thực phẩm nên hỏi quán về thành phần."

fun guideDish(id: String) = GUIDE_DISHES.firstOrNull { it.id == id } ?: GUIDE_DISHES.first()

val GUIDE_STORIES = listOf(
    Story(
        "hcm-story-architecture", "Giữa những lớp kiến trúc của Sài Gòn", "Một buổi đi bộ trung tâm có thể đưa bạn qua nhiều dấu ấn kiến trúc khác nhau.",
        listOf(
            "Khi đi qua trung tâm TP.HCM, bạn có thể nhận ra thành phố không mang một diện mạo duy nhất. Có những công trình ghi dấu một thời kỳ phát triển đô thị, trong khi đường phố và những không gian công cộng xung quanh tiếp tục thay đổi theo nhịp sống hôm nay.",
            "Bưu điện Trung tâm Sài Gòn dễ nhận ra bởi không gian sảnh rộng và những chi tiết kiến trúc gợi nhắc lịch sử giao thông liên lạc. Nhà hát Thành phố nổi bật ở mặt tiền và vai trò như một điểm hẹn văn hóa. Dinh Độc Lập lại đưa người tham quan vào các không gian trưng bày gắn với lịch sử hiện đại.",
            "Thay vì ghé qua để chụp một tấm ảnh rồi rời đi, hãy chọn một chi tiết để quan sát kỹ hơn: cửa sổ, hành lang, bản đồ hay cách ánh sáng đi vào công trình. Mỗi chi tiết có thể là điểm bắt đầu cho một câu hỏi về đời sống của những người từng sử dụng không gian ấy.",
        ),
        "Chọn một công trình, đọc bảng giới thiệu tại chỗ, sau đó lưu một ghi chú ngắn về điều khiến bạn chú ý.",
        "Cục Du lịch Quốc gia Việt Nam (vietnam.travel); Dinh Độc Lập (dinhdoclap.gov.vn)",
    ),
    Story(
        "hcm-story-food-culture", "Câu chuyện của những bữa ăn địa phương", "Ẩm thực Sài Gòn mang nhiều dấu ấn giao thoa qua những món ăn quen thuộc.",
        listOf(
            "Một cách gần gũi để tìm hiểu TP.HCM là bắt đầu bằng một món ăn. Cơm tấm, hủ tiếu và bánh mì xuất hiện ở nhiều khu phố, từ hàng quán nhỏ đến những địa điểm được giới thiệu trong cẩm nang du lịch. Mỗi món lại có nhiều cách chế biến và phục vụ, tùy vùng miền, gia đình và thói quen của người bán.",
            "Cơm tấm gợi nhớ tới bữa ăn quen thuộc với sườn nướng và nước mắm pha. Hủ tiếu thể hiện sự đa dạng trong cách kết hợp sợi, nước dùng và phần ăn kèm. Bánh mì lại thuận tiện cho người muốn dùng một bữa nhanh trước khi tiếp tục hành trình.",
            "Thưởng thức ẩm thực địa phương không nhất thiết phải tìm quán nổi tiếng nhất. Hãy thử quan sát cách mọi người gọi món, hỏi rõ nguyên liệu và dành thời gian cảm nhận hương vị. Đối với du khách quốc tế, việc ghi kèm tên món bằng tiếng Việt sẽ hữu ích khi gọi món hoặc hỏi đường.",
        ),
        "Lưu một món muốn thử vào danh sách cá nhân, tìm một quán phù hợp với khu vực đang đi và tự ghi lại cảm nhận.",
        "Cục Du lịch Quốc gia Việt Nam (vietnam.travel)",
    ),
    Story(
        "hcm-story-river-city", "Thành phố nhìn từ bờ sông", "Bờ sông là nơi dễ cảm nhận sự giao thoa giữa những tuyến phố cũ và đô thị mới.",
        listOf(
            "Từ phố Nguyễn Huệ, người đi bộ có thể tiếp tục về hướng Bến Bạch Đằng để nhìn thành phố từ phía sông Sài Gòn. Trên đoạn đường này, không gian đô thị thay đổi dần: từ trục phố rộng, các hàng quán và công trình trung tâm đến cảnh quan ven sông thoáng đãng hơn.",
            "Khu vực bờ sông phù hợp để nghỉ chân, quan sát những phương tiện di chuyển trên mặt nước và cảm nhận nhịp sinh hoạt vào những thời điểm khác nhau trong ngày. Khi trời nắng nóng hoặc có mưa, kế hoạch dạo bộ cũng nên linh hoạt: dừng ở một địa điểm trong nhà, chọn thời điểm mát hơn hoặc rút ngắn hành trình.",
            "Một trải nghiệm khám phá không nhất thiết phải gồm thật nhiều điểm dừng. Đôi khi chỉ cần chọn một góc nhìn, lắng nghe âm thanh thành phố và ghi lại vài dòng cảm nhận là đủ tạo nên kỷ niệm riêng.",
        ),
        "Chụp một tấm ảnh do bạn sở hữu và ghi lại cảm nhận về thành phố; không bắt buộc chia sẻ công khai vị trí hoặc thời gian đã ghé thăm.",
        "Cục Du lịch Quốc gia Việt Nam (vietnam.travel); Wikidata (Bạch Đằng Quay)",
    ),
)

private fun Highlight.stop(note: String) = Stop(name, note, lat, lon)

/** J03 (Metro số 1 + bờ sông) of the file is left out: the file marks it not to be published until licensed transit data exists. */
val GUIDE_TRIPS = listOf(
    SuggestedTrip(
        "hcm-trip-central-heritage", "Dấu ấn Sài Gòn ở trung tâm", "4 điểm · Kiến trúc · Lịch sử · Đi bộ",
        "Khám phá những địa danh tiêu biểu qua một hành trình dạo bộ trong lõi trung tâm.",
        "Kiểm tra thời gian vào cổng Dinh Độc Lập; Nhà hát không phải điểm tự do tham quan bên trong.",
        listOf(
            BEN_THANH.stop("Khởi đầu với nhịp sống khu chợ và một điểm mốc quen thuộc."),
            PALACE.stop("Tìm hiểu di tích lịch sử và các không gian trưng bày."),
            POST_OFFICE.stop("Khám phá chi tiết kiến trúc và ký ức bưu chính."),
            OPERA.stop("Quan sát mặt tiền nhà hát và khu phố văn hóa xung quanh."),
        ),
        editorsPick = true,
    ),
    SuggestedTrip(
        "hcm-trip-art-and-river", "Nghệ thuật, chợ và nhịp sống ven sông", "4 điểm · Nghệ thuật · Đời sống đô thị · Ven sông",
        "Từ không gian mỹ thuật đến khu chợ và những góc nhìn về sông Sài Gòn.",
        "Không giả định mọi góc bờ sông đều có lối tiếp cận; chú ý giờ hoạt động của bảo tàng.",
        listOf(
            FINE_ARTS.stop("Khám phá trưng bày và kiến trúc bảo tàng."),
            BEN_THANH.stop("Quan sát sinh hoạt buôn bán, chọn một món ăn nhẹ."),
            NGUYEN_HUE.stop("Dạo tuyến phố rộng ở khu trung tâm."),
            BACH_DANG.stop("Nghỉ chân, ngắm cảnh quan thành phố bên sông."),
        ),
    ),
)
const val GUIDE_TRIPS_NOTE = "Thứ tự điểm dừng do biên tập gợi ý. Thời gian đi giữa các điểm chưa được kiểm chứng: bấm Bắt đầu chuyến để tìm đường thật tới điểm đầu tiên."

fun guideTrip(id: String) = GUIDE_TRIPS.firstOrNull { it.id == id } ?: GUIDE_TRIPS.first()
