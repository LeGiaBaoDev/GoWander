# UX E2E (Android emulator) — 2026-11-15 23:52

**117/118 kiểm tra đạt.** Tiêu chí: `docs/testing/ux-checklist.md`.

## Hành trình

- ✅ Lần đầu mở app: màn chào có logo đầy đủ (biểu tượng + chữ GoWander) và khẩu hiệu
- ✅ [Màn chào (S0)] T1–T4: kích thước, nhãn, chồng lấn, thanh trạng thái
- ✅ Bắt đầu: vào thẳng bản đồ, không có màn chọn ngôn ngữ / vị trí
- ✅ Mở lại app: không hiện màn chào lần nữa
- ✅ Bản đồ (mẫu màn 1): thẻ quán nổi có sao (mẫu), hàng lọc, nút Danh sách
- ✅ [Bản đồ chính] T1–T4: kích thước, nhãn, chồng lấn, thanh trạng thái
- ✅ Thanh dưới 5 mục: Bản đồ · Khám phá · Đi đâu? · Hành trình · Hồ sơ
- ✅ Không còn nút 'VI' khi chưa có tiếng Anh (ADR-034)
- ✅ Bấm địa điểm gợi ý: mở trang địa điểm (có nút Chỉ đường)
- ✅ [Trang địa điểm] T1–T4: kích thước, nhãn, chồng lấn, thanh trạng thái
- ✅ Chỉ đường: vào danh sách tuyến + dòng 'Thông tin về …'
- ✅ Back từ danh sách tuyến: về đúng trang địa điểm
- ✅ Back lần nữa: về bản đồ
- ✅ Bấm một chấm trên bản đồ: chấm thành thẻ của đúng quán đó
- ✅ [Bản đồ sau khi bấm chấm] T1–T4: kích thước, nhãn, chồng lấn, thanh trạng thái
- ✅ Zoom xa: bản đồ gom thành bong bóng có số địa điểm, không còn thẻ quán
- ✅ [Bản đồ zoom xa] T1–T4: kích thước, nhãn, chồng lấn, thanh trạng thái
- ✅ Bấm bong bóng: phóng to, hiện lại quán
- ✅ Bấm 'Loại bản đồ': có 2D / 3D / Vệ tinh
- ✅ Bấm Vệ tinh: báo chưa có ảnh vệ tinh, không đổi bản đồ
- ✅ Chọn 3D: bảng tự đóng
- ✅ Bấm ra ngoài bảng: bảng đóng
- ✅ Lọc Cà phê rồi Tất cả: thẻ quán hiện lại
- ✅ Khám phá (mẫu màn 8): 2 tab, hành trình gợi ý
- ✅ [Khám phá] T1–T4: kích thước, nhãn, chồng lấn, thanh trạng thái
- ✅ Khám phá: bấm địa điểm mở sheet địa điểm (mẫu màn 2), không tự nhảy sang tuyến (T4)
- ✅ Sheet địa điểm › Đánh giá: điểm sao (mẫu) + nút Viết đánh giá
- ✅ Bấm Lưu: nút đổi thành Bỏ lưu
- ✅ Back từ trang địa điểm: về danh sách Khám phá
- ✅ Điểm đến Việt Nam (mẫu màn 13): bản đồ có thẻ vùng
- ✅ [Điểm đến Việt Nam] T1–T4: kích thước, nhãn, chồng lấn, thanh trạng thái
- ✅ Cẩm nang TP.HCM (mẫu màn 14): bìa + mục lục
- ✅ [Cẩm nang TP.HCM] T1–T4: kích thước, nhãn, chồng lấn, thanh trạng thái
- ✅ Bấm món (mẫu màn 15): mô tả + quán gợi ý
- ✅ [Món ăn] T1–T4: kích thước, nhãn, chồng lấn, thanh trạng thái
- ✅ Thêm vào kế hoạch: mở Tự lên kế hoạch (mẫu màn 16) có món vừa thêm
- ✅ [Tự lên kế hoạch] T1–T4: kích thước, nhãn, chồng lấn, thanh trạng thái
- ✅ Xoá một điểm: điểm biến mất, giờ tính lại
- ✅ Lưu kế hoạch: quay về màn trước
- ✅ Hành trình › Của tôi (mẫu màn 17): kế hoạch của bạn + tạo mới
- ✅ [Hành trình Của tôi] T1–T4: kích thước, nhãn, chồng lấn, thanh trạng thái
- ✅ Hành trình (mẫu màn 9): các chuyến do biên tập soạn
- ✅ [Hành trình] T1–T4: kích thước, nhãn, chồng lấn, thanh trạng thái
- ✅ Kế hoạch chuyến (mẫu màn 10): các điểm đánh số
- ✅ [Kế hoạch chuyến] T1–T4: kích thước, nhãn, chồng lấn, thanh trạng thái
- ✅ Bắt đầu chuyến: mở kế hoạch, điểm kế tiếp là điểm đầu
- ✅ Kế hoạch: thời gian từng chặng do bộ tìm tuyến tính, có tổng quãng đi bộ
- ✅ Chọn ngày: mở danh sách 14 ngày tới
- ✅ Chọn Ngày mai: ô ngày đổi thành ngày đã chọn
- ✅ Giờ bắt đầu +15 phút: 08:00 thành 08:15
- ✅ [Kế hoạch đang chọn phương tiện] T1–T4: kích thước, nhãn, chồng lấn, thanh trạng thái
- ✅ Đổi sang Đi bộ: các chặng được tính lại
- ✅ Đi bộ: thời gian từng chặng tính xong
- ✅ Kế hoạch có bản đồ: các điểm đánh số và đường đi
- ✅ Xem trên bản đồ: bản đồ toàn màn hình có tên các điểm, 2D/3D và nút đi tiếp
- ✅ [Bản đồ kế hoạch toàn màn hình] T1–T4: kích thước, nhãn, chồng lấn, thanh trạng thái
- ✅ Sắp xếp lại: thứ tự đã gọn thì không đổi gì
- ✅ Bấm một điểm: mở chỉnh thời gian ở lại, ghi chú, chỉ đường tới đây
- ✅ Ở lại thêm 15 phút: 30 thành 45, giờ các điểm sau tính lại
- ✅ [Kế hoạch đang mở một điểm] T1–T4: kích thước, nhãn, chồng lấn, thanh trạng thái
- ✅ Đi tới điểm kế tiếp: mở tìm tuyến tới điểm đó
- ✅ Quay lại kế hoạch: đang trên đường, có nút Đã đến nơi
- ✅ Đã đến nơi: điểm đó được đánh dấu, điểm kế tiếp đổi
- ✅ [Kế hoạch đang đi] T1–T4: kích thước, nhãn, chồng lấn, thanh trạng thái
- ✅ Của tôi: kế hoạch đang đi nằm trên cùng, có điểm kế tiếp và nút Tiếp tục
- ✅ Hồ sơ (mẫu màn 11): portfolio có bìa + nút Thiết kế
- ✅ [Hồ sơ] T1–T4: kích thước, nhãn, chồng lấn, thanh trạng thái
- ✅ Hồ sơ không còn thẻ Kế hoạch (đã chuyển sang Hành trình)
- ✅ Tắt app rồi mở lại: địa điểm đã lưu vẫn còn
- ✅ Thiết kế portfolio (mẫu màn 12)
- ✅ [Thiết kế portfolio] T1–T4: kích thước, nhãn, chồng lấn, thanh trạng thái
- ✅ Hồ sơ › Cài đặt: chế độ khách + nguồn dữ liệu và giấy phép
- ✅ Cài đặt › Giao diện: chọn Tối, lựa chọn được lưu trên máy
- ✅ [Cài đặt nền tối] T1–T4: kích thước, nhãn, chồng lấn, thanh trạng thái
- ✅ Đi đâu?: ô tìm + dòng 'Từ: … · Đổi' + 'Chọn trên bản đồ' (mẫu màn 3)
- ✅ [Đi đâu?] T1–T4: kích thước, nhãn, chồng lấn, thanh trạng thái
- ✅ Gõ tên: kết quả nằm dưới tiêu đề 'Kết quả'
- ✅ Chọn điểm đến: vào thẳng danh sách tuyến, 5 tab phương tiện
- ❌ Tab Buýt: mỗi tuyến ghi rõ phần đi bộ ra/vào trạm ('Đi bộ tổng … phút')
- ✅ Danh sách tuyến: sửa điểm đi / điểm đến / đổi chiều ngay ở đầu màn
- ✅ Không lặp nhãn 'Nhanh nhất' trên thẻ (chỉ còn chip sắp xếp)
- ✅ Chuyến giống nhau gộp một thẻ (lần này không có chuyến trùng)
- ✅ Bấm 'Đi ngay ▾': mở lựa chọn giờ
- ✅ Bấm lại 'Đi ngay ▴': lựa chọn giờ đóng lại
- ✅ [Danh sách tuyến] T1–T4: kích thước, nhãn, chồng lấn, thanh trạng thái
- ✅ Chọn tab Ô tô: có kết quả ngay, không phải bấm thêm
- ✅ Ô tô: không hiện chip/nhãn 'Nhanh nhất / Ít đi bộ' vô nghĩa
- ✅ Chi tiết tuyến: 2 nút Bắt đầu · Xem từng bước (không còn 'Xem trước' riêng)
- ✅ Chi tiết tuyến: thanh tab dưới ẩn (màn con)
- ✅ [Chi tiết tuyến] T1–T4: kích thước, nhãn, chồng lấn, thanh trạng thái
- ✅ Bấm phần đầu thẻ: thẻ mở rộng, nhãn đổi thành 'Thu gọn'
- ✅ Chi tiết tuyến như mẫu màn 5: dòng thời gian các chặng đến điểm đến, không có thanh phương tiện
- ✅ Xem từng bước: chia đôi, 3D trên có ︿ / ﹀, 2D dưới, thẻ bước
- ✅ Xem từng bước: bản đồ dưới có 2D | 3D, nút '▶▶ Chạy' có chữ (mẫu màn 6)
- ✅ [Xem từng bước] T1–T4: kích thước, nhãn, chồng lấn, thanh trạng thái
- ✅ Chọn 3D ở bản đồ dưới: nút 3D được chọn (báo cho trình đọc màn hình)
- ✅ Bấm ▶▶: tự chạy, nút đổi thành Tạm dừng
- ✅ Bấm Tạm dừng: dừng lại
- ✅ Nút Sau: đúng +1 (Bước 2/15 · còn 120 m -> Bước 3/15 · còn 150 m)
- ✅ Vuốt trái trên thẻ: sang đúng bước kế tiếp (Bước 3/15 · còn 150 m -> Bước 4/15 · còn 770 m)
- ✅ Nút danh sách: mở các bước rẽ
- ✅ Chọn một bước trong danh sách: về màn Xem từng bước tại bước đó
- ✅ Back từ Xem từng bước: về Chi tiết tuyến (không qua danh sách)
- ✅ Bắt đầu: băng chỉ hướng + nút Thoát
- ✅ Dẫn đường: nút kiểu bản đồ dưới ghi '2D' (mẫu màn 7)
- ✅ [Dẫn đường] T1–T4: kích thước, nhãn, chồng lấn, thanh trạng thái
- ✅ Bấm nút '2D': bản đồ dưới đổi sang 3D
- ✅ Dẫn đường: nút loa (mẫu màn 7), đủ lớn để bấm
- ✅ Bấm loa lần 1: Chỉ đọc cảnh báo
- ✅ Bấm loa lần 2: Tắt
- ✅ Bấm loa lần 3: Bật lại
- ✅ Dẫn đường › Đường đi: danh sách các bước rẽ
- ✅ [Đường đi] T1–T4: kích thước, nhãn, chồng lấn, thanh trạng thái
- ✅ Chạm một bước: mở Xem từng bước (chia đôi)
- ✅ Back trong Xem từng bước (mở từ Đường đi): về Đường đi
- ✅ Back từ Đường đi: về màn dẫn đường
- ✅ Thoát: về chi tiết tuyến, tuyến vẫn còn
- ✅ Back từ danh sách tuyến: về bản đồ, thanh tab dưới hiện lại

