package vn.finledger.auth.dto;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfileDto {
    private UUID id;
    private String email;
    private String fullName;
    private String defaultCurrency;
    private String avatarColor;
    private Instant createdAt;
}

