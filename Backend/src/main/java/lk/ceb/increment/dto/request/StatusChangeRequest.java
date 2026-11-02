package lk.ceb.increment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class StatusChangeRequest {

    @NotNull
    private String newStatus; // matches IncrementStatus enum name

    @NotBlank
    private String changedBy; // login username

    /** Required when status is AP_STOPPED, AP_PAUSED, or REJECTED */
    private String notes;
}