## Kiểm tra từng màn (ux_audit.py)

### Màn chào (S0)

1 phần tử bấm được · **ĐẠT**


### Bản đồ chính

17 phần tử bấm được · **ĐẠT**


### Trang địa điểm

6 phần tử bấm được · **ĐẠT**


### Bản đồ sau khi bấm chấm

17 phần tử bấm được · **ĐẠT**


### Bản đồ zoom xa

18 phần tử bấm được · **ĐẠT**


### Khám phá

17 phần tử bấm được · **ĐẠT**


### Điểm đến Việt Nam

13 phần tử bấm được · **ĐẠT**


### Cẩm nang TP.HCM

6 phần tử bấm được · **ĐẠT**


### Món ăn

4 phần tử bấm được · **ĐẠT**


### Tự lên kế hoạch

21 phần tử bấm được · **ĐẠT**


### Hành trình Của tôi

11 phần tử bấm được · **ĐẠT**


### Hành trình

7 phần tử bấm được · **ĐẠT**


### Kế hoạch chuyến

3 phần tử bấm được · **ĐẠT**


### Kế hoạch đang chọn phương tiện

16 phần tử bấm được · **ĐẠT**


### Bản đồ kế hoạch toàn màn hình

4 phần tử bấm được · **ĐẠT**


### Kế hoạch đang mở một điểm

20 phần tử bấm được · **ĐẠT**


### Kế hoạch đang đi

15 phần tử bấm được · **ĐẠT**


### Hồ sơ

8 phần tử bấm được · **ĐẠT**


### Thiết kế portfolio

9 phần tử bấm được · **ĐẠT**


### Cài đặt nền tối

2 phần tử bấm được · **ĐẠT**


### Đi đâu?

4 phần tử bấm được · **ĐẠT**


### Danh sách tuyến

11 phần tử bấm được · **ĐẠT**


### Chi tiết tuyến

5 phần tử bấm được · **ĐẠT**


### Xem từng bước

9 phần tử bấm được · **ĐẠT**


### Dẫn đường

4 phần tử bấm được · **ĐẠT**


### Đường đi

15 phần tử bấm được · **ĐẠT**


