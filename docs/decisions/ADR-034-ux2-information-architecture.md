# ADR-034 — Kiến trúc thông tin và điều hướng app (UX-2)

- **Trạng thái:** Accepted (hướng thiết kế) · **Ngày:** 14-11-2026 · **Người quyết định:** chủ dự án ("tôi thấy oke")
- **Chi tiết:** `docs/plans/UX-2-ke-hoach-lam-lai-trai-nghiem.md`. Bản vẽ: artifact https://claude.ai/artifact/XtZuZY6CkCk98ZPVd3dSp4
- **Liên quan:** ADR-022 (đăng nhập, ngôn ngữ), ADR-030 (Design Kit v4), ADR-033 (NAV-1), `PIPELINE.md` UX-2 / M06b / NAV-1 / DATA-1 / M07
- **Thay thế một phần:**
  - ADR-033: "chia đôi màn chỉ khi Vệ tinh" (nay chia đôi luôn), "giọng nói để sau" (nay nằm trong UX-2 P2b);
  - M06b: thanh dưới 3 tab.

## Bối cảnh

- Chủ dự án thấy luồng hiện tại rối. Các vấn đề cụ thể đã kiểm trong code, xem §1 của tài liệu chi tiết. Ví dụ:
  - cùng là chọn điểm đến mà mỗi cách dẫn tới một chỗ khác nhau;
  - có 3 lối vào tìm đường;
  - Back không về đúng chỗ;
  - nhiều nút "Sắp có".
- GoWander nhắm vào khách du lịch, nên cần có:
  - cẩm nang từng vùng;
  - lên kế hoạch chuyến đi;
  - hồ sơ dạng portfolio.

## Quyết định

1. **Thanh dưới 5 mục** theo Design Kit v4: Bản đồ · Khám phá · **Đi đâu?** (nút giữa nổi) · Hành trình · Hồ sơ.
   - Nút giữa là hành động: mở ô tìm điểm đến, không mở một màn riêng.
   - Bỏ tab Tìm tuyến.
2. **Điều hướng theo ý định:** "tìm để đi" thì vào Danh sách tuyến; "tìm để xem" thì vào sheet địa điểm hoặc portfolio vùng. Chọn địa điểm, địa chỉ hay điểm trên bản đồ đều vào thẳng Danh sách tuyến.
3. **Back stack cho từng tab** trong `commonMain`, thay các cờ trong `App.kt`. Android và iOS dùng chung một logic, có test đơn vị.
4. **Sheet địa điểm dùng chung** cho Bản đồ, Khám phá và cẩm nang.
   - Đánh giá và bình luận: R1 hiện khu vực trống; R2 làm khi có đăng nhập và kiểm duyệt (COM-1).
5. **Bản đồ:** quán và điểm hiện thành thẻ nổi (tối đa 6) trên bản đồ thay cho danh sách; luôn có nút Danh sách để đọc được bằng trình đọc màn hình.
   - Cách vẽ (Compose overlay hay symbol layer) quyết định sau spike P3a.
6. **Tìm tuyến:** Chi tiết tuyến có 2 nút, Bắt đầu và Xem từng bước. Gộp các chuyến giống nhau theo khóa chuỗi chặng.
7. **Xem từng bước và Dẫn đường chia đôi màn:** 3D góc nhìn trên đường ở trên, 2D ở dưới, nút nhích 20 m.
   - **Giọng nói** dùng TTS có sẵn của máy (`vi-VN`), nút loa có 3 mức.
8. **Khám phá có 2 thẻ:**
   - **Gần bạn**;
   - **Điểm đến Việt Nam**: bản đồ cả nước, có Hoàng Sa và Trường Sa, các vùng nổi lên, bấm vào mở **portfolio vùng** (giới thiệu, nổi bật, ẩm thực, văn hóa, ảnh cộng đồng, lên chuyến, video).
   - Bấm ảnh thì xem chi tiết, kèm nút Xem trên bản đồ và Thêm vào kế hoạch.
9. **Hành trình:** chuyến gợi ý do biên tập viên soạn; **tự lên kế hoạch thủ công**.
10. **Hồ sơ có 2 thẻ: Portfolio · Đã lưu.** (Sửa 15-11-2026, chủ dự án đồng ý: Kế hoạch chuyển sang tab Hành trình, thẻ "Của tôi", đứng trước "Gợi ý"; kế hoạch đang đi nằm trên cùng. Lý do: kế hoạch là thứ dùng lúc đang đi, để trong Hồ sơ phải bấm ba lần mới tới. Bản gốc ghi 3 thẻ: Portfolio · Kế hoạch · Đã lưu.)
    - Portfolio do người dùng thiết kế (mẫu, màu, mục); mặc định riêng tư, riêng tư theo từng mục; không xếp hạng người dùng.
11. **Một font Be Vietnam Pro** cho toàn bộ app.
12. **Ẩn tính năng chưa xong**, không hiện "Sắp có". Xin quyền vị trí khi cần, bỏ màn Lần đầu.
13. **Android và iOS ngang nhau:** mỗi giai đoạn phải build, test và chụp ảnh trên cả hai nền tảng.

## Các phương án đã cân nhắc

- **Thanh dưới 2–3 mục (Bản đồ · Đi đâu? · Khám phá):** gọn hơn, nhưng thiếu Hành trình và Hồ sơ. Đã bỏ khi chủ dự án nhắc rằng dự án còn 2 tab đó.
- **Cẩm nang vùng là tab thứ 6:** thanh dưới quá 5 mục (vượt khuyến nghị Material) và trùng ý định với Khám phá. Đã bỏ.
- **Điểm đến dạng danh sách:** chủ dự án chọn bản đồ cả nước.
- **Gộp Xem trước vào một danh sách bước, không có màn chia đôi:** chủ dự án yêu cầu giữ chia đôi.
- **Thư viện TTS bên ngoài:** không cần, vì TTS của hệ điều hành đủ dùng và không gửi dữ liệu ra ngoài.

## Hệ quả

- `App.kt` viết lại phần điều hướng (P1). Màn Bản đồ, sheet địa điểm và Khám phá làm mới (P3–P4).
- **API mới:** `GET /api/v1/places/map`. Sau này thêm `regions`, `stories`, `trips`, `plans` và `reviews`.
- **Cần thêm ADR:**
  - cách lưu kế hoạch và địa điểm đã lưu trên máy;
  - cách vẽ thẻ nổi (sau P3a);
  - nhà cung cấp ảnh vệ tinh;
  - nguồn ranh giới bản đồ cả nước.
- **Nội dung lớn** (cẩm nang vùng, câu chuyện, chuyến gợi ý, video) thuộc DATA-1 (CONTENT) và cần Admin soạn bài (M07).
- **Test:** e2e_ux, ux_audit và iOS UI test phải cập nhật theo màn mới. Các test case T1–T12, M1–M7, V1–V6, K1–K5 nằm trong tài liệu chi tiết §14.

## Câu hỏi còn mở

Xem §16 tài liệu chi tiết: Community, Ô tô / Xe đạp trong pilot, đánh giá cho pilot, các vùng pilot, người viết nội dung, nguồn video, ẩn nút ngôn ngữ.
