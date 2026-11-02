package lk.ceb.increment.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SalaryTypeResponse {
    private Long id;
    private String code;
    private String description;
    private Double startSalary;
    private Double band1Increment;
    private Integer band1Years;
    private Double band2Increment;
    private Integer band2Years;
    private Double maxSalary;
    private String scaleString;
}
