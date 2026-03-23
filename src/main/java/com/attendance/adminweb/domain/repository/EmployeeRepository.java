package com.attendance.adminweb.domain.repository;

import com.attendance.adminweb.domain.entity.Employee;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    @EntityGraph(attributePaths = "company")
    Optional<Employee> findByEmployeeCode(String employeeCode);

    @EntityGraph(attributePaths = "company")
    List<Employee> findAllByCompanyIdOrderByNameAsc(Long companyId);

    @EntityGraph(attributePaths = "company")
    List<Employee> findAllByCompanyIdAndActiveTrueOrderByNameAsc(Long companyId);

    boolean existsByEmployeeCode(String employeeCode);

    boolean existsByEmployeeCodeAndIdNot(String employeeCode, Long id);
}
