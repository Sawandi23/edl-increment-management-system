package lk.ceb.increment.dto.response;

import lk.ceb.increment.enums.IncrementStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class IncrementStatusLogResponse {
    private Long id;
    private IncrementStatus oldStatus;
    private IncrementStatus newStatus;
    private String changedBy;
    private LocalDateTime changedAt;
    private String notes;
}