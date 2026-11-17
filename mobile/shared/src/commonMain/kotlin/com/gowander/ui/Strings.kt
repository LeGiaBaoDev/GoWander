package com.gowander.ui

/** Every user-facing text of M06 in one place, so English can be added later (ADR-022, ADR-029 §4). */
object Strings {
    /** Vietnamese text plus its English translation; unknown keys fall back to Vietnamese. */
    private fun t(key: String, vi: String) = if (Language.current == Lang.EN) EN[key] ?: vi else vi

    val TAB_MAP get() = t("TAB_MAP", "Bản đồ")
    val TAB_DISCOVER get() = t("TAB_DISCOVER", "Khám phá")
    val TAB_GO get() = t("TAB_GO", "Đi đâu?")
    val RECENT_TRIPS get() = t("RECENT_TRIPS", "Nơi bạn đã tìm đường")
    val RECENT_TRIPS_EMPTY get() = t("RECENT_TRIPS_EMPTY", "Các nơi bạn tìm đường trong lần dùng này sẽ hiện ở đây.")
    val ROUTE_AGAIN get() = t("ROUTE_AGAIN", "Xem lại tuyến đường")
    val GUEST get() = t("GUEST", "Bạn đang dùng chế độ khách")
    val SIGNED_IN get() = t("SIGNED_IN", "Bạn đã đăng nhập")
    val GUEST_NOTE get() = t("GUEST_NOTE", "Không cần tài khoản. Mọi thứ chỉ lưu trên máy này.")
    val DATA_SOURCES get() = t("DATA_SOURCES", "Nguồn dữ liệu và giấy phép")
    val SOURCES get() = if (Language.current == Lang.EN) listOf(
        "Base map" to "© OpenStreetMap contributors (ODbL 1.0) · OpenMapTiles · OpenFreeMap",
        "Bus routes" to "Estimated from OpenStreetMap (ODbL), not an official timetable",
        "Metro Line 1" to "Estimated timetable, not official",
        "Places" to "OpenStreetMap (ODbL 1.0)",
        "Address search" to "Photon, using OpenStreetMap data",
        "Street photos" to "Mapillary (CC BY-SA 4.0), photographer credited on each photo",
        "Weather" to "Open-Meteo (CC BY 4.0), hourly forecast",
    ) else listOf(
        "Bản đồ nền" to "© OpenStreetMap contributors (ODbL 1.0) · OpenMapTiles · OpenFreeMap",
        "Tuyến xe buýt" to "Ước tính từ dữ liệu OpenStreetMap (ODbL), chưa phải lịch chính thức",
        "Metro số 1" to "Lịch ước tính, chưa phải lịch chính thức",
        "Địa điểm" to "OpenStreetMap (ODbL 1.0)",
        "Tìm địa chỉ" to "Photon, dùng dữ liệu OpenStreetMap",
        "Ảnh đường phố" to "Mapillary (CC BY-SA 4.0), ghi tên người chụp trên từng ảnh",
        "Thời tiết" to "Open-Meteo (CC BY 4.0), dự báo theo giờ",
    )
    val TAB_JOURNEY get() = t("TAB_JOURNEY", "Hành trình")
    val TAB_PROFILE get() = t("TAB_PROFILE", "Hồ sơ")
    val ORIGIN get() = t("ORIGIN", "Điểm đi")
    val DESTINATION get() = t("DESTINATION", "Điểm đến")
    val MY_LOCATION get() = t("MY_LOCATION", "Vị trí của bạn")
    val PICKED_ON_MAP get() = t("PICKED_ON_MAP", "Điểm chọn trên bản đồ")
    val NOT_SET get() = t("NOT_SET", "Chưa chọn")
    val SET_ORIGIN_MODE get() = t("SET_ORIGIN_MODE", "Nhấn giữ để đặt điểm đi")
    val SET_DESTINATION_MODE get() = t("SET_DESTINATION_MODE", "Nhấn giữ để đặt điểm đến")
    val USE_MY_LOCATION get() = t("USE_MY_LOCATION", "Dùng vị trí của tôi")
    val SWAP get() = t("SWAP", "Đổi chiều")
    val FIND_ROUTE get() = t("FIND_ROUTE", "Tìm tuyến")
    val SEARCHING get() = t("SEARCHING", "Đang tìm tuyến…")
    val RETRY get() = t("RETRY", "Thử lại")
    val NEED_ORIGIN get() = t("NEED_ORIGIN", "Chưa có điểm đi. Dùng vị trí của bạn hoặc chọn điểm đi trên bản đồ.")
    val STEP_BY_STEP get() = t("STEP_BY_STEP", "Xem từng bước")
    val FROM get() = t("FROM", "Từ")
    val CHANGE get() = t("CHANGE", "Đổi")
    val STREET_VIEW_3D get() = t("STREET_VIEW_3D", "Góc nhìn trên đường · 3D")
    val LATER_TRIPS get() = t("LATER_TRIPS", "Chuyến sau:")
    val METRO_WITH_BUS get() = t("METRO_WITH_BUS", "Gồm cả tuyến đi Metro kết hợp xe buýt.")
    val NEED_BOTH get() = t("NEED_BOTH", "Chọn điểm đi và điểm đến để tìm tuyến")
    val NO_ROUTE get() = t("NO_ROUTE", "Không tìm thấy tuyến phù hợp.")
    val NETWORK_ERROR get() = t("NETWORK_ERROR", "Không kết nối được máy chủ GoWander. Kiểm tra mạng rồi thử lại.")
    val UNEXPECTED_ERROR get() = t("UNEXPECTED_ERROR", "Có lỗi không mong muốn, vui lòng thử lại.")
    val SCHEDULED get() = t("SCHEDULED", "Theo lịch")
    val WALK get() = t("WALK", "Đi bộ")
    val TRANSFERS get() = t("TRANSFERS", "chuyển tuyến")
    val DIRECTIONS get() = t("DIRECTIONS", "Chỉ đường")
    val SEARCH_PLACES get() = t("SEARCH_PLACES", "Tìm địa điểm")
    val SEARCH_MIN get() = t("SEARCH_MIN", "Nhập ít nhất 2 ký tự")
    val ALL get() = t("ALL", "Tất cả")
    val LOAD_MORE get() = t("LOAD_MORE", "Xem thêm")
    val LOADING get() = t("LOADING", "Đang tải…")
    val EMPTY_PLACES get() = t("EMPTY_PLACES", "Không có địa điểm phù hợp.")
    val BACK get() = t("BACK", "Quay lại")
    val SOURCE get() = t("SOURCE", "Nguồn")
    val LICENSE get() = t("LICENSE", "Giấy phép")
    val ADDRESS get() = t("ADDRESS", "Địa chỉ")
    val NO_DESCRIPTION get() = t("NO_DESCRIPTION", "Chưa có mô tả cho địa điểm này.")
    val SELECTED get() = t("SELECTED", "Đang chọn")
    val TOWARDS get() = t("TOWARDS", "hướng")
    val OPEN_LOCATION_SETTINGS get() = t("OPEN_LOCATION_SETTINGS", "Mở Cài đặt vị trí")
    val RETRY_GPS get() = t("RETRY_GPS", "Thử lại GPS")
    val SEARCH get() = t("SEARCH", "Tìm")
    val TAB_DISCOVER_TITLE get() = t("TAB_DISCOVER_TITLE", "Khám phá")
    val SEARCH_PLACES_HINT get() = t("SEARCH_PLACES_HINT", "Tìm địa điểm, ga metro")
    val NEAR_PIN get() = t("NEAR_PIN", "Gần điểm ghim — chọn để chính xác hơn")
    val EXACT_PIN get() = t("EXACT_PIN", "Đúng điểm ghim")
    val APPROXIMATE get() = t("APPROXIMATE", "Vị trí gần đúng: không tìm thấy số nhà")
    val OSM_ATTRIBUTION get() = t("OSM_ATTRIBUTION", "Địa chỉ: © OpenStreetMap contributors (ODbL)")
    val WHERE_TO get() = t("WHERE_TO", "Bạn muốn đến đâu?")
    val NEAR_YOU get() = t("NEAR_YOU", "Gợi ý gần bạn")
    val SEE_ALL get() = t("SEE_ALL", "Xem tất cả")
    val SEARCH_DESTINATION get() = t("SEARCH_DESTINATION", "Tìm điểm đến")
    val PICK_ON_MAP get() = t("PICK_ON_MAP", "Chọn trên bản đồ")
    val CONFIRM_DESTINATION get() = t("CONFIRM_DESTINATION", "Xác nhận điểm đến")
    val CONFIRM_ORIGIN get() = t("CONFIRM_ORIGIN", "Xác nhận điểm đi")
    val LEAVE_NOW get() = t("LEAVE_NOW", "Khởi hành ngay")
    val ARRIVE_BY get() = t("ARRIVE_BY", "Đến trước lúc")
    val RECENT get() = t("RECENT", "Gần đây")
    val FASTEST get() = t("FASTEST", "Nhanh nhất")
    val LEAST_WALKING get() = t("LEAST_WALKING", "Ít đi bộ")
    val ROUTE_DETAIL get() = t("ROUTE_DETAIL", "Chi tiết tuyến")
    val ASSUMED_NOTE get() = t("ASSUMED_NOTE", "Giờ Metro số 1 và xe buýt là ước tính (xe buýt lấy từ OpenStreetMap), chưa phải lịch chính thức.")
    val MODE_TRANSIT get() = t("MODE_TRANSIT", "Công cộng")
    val MODE_BUS get() = t("MODE_BUS", "Buýt")
    val WALK_TOTAL get() = t("WALK_TOTAL", "Đi bộ tổng")
    val MINUTES_SHORT get() = t("MINUTES_SHORT", "phút")
    val GO_FORWARD get() = t("GO_FORWARD", "Tiến lên 20 m")
    val GO_BACK get() = t("GO_BACK", "Lùi lại 20 m")
    val PLAY_PREVIEW get() = t("PLAY_PREVIEW", "Tự chạy xem trước toàn tuyến")
    val PAUSE_PREVIEW get() = t("PAUSE_PREVIEW", "Tạm dừng xem trước")
    val ABOUT_PLACE get() = t("ABOUT_PLACE", "Thông tin về")
    val ABOUT_PLACE_HINT get() = t("ABOUT_PLACE_HINT", "Mô tả, ảnh, địa chỉ và nguồn")
    val MODE_METRO get() = t("MODE_METRO", "Metro")
    val NO_METRO_ROUTE get() = t("NO_METRO_ROUTE", "Không có tuyến đi Metro cho chuyến này. Hãy thử tab Buýt.")
    val MODE_BICYCLE get() = t("MODE_BICYCLE", "Xe đạp")
    val MODE_CAR get() = t("MODE_CAR", "Ô tô")
    val MODE_WALK get() = t("MODE_WALK", "Đi bộ")
    val UNNAMED_STREET get() = t("UNNAMED_STREET", "đường không tên")
    val ORIGIN_ON_MAP get() = t("ORIGIN_ON_MAP", "Chọn điểm đi trên bản đồ")
    val DESTINATION_ON_MAP get() = t("DESTINATION_ON_MAP", "Chọn điểm đến trên bản đồ")
    val BUS get() = t("BUS", "Buýt")
    val CYCLE get() = t("CYCLE", "Đạp xe")
    val DRIVE get() = t("DRIVE", "Lái xe")
    val CAR_NOTE get() = t("CAR_NOTE", "Thời gian ô tô chưa tính kẹt xe.")
    val NO_ROUTE_TITLE get() = t("NO_ROUTE_TITLE", "Chưa tìm được tuyến cho hành trình này")
    val CHANGE_DESTINATION get() = t("CHANGE_DESTINATION", "Đổi điểm đến")
    val OFFLINE get() = t("OFFLINE", "Không có kết nối mạng")
    val STATION get() = t("STATION", "Ga")
    val PLACE get() = t("PLACE", "Địa điểm")
    val NEAR_STATION get() = t("NEAR_STATION", "Gần ga")
    val NEAR get() = t("NEAR", "Gần")
    val START get() = t("START", "Bắt đầu")
    val PREVIEW get() = t("PREVIEW", "Xem trước")
    val STEPS get() = t("STEPS", "Đường đi")
    val STEPS_HINT get() = t("STEPS_HINT", "Chạm vào một bước để xem trước chỗ đó.")
    val EXIT get() = t("EXIT", "Thoát")
    val ARRIVED get() = t("ARRIVED", "Đã đến")
    val WAITING_GPS get() = t("WAITING_GPS", "Đang chờ tín hiệu GPS…")
    val OFF_ROUTE get() = t("OFF_ROUTE", "Bạn đang đi lệch khỏi tuyến.")
    val WRONG_WAY_TITLE get() = t("WRONG_WAY_TITLE", "Bạn đang đi sai tuyến")
    val WRONG_WAY_BODY get() = t("WRONG_WAY_BODY", "Có thể bạn đang đi ngược chiều hoặc lệch khỏi tuyến. Hãy quay lại hoặc đi theo chỉ dẫn.")
    val WRONG_WAY_SPOKEN get() = t("WRONG_WAY_SPOKEN", "Bạn đang đi sai tuyến hoặc ngược chiều. Hãy quay lại.")
    val WRONG_WAY_DISMISS get() = t("WRONG_WAY_DISMISS", "Tắt cảnh báo")
    val REROUTE get() = t("REROUTE", "Tìm lại tuyến từ vị trí này")
    val STEP get() = t("STEP", "Bước")
    val PREVIOUS get() = t("PREVIOUS", "Trước")
    val NEXT get() = t("NEXT", "Sau")
    val BACK_TO_ROUTE get() = t("BACK_TO_ROUTE", "Quay lại tuyến đường")
    val STREET_PHOTO get() = t("STREET_PHOTO", "Ảnh đường phố")
    val NO_STREET_PHOTO get() = t("NO_STREET_PHOTO", "Chưa có ảnh đường phố ở đây: đang hiện bản đồ 3D")
    val EXPAND get() = t("EXPAND", "Mở rộng")
    val MAP_VIEW get() = t("MAP_VIEW", "Loại bản đồ")
    val MAP_CREDIT get() = t("MAP_CREDIT", "© OpenStreetMap · OpenMapTiles · OpenFreeMap")
    val STREET_PHOTO_CREDIT get() = t("STREET_PHOTO_CREDIT", "Ảnh đường phố: Mapillary, CC BY-SA 4.0")
    val PHOTO_SOURCES get() = t("PHOTO_SOURCES", "Nguồn ảnh")
    val EAST_SEA get() = t("EAST_SEA", "Biển\nĐông")
    val APP_NAME get() = t("APP_NAME", "GoWander")
    val TAGLINE get() = t("TAGLINE", "Move Smart. Explore More.")
    val CHOOSE_LANGUAGE get() = t("CHOOSE_LANGUAGE", "Chọn ngôn ngữ")
    val VIETNAMESE get() = t("VIETNAMESE", "Tiếng Việt")
    val ENGLISH get() = t("ENGLISH", "English")
    val ENGLISH_SOON get() = t("ENGLISH_SOON", "English sắp có; hiện app dùng tiếng Việt.")
    val MORE_LANGUAGES_LATER get() = t("MORE_LANGUAGES_LATER", "Tiếng Anh, tiếng Trung và tiếng Hàn sẽ có sau.")
    val LOCATION_QUESTION get() = t("LOCATION_QUESTION", "Cho phép GoWander biết vị trí của bạn?")
    val LOCATION_WHY get() = t("LOCATION_WHY", "Vị trí giúp tìm tuyến từ chỗ bạn đứng và gợi ý địa điểm gần đó. GoWander không ghi lại lộ trình của bạn.")
    val CONTINUE get() = t("CONTINUE", "Tiếp tục")
    val LATER_TYPE_ORIGIN get() = t("LATER_TYPE_ORIGIN", "Để sau, tôi sẽ nhập điểm đi")
    val CLOSE_PANEL get() = t("CLOSE_PANEL", "Đóng bảng chọn")
    val RECENTER get() = t("RECENTER", "Căn giữa lại")
    val SHOW_NEARBY get() = t("SHOW_NEARBY", "Hiện gợi ý gần bạn")
    val HIDE_NEARBY get() = t("HIDE_NEARBY", "Thu gọn gợi ý")
    val VIEW_2D get() = t("VIEW_2D", "2D")
    val VIEW_3D get() = t("VIEW_3D", "3D")
    val SOON get() = t("SOON", "Sắp có")
    val CLOSE get() = t("CLOSE", "Đóng")
    val VIEW_SATELLITE get() = t("VIEW_SATELLITE", "Vệ tinh")
    val SATELLITE_PENDING get() = t("SATELLITE_PENDING", "Chưa có ảnh vệ tinh: đang chọn nhà cung cấp được phép dùng.")
    val SATELLITE_CREDIT get() = t("SATELLITE_CREDIT", "Ảnh vệ tinh: Esri, Maxar, Earthstar Geographics")
    val COLLAPSE get() = t("COLLAPSE", "Thu gọn")
    val WAIT get() = t("WAIT", "Chờ tàu khoảng")
    val LEAVE_AT get() = t("LEAVE_AT", "Rời đi lúc")
    val ARRIVE_ABOUT get() = t("ARRIVE_ABOUT", "đến khoảng")
    val NO_TRANSFER get() = t("NO_TRANSFER", "0 lần chuyển")
    val LANGUAGE_VI get() = t("LANGUAGE_VI", "VI")
    val LANGUAGE_EN get() = t("LANGUAGE_EN", "EN")
    val LANGUAGE get() = t("LANGUAGE", "Ngôn ngữ")
    val PREFERENCES get() = t("PREFERENCES", "Tùy chọn")
    val LANGUAGE_VI_LABEL get() = t("LANGUAGE_VI_LABEL", "Tiếng Việt")
    val LANGUAGE_EN_LABEL get() = t("LANGUAGE_EN_LABEL", "English")
    val LANGUAGE_NOTE get() = t("LANGUAGE_NOTE", "Áp dụng cho toàn bộ giao diện. Tên địa điểm và mô tả sẽ hiện tiếng Anh khi có bản dịch.")
    val LANGUAGE_ADD_LATER get() = t("LANGUAGE_ADD_LATER", "Trung và Đức cần bản dịch trước khi bật.")
    val LANGUAGE_MENU get() = t("LANGUAGE_MENU", "Ngôn ngữ: Tiếng Việt")
    val RESULTS get() = t("RESULTS", "Kết quả")
    val GO_NOW get() = t("GO_NOW", "Đi ngay")
    val TO_PLACE get() = t("TO_PLACE", "đến")
    val ARRIVE_AT get() = t("ARRIVE_AT", "Đến")
    val RUN_PREVIEW get() = t("RUN_PREVIEW", "Chạy")
    val PAUSE get() = t("PAUSE", "Dừng")
    val REMAINING get() = t("REMAINING", "còn")
    val LOWER_MAP_VIEW get() = t("LOWER_MAP_VIEW", "Kiểu bản đồ phía dưới")
    val PARTIAL_DRAW get() = t("PARTIAL_DRAW", "Không vẽ được một phần tuyến trên bản đồ; danh sách chặng bên dưới vẫn đúng.")

    // ---------- UX-2 canvas screens 1, 2, 8-17 ----------
    val FIND_PLACE get() = t("FIND_PLACE", "Tìm quán, món ăn, địa điểm")
    val LIST get() = t("LIST", "Danh sách")
    val SAMPLE_STARS get() = t("SAMPLE_STARS", "số sao mẫu")
    val SAMPLE_REVIEWS get() = t("SAMPLE_REVIEWS", "đánh giá mẫu")
    val SAMPLE_USER get() = t("SAMPLE_USER", "[Người dùng mẫu]")
    val SAMPLE_PHOTOS get() = t("SAMPLE_PHOTOS", "Ảnh mẫu: chưa có ảnh được phép dùng cho nơi này.")
    fun photoCredit(credit: String, license: String) = "© $credit · $license"
    val SAMPLE_PHOTO_CREDIT get() = t("SAMPLE_PHOTO_CREDIT", "Ảnh mẫu")
    val SAMPLE_USER_PHOTO get() = t("SAMPLE_USER_PHOTO", "[Người dùng] · ảnh mẫu")
    val SAMPLE_DISH_PHOTO get() = t("SAMPLE_DISH_PHOTO", "[Ảnh món mẫu]")
    val TIMES get() = t("TIMES", "lượt")
    val SAVE_PLACE get() = t("SAVE_PLACE", "Lưu địa điểm")
    val UNSAVE_PLACE get() = t("UNSAVE_PLACE", "Bỏ lưu địa điểm")
    val SHARE get() = t("SHARE", "Chia sẻ")
    val SHARE_PORTFOLIO get() = t("SHARE_PORTFOLIO", "Chia sẻ portfolio")
    val SHARE_LATER get() = t("SHARE_LATER", "Chia sẻ sẽ có khi có tài khoản.")
    val OVERVIEW get() = t("OVERVIEW", "Tổng quan")
    val REVIEWS get() = t("REVIEWS", "Đánh giá")
    val DISHES get() = t("DISHES", "Món nổi bật")
    val NO_DISHES get() = t("NO_DISHES", "Chưa có món nổi bật cho nơi này.")
    val WRITE_REVIEW get() = t("WRITE_REVIEW", "Viết đánh giá")
    val REVIEW_LATER get() = t("REVIEW_LATER", "Viết đánh giá sẽ có khi có tài khoản.")
    val NEAR_YOU_TAB get() = t("NEAR_YOU_TAB", "Gần bạn")
    val POPULAR get() = t("POPULAR", "Đang được tìm nhiều")
    val FEEDBACK_SENT get() = t("FEEDBACK_SENT", "Cảm ơn bạn! GoWander đã nhận feedback.")
    val POPULAR_SPAN get() = t("POPULAR_SPAN", "7 ngày qua")
    fun popularBadge(rank: Int) = if (Language.current == Lang.EN) "Top $rank most searched this week" else "Top $rank được tìm nhiều tuần này"
    fun popularCard(rank: Int, name: String, category: String?) = if (Language.current == Lang.EN) "Rank $rank: $name${category?.let { ", $it" } ?: ""}" else "Hạng $rank: $name${category?.let { ", $it" } ?: ""}"
    val VIETNAM_PLACES get() = t("VIETNAM_PLACES", "Điểm đến Việt Nam")
    val SUGGESTED_TRIP get() = t("SUGGESTED_TRIP", "HÀNH TRÌNH GỢI Ý")
    val SHOW_ON_MAP get() = t("SHOW_ON_MAP", "Xem trên bản đồ")
    val ALL_PLACES get() = t("ALL_PLACES", "Tất cả địa điểm")
    val STORIES get() = t("STORIES", "Câu chuyện")
    val FROM_YOU get() = t("FROM_YOU", "từ chỗ bạn")
    val VIETNAM_MAP get() = t("VIETNAM_MAP", "Bản đồ Việt Nam")
    val ISLANDS_NORTH get() = t("ISLANDS_NORTH", "QĐ. Hoàng Sa\n(Việt Nam)")
    val ISLANDS_SOUTH get() = t("ISLANDS_SOUTH", "QĐ. Trường Sa\n(Việt Nam)")
    val OPEN_GUIDE get() = t("OPEN_GUIDE", "xem cẩm nang")
    val GUIDE get() = t("GUIDE", "CẨM NANG")
    val HAS_ROUTING get() = t("HAS_ROUTING", "Có tìm tuyến · dẫn đường")
    val VIDEO get() = t("VIDEO", "Video 1:30")
    val VIDEO_LATER get() = t("VIDEO_LATER", "Video giới thiệu chưa có.")
    val DISH_PICKS get() = t("DISH_PICKS", "GỢI Ý MÓN")
    val SHOP_NEAR_ON_MAP get() = t("SHOP_NEAR_ON_MAP", "Xem quán gần bạn trên bản đồ")
    val STORY_LABEL get() = t("STORY_LABEL", "CÂU CHUYỆN")
    val COMMUNITY_PHOTOS get() = t("COMMUNITY_PHOTOS", "Ảnh từ cộng đồng")
    val COMMUNITY_RULE get() = t("COMMUNITY_RULE", "Ảnh được chọn khi: người đăng đã chọn giấy phép chia sẻ, ảnh đã qua kiểm duyệt, và địa điểm có số sao ổn định.")
    val SUGGESTION get() = t("SUGGESTION", "GỢI Ý")
    val PLAN_YOURSELF get() = t("PLAN_YOURSELF", "Tự lên kế hoạch")
    val GETTING_AROUND get() = t("GETTING_AROUND", "Đi lại ở TP.HCM")
    val GETTING_AROUND_NOTE get() = t("GETTING_AROUND_NOTE", "Metro số 1, xe buýt và đi bộ. GoWander tìm tuyến và dẫn đường cho bạn.")
    val ONE_DAY_PLAN get() = t("ONE_DAY_PLAN", "Lên chuyến 1 ngày")
    val CLOSE_GUIDE_ITEM get() = t("CLOSE_GUIDE_ITEM", "Đóng, quay lại cẩm nang")
    val FOOD_HCMC get() = t("FOOD_HCMC", "ẨM THỰC · TP.HCM")
    val SHOPS_NEAR get() = t("SHOPS_NEAR", "Quán gợi ý")
    val SHOW_CARD get() = t("SHOW_CARD", "hiện thẻ")
    fun clusterLabel(n: Int) = if (Language.current == Lang.EN) "$n places, zoom in" else "$n địa điểm, phóng to"
    val TRAVEL_TIPS get() = t("TRAVEL_TIPS", "Mẹo đi lại")
    val OPENING_HOURS get() = t("OPENING_HOURS", "Giờ mở cửa")
    val TRY_THIS get() = t("TRY_THIS", "Gợi ý trải nghiệm")
    fun readMinutes(n: Int) = if (Language.current == Lang.EN) "$n min read · sources cited" else "Đọc $n phút · có ghi nguồn"
    val ADD_TO_PLAN get() = t("ADD_TO_PLAN", "+ Thêm vào kế hoạch")
    val SUGGESTIONS get() = t("SUGGESTIONS", "Gợi ý")
    val MINE get() = t("MINE", "Của tôi")
    val PLAN_MAP get() = t("PLAN_MAP", "Bản đồ kế hoạch")
    val VIEW_ON_MAP get() = t("VIEW_ON_MAP", "Xem trên bản đồ")
    val PLAN_MAP_NOTE get() = t("PLAN_MAP_NOTE", "Nét liền: đường đã tính. Nét đứt: chưa tính, nối thẳng giữa hai điểm.")
    val PROPOSED_ORDER get() = t("PROPOSED_ORDER", "Thứ tự gợi ý")
    val USE_ORDER get() = t("USE_ORDER", "Dùng thứ tự này")
    val KEEP_ORDER get() = t("KEEP_ORDER", "Giữ như cũ")
    val ORDER_ALREADY_SHORT get() = t("ORDER_ALREADY_SHORT", "Thứ tự hiện tại đã ít đi vòng nhất.")
    fun shorterBy(km: String) = if (Language.current == Lang.EN) "about $km shorter (straight-line, not road distance)" else "ngắn hơn khoảng $km (đo đường thẳng, chưa tính đường thật)"
    fun stopNumber(n: Int, name: String) = if (Language.current == Lang.EN) "Stop $n: $name" else "Điểm $n: $name"
    val APPEARANCE get() = t("APPEARANCE", "Giao diện")
    val LIGHT get() = t("LIGHT", "Sáng")
    val DARK get() = t("DARK", "Tối")
    val GOING get() = t("GOING", "ĐANG ĐI")
    val OPEN_PLAN get() = t("OPEN_PLAN", "Mở kế hoạch")
    val RECENT_ROUTES get() = t("RECENT_ROUTES", "TUYẾN ĐÃ TÌM GẦN ĐÂY")
    val NO_PLANS get() = t("NO_PLANS", "Chưa có kế hoạch nào.")
    val HOW_LONG get() = t("HOW_LONG", "Bạn có bao lâu?")
    val ANY_TIME get() = t("ANY_TIME", "Không giới hạn")
    val NO_TRIP_FITS get() = t("NO_TRIP_FITS", "Chưa có chuyến gợi ý vừa khoảng thời gian này.")
    val EDITORS_PICK get() = t("EDITORS_PICK", "Biên tập viên chọn")
    val ADD_STOP get() = t("ADD_STOP", "Thêm điểm")
    val START_TRIP get() = t("START_TRIP", "Bắt đầu chuyến")
    val PLAN_NAME get() = t("PLAN_NAME", "Tên kế hoạch")
    val START_AT get() = t("START_AT", "Bắt đầu")
    val TODAY get() = t("TODAY", "Hôm nay")
    val CLEAR_DATE get() = t("CLEAR_DATE", "Bỏ chọn ngày")
    val PREV_MONTH get() = t("PREV_MONTH", "Tháng trước")
    val NEXT_MONTH get() = t("NEXT_MONTH", "Tháng sau")
    val HOUR get() = t("HOUR", "Giờ")
    val MINUTE get() = t("MINUTE", "Phút")
    val WEEK_SHORT get() = if (Language.current == Lang.EN) listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun") else listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")
    fun monthTitle(m: Int, y: Int) = if (Language.current == Lang.EN) "Month $m, $y" else "Tháng $m, $y"
    fun dayLabel(day: Int, month: Int, weekday: String) = "$weekday ${day.toString().padStart(2, '0')}-${month.toString().padStart(2, '0')}"
    val TOMORROW get() = t("TOMORROW", "Ngày mai")
    val NO_DATE get() = t("NO_DATE", "Chưa chọn ngày")
    val PLAN_DAY get() = t("PLAN_DAY", "Ngày đi")
    val PLAN_START get() = t("PLAN_START", "Giờ bắt đầu")
    val PLAN_TRAVEL get() = t("PLAN_TRAVEL", "Đi bằng")
    val BUS_AND_WALK get() = t("BUS_AND_WALK", "Buýt + đi bộ")
    val METRO_AND_WALK get() = t("METRO_AND_WALK", "Metro + đi bộ")
    val EARLIER_15 get() = t("EARLIER_15", "Sớm hơn 15 phút")
    val LATER_15 get() = t("LATER_15", "Muộn hơn 15 phút")
    val DONE_PICKING get() = t("DONE_PICKING", "Xong")
    val TIMES_FOR_DAY get() = t("TIMES_FOR_DAY", "Thời gian đi tính theo lịch xe của ngày đã chọn.")
    val WEEKDAYS get() = if (Language.current == Lang.EN) listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday") else listOf("Chủ nhật", "Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy")
    val PLAN_EMPTY get() = t("PLAN_EMPTY", "Chưa có điểm nào. Thêm điểm bằng nút bên dưới.")
    val REMOVE_STOP get() = t("REMOVE_STOP", "Xoá điểm")
    val ADD_STOP_LONG get() = t("ADD_STOP_LONG", "+ Thêm điểm (tìm hoặc chọn trên bản đồ)")
    val MORE_NEARBY get() = t("MORE_NEARBY", "Gợi ý thêm, gần các điểm của bạn")
    val REORDER get() = t("REORDER", "Sắp xếp lại cho đỡ đi vòng")
    val STOPS get() = t("STOPS", "điểm")
    val UNTIL get() = t("UNTIL", "đến")
    val TIMES_ESTIMATED get() = t("TIMES_ESTIMATED", "giờ là ước tính")
    val LEG_PENDING get() = t("LEG_PENDING", "Đang tính thời gian đi…")
    val LEG_UNKNOWN get() = t("LEG_UNKNOWN", "Chưa tính được thời gian đi")
    val NEXT_STOP get() = t("NEXT_STOP", "Điểm kế tiếp")
    val GO_NEXT get() = t("GO_NEXT", "Đi tới điểm kế tiếp")
    val ARRIVED_HERE get() = t("ARRIVED_HERE", "Đã đến nơi")
    val ROUTE_AGAIN_SHORT get() = t("ROUTE_AGAIN_SHORT", "Chỉ đường lại")
    val PLAN_DONE get() = t("PLAN_DONE", "Đã đi hết các điểm của kế hoạch.")
    val PLAN_RESTART get() = t("PLAN_RESTART", "Đi lại từ đầu")
    val ROUTE_HERE get() = t("ROUTE_HERE", "Chỉ đường tới đây")
    val STAY get() = t("STAY", "ở")
    val STAY_LESS get() = t("STAY_LESS", "Ở lại ít hơn 15 phút")
    val STAY_MORE get() = t("STAY_MORE", "Ở lại thêm 15 phút")
    val MEMO get() = t("MEMO", "Ghi chú của bạn")
    val MEMO_HINT get() = t("MEMO_HINT", "Thêm ghi chú: giá vé, giờ hẹn, mang gì…")
    val STOP_DONE get() = t("STOP_DONE", "đã đến")
    val PLAN_WALK get() = t("PLAN_WALK", "đi bộ")
    fun opensAt(arrive: String, open: String) = if (Language.current == Lang.EN) "Arriving at $arrive; this place opens at $open." else "Tới lúc $arrive, nơi này mở cửa từ $open."
    fun closedAt(arrive: String, close: String) = if (Language.current == Lang.EN) "Arriving at $arrive; this place closes at $close." else "Tới lúc $arrive, nơi này đóng cửa lúc $close."
    val SAVE_TO_PROFILE get() = t("SAVE_TO_PROFILE", "Lưu kế hoạch")
    val NO_ROUTING_HERE get() = t("NO_ROUTING_HERE", "Chưa tìm tuyến được cho kế hoạch này (chỉ có cẩm nang).")

    // Trip weather (Open-Meteo through the API). Advice is a suggestion, never a promise about the day.
    val TRIP_WEATHER get() = t("TRIP_WEATHER", "Thời tiết chuyến đi")
    val WEATHER_RAIN_TIP get() = t("WEATHER_RAIN_TIP", "Gợi ý: đổi sang điểm trong nhà hoặc dời giờ khô hơn.")
    val WEATHER_RAIN_ALL_INDOOR get() = t("WEATHER_RAIN_ALL_INDOOR", "Các điểm đều trong nhà, ít bị ảnh hưởng.")
    val WEATHER_HEAT_TIP get() = t("WEATHER_HEAT_TIP", "Gợi ý: đi sớm hơn hoặc nghỉ trong nhà giữa trưa.")
    val WEATHER_HEAT_INDOOR get() = t("WEATHER_HEAT_INDOOR", "Ngoài trời nắng nóng, nhưng các điểm đều trong nhà.")
    val WEATHER_CLEAR_TIP get() = t("WEATHER_CLEAR_TIP", "Thuận tiện cho cả điểm ngoài trời.")
    val WEATHER_EARLIER get() = t("WEATHER_EARLIER", "Sớm 1 giờ")
    val WEATHER_LATER get() = t("WEATHER_LATER", "Muộn 1 giờ")
    val WEATHER_SOURCE get() = t("WEATHER_SOURCE", "Nguồn: Open-Meteo · CC BY 4.0")
    fun rainAt(percent: Int, time: String) = if (Language.current == Lang.EN) "Rain $percent% at $time" else "Mưa $percent% lúc $time"
    fun rainAtTime(time: String) = if (Language.current == Lang.EN) "Rain at $time" else "Có mưa lúc $time"
    fun hotAt(degrees: Int, time: String) = if (Language.current == Lang.EN) "Hot $degrees° at $time" else "Nắng nóng $degrees° lúc $time"
    fun dryAt(degrees: Int, time: String) = if (Language.current == Lang.EN) "Dry $degrees° at $time" else "Khô ráo $degrees° lúc $time"
    fun indoorStops(n: Int) = if (Language.current == Lang.EN) "$n indoor ${if (n == 1) "stop" else "stops"}" else "$n điểm trong nhà"
    fun outdoorStops(n: Int) = if (Language.current == Lang.EN) "$n outdoor ${if (n == 1) "stop" else "stops"}" else "$n điểm ngoài trời"
    val DESIGN get() = t("DESIGN", "Thiết kế")
    val SETTINGS get() = t("SETTINGS", "Cài đặt")
    val PORTFOLIO get() = t("PORTFOLIO", "Portfolio")
    val PLANS get() = t("PLANS", "Kế hoạch")
    val SAVED get() = t("SAVED", "Đã lưu")
    val SAVED_EMPTY get() = t("SAVED_EMPTY", "Chưa lưu địa điểm nào. Bấm biểu tượng lưu ở trang địa điểm.")
    val PORTFOLIO_SAMPLE get() = t("PORTFOLIO_SAMPLE", "Portfolio dưới đây là dữ liệu mẫu cho tới khi có tài khoản.")
    val JOURNEY_OF get() = t("JOURNEY_OF", "Hành trình của")
    val ABOUT_ME get() = t("ABOUT_ME", "Về tôi")
    val FEATURED_TRIPS get() = t("FEATURED_TRIPS", "CHUYẾN NỔI BẬT")
    val STORYBOOK get() = t("STORYBOOK", "Xem storybook")
    val DISCOVERY_MAP get() = t("DISCOVERY_MAP", "BẢN ĐỒ KHÁM PHÁ")
    val CONTRIBUTIONS get() = t("CONTRIBUTIONS", "ĐÓNG GÓP CHO CỘNG ĐỒNG")
    val CONTRIBUTIONS_RULE get() = t("CONTRIBUTIONS_RULE", "Chỉ tính đóng góp đã được duyệt. GoWander không xếp hạng hay cấp bậc người dùng.")
    val MOMENTS get() = t("MOMENTS", "Khoảnh khắc")
    val WHO_SEES get() = t("WHO_SEES", "Ai xem được")
    val LINK_HOLDERS get() = t("LINK_HOLDERS", "Người có link")
    val PER_SECTION_PRIVATE get() = t("PER_SECTION_PRIVATE", "Từng mục có thể để riêng tư")
    val UPCOMING get() = t("UPCOMING", "SẮP TỚI")
    val DRAFTS get() = t("DRAFTS", "BẢN NHÁP")
    val DONE get() = t("DONE", "ĐÃ ĐI")
    val GUIDE_ONLY get() = t("GUIDE_ONLY", "chỉ có cẩm nang, chưa hỗ trợ tìm tuyến")
    val IN_PORTFOLIO get() = t("IN_PORTFOLIO", "có trong portfolio")
    val EDIT get() = t("EDIT", "Sửa")
    val NEW_PLAN get() = t("NEW_PLAN", "Tạo kế hoạch mới")
    val DESIGN_PORTFOLIO get() = t("DESIGN_PORTFOLIO", "Thiết kế portfolio")
    val SAVE get() = t("SAVE", "Lưu")
    val VOICE get() = t("VOICE", "Âm thanh")
    val VOICE_ON get() = t("VOICE_ON", "Bật (đọc mọi chỉ dẫn)")
    val VOICE_ALERTS get() = t("VOICE_ALERTS", "Chỉ đọc cảnh báo")
    val VOICE_OFF get() = t("VOICE_OFF", "Tắt")
    val NO_VI_VOICE get() = t("NO_VI_VOICE", "Máy chưa có giọng đọc tiếng Việt, GoWander sẽ chỉ hướng dẫn bằng chữ. Có thể cài giọng tiếng Việt trong Cài đặt của máy.")
    val SAVED_TO_PROFILE get() = t("SAVED_TO_PROFILE", "Đã lưu vào Hành trình › Của tôi.")
    val EAT get() = t("EAT", "Ăn")
    val PORTFOLIO_SAVE_LATER get() = t("PORTFOLIO_SAVE_LATER", "Lưu portfolio sẽ có khi có tài khoản.")
    val LAYOUT get() = t("LAYOUT", "Mẫu trình bày")
    val THEME_COLOUR get() = t("THEME_COLOUR", "Màu chủ đề")
    val SECTIONS get() = t("SECTIONS", "Các mục")
    val VISIBILITY get() = t("VISIBILITY", "Ai xem được")
    val PRIVACY_NOTE get() = t("PRIVACY_NOTE", "Bản đồ khám phá cho biết nơi bạn hay đến, nên mặc định chỉ mình bạn xem. Ảnh chỉ hiện công khai sau khi bạn chọn giấy phép chia sẻ (ví dụ CC BY-SA).")

    fun minutes(sec: Int) = if (Language.current == Lang.EN) "${(sec + 59) / 60} min" else "${(sec + 59) / 60} phút"
    fun meters(m: Double) = if (m >= 1000) "${(m / 100).toInt() / 10.0} km".let { if (Language.current == Lang.EN) it else it.replace('.', ',') } else "${m.toInt()} m"
}

