package com.fyp.kyc.repository;

import com.fyp.kyc.entity.SanctionEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SanctionEntryRepository extends JpaRepository<SanctionEntry, Long> {
    List<SanctionEntry> findByActiveTrue();
    List<SanctionEntry> findByCnicHash(String cnicHash);
    
    @Query("SELECT s FROM SanctionEntry s WHERE s.active = true AND LOWER(s.fullName) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<SanctionEntry> searchByName(@Param("name") String name);
    
    List<SanctionEntry> findByListName(String listName);
}
