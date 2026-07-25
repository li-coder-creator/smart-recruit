package com.recruit.smartrecruit.mapper;
import com.recruit.smartrecruit.entity.Resume;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ResumeMapper {
    //添加简历
    @Insert("""
            INSERT INTO resume
            (
                user_id,
                title,
                description,
                file_url
            ) VALUES
            (
                #{userId},
                #{title},
                #{description},
                #{fileUrl}
            )
            """)
    void add(Resume resume);
    //获取当前用户所有简历
    @Select("""
            SELECT *
            FROM resume
            WHERE user_id= #{userId}
            ORDER BY create_time DESC
            """)
    List<Resume> findByUserId(Long userId);
    //查询简历详情
    @Select("""
            SELECT *
            FROM resume
            WHERE id= #{Id}
            """)
    Resume findById(Long id);
    //修改简历
    @Update("""
            UPDATE resume
            SET
            title=#{title},
            description=#{description},
            file_url=#{fileUrl}  
            WHERE id=#{id}
            """
    )
    void update(Resume newResume);
    //删除简历
    @Delete("""
            DELETE
            FROM resume
            WHERE id=#{id}
            """)
    void delete(Long id);
}
