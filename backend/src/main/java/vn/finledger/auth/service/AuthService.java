package vn.finledger.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.finledger.auth.domain.User;
import vn.finledger.auth.dto.*;
import vn.finledger.auth.repository.UserRepository;
import vn.finledger.auth.security.JwtService;
import vn.finledger.ledger.domain.AccountType;
import vn.finledger.ledger.dto.CreateAccountRequest;
import vn.finledger.ledger.service.LedgerService;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LedgerService ledgerService;

    private record CategorySeed(String name, String icon, String color) {}

    private static final List<CategorySeed> DEFAULT_EXPENSE_CATEGORIES = List.of(
            new CategorySeed("Ăn uống", "utensils", "#ef4444"),
            new CategorySeed("Thuê nhà & Điện nước", "home", "#f59e0b"),
            new CategorySeed("Mua sắm", "shopping-bag", "#ec4899"),
            new CategorySeed("Di chuyển & Xăng xe", "car", "#3b82f6"),
            new CategorySeed("Siêu thị & Tạp hóa", "shopping-cart", "#10b981"),
            new CategorySeed("Sức khỏe & Y tế", "heart-pulse", "#f43f5e"),
            new CategorySeed("Giải trí & Du lịch", "film", "#8b5cf6"),
            new CategorySeed("Giáo dục", "graduation-cap", "#06b6d4"),
            new CategorySeed("Hóa đơn & Dịch vụ", "receipt", "#6366f1"),
            new CategorySeed("Chi phí khác", "more-horizontal", "#64748b")
    );

    private static final List<CategorySeed> DEFAULT_INCOME_CATEGORIES = List.of(
            new CategorySeed("Lương", "briefcase", "#22c55e"),
            new CategorySeed("Thưởng & Phụ cấp", "award", "#10b981"),
            new CategorySeed("Đầu tư & Tiết kiệm", "trending-up", "#06b6d4"),
            new CategorySeed("Thu nhập ngoài", "laptop", "#8b5cf6"),
            new CategorySeed("Được tặng / Hoàn tiền", "gift", "#f59e0b"),
            new CategorySeed("Thu nhập khác", "plus-circle", "#64748b")
    );

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new IllegalArgumentException("Email này đã được đăng ký. Vui lòng đăng nhập hoặc dùng email khác.");
        }

        User user = User.builder()
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName().trim())
                .defaultCurrency("VND")
                .avatarColor("#22c55e")
                .build();

        user = userRepository.save(user);

        provisionDefaultPersonalAccounts(user.getId(), user.getDefaultCurrency());

        String token = jwtService.generateToken(user);
        log.info("Registered new user: id={}, email={}", user.getId(), user.getEmail());

        return AuthResponse.builder()
                .token(token)
                .user(mapToProfile(user))
                .build();
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new BadCredentialsException("Email hoặc mật khẩu không chính xác."));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Email hoặc mật khẩu không chính xác.");
        }

        String token = jwtService.generateToken(user);
        return AuthResponse.builder()
                .token(token)
                .user(mapToProfile(user))
                .build();
    }

    @Transactional(readOnly = true)
    public UserProfileDto getProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản người dùng."));
        return mapToProfile(user);
    }

    @Transactional
    public UserProfileDto updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản người dùng."));
        user.setFullName(request.getFullName().trim());
        if (request.getAvatarColor() != null && !request.getAvatarColor().isBlank()) {
            user.setAvatarColor(request.getAvatarColor().trim());
        }
        return mapToProfile(userRepository.save(user));
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản người dùng."));
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Mật khẩu hiện tại không chính xác.");
        }
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    private void provisionDefaultPersonalAccounts(UUID userId, String currency) {
        // 1. System Equity account for Double-Entry Opening Balances & Balance Adjustments
        ledgerService.createAccount(CreateAccountRequest.builder()
                .userId(userId)
                .name("Vốn đối ứng số dư")
                .accountType(AccountType.EQUITY)
                .accountSubtype("SYSTEM_EQUITY")
                .currency(currency)
                .initialBalance(BigDecimal.ZERO)
                .allowNegative(true)
                .includeInTotal(false)
                .icon("shield")
                .color("#64748b")
                .build());

        // 2. Default Cash Wallet (0 VND)
        ledgerService.createAccount(CreateAccountRequest.builder()
                .userId(userId)
                .name("Tiền mặt")
                .accountType(AccountType.ASSET)
                .accountSubtype("CASH")
                .currency(currency)
                .initialBalance(BigDecimal.ZERO)
                .allowNegative(false)
                .includeInTotal(true)
                .icon("wallet")
                .color("#f97316")
                .build());

        // 3. Default Expense Categories
        for (CategorySeed seed : DEFAULT_EXPENSE_CATEGORIES) {
            ledgerService.createAccount(CreateAccountRequest.builder()
                    .userId(userId)
                    .name(seed.name())
                    .accountType(AccountType.EXPENSE)
                    .accountSubtype("CATEGORY_EXPENSE")
                    .currency(currency)
                    .initialBalance(BigDecimal.ZERO)
                    .allowNegative(true)
                    .includeInTotal(false)
                    .icon(seed.icon())
                    .color(seed.color())
                    .build());
        }

        // 4. Default Income Categories
        for (CategorySeed seed : DEFAULT_INCOME_CATEGORIES) {
            ledgerService.createAccount(CreateAccountRequest.builder()
                    .userId(userId)
                    .name(seed.name())
                    .accountType(AccountType.REVENUE)
                    .accountSubtype("CATEGORY_INCOME")
                    .currency(currency)
                    .initialBalance(BigDecimal.ZERO)
                    .allowNegative(true)
                    .includeInTotal(false)
                    .icon(seed.icon())
                    .color(seed.color())
                    .build());
        }
    }

    private UserProfileDto mapToProfile(User user) {
        return UserProfileDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .defaultCurrency(user.getDefaultCurrency())
                .avatarColor(user.getAvatarColor())
                .createdAt(user.getCreatedAt())
                .build();
    }
}

