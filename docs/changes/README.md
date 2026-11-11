# Nhật ký thay đổi (change records)

Mỗi thay đổi trong repository (code, cấu hình, schema, hạ tầng, dữ liệu, tài liệu) đều phải được ghi lại ở thư mục này, trong cùng nhánh và cùng commit với thay đổi đó.

## Quy ước

- **Tên file:** `change-DD-MM-YYYY.md` theo ngày thực hiện (giờ Asia/Ho_Chi_Minh). Ví dụ: `change-11-11-2026.md`.
- **Mỗi ngày một file.** Nếu file của hôm nay đã có, thêm một mục mới đánh số tiếp theo ở cuối file. Không sửa hay xoá các mục cũ; nếu mục cũ sai, viết mục mới đính chính.
- **Ngôn ngữ:** tiếng Việt, giữ nguyên tên kỹ thuật bằng tiếng Anh.
- **Trung thực:** chỉ ghi bằng chứng đã thực sự chạy. Phần nào chưa kiểm chứng thì ghi rõ "chưa kiểm chứng".
- Không ghi mật khẩu, token, dữ liệu cá nhân vào đây.

## Mẫu

```markdown
# Thay đổi ngày DD-MM-YYYY

## 1. <Tiêu đề ngắn của thay đổi>

- **Milestone / nhánh:** M0X · `ten-nhanh`
- **Người / agent thực hiện:**
- **Thay đổi gì:** mô tả ngắn gọn kết quả của thay đổi.
- **Ở đâu:** danh sách file/thư mục bị thêm, sửa, xoá.
- **Làm gì:** các bước đã thực hiện, lệnh quan trọng đã chạy.
- **Vì sao:** lý do, vấn đề cần giải quyết, liên kết ADR/milestone liên quan.
- **Bằng chứng / kiểm thử:** lệnh đã chạy và kết quả quan sát được (PASS / FAIL / chưa kiểm chứng).
- **Ảnh hưởng / rủi ro:** phần nào của hệ thống bị tác động, cách quay lại nếu cần.
- **Việc tiếp theo:**
```
