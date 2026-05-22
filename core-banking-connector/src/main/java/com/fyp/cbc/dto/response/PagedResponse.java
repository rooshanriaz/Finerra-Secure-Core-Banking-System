package com.fyp.cbc.dto.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Generic paged response wrapper for Fineract API responses.
 * Fineract returns paginated data in this format:
 * {
 *   "totalFilteredRecords": 100,
 *   "pageItems": [...]
 * }
 * 
 * @param <T> The type of items in the page
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PagedResponse<T> {
    
    /**
     * Total number of records matching the filter criteria.
     */
    private Integer totalFilteredRecords;
    
    /**
     * The items in the current page.
     */
    private List<T> pageItems;
}
