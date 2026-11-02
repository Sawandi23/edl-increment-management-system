package lk.ceb.increment.service;

import lk.ceb.increment.dto.response.SalaryTypeResponse;
import lk.ceb.increment.entity.SalaryType;
import lk.ceb.increment.exception.ResourceNotFoundException;
import lk.ceb.increment.repository.SalaryTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SalaryTypeService {

    private final SalaryTypeRepository salaryTypeRepository;

    public List<SalaryTypeResponse> getAll() {
        return salaryTypeRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public SalaryTypeResponse getByCode(String code) {
        SalaryType type = salaryTypeRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Salary type not found: " + code));
        return toResponse(type);
    }

    private SalaryTypeResponse toResponse(SalaryType t) {
        return SalaryTypeResponse.builder()
                .id(t.getId())
                .code(t.getCode())
                .description(t.getDescription())
                .startSalary(t.getStartSalary().doubleValue())
                .band1Increment(t.getBand1Increment().doubleValue())
                .band1Years(t.getBand1Years())
                .band2Increment(t.getBand2Increment().doubleValue())
                .band2Years(t.getBand2Years())
                .maxSalary(t.getMaxSalary().doubleValue())
                .scaleString(t.getScaleString())
                .build();
    }
}
