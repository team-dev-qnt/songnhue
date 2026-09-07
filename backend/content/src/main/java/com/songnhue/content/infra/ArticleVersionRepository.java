package com.songnhue.content.infra;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.songnhue.content.domain.ArticleVersion;

/** Truy vấn phiên bản bài viết — CN-01.1 (so sánh, phục hồi). */
public interface ArticleVersionRepository extends JpaRepository<ArticleVersion, Long> {

    List<ArticleVersion> findByArticleIdOrderByVersionNoDesc(Long articleId);

    Optional<ArticleVersion> findByPublicId(UUID publicId);

    /** Số hiệu phiên bản kế tiếp. Trả 0 khi bài chưa có bản nào, nên bản đầu tiên là số 1. */
    @Query("SELECT COALESCE(MAX(v.versionNo), 0) FROM ArticleVersion v WHERE v.articleId = :articleId")
    int maxVersionNo(@Param("articleId") Long articleId);
}
