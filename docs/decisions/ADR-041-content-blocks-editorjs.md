# ADR-041 — Nội dung do Admin soạn: Editor.js, lưu block JSON, app render native

- **Trạng thái:** Accepted (hướng đi, chủ dự án 16-11-2026) · **phần 1 đã triển khai 17-11-2026**: câu chuyện (`historical_stories.blocks`) có Editor.js trong Admin (chữ: đoạn, tiêu đề, danh sách, trích dẫn, phân cách), API trả `blocks`, app render native; khối ảnh + seed builder còn lại (xem mục 1 của `docs/changes/change-17-11-2026.md`).
- **Liên quan:** ADR-035 (ảnh: R2, nén lại, bắt credit + licence), ADR-037 (Admin Go server-rendered + htmx), ADR-030 (Design Kit v4), `docs/plans/AUTH-tai-khoan-nguoi-dung.md` §7 (portfolio dùng lại định dạng này).

## Bối cảnh

Nội dung dài của GoWander (`historical_stories.content`, `guide_items.body`, `places.description`) hiện là **plain text** trong `<textarea>` của Admin, app Compose hiển thị nguyên văn. Chủ dự án hỏi có nên dùng TinyMCE cho chỗ cần văn bản có định dạng, hay công cụ khác chi phí ổn (16-11-2026).

Ràng buộc quyết định:

1. App là **Kotlin Multiplatform / Compose**, không có HTML → Compose tử tế. Nhận HTML nghĩa là WebView (chữ không theo theme, cỡ chữ hệ thống, khó TalkBack) hoặc tự viết parser.
2. TinyMCE (từ v6) và CKEditor 5 tự host là **GPLv2+**, bản cloud trả phí; cả hai xuất **HTML**.
3. Nội dung còn phải đi qua `scripts/build_guide_seed.py` và bản offline `content/GuideHcm.kt`, nên đổi định dạng một lần là phải sửa cả ba chỗ.

## Quyết định

1. **Editor.js** (Apache-2.0, tự host trong `backend/internal/admin/web/static/`) là trình soạn cho nội dung dài. Không dùng TinyMCE, CKEditor, Quill hay Tiptap.
2. **Lưu block JSON, không lưu HTML.** Mỗi bài có cột `blocks JSONB`; block hỗ trợ đợt đầu: `paragraph`, `header` (cấp 2–3), `list` (có thứ tự / không), `quote`, `image` (trỏ tới `media` đã xử lý), `delimiter`. Không nhận block khác.
3. **Phạm vi rich text:** `historical_stories.content`, `guide_items.body` (cả cẩm nang lẫn món ăn), về sau là Portfolio. **Giữ plain text:** `places.description` (≤ 500 ký tự), `guide_items.summary`/`extra`, `feedback.note`, mọi ô ghi chú nội bộ của Admin.
4. **Server là nơi quyết định:** validate allowlist block và giới hạn (số block, ký tự mỗi block, số ảnh mỗi bài); `image` chỉ nhận khoá media đã có trong bảng `place_media`/media của bài, ảnh vẫn qua pipeline ADR-035 (nén lại, bắt credit + licence); link chỉ `http(s)`, thêm `rel="noopener noreferrer"`. Block lạ, quá giới hạn hay chứa thẻ HTML → từ chối, không lưu.
5. **API trả `blocks` cạnh `content`** (content vẫn là bản text để tương thích, tìm kiếm và cho bản app cũ). App render `blocks` nếu có, không có thì rơi về `content`.
6. **App render native:** một composable cho mỗi loại block, dùng token Design Kit v4 (Be Vietnam Pro, `#123C48`, khoảng cách 12–16). Không WebView, không XSS, chữ theo cỡ chữ hệ thống.
7. **Dữ liệu cũ không tự chuyển:** bài đang có chỉ có `content`; khi chủ dự án mở bài trong Admin mới và bấm lưu thì mới sinh `blocks`. Không viết bộ chuyển text → block tự động.

## Không chọn

- **TinyMCE / CKEditor 5:** GPLv2+ khi tự host (phải mua nếu không muốn dính), xuất HTML, kéo theo sanitize phía server và bài toán render trong Compose. Nhiều tính năng nhưng không dùng hết.
- **Quill 2 / Tiptap / Lexical:** giấy phép ổn (BSD/MIT) nhưng mặc định vẫn là HTML; muốn sạch thì phải tự dựng schema JSON — tức là làm lại phần việc Editor.js đã có.
- **Markdown:** rẻ công nhất, nhưng chèn ảnh giữa bài và kiểm soát bố cục kém; chỉ phù hợp ô ngắn. Vẫn có thể dùng cho `summary`/`extra` nếu sau này cần in đậm.

## Hệ quả

- Migration mới thêm cột `blocks JSONB` cho `historical_stories`, `guide_items`; seed builder và API guide phải mang thêm `blocks`; `GuideHcm.kt` (bản offline) cần kiểu dữ liệu tương ứng.
- Admin thêm JS/CSS của Editor.js (~150 KB) và một trang soạn mới; phần còn lại của Admin không đổi.
- Nếu Editor.js ngừng phát triển: dữ liệu là JSON thuần nên đổi trình soạn chỉ là đổi phần UI, không phải migrate dữ liệu.
- Việc này **không chặn AUTH**: tài khoản, phiên, API không phụ thuộc định dạng nội dung. Làm sau AUTH-2/AUTH-3, trước khi viết nhiều nội dung thật.
