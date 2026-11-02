package lk.ceb.increment.controller;

import lk.ceb.increment.dto.response.IncrementStatusLogResponse;
import lk.ceb.increment.service.IncrementCycleStatusHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/increment-cycles")
@RequiredArgsConstructor
public class IncrementCycleStatusHistoryController {

    private final IncrementCycleStatusHistoryService historyService;

    @GetMapping("/{id}/status-history")
    public ResponseEntity<List<IncrementStatusLogResponse>> getStatusHistory(@PathVariable Long id) {
        return ResponseEntity.ok(historyService.getStatusHistory(id));
    }
}