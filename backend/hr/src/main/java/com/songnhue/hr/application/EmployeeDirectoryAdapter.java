package com.songnhue.hr.application;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.spi.EmployeeDirectoryPort;
import com.songnhue.core.spi.EmployeeRef;
import com.songnhue.hr.domain.Employee;
import com.songnhue.hr.infra.EmployeeRepository;

/**
 * Cài đặt {@link EmployeeDirectoryPort} — T51.8.
 *
 * <p>Đúng khuôn {@code ConstructionLookupAdapter}: hợp đồng ở {@code core.spi}, cài đặt ở tầng
 * {@code application} của module sở hữu dữ liệu ({@code LayeringTest} đòi ranh giới giao dịch nằm ở
 * đây, ⛔ không ở {@code spi}), Spring nối hai đầu lúc dựng context. Nhờ vậy {@code core} gọi được
 * mà ⛔ không cần một dòng phụ thuộc Maven nào sang {@code hr} — thứ về nguyên tắc ⛔ không thể có,
 * vì mọi module đều phụ thuộc ngược lại vào {@code core}.
 *
 * <h2>⚠ Hai phương thức đi HAI con đường khác nhau, có chủ ý</h2>
 *
 * <p>{@link #timTheoPublicId} đi qua {@link EmployeeService#get} nên hưởng trọn {@code ScopeGuard}
 * — ⛔ không tự chép lại điều kiện phạm vi ở đây (quy tắc 5), và {@code ScopeGuard} còn ghi một
 * <b>sự kiện an ninh</b> mà một câu {@code WHERE} chép tay ⛔ không ghi.
 *
 * <p>{@link #timTheoId} đọc thẳng repository, ⛔ <b>không</b> lọc phạm vi — lý do ở javadoc của cổng.
 */
@Component
public class EmployeeDirectoryAdapter implements EmployeeDirectoryPort {

    private final EmployeeService employees;
    private final EmployeeRepository repository;

    public EmployeeDirectoryAdapter(EmployeeService employees, EmployeeRepository repository) {
        this.employees = employees;
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EmployeeRef> timTheoPublicId(UUID publicId) {
        if (publicId == null) {
            return Optional.empty();
        }
        // ⛔ Bắt ĐÚNG một loại. `PermissionDeniedException` (AUTH-3002) của ScopeGuard đi thẳng ra
        //    ngoài — xem javadoc cổng.
        try {
            return Optional.of(chuyen(employees.get(publicId)));
        } catch (ResourceNotFoundException e) {
            return Optional.empty();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EmployeeRef> timTheoId(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        // ⚠ `findById` ⛔ KHÔNG biết tới xoá mềm — lọc ở đây, đúng như hợp đồng đã hứa. Thiếu dòng
        //   này thì một hồ sơ đã nghỉ việc và bị xoá mềm vẫn hiện tên ở ô "Hồ sơ liên kết", và
        //   người quản trị tin rằng liên kết ấy còn sống.
        return repository.findById(id).filter(e -> e.getDeletedAt() == null).map(EmployeeDirectoryAdapter::chuyen);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, EmployeeRef> timTheoIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        List<Employee> tim = repository.findAllById(ids);
        Map<Long, EmployeeRef> ket = new HashMap<>(tim.size());
        for (Employee e : tim) {
            // ⚠ `findAllById` ⛔ KHÔNG biết tới xoá mềm — lọc ở đây, đúng như hợp đồng đã hứa.
            if (e.getDeletedAt() == null) {
                ket.put(e.getId(), chuyen(e));
            }
        }
        return ket;
    }

    private static EmployeeRef chuyen(Employee e) {
        return new EmployeeRef(e.getId(), e.getPublicId(), e.getCode(), e.getFullName());
    }
}
