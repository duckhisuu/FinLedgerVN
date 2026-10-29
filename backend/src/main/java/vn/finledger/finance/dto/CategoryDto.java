package vn.finledger.finance.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryDto {
    private UUID id;
    private String name;
    private String type; // "EXPENSE" or "INCOME"
    private String icon;
    private String color;
}

