package com.ris.rms.repository;
import com.ris.rms.entity.EmployeeStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface EmployeeStatusHistoryRepository extends JpaRepository<EmployeeStatusHistory, Long> {
    List<EmployeeStatusHistory> findByEmployeeIdOrderByChangedAtDesc(Long employeeId);
}
