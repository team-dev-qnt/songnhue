package com.songnhue.content.infra;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.songnhue.content.domain.Tag;

/** Truy vấn thẻ bài viết. */
public interface TagRepository extends JpaRepository<Tag, Long> {

    Optional<Tag> findBySlug(String slug);
}
