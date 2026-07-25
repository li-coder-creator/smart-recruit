package com.recruit.smartrecruit.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationStatusUpdateDTO {
    @NotNull(message="投递状态不能为空")
    @Min(value = 0,message="投递状态不能小于0")
    @Max(value = 4,message="投递状态不能大于4")
    private Integer status;

}
