package com.songnhue.content.infra;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.songnhue.content.domain.Contact;
import com.songnhue.content.domain.ContactStatus;

/** Truy vấn liên hệ — CN-01.4. */
public interface ContactRepository extends JpaRepository<Contact, Long> {

    Optional<Contact> findByPublicIdAndDeletedAtIsNull(UUID publicId);

    Page<Contact> findAllByDeletedAtIsNullOrderByCreatedAtDesc(Pageable pageable);

    Page<Contact> findAllByStatusAndDeletedAtIsNullOrderByCreatedAtDesc(ContactStatus status, Pageable pageable);

    long countByStatusAndDeletedAtIsNull(ContactStatus status);
}
