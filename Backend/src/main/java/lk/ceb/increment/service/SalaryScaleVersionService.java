package lk.ceb.increment.service;

import lk.ceb.increment.dto.request.SalaryScaleVersionCreateRequest;
import lk.ceb.increment.dto.response.SalaryScaleVersionResponse;
import lk.ceb.increment.entity.SalaryScaleVersion;
import lk.ceb.increment.exception.ResourceNotFoundException;
import lk.ceb.increment.repository.SalaryScaleVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SalaryScaleVersionService {

    private final SalaryScaleVersionRepository salaryScaleVersionRepository;

    public List<SalaryScaleVersionResponse> getAll() {
        return salaryScaleVersionRepository.findAll().stream()
                .sorted((a, b) -> {
                    int codeCompare = a.getSalaryTypeCode().compareToIgnoreCase(b.getSalaryTypeCode());
                    if (codeCompare != 0) {
                        return codeCompare;
                    }
                    return b.getEffectiveFrom().compareTo(a.getEffectiveFrom());
                })
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<SalaryScaleVersionResponse> getBySalaryTypeCode(String salaryTypeCode) {
        return salaryScaleVersionRepository.findBySalaryTypeCodeOrderByEffectiveFromDesc(salaryTypeCode).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public SalaryScaleVersionResponse getEffectiveVersion(String salaryTypeCode, LocalDate onDate) {
        return toResponse(findEffectiveVersionEntity(salaryTypeCode, onDate)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Salary scale version not found for " + salaryTypeCode + " on " + onDate)));
    }

    @Transactional
    public SalaryScaleVersionResponse create(SalaryScaleVersionCreateRequest request) {
        String code = request.getSalaryTypeCode().trim();
        LocalDate effectiveFrom = request.getEffectiveFrom();

        if (salaryScaleVersionRepository.existsBySalaryTypeCodeAndEffectiveFrom(code, effectiveFrom)) {
            throw new IllegalArgumentException(
                    "Salary scale version already exists for " + code + " on " + effectiveFrom);
        }

        Optional<SalaryScaleVersion> currentVersion =
                salaryScaleVersionRepository.findTopBySalaryTypeCodeAndEffectiveToIsNullOrderByEffectiveFromDesc(code);

        if (currentVersion.isPresent() && !effectiveFrom.isAfter(currentVersion.get().getEffectiveFrom())) {
            throw new IllegalArgumentException(
                    "New version must start after the current version for " + code);
        }

        currentVersion.ifPresent(version -> {
            version.setEffectiveTo(effectiveFrom);
            salaryScaleVersionRepository.save(version);
        });

        SalaryScaleVersion version = SalaryScaleVersion.builder()
                .salaryTypeCode(code)
                .scaleString(request.getScaleString().trim())
                .effectiveFrom(effectiveFrom)
                .effectiveTo(null)
                .createdBy(request.getCreatedBy().trim())
                .build();

        return toResponse(salaryScaleVersionRepository.save(version));
    }

    public String resolveScaleString(String salaryTypeCode, LocalDate onDate) {
        return findEffectiveVersionEntity(salaryTypeCode, onDate)
                .map(SalaryScaleVersion::getScaleString)
                .orElseThrow(() -> new IllegalStateException(
                        "No salary scale version found for " + salaryTypeCode + " on " + onDate));
    }

    private Optional<SalaryScaleVersion> findEffectiveVersionEntity(String salaryTypeCode, LocalDate onDate) {
        if (salaryTypeCode == null || salaryTypeCode.isBlank() || onDate == null) {
            return Optional.empty();
        }
        return salaryScaleVersionRepository.findEffectiveVersion(salaryTypeCode.trim(), onDate);
    }

    private SalaryScaleVersionResponse toResponse(SalaryScaleVersion version) {
        return SalaryScaleVersionResponse.builder()
                .id(version.getId())
                .salaryTypeCode(version.getSalaryTypeCode())
                .scaleString(version.getScaleString())
                .effectiveFrom(version.getEffectiveFrom())
                .effectiveTo(version.getEffectiveTo())
                .createdAt(version.getCreatedAt())
                .createdBy(version.getCreatedBy())
                .build();
    }
}