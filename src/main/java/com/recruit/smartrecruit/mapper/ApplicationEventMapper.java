package com.recruit.smartrecruit.mapper;

import com.recruit.smartrecruit.entity.ApplicationEvent;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

/** 业务事件只追加，不提供更新或删除方法。 */
@Mapper
public interface ApplicationEventMapper {
    @Insert("""
            INSERT INTO application_event
                (application_id, event_type, from_status, to_status, interview_id,
                 operator_user_id, actor_type, occurred_at, detail_json)
            VALUES
                (#{applicationId}, #{eventType}, #{fromStatus}, #{toStatus}, #{interviewId},
                 #{operatorUserId}, #{actorType}, #{occurredAt}, #{detailJson})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(ApplicationEvent event);
}
