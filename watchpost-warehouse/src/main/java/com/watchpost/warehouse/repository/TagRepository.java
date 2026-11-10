package com.watchpost.warehouse.repository;

import com.watchpost.warehouse.entity.TagEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TagRepository extends JpaRepository<TagEntity, Long> {
    Optional<TagEntity> findByTagKeyAndTagValue(String tagKey, String tagValue);
}
