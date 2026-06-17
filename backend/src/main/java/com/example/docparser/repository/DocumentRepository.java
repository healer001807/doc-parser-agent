package com.example.docparser.repository;

import com.example.docparser.model.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Document> findByUserIdAndParseStatusOrderByCreatedAtDesc(Long userId, String parseStatus);
}
