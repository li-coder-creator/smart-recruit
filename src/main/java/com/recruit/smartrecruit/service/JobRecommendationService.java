package com.recruit.smartrecruit.service;

import com.recruit.smartrecruit.dto.JobRecommendationQueryDTO;
import com.recruit.smartrecruit.vo.RecommendationApiVO.*;

public interface JobRecommendationService {
    Options options(Long userId);
    Context context(Long userId,Long resumeId);
    QueryResult query(Long userId,JobRecommendationQueryDTO request);
}
