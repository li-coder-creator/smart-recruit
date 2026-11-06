package com.recruit.smartrecruit.entity.enums;

/** 普通岗位列表允许的排序方式；Mapper 只选择固定 SQL 分支。 */
public enum JobBrowseSort {
    UPDATED_DESC,
    /** 有效月薪上限降序，其余薪资记录置后。 */
    SALARY_DESC
}
