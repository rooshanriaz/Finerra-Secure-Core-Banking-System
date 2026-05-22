package com.fyp.kyc.service;

import com.fyp.kyc.dto.AmlScreeningResponse;
import com.fyp.kyc.dto.AmlScreeningResponse.MatchDetail;
import com.fyp.kyc.entity.AmlCheck;
import com.fyp.kyc.entity.SanctionEntry;
import com.fyp.kyc.repository.AmlCheckRepository;
import com.fyp.kyc.repository.SanctionEntryRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * AML (Anti-Money Laundering) sanctions screening service.
 * Screens names and CNIC hashes against a mock sanctions list.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AmlScreeningService {

    private final SanctionEntryRepository sanctionEntryRepository;
    private final AmlCheckRepository amlCheckRepository;

    @Value("${aml.screening.name-match-threshold:0.85}")
    private double nameMatchThreshold;

    /**
     * Initialize mock sanctions data on startup.
     */
    @PostConstruct
    public void initSanctionsData() {
        if (sanctionEntryRepository.count() == 0) {
            log.info("Loading mock sanctions data...");
            List<SanctionEntry> entries = List.of(
                SanctionEntry.builder()
                    .fullName("Ahmed Khan Terrorist")
                    .listName("NACTA").entityType("INDIVIDUAL")
                    .country("PK").reason("Terrorism financing").active(true).build(),
                SanctionEntry.builder()
                    .fullName("Mohammad Fraud Criminal")
                    .listName("SBP_LIST").entityType("INDIVIDUAL")
                    .country("PK").reason("Financial fraud").active(true).build(),
                SanctionEntry.builder()
                    .fullName("Fake Organization LLC")
                    .listName("UN_SANCTIONS").entityType("ORGANIZATION")
                    .country("PK").reason("Sanctions violation").active(true).build(),
                SanctionEntry.builder()
                    .fullName("Ali Sanctioned Person")
                    .cnicHash("a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2")
                    .listName("NACTA").entityType("INDIVIDUAL")
                    .country("PK").reason("Money laundering").active(true).build(),
                SanctionEntry.builder()
                    .fullName("Terrorist Finance Network")
                    .listName("UN_SANCTIONS").entityType("ORGANIZATION")
                    .country("INTL").reason("Terror financing network").active(true).build()
            );
            sanctionEntryRepository.saveAll(entries);
            log.info("Loaded {} mock sanctions entries", entries.size());
        }
    }

    /**
     * Screen a person against sanctions lists.
     * 
     * @param fullName     Full name to screen
     * @param cnicHash     CNIC hash to check
     * @param kycRefId     KYC reference for audit trail
     * @return Screening response with results
     */
    public AmlScreeningResponse screenPerson(String fullName, String cnicHash, String kycRefId) {
        log.info("AML screening for: {} (ref: {})", fullName, kycRefId);

        List<MatchDetail> matches = new ArrayList<>();

        // 1. Exact CNIC hash match
        List<SanctionEntry> cnicMatches = sanctionEntryRepository.findByCnicHash(cnicHash);
        for (SanctionEntry entry : cnicMatches) {
            matches.add(MatchDetail.builder()
                .matchedName(entry.getFullName())
                .listName(entry.getListName())
                .matchScore(1.0)
                .entityType(entry.getEntityType())
                .reason(entry.getReason())
                .build());
        }

        // 2. Name similarity search
        List<SanctionEntry> nameMatches = sanctionEntryRepository.findByActiveTrue();
        for (SanctionEntry entry : nameMatches) {
            double similarity = calculateNameSimilarity(fullName, entry.getFullName());
            if (similarity >= nameMatchThreshold) {
                // Avoid duplicates from CNIC match
                boolean alreadyMatched = matches.stream()
                    .anyMatch(m -> m.getMatchedName().equals(entry.getFullName()));
                if (!alreadyMatched) {
                    matches.add(MatchDetail.builder()
                        .matchedName(entry.getFullName())
                        .listName(entry.getListName())
                        .matchScore(similarity)
                        .entityType(entry.getEntityType())
                        .reason(entry.getReason())
                        .build());
                }
            }
        }

        // Determine result
        AmlCheck.ScreeningResult result;
        AmlCheck.RiskLevel riskLevel;
        String details;

        if (!matches.isEmpty()) {
            double maxScore = matches.stream().mapToDouble(MatchDetail::getMatchScore).max().orElse(0);
            if (maxScore >= 0.95) {
                result = AmlCheck.ScreeningResult.MATCH_FOUND;
                riskLevel = AmlCheck.RiskLevel.CRITICAL;
                details = "High-confidence match found in sanctions list.";
            } else {
                result = AmlCheck.ScreeningResult.POTENTIAL_MATCH;
                riskLevel = AmlCheck.RiskLevel.HIGH;
                details = "Potential match found. Manual review recommended.";
            }
        } else {
            result = AmlCheck.ScreeningResult.CLEAR;
            riskLevel = AmlCheck.RiskLevel.LOW;
            details = "No matches found in sanctions lists.";
        }

        // Save audit record
        AmlCheck amlCheck = AmlCheck.builder()
            .kycReferenceId(kycRefId)
            .nameSearched(fullName)
            .cnicHash(cnicHash)
            .result(result)
            .matchScore(matches.isEmpty() ? 0.0 : matches.stream().mapToDouble(MatchDetail::getMatchScore).max().orElse(0))
            .matchedEntity(matches.isEmpty() ? null : matches.get(0).getMatchedName())
            .sanctionsListName(matches.isEmpty() ? null : matches.get(0).getListName())
            .riskLevel(riskLevel)
            .details(details)
            .build();
        amlCheckRepository.save(amlCheck);

        log.info("AML screening result: {} (risk: {}) for ref: {}", result, riskLevel, kycRefId);

        return AmlScreeningResponse.builder()
            .result(result.name())
            .riskLevel(riskLevel.name())
            .matches(matches.isEmpty() ? null : matches)
            .details(details)
            .build();
    }

    /**
     * Search sanctions list by name.
     */
    public List<SanctionEntry> searchSanctions(String name) {
        return sanctionEntryRepository.searchByName(name);
    }

    /**
     * Calculate name similarity using Levenshtein distance normalized to 0-1.
     */
    private double calculateNameSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) return 0;
        
        String a = s1.toLowerCase().trim();
        String b = s2.toLowerCase().trim();
        
        if (a.equals(b)) return 1.0;
        if (a.isEmpty() || b.isEmpty()) return 0.0;

        int maxLen = Math.max(a.length(), b.length());
        int distance = levenshteinDistance(a, b);
        return 1.0 - ((double) distance / maxLen);
    }

    private int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= s2.length(); j++) dp[0][j] = j;

        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        return dp[s1.length()][s2.length()];
    }
}
