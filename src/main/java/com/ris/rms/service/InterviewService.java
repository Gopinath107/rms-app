package com.ris.rms.service;

import java.util.List;

import com.ris.rms.dto.InterviewDto;

public interface InterviewService {
    InterviewDto create(InterviewDto dto);

    InterviewDto getById(Long id);

    List<InterviewDto> list(Long requestId, Long employeeId, String status, String interviewType, Integer page,
            Integer size);

    InterviewDto updateWithRequestCheck(Long interviewId, Long requestId, InterviewDto dto);

    InterviewDto cancel(Long interviewId, Long requestId, String reason);

    void delete(Long id);

    InterviewDto noShow(Long interviewId, Long requestId, String who, String feedback, List<String> levels);

    InterviewDto updateOnboarding(Long interviewId, String status, String note);

    InterviewDto completeLevels(Long interviewId, Long requestId, List<String> levels, String notes,
            Long interviewerUserId, String decision);

    InterviewDto createBatchNoInterviewer(InterviewDto dto);
}
