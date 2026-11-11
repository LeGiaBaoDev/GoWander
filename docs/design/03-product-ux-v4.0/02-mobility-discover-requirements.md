### 04. MOBILITY REQUIREMENTS

    ID               Feature                               Acceptance

    FR-01     Interactive Map          Pan/zoom/markers hoạt động Android và iOS

    FR-02     GPS                      Có quyền, từ chối và vị trí không khả dụng

    FR-03     Search                   Tìm theo tên/phạm vi; có empty/loading/error

    FR-04     Route Planning           Nhập điểm đi/đến, tìm tuyến từ provider hợp lệ

    FR-05     Route Comparison         Thời gian dự kiến, phương tiện, trung chuyển, walking

    FR-06     Route Details            Các chặng và điểm chuyển rõ ràng

    FR-07     Transit Stop             Tên trạm/tuyến theo dữ liệu hỗ trợ

    FR-08     Basic Navigation         Hiển thị hướng dẫn được dữ liệu chứng minh

    FR-09     Route-aware Discovery      Gợi ý Place gần tuyến phù hợp

    FR-10     Route Feedback             Báo sai tuyến/trạm hoặc thiếu thông tin

#### 4.1. Routing Data Labels

- ​    Scheduled Duration: thời gian theo lịch.
- ​    Realtime ETA: chỉ hiển thị khi có realtime hợp lệ.
- ​    Fare Estimate: chỉ khi có nguồn phí.
- ​    Unknown: không suy đoán giá trị.

#### 4.2. Routing Error States

    State                       UX Response

    No Route               Không có tuyến trong phạm vi dữ liệu

    Out of Coverage        Khu vực chưa được hỗ trợ

    Timeout                Thử lại hoặc thay đổi yêu cầu

    Provider Error         Thông báo tạm thời không khả dụng

    Permission Denied      Nhập điểm đi thủ công

    No Network             Retry và giữ thông tin đã nhập

Không hiển thị tuyến mô phỏng như tuyến giao thông thực.

### 05. DISCOVER REQUIREMENTS


    ID          Feature                               Acceptance

    FR-11    Discover Home        Hiển thị các danh mục và nội dung được xuất bản

    FR-12    Food Discovery       Phân biệt Food Item và Place

    FR-13    Place Discovery      Tìm địa điểm theo loại và vị trí

    FR-14    Historical Stories   Nội dung có nguồn, được biên tập

    FR-15    Hidden Gems          Có giải thích nguồn/gợi ý phù hợp

    FR-16    Trip Ideas           Các địa điểm theo thứ tự được biên tập

    FR-17    Place Details        Mô tả, ảnh, địa chỉ, nguồn phù hợp

    FR-18    Discover-to-Route    Chọn Place rồi chuyển sang Map/Route

    FR-19    Recommendation       Rule-based theo nhu cầu đã hỗ trợ

#### 5.1. Discover Screens

Discover Home: Cards, danh mục, nội dung nổi bật.

Food Detail: Mô tả món và các địa điểm phục vụ.

Place Detail: Ảnh, thông tin, nội dung văn hóa, nút Directions.

Historical Story: Nội dung dài, hình ảnh và nguồn.

Trip Idea: Danh sách điểm tham quan theo thứ tự, không đồng nghĩa với itinerary tự tối ưu.

#### 5.2. Content Quality UX


Khi thiếu giá vé hoặc giờ mở cửa, không nên hiển thị dữ liệu đoán.

Các nội dung tài trợ phải được phân biệt với nội dung biên tập.

