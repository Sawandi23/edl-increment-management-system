package lk.ceb.increment.service;

import lk.ceb.increment.dto.response.IncrementStatusLogResponse;
import lk.ceb.increment.entity.IncrementStatusLog;
import lk.ceb.increment.exception.ResourceNotFoundException;
import lk.ceb.increment.repository.IncrementCycleRepository;
import lk.ceb.increment.repository.IncrementStatusLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IncrementCycleStatusHistoryService {

    private final IncrementStatusLogRepository statusLogRepository;
    private final IncrementCycleRepository cycleRepository;

    @Transactional(readOnly = true)
    public List<IncrementStatusLogResponse> getStatusHistory(Long cycleId) {
        if (!cycleRepository.existsById(cycleId)) {
            throw new ResourceNotFoundException("Increment cycle not found: " + cycleId);
        }

        return statusLogRepository.findByIncrementCycleIdOrderByChangedAtDesc(cycleId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private IncrementStatusLogResponse toResponse(IncrementStatusLog log) {
        return IncrementStatusLogResponse.builder()
                .id(log.getId())
                .oldStatus(log.getOldStatus())
                .newStatus(log.getNewStatus())
                .changedBy(log.getChangedBy())
                .changedAt(log.getChangedAt())
                .notes(log.getNotes())
                .build();
    }
}