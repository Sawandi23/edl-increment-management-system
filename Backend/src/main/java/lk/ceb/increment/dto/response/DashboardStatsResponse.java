package lk.ceb.increment.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DashboardStatsResponse {
    private long dueThisPeriod;
    private long apFormsPending;
    private long sentToUnit;
    private long paidThisYear;
}
