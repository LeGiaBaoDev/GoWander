# GoWander — Complete Project Documentation

> Converted from `GoWander.pdf`. The content and order follow the original PDF.
> Tables with complex PDF layouts are preserved as fixed-width text for fidelity.

---

<!-- Original PDF page 1 -->

---

## GOWANDER

### PROJECT OVERVIEW & DEVELOPMENT ROADMAP

Version: 4.0 — Unified Product, Administration & Business Architecture

Tagline: Move Smart. Explore More.

Product: Smart Mobility · Urban Discovery · Community

Platforms: Android / iOS + Admin Web

Initial Market: Thành phố Hồ Chí Minh, Việt Nam

Team Model: Solo Founder / Solo Developer

Business Direction: B2B2C — chưa xác thực thương mại

Status: Research & MVP Planning — Technical PoC Pending

### 01. GIỚI THIỆU DỰ ÁN

GoWander là nền tảng hỗ trợ di chuyển thông minh kết hợp khám phá đô thị, lưu giữ trải nghiệm cá
nhân và kết nối cộng đồng, hướng đến du khách trong nước và quốc tế khi đến một thành phố chưa
quen thuộc.

Sản phẩm giải quyết hai điểm bắt đầu của hành trình:

Destination-first: Người dùng đã biết điểm đến. Họ chọn điểm xuất phát và điểm đến, tìm phương án
xe buýt, metro hoặc đi bộ, so sánh thời gian, số lần trung chuyển và chi phí khi có dữ liệu đáng tin
cậy, sau đó chọn hành trình phù hợp.

Discover-first: Người dùng chưa biết đi đâu. Họ tìm món ăn, địa danh, Hidden Gems, Historical
Stories và Trip Ideas, chọn một địa điểm rồi chuyển sang tìm đường.

GoWander hướng đến vòng trải nghiệm:

Discover → Plan → Navigate → Experience → Remember → Share.

Dự án không đặt mục tiêu thay thế toàn bộ Google Maps, không xây một mạng xã hội tổng quát và
không cam kết dữ liệu transit realtime khi chưa có nguồn hợp lệ.

#### 1.1. Tầm nhìn


Trở thành người bạn đồng hành đáng tin cậy giúp du khách di chuyển thuận tiện, khám phá trải
nghiệm địa phương, lưu giữ kỷ niệm và kết nối với cộng đồng cùng sở thích.

#### 1.2. Sứ mệnh

- ​   Đơn giản hóa việc lựa chọn và sử dụng phương tiện công cộng.
- ​   Kết nối tìm tuyến với khám phá văn hóa, địa danh và ẩm thực.
- ​   Tạo trải nghiệm khám phá cá nhân dễ sử dụng và riêng tư.
- ​   Khuyến khích nội dung chia sẻ có chất lượng.
- ​   Cải thiện sản phẩm dựa trên dữ liệu thực tế.
- ​   Phát triển doanh thu khi có mô hình hợp tác được xác nhận.

#### 1.3. Giá trị sản phẩm

    Giá trị                                 Ý nghĩa

    Move Smart                   Tìm và so sánh hành trình dễ hiểu

    Explore More                 Khám phá địa điểm, món ăn và câu chuyện

    Connect Local                Tiếp cận cộng đồng và trải nghiệm địa phương

    Remember Your Journey        Lưu giữ các địa điểm và kỷ niệm

    Discover Together            Chia sẻ và theo dõi trải nghiệm hữu ích

### 02. ĐỐI TƯỢNG NGƯỜI DÙNG

#### 2.1. B2C — Người dùng ứng dụng

International Independent Tourists: Khách nước ngoài đi tự túc, cần thông tin dễ hiểu, hỗ trợ đa
ngôn ngữ.

Domestic Independent Tourists: Khách nội địa muốn tìm tuyến, địa điểm và món ăn địa phương.

Short-stay Visitors: Người có thời gian lưu trú ngắn, cần chọn điểm đến và hành trình hiệu quả.

Social Explorers: Người quan tâm đến đánh giá, chia sẻ và Follow.

Travel Memory Keepers: Người muốn xây dựng bản đồ khám phá và nhật ký cá nhân.

Các phân khúc là giả thuyết định hướng, chưa phải kết quả khảo sát riêng của GoWander.

#### 2.2. B2B — Đối tác tiềm năng

- ​   Nhà hàng, quán ăn, quán cà phê.
- ​   Đơn vị tổ chức trải nghiệm địa phương.
- ​   Điểm tham quan, dịch vụ du lịch.
- ​   Cửa hàng đặc sản, quà lưu niệm.
- ​   Đối tác affiliate, vé, tour, lưu trú và eSIM.

#### 2.3. Người vận hành

Content Editor: Biên tập địa điểm, ảnh, món ăn và Historical Stories.

Moderator: Kiểm duyệt bài đăng, reviews và báo cáo.

Operations Admin: Quản lý nguồn transit, feedback và tình trạng hệ thống.

Business Admin: Theo dõi đối tác, giao dịch và doanh thu khi thương mại hóa.

System Admin: Quản trị quyền, cấu hình và nhật ký thao tác.

Một người có thể đảm nhận nhiều vai trò trong giai đoạn đầu.

### 03. VẤN ĐỀ VÀ CƠ HỘI

    Mã                          Vấn đề                            Giải pháp dự kiến

    P1        Khó kết hợp bus, metro, đi bộ                   Multimodal Routing

    P2        Khó hiểu trạm và trung chuyển                   Transit Guidance

    P3        Không dễ so sánh hành trình                     Route Comparison

    P4        Chưa biết đi đâu, ăn gì                         Discover-first

    P5        Bỏ lỡ địa điểm gần tuyến                  Route-aware Discovery

    P6        Thông tin du lịch phân tán                Integrated Experience

    P7        Muốn lưu giữ nơi đã đi                    My Exploration Map

    P8        Muốn học hỏi cộng đồng                    Community & Following

    P9        Cần ghi nhận đóng góp hữu ích             Verified Reward Points

    P10       Doanh nghiệp muốn tiếp cận du khách       Future Partnerships

Đây là các vấn đề cần phân biệt giữa bằng chứng từ nghiên cứu công khai và giả thuyết chưa xác thực
của GoWander.

