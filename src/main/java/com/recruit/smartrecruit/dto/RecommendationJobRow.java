package com.recruit.smartrecruit.dto;

import com.recruit.smartrecruit.entity.Job;
import lombok.*;

/** Mapper内部投影，不能作为公开API请求或响应直接接收/返回。 */
@Getter @Setter @NoArgsConstructor @EqualsAndHashCode(callSuper=true)
public class RecommendationJobRow extends Job {
    private String companyName;
    private String companyLogo;
    private Boolean applied;
}
