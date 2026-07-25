package com.recruit.smartrecruit.service;

import com.recruit.smartrecruit.entity.Job;

import java.util.List;

public interface JobService {
    void add(Job job, Long userId);

    List<Job> findAllJob();

    Job findById(long id);

    void update(Job newJob, Long userId);

    void delete(long id, Long userId);
}