private val EN: Map<String, String> = mapOf(
    "TAB_MAP" to "Map",
    "TAB_DISCOVER" to "Discover",
    "TAB_GO" to "Where to?",
    "TAB_JOURNEY" to "Trips",
    "TAB_PROFILE" to "Profile",
    "RECENT_TRIPS" to "Places you routed to",
    "RECENT_TRIPS_EMPTY" to "Places you route to in this session will appear here.",
    "ROUTE_AGAIN" to "View route again",
    "GUEST" to "You're using guest mode",
    "SIGNED_IN" to "You're signed in",
    "GUEST_NOTE" to "No account needed. Everything stays on this device.",
    "DATA_SOURCES" to "Data sources and licences",
    "ORIGIN" to "From",
    "DESTINATION" to "To",
    "MY_LOCATION" to "Your location",
    "PICKED_ON_MAP" to "Point picked on the map",
    "NOT_SET" to "Not set",
    "SET_ORIGIN_MODE" to "Long-press to set the origin",
    "SET_DESTINATION_MODE" to "Long-press to set the destination",
    "USE_MY_LOCATION" to "Use my location",
    "SWAP" to "Swap",
    "FIND_ROUTE" to "Find route",
    "SEARCHING" to "Finding a route…",
    "RETRY" to "Try again",
    "NEED_ORIGIN" to "No origin yet. Use your location or pick an origin on the map.",
    "STEP_BY_STEP" to "Step by step",
    "FROM" to "From",
    "CHANGE" to "Change",
    "STREET_VIEW_3D" to "Street view · 3D",
    "LATER_TRIPS" to "Next trips:",
    "METRO_WITH_BUS" to "Includes Metro combined with bus.",
    "NEED_BOTH" to "Pick an origin and a destination to find a route",
    "NO_ROUTE" to "No suitable route found.",
    "NETWORK_ERROR" to "Can't reach the GoWander server. Check your connection and try again.",
    "UNEXPECTED_ERROR" to "Something went wrong. Please try again.",
    "SCHEDULED" to "Scheduled",
    "WALK" to "Walk",
    "TRANSFERS" to "transfers",
    "DIRECTIONS" to "Directions",
    "SEARCH_PLACES" to "Search places",
    "SEARCH_MIN" to "Type at least 2 characters",
    "ALL" to "All",
    "LOAD_MORE" to "Load more",
    "LOADING" to "Loading…",
    "EMPTY_PLACES" to "No matching places.",
    "BACK" to "Back",
    "SOURCE" to "Source",
    "LICENSE" to "Licence",
    "ADDRESS" to "Address",
    "NO_DESCRIPTION" to "No description for this place yet.",
    "SELECTED" to "Selected",
    "TOWARDS" to "towards",
    "OPEN_LOCATION_SETTINGS" to "Open location settings",
    "RETRY_GPS" to "Retry GPS",
    "SEARCH" to "Search",
    "TAB_DISCOVER_TITLE" to "Discover",
    "SEARCH_PLACES_HINT" to "Search places, metro stations",
    "NEAR_PIN" to "Near the pin — choose for more accuracy",
    "EXACT_PIN" to "Exact pin",
    "APPROXIMATE" to "Approximate location: house number not found",
    "OSM_ATTRIBUTION" to "Address: © OpenStreetMap contributors (ODbL)",
    "WHERE_TO" to "Where do you want to go?",
    "NEAR_YOU" to "Suggested near you",
    "SEE_ALL" to "See all",
    "SEARCH_DESTINATION" to "Search destination",
    "PICK_ON_MAP" to "Pick on map",
    "CONFIRM_DESTINATION" to "Confirm destination",
    "CONFIRM_ORIGIN" to "Confirm origin",
    "LEAVE_NOW" to "Leave now",
    "ARRIVE_BY" to "Arrive by",
    "RECENT" to "Recent",
    "FASTEST" to "Fastest",
    "LEAST_WALKING" to "Least walking",
    "ROUTE_DETAIL" to "Route details",
    "ASSUMED_NOTE" to "Metro Line 1 and bus times are estimates (bus data from OpenStreetMap), not an official timetable.",
    "MODE_TRANSIT" to "Public transport",
    "MODE_BUS" to "Bus",
    "WALK_TOTAL" to "Total walking",
    "MINUTES_SHORT" to "min",
    "GO_FORWARD" to "Move forward 20 m",
    "GO_BACK" to "Move back 20 m",
    "PLAY_PREVIEW" to "Play the whole route preview",
    "PAUSE_PREVIEW" to "Pause preview",
    "ABOUT_PLACE" to "About",
    "ABOUT_PLACE_HINT" to "Description, photos, address and sources",
    "MODE_METRO" to "Metro",
    "NO_METRO_ROUTE" to "No Metro route for this trip. Try the Bus tab.",
    "MODE_BICYCLE" to "Bicycle",
    "MODE_CAR" to "Car",
    "MODE_WALK" to "Walk",
    "UNNAMED_STREET" to "unnamed street",
    "ORIGIN_ON_MAP" to "Pick origin on the map",
    "DESTINATION_ON_MAP" to "Pick destination on the map",
    "BUS" to "Bus",
    "CYCLE" to "Cycle",
    "DRIVE" to "Drive",
    "CAR_NOTE" to "Car times do not include traffic.",
    "NO_ROUTE_TITLE" to "No route found for this trip",
    "CHANGE_DESTINATION" to "Change destination",
    "OFFLINE" to "No internet connection",
    "STATION" to "Station",
    "PLACE" to "Place",
    "NEAR_STATION" to "Near station",
    "NEAR" to "Near",
    "START" to "Start",
    "PREVIEW" to "Preview",
    "STEPS" to "Steps",
    "STEPS_HINT" to "Tap a step to preview that spot.",
    "EXIT" to "Exit",
    "ARRIVED" to "Arrived",
    "WAITING_GPS" to "Waiting for GPS…",
    "OFF_ROUTE" to "You are off the route.",
    "WRONG_WAY_TITLE" to "You are off the route",
    "REROUTE" to "Recalculate from here",
    "STEP" to "Step",
    "PREVIOUS" to "Previous",
    "NEXT" to "Next",
    "BACK_TO_ROUTE" to "Back to route",
    "STREET_PHOTO" to "Street photo",
    "NO_STREET_PHOTO" to "No street photo here: showing 3D map",
    "EXPAND" to "Expand",
    "MAP_VIEW" to "Map type",
    "MAP_CREDIT" to "© OpenStreetMap · OpenMapTiles · OpenFreeMap",
    "STREET_PHOTO_CREDIT" to "Street photo: Mapillary, CC BY-SA 4.0",
    "PHOTO_SOURCES" to "Photo sources",
    "EAST_SEA" to "East Sea",
    "APP_NAME" to "GoWander",
    "TAGLINE" to "Move Smart. Explore More.",
    "CHOOSE_LANGUAGE" to "Choose language",
    "VIETNAMESE" to "Vietnamese",
    "ENGLISH" to "English",
    "ENGLISH_SOON" to "English is available now.",
    "MORE_LANGUAGES_LATER" to "Chinese and Korean will come later.",
    "LOCATION_QUESTION" to "Allow GoWander to know your location?",
    "LOCATION_WHY" to "Location helps find routes from where you are and suggest nearby places. GoWander does not record your route.",
    "CONTINUE" to "Continue",
    "LATER_TYPE_ORIGIN" to "Later, I'll type the origin",
    "CLOSE_PANEL" to "Close picker",
    "RECENTER" to "Re-center",
    "SHOW_NEARBY" to "Show nearby suggestions",
    "HIDE_NEARBY" to "Collapse suggestions",
    "VIEW_2D" to "2D",
    "VIEW_3D" to "3D",
    "SOON" to "Soon",
    "CLOSE" to "Close",
    "VIEW_SATELLITE" to "Satellite",
    "SATELLITE_PENDING" to "No satellite imagery yet: selecting a permitted provider.",
    "SATELLITE_CREDIT" to "Satellite imagery: Esri, Maxar, Earthstar Geographics",
    "COLLAPSE" to "Collapse",
    "WAIT" to "Wait about",
    "LEAVE_AT" to "Leave at",
    "ARRIVE_ABOUT" to "arrive about",
    "NO_TRANSFER" to "0 transfers",
    "LANGUAGE_VI" to "VI",
    "LANGUAGE_EN" to "EN",
    "LANGUAGE_MENU" to "Language: English",
    "RESULTS" to "Results",
    "GO_NOW" to "Go now",
    "TO_PLACE" to "to",
    "ARRIVE_AT" to "Arrive",
    "RUN_PREVIEW" to "Run",
    "PAUSE" to "Pause",
    "REMAINING" to "left",
    "LOWER_MAP_VIEW" to "Map type below",
    "PARTIAL_DRAW" to "Part of the route could not be drawn; the leg list below is still correct.",
    "FIND_PLACE" to "Find food, dishes, places",
    "LIST" to "List",
    "SAMPLE_STARS" to "sample stars",
    "SAMPLE_REVIEWS" to "sample reviews",
    "SAMPLE_USER" to "[Sample user]",
    "SAMPLE_PHOTOS" to "Sample photos: no licensed photos for this place yet.",
    "SAMPLE_PHOTO_CREDIT" to "Sample photo",
    "SAMPLE_USER_PHOTO" to "[User] · sample photo",
    "SAMPLE_DISH_PHOTO" to "[Sample dish photo]",
    "TIMES" to "views",
    "SAVE_PLACE" to "Save place",
    "UNSAVE_PLACE" to "Remove saved place",
    "SHARE" to "Share",
    "SHARE_PORTFOLIO" to "Share portfolio",
    "SHARE_LATER" to "Sharing will be available with an account.",
    "OVERVIEW" to "Overview",
    "REVIEWS" to "Reviews",
    "DISHES" to "Featured dishes",
    "NO_DISHES" to "No featured dishes for this place yet.",
    "WRITE_REVIEW" to "Write a review",
    "REVIEW_LATER" to "Reviews will be available with an account.",
    "NEAR_YOU_TAB" to "Near you",
    "POPULAR" to "Trending",
    "FEEDBACK_SENT" to "Thanks! GoWander received your feedback.",
    "POPULAR_SPAN" to "Last 7 days",
    "VIETNAM_PLACES" to "Places in Vietnam",
    "SUGGESTED_TRIP" to "SUGGESTED TRIP",
    "SHOW_ON_MAP" to "Show on map",
    "ALL_PLACES" to "All places",
    "STORIES" to "Stories",
    "FROM_YOU" to "from you",
    "VIETNAM_MAP" to "Map of Vietnam",
    "ISLANDS_NORTH" to "Paracel Islands\n(Vietnam)",
    "ISLANDS_SOUTH" to "Spratly Islands\n(Vietnam)",
    "OPEN_GUIDE" to "open guide",
    "GUIDE" to "GUIDE",
    "HAS_ROUTING" to "Routing · navigation",
    "VIDEO" to "Video 1:30",
    "VIDEO_LATER" to "Intro video not available yet.",
    "DISH_PICKS" to "DISH PICKS",
    "SHOP_NEAR_ON_MAP" to "See nearby shops on the map",
    "STORY_LABEL" to "STORY",
    "COMMUNITY_PHOTOS" to "Community photos",
    "COMMUNITY_RULE" to "Photos are shown when: the uploader chose a share licence, the photo was reviewed, and the place has a stable rating.",
    "SUGGESTION" to "SUGGESTION",
    "PLAN_YOURSELF" to "Plan it yourself",
    "GETTING_AROUND" to "Getting around Ho Chi Minh City",
    "GETTING_AROUND_NOTE" to "Metro Line 1, bus and walking. GoWander finds routes and guides you.",
    "ONE_DAY_PLAN" to "Plan a one-day trip",
    "CLOSE_GUIDE_ITEM" to "Close, back to guide",
    "FOOD_HCMC" to "FOOD · HO CHI MINH CITY",
    "SHOPS_NEAR" to "Suggested shops",
    "SHOW_CARD" to "show card",
    "TRAVEL_TIPS" to "Travel tips",
    "OPENING_HOURS" to "Opening hours",
    "TRY_THIS" to "Try this",
    "ADD_TO_PLAN" to "+ Add to plan",
    "SUGGESTIONS" to "Suggestions",
    "MINE" to "Mine",
    "PLAN_MAP" to "Plan map",
    "VIEW_ON_MAP" to "View on map",
    "PLAN_MAP_NOTE" to "Solid line: route calculated. Dashed line: not calculated, straight between two points.",
    "PROPOSED_ORDER" to "Suggested order",
    "USE_ORDER" to "Use this order",
    "KEEP_ORDER" to "Keep current order",
    "ORDER_ALREADY_SHORT" to "The current order already has the least backtracking.",
    "APPEARANCE" to "Appearance",
    "LIGHT" to "Light",
    "DARK" to "Dark",
    "GOING" to "ON THE WAY",
    "OPEN_PLAN" to "Open plan",
    "RECENT_ROUTES" to "RECENT ROUTES",
    "NO_PLANS" to "No plans yet.",
    "HOW_LONG" to "How much time do you have?",
    "ANY_TIME" to "Any time",
    "NO_TRIP_FITS" to "No suggested trip fits this time window.",
    "EDITORS_PICK" to "Editor's pick",
    "ADD_STOP" to "Add stop",
    "START_TRIP" to "Start trip",
    "PLAN_NAME" to "Plan name",
    "START_AT" to "Start",
    "TODAY" to "Today",
    "CLEAR_DATE" to "Clear date",
    "PREV_MONTH" to "Previous month",
    "NEXT_MONTH" to "Next month",
    "HOUR" to "Hour",
    "MINUTE" to "Minute",
    "TOMORROW" to "Tomorrow",
    "NO_DATE" to "No date selected",
    "PLAN_DAY" to "Travel date",
    "PLAN_START" to "Start time",
    "PLAN_TRAVEL" to "Travel by",
    "BUS_AND_WALK" to "Bus + walk",
    "METRO_AND_WALK" to "Metro + walk",
    "EARLIER_15" to "15 minutes earlier",
    "LATER_15" to "15 minutes later",
    "DONE_PICKING" to "Done",
    "TIMES_FOR_DAY" to "Travel times use the timetable for the selected day.",
    "PLAN_EMPTY" to "No stops yet. Add one with the button below.",
    "REMOVE_STOP" to "Remove stop",
    "ADD_STOP_LONG" to "+ Add stop (search or pick on map)",
    "MORE_NEARBY" to "More suggestions near your stops",
    "REORDER" to "Reorder to reduce backtracking",
    "STOPS" to "stops",
    "UNTIL" to "until",
    "TIMES_ESTIMATED" to "times are estimates",
    "LEG_PENDING" to "Calculating travel time…",
    "LEG_UNKNOWN" to "Travel time unavailable",
    "NEXT_STOP" to "Next stop",
    "GO_NEXT" to "Go to next stop",
    "ARRIVED_HERE" to "Arrived here",
    "ROUTE_AGAIN_SHORT" to "Route again",
    "PLAN_DONE" to "You've visited every stop in the plan.",
    "PLAN_RESTART" to "Start over",
    "ROUTE_HERE" to "Directions here",
    "STAY" to "stay",
    "STAY_LESS" to "Stay 15 minutes less",
    "STAY_MORE" to "Stay 15 minutes more",
    "MEMO" to "Your note",
    "MEMO_HINT" to "Add a note: fare, meeting time, what to bring…",
    "STOP_DONE" to "arrived",
    "PLAN_WALK" to "walk",
    "SAVE_TO_PROFILE" to "Save plan",
    "NO_ROUTING_HERE" to "Routing is not available for this plan (guide only).",
    "TRIP_WEATHER" to "Trip weather",
    "WEATHER_RAIN_TIP" to "Tip: switch to an indoor stop or move to a drier hour.",
    "WEATHER_RAIN_ALL_INDOOR" to "All stops are indoors, so little impact.",
    "WEATHER_HEAT_TIP" to "Tip: go earlier or rest indoors at midday.",
    "WEATHER_HEAT_INDOOR" to "Hot outside, but all stops are indoors.",
    "WEATHER_CLEAR_TIP" to "Good for outdoor stops too.",
    "WEATHER_EARLIER" to "1 hour earlier",
    "WEATHER_LATER" to "1 hour later",
    "WEATHER_SOURCE" to "Source: Open-Meteo · CC BY 4.0",
    "DESIGN" to "Design",
    "SETTINGS" to "Settings",
    "PORTFOLIO" to "Portfolio",
    "PLANS" to "Plans",
    "SAVED" to "Saved",
    "SAVED_EMPTY" to "No saved places yet. Tap the save icon on a place page.",
    "PORTFOLIO_SAMPLE" to "The portfolio below is sample data until you have an account.",
    "JOURNEY_OF" to "Journey of",
    "ABOUT_ME" to "About me",
    "FEATURED_TRIPS" to "FEATURED TRIPS",
    "STORYBOOK" to "View storybook",
    "DISCOVERY_MAP" to "DISCOVERY MAP",
    "CONTRIBUTIONS" to "COMMUNITY CONTRIBUTIONS",
    "CONTRIBUTIONS_RULE" to "Only approved contributions count. GoWander does not rank or level users.",
    "MOMENTS" to "Moments",
    "WHO_SEES" to "Who can see",
    "LINK_HOLDERS" to "People with the link",
    "PER_SECTION_PRIVATE" to "Each section can be private",
    "UPCOMING" to "UPCOMING",
    "DRAFTS" to "DRAFTS",
    "DONE" to "DONE",
    "GUIDE_ONLY" to "guide only, no routing yet",
    "IN_PORTFOLIO" to "in portfolio",
    "EDIT" to "Edit",
    "NEW_PLAN" to "Create a new plan",
    "DESIGN_PORTFOLIO" to "Design portfolio",
    "SAVE" to "Save",
    "VOICE" to "Audio",
    "VOICE_ON" to "On (read every instruction)",
    "VOICE_ALERTS" to "Alerts only",
    "VOICE_OFF" to "Off",
    "NO_VI_VOICE" to "This device has no Vietnamese voice; GoWander will show text directions only. You can install a Vietnamese voice in device settings.",
    "SAVED_TO_PROFILE" to "Saved to Trips › Mine.",
    "EAT" to "Eat",
    "PORTFOLIO_SAVE_LATER" to "Saving a portfolio will be available with an account.",
    "LAYOUT" to "Layout",
    "THEME_COLOUR" to "Theme colour",
    "SECTIONS" to "Sections",
    "VISIBILITY" to "Who can see",
    "PRIVACY_NOTE" to "The discovery map shows where you often go, so it is private by default. Photos become public only after you choose a share licence (for example CC BY-SA).",
    "LANGUAGE" to "Language",
    "PREFERENCES" to "Preferences",
    "LANGUAGE_VI_LABEL" to "Tiếng Việt",
    "LANGUAGE_EN_LABEL" to "English",
    "LANGUAGE_NOTE" to "Applies to the whole interface. Place names and descriptions appear in English when a translation exists.",
    "LANGUAGE_ADD_LATER" to "Chinese and German need translations before they can be enabled.",
)
