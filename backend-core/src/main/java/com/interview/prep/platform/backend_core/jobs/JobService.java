package com.interview.prep.platform.backend_core.jobs;

import com.interview.prep.platform.backend_core.common.error.ApiException;
import com.interview.prep.platform.backend_core.common.error.ErrorCode;
import com.interview.prep.platform.backend_core.jobs.dto.JobAlertDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobService {

    private final JobAlertRepository jobAlertRepository;

    public List<JobAlertDto> list(Long userId) {
        return jobAlertRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toDto).toList();
    }

    @Transactional
    public JobAlertDto patch(Long userId, Long id, Map<String, Object> patch) {
        JobAlert job = jobAlertRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND));
        if (patch.containsKey("status")) job.setStatus((String) patch.get("status"));
        if (patch.containsKey("matchScore")) {
            Object score = patch.get("matchScore");
            job.setMatchScore(score instanceof Number n ? n.doubleValue() : null);
        }
        jobAlertRepository.save(job);
        return toDto(job);
    }

    @Transactional
    public JobAlert ingestFromEmail(Long userId, String emailMsgId, String company, String role, String jdText) {
        if (jobAlertRepository.findByEmailMsgId(emailMsgId).isPresent()) return null;
        JobAlert alert = new JobAlert();
        alert.setUserId(userId); alert.setEmailMsgId(emailMsgId);
        alert.setCompany(company); alert.setRole(role); alert.setJdText(jdText);
        return jobAlertRepository.save(alert);
    }

    private JobAlertDto toDto(JobAlert j) {
        return new JobAlertDto(j.getId(), j.getCompany(), j.getRole(),
                j.getStatus(), j.getMatchScore(), j.getJdText(), j.getCreatedAt());
    }
}
