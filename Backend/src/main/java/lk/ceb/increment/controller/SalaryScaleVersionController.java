package lk.ceb.increment.controller;

import jakarta.validation.Valid;
import lk.ceb.increment.dto.request.SalaryScaleVersionCreateRequest;
import lk.ceb.increment.dto.response.SalaryScaleVersionResponse;
import lk.ceb.increment.service.SalaryScaleVersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/salary-scale-versions")
@RequiredArgsConstructor
public class SalaryScaleVersionController {

    private final SalaryScaleVersionService salaryScaleVersionService;

    @GetMapping
    public ResponseEntity<List<SalaryScaleVersionResponse>> getAll() {
        return ResponseEntity.ok(salaryScaleVersionService.getAll());
    }

    @GetMapping("/{salaryTypeCode}")
    public ResponseEntity<List<SalaryScaleVersionResponse>> getBySalaryTypeCode(@PathVariable String salaryTypeCode) {
        return ResponseEntity.ok(salaryScaleVersionService.getBySalaryTypeCode(salaryTypeCode));
    }

    @GetMapping("/effective/{salaryTypeCode}")
    public ResponseEntity<SalaryScaleVersionResponse> getEffectiveVersion(
            @PathVariable String salaryTypeCode,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate onDate) {
        return ResponseEntity.ok(salaryScaleVersionService.getEffectiveVersion(salaryTypeCode, onDate));
    }

    @PostMapping
    public ResponseEntity<SalaryScaleVersionResponse> create(@Valid @RequestBody SalaryScaleVersionCreateRequest request) {
        return ResponseEntity.ok(salaryScaleVersionService.create(request));
    }
}