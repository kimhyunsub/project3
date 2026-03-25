package com.attendance.adminweb.domain.repository;

import com.attendance.adminweb.domain.entity.Workplace;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkplaceRepository extends JpaRepository<Workplace, Long> {

    List<Workplace> findAllByCompanyIdOrderByNameAsc(Long companyId);

    Optional<Workplace> findByIdAndCompanyId(Long workplaceId, Long companyId);
}
