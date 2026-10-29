# FinLedger VN 🇻🇳
> **Ứng dụng Quản lý Tài chính Cá nhân, Ngân sách & Chia tiền Nhóm thông minh**

[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot 3.3](https://img.shields.io/badge/Spring%20Boot-3.3-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL 16](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![React 18](https://img.shields.io/badge/React-18-blue.svg)](https://react.dev/)

---

## ✨ Tính năng chính

1. **Xác thực & Cô lập Tài khoản Cá nhân (JWT Multi-Tenant):**
   - Đăng ký / Đăng nhập bảo mật bằng BCrypt + JWT.
   - Khi tạo tài khoản mới, hệ thống tự động khởi tạo ví **Tiền mặt (`0 ₫`)** và bộ **16 danh mục thu/chi chuẩn Việt Nam** (Ăn uống, Di chuyển, Tiền nhà, Mua sắm, Hóa đơn & Tiện ích, Tiền lương, Đầu tư...) — hoàn toàn sạch dữ liệu mẫu để sử dụng thực tế ngay lập tức.
2. **Tổng quan Tài chính (Money Lover Dark UI):**
   - Tổng số dư ròng có nút ẩn/hiện số dư riêng tư, danh sách **Ví của tôi**, biểu đồ **Xu hướng chi tiêu lũy kế trong tháng so với trung bình 3 tháng trước**, **Dòng tiền theo ngày**, **Chi tiêu nhiều nhất (Tuần / Tháng)** và **Giao dịch gần đây**.
3. **Quản lý Đa Ví & Sổ Giao dịch:**
   - Hỗ trợ Tiền mặt, Tài khoản ngân hàng Việt Nam (kèm liên kết tự động nhận biến động số dư), Ví điện tử, Thẻ tín dụng (cho phép hạn mức âm), Sổ tiết kiệm và Điều chỉnh số dư thực tế.
   - Ghi chép **Khoản chi**, **Khoản thu**, **Chuyển tiền giữa các ví**, và **Quét ảnh hóa đơn (OCR)** xem trước mặt hàng trước khi lưu.
4. **Ngân sách Tháng & Báo cáo Phân tích 6 Tháng:**
   - Thiết lập hạn mức chi tiêu cho từng danh mục hoặc tổng chi tháng, tự động tính **Mức chi tiêu khuyến nghị mỗi ngày** và cảnh báo khi vượt 80% / 100% ngân sách.
5. **Chia tiền Nhóm & Mã QR Chuyển khoản (VietQR):**
   - Tạo nhóm bạn bè/gia đình, chia hóa đơn đều / theo số tiền / theo phần trăm, tối giản số lượt chuyển khoản và chia sẻ link công khai kèm mã VietQR.

---

## 🚀 Khởi chạy nhanh (Quick Start)

### 1. Cấu hình biến môi trường
```bash
# Windows PowerShell
Copy-Item .env.example .env

# Linux / macOS / Git Bash
cp .env.example .env
```

### 2. Khởi động Hạ tầng (PostgreSQL 16, Redis 7 & OCR Worker)
```bash
docker compose --env-file .env -f infra/docker-compose.dev.yml up -d
```

### 3. Chạy Backend (Spring Boot 3.3+)
```bash
cd backend

# Chạy toàn bộ Integration & Concurrency Tests
./mvnw test

# Khởi chạy ứng dụng tại http://localhost:8080
./mvnw spring-boot:run
```

### 4. Chạy Frontend (React 18 + TypeScript + Vite + TailwindCSS + PWA)
```bash
cd frontend

npm install
npm run dev
```
Mở trình duyệt tại **`http://localhost:5173`**, chuyển sang tab **Đăng ký** để tạo tài khoản cá nhân đầu tiên của bạn.

---

## 🏛️ Nền tảng Bất biến Kế toán (Core Accounting Invariants)
Toàn bộ giao dịch cá nhân bên dưới giao diện đều vận hành trên lõi **Sổ cái Kép (Double-Entry Bookkeeping)** nghiêm ngặt:
- **Chính xác tuyệt đối:** Mọi giá trị tiền tệ dùng `java.math.BigDecimal` với `NUMERIC(19, 4)` và `RoundingMode.HALF_EVEN` (tuyệt đối không dùng `float`/`double`).
- **Cân bằng Sổ cái Kép:** Mỗi bút toán (`journal_entries`) luôn có ít nhất 2 dòng (`posting_legs`) với tổng đại số bằng đúng `0.0000`.
- **Append-Only Bất biến:** Cấm `UPDATE` / `DELETE` trực tiếp trên sổ cái bằng PostgreSQL Trigger (`trg_prevent_ledger_mutation`). Chỉnh sửa hoặc hủy giao dịch tự động sinh bút toán đảo (`reversal_of_id`).
- **Chống Deadlock Đa luồng:** Khóa bi quan (`SELECT ... FOR UPDATE`) luôn sắp xếp `account_id` tăng dần (`ORDER BY id ASC`).
