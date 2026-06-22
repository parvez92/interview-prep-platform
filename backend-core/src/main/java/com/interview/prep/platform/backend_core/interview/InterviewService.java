package com.interview.prep.platform.backend_core.interview;

import com.interview.prep.platform.backend_core.common.error.ApiException;
import com.interview.prep.platform.backend_core.common.error.ErrorCode;
import com.interview.prep.platform.backend_core.interview.dto.*;
import com.interview.prep.platform.backend_core.study.Topic;
import com.interview.prep.platform.backend_core.study.TopicRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InterviewService {

    private final InterviewRepository interviewRepository;
    private final InterviewQuestionRepository questionRepository;
    private final ReviewFlagRepository flagRepository;
    private final TopicRepository topicRepository;
    private final FeedbackLoopService feedbackLoopService;

    public List<InterviewSummaryDto> listInterviews(Long userId) {
        return interviewRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(i -> new InterviewSummaryDto(i.getId(), i.getCompany(), i.getRole(),
                        i.getStage(), i.getRound(), i.getScheduledAt(), i.getOutcome(), weakSlugs(userId, i.getId())))
                .toList();
    }

    public InterviewDetailDto getInterview(Long userId, Long id) {
        Interview i = requireOwned(userId, id);
        List<InterviewQuestion> qs = questionRepository.findByInterviewId(id);
        List<InterviewDetailDto.QuestionDto> qDtos = qs.stream()
                .map(q -> new InterviewDetailDto.QuestionDto(q.getId(), q.getText(),
                        topicSlug(q.getTopicId()), q.getSelfRating()))
                .toList();
        return new InterviewDetailDto(i.getId(), i.getCompany(), i.getRole(), i.getStage(),
                i.getRound(), i.getScheduledAt(), i.getOutcome(), i.getJdText(), i.getNotes(),
                qDtos, weakSlugs(userId, id));
    }

    @Transactional
    public InterviewSummaryDto createInterview(Long userId, CreateInterviewDto dto) {
        Interview i = new Interview();
        i.setUserId(userId);
        i.setCompany(dto.company()); i.setRole(dto.role()); i.setStage(dto.stage());
        i.setRound(dto.round()); i.setScheduledAt(dto.scheduledAt()); i.setJdText(dto.jdText());
        interviewRepository.save(i);
        return new InterviewSummaryDto(i.getId(), i.getCompany(), i.getRole(),
                i.getStage(), i.getRound(), i.getScheduledAt(), i.getOutcome(), List.of());
    }

    @Transactional
    public InterviewSummaryDto updateInterview(Long userId, Long id, UpdateInterviewDto dto) {
        Interview i = requireOwned(userId, id);
        if (dto.stage() != null) i.setStage(dto.stage());
        if (dto.outcome() != null) i.setOutcome(dto.outcome());
        if (dto.round() != null) i.setRound(dto.round());
        if (dto.scheduledAt() != null) i.setScheduledAt(dto.scheduledAt());
        if (dto.jdText() != null) i.setJdText(dto.jdText());
        if (dto.notes() != null) i.setNotes(dto.notes());
        interviewRepository.save(i);
        return new InterviewSummaryDto(i.getId(), i.getCompany(), i.getRole(),
                i.getStage(), i.getRound(), i.getScheduledAt(), i.getOutcome(), weakSlugs(userId, id));
    }

    @Transactional
    public InterviewDetailDto logQuestion(Long userId, Long interviewId, LogQuestionDto dto) {
        Interview interview = requireOwned(userId, interviewId);
        Long topicId = dto.topicSlug() != null
                ? topicRepository.findByUserIdAndSlug(userId, dto.topicSlug()).map(Topic::getId).orElse(null)
                : null;
        InterviewQuestion q = new InterviewQuestion();
        q.setUserId(userId); q.setInterviewId(interviewId);
        q.setTopicId(topicId); q.setText(dto.text()); q.setSelfRating(dto.selfRating());
        questionRepository.save(q);
        feedbackLoopService.handleRating(q, interview.getCompany());
        return getInterview(userId, interviewId);
    }

    @Transactional
    public InterviewDetailDto updateQuestion(Long userId, Long questionId, LogQuestionDto dto) {
        InterviewQuestion q = questionRepository.findByIdAndUserId(questionId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND));
        Interview interview = requireOwned(userId, q.getInterviewId());
        if (dto.text() != null) q.setText(dto.text());
        q.setSelfRating(dto.selfRating());
        questionRepository.save(q);
        feedbackLoopService.handleRating(q, interview.getCompany());
        return getInterview(userId, q.getInterviewId());
    }

    @Transactional
    public void deleteQuestion(Long userId, Long questionId) {
        InterviewQuestion q = questionRepository.findByIdAndUserId(questionId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND));
        feedbackLoopService.onQuestionRemoved(q);
        questionRepository.delete(q);
    }

    public Interview requireOwned(Long userId, Long id) {
        return interviewRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND));
    }

    private List<String> weakSlugs(Long userId, Long interviewId) {
        return questionRepository.findByInterviewId(interviewId).stream()
                .filter(q -> q.getSelfRating() <= 2 && q.getTopicId() != null)
                .map(q -> topicSlug(q.getTopicId()))
                .filter(s -> s != null)
                .distinct().toList();
    }

    private String topicSlug(Long topicId) {
        if (topicId == null) return null;
        return topicRepository.findById(topicId).map(Topic::getSlug).orElse(null);
    }
}
