package vn.finledger.finance.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateCategoryRequest {

    @NotBlank(message = "Tên danh mục không được để trống")
    private String name;

    @NotBlank(message = "Loại danh mục không được để trống (EXPENSE hoặc INCOME)")
    private String type;

    private String icon;
    private String color;
}

