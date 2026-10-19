# FinLedger VN 🇻🇳
> **Nền tảng Kế toán Kép Tự động & Tối giản Công nợ Nhóm theo Chuẩn Ngân hàng Việt Nam**

[![FinLedger CI](https://github.com/your-username/finledger-vn/actions/workflows/ci.yml/badge.svg)](https://github.com/your-username/finledger-vn/actions/workflows/ci.yml)
[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot 3.3](https://img.shields.io/badge/Spring%20Boot-3.3-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL 16](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![React 18](https://img.shields.io/badge/React-18-blue.svg)](https://react.dev/)

---

## 🚀 Khởi chạy nhanh (Quick Start)

### 1. Cấu hình biến môi trường
Sao chép file mẫu `.env.example` thành `.env`:
```bash
# Windows PowerShell
Copy-Item .env.example .env

# Linux / macOS / Git Bash
cp .env.example .env
```
*(Lưu ý: File `.env` chứa mật khẩu cục bộ và đã được `.gitignore` bảo vệ, không bao giờ bị đẩy lên GitHub).*

### 2. Khởi động Hạ tầng Dev (PostgreSQL 16 & Redis 7)
```bash
docker compose -f infra/docker-compose.dev.yml up -d
```

### 3. Chạy Backend (Spring Boot 3.3+, Java 21)
```bash
cd backend

# Chạy toàn bộ Test suite (Double-Entry Invariant & Concurrency Deadlock Test)
./mvnw clean test

# Khởi chạy ứng dụng
./mvnw spring-boot:run
```
Backend API sẽ hoạt động tại: `http://localhost:8080`

### 4. Chạy Frontend (React 18 + Vite + TailwindCSS + PWA)
```bash
cd frontend

# Cài đặt thư viện
npm install

# Khởi chạy giao diện Dev
npm run dev
```
Giao diện người dùng sẽ hoạt động tại: `http://localhost:5173`

---

## 🏛️ Kiến trúc & Bất biến Kế toán (Financial Invariants)
- **Bất biến Số học:** Không sử dụng floating-point. Sử dụng `BigDecimal` (`NUMERIC(19,4)` và `RoundingMode.HALF_EVEN`).
- **Bất biến Sổ cái kép (Double-Entry):** Mỗi giao dịch chứa ít nhất 2 legs, tổng $\sum amount = 0.0000$.
- **Tính Bất biến (Immutability):** Cấm `UPDATE`/`DELETE` ở cấp Database Trigger (`trg_prevent_ledger_mutation`). Đảo giao dịch bằng reversing entry (`reversal_of_id`).
- **Khóa Bi quan Chống Deadlock:** Sắp xếp danh sách tài khoản theo thứ tự tăng dần cố định `ORDER BY a.id ASC` trước khi thực hiện `SELECT ... FOR UPDATE`.
