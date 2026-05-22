package com.fyp.auth.service;

import com.fyp.auth.config.AbacProperties;
import com.fyp.auth.repository.IpWhitelistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class LightAbacServiceTest {

    @Mock
    private IpWhitelistRepository ipWhitelistRepository;

    @Mock
    private AuditService auditService;

    private AbacProperties abacProperties;
    private LightAbacService lightAbacService;

    @BeforeEach
    void setUp() {
        abacProperties = new AbacProperties();
        abacProperties.setEnabled(true);
        
        AbacProperties.IpWhitelistConfig ipConfig = new AbacProperties.IpWhitelistConfig();
        ipConfig.setEnabled(true);
        ipConfig.setAllowedIps(List.of("127.0.0.1", "192.168.0.0/16"));
        abacProperties.setIpWhitelist(ipConfig);
        
        AbacProperties.BusinessHoursConfig hoursConfig = new AbacProperties.BusinessHoursConfig();
        hoursConfig.setEnabled(false); // Disabled for testing
        abacProperties.setBusinessHours(hoursConfig);

        lightAbacService = new LightAbacService(abacProperties, ipWhitelistRepository, auditService);
    }

    @Test
    void shouldAllowLocalhost() {
        // Given
        when(ipWhitelistRepository.findApplicableToUser(anyLong())).thenReturn(Collections.emptyList());

        // When
        boolean allowed = lightAbacService.isIpAllowed(1L, "127.0.0.1");

        // Then
        assertTrue(allowed);
    }

    @Test
    void shouldAllowIpInCidrRange() {
        // Given
        when(ipWhitelistRepository.findApplicableToUser(anyLong())).thenReturn(Collections.emptyList());

        // When
        boolean allowed = lightAbacService.isIpAllowed(1L, "192.168.1.100");

        // Then
        assertTrue(allowed);
    }

    @Test
    void shouldDenyIpNotInWhitelist() {
        // Given
        when(ipWhitelistRepository.findApplicableToUser(anyLong())).thenReturn(Collections.emptyList());

        // When
        boolean allowed = lightAbacService.isIpAllowed(1L, "10.0.0.1");

        // Then
        assertFalse(allowed);
    }

    @Test
    void shouldAllowWhenAbacDisabled() {
        // Given
        abacProperties.setEnabled(false);

        // When
        boolean allowed = lightAbacService.isAccessAllowed(1L, "testuser", "1.2.3.4");

        // Then
        assertTrue(allowed);
    }

    @Test
    void shouldAllowWhenIpWhitelistDisabled() {
        // Given
        abacProperties.getIpWhitelist().setEnabled(false);

        // When
        boolean allowed = lightAbacService.isIpAllowed(1L, "1.2.3.4");

        // Then
        assertTrue(allowed);
    }

    @Test
    void shouldHandleIpv6Localhost() {
        // Given
        when(ipWhitelistRepository.findApplicableToUser(anyLong())).thenReturn(Collections.emptyList());
        List<String> allowedIps = new java.util.ArrayList<>(abacProperties.getIpWhitelist().getAllowedIps());
        allowedIps.add("0:0:0:0:0:0:0:1");
        abacProperties.getIpWhitelist().setAllowedIps(allowedIps);

        // When
        boolean allowed = lightAbacService.isIpAllowed(1L, "0:0:0:0:0:0:0:1");

        // Then
        assertTrue(allowed);
    }
}
