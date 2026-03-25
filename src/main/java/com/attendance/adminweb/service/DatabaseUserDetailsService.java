package com.attendance.adminweb.service;

import com.attendance.adminweb.domain.entity.Employee;
import com.attendance.adminweb.domain.entity.EmployeeRole;
import com.attendance.adminweb.domain.repository.EmployeeRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final EmployeeRepository employeeRepository;

    public DatabaseUserDetailsService(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Employee employee = employeeRepository.findByEmployeeCode(username)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));

        if (employee.getRole() != EmployeeRole.ADMIN && employee.getRole() != EmployeeRole.WORKPLACE_ADMIN) {
            throw new UsernameNotFoundException("관리자 권한 계정만 로그인할 수 있습니다.");
        }

        return new User(
                employee.getEmployeeCode(),
                employee.getPassword(),
                employee.isActive(),
                true,
                true,
                true,
                List.of(new SimpleGrantedAuthority("ROLE_" + employee.getRole().name()))
        );
    }
}
