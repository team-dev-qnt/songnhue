package com.songnhue.content.infra;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.songnhue.content.domain.ContactNote;

/** Truy vấn ghi chú nội bộ — CN-01.4. */
public interface ContactNoteRepository extends JpaRepository<ContactNote, Long> {

    Optional<ContactNote> findByPublicIdAndDeletedAtIsNull(UUID publicId);

    List<ContactNote> findAllByContactIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long contactId);
}
