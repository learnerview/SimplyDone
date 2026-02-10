package com.learnerview.simplydone.service;

import com.learnerview.simplydone.entity.JobEntity;

public interface JobExecutorService {

    void execute(JobEntity job);
}
