package com.fyp.auth.dto.request;

import com.fyp.auth.entity.IpWhitelist;
import lombok.Data;

@Data
public class IpWhitelistRequest {
    private String ipAddress;
    // Backward compatibility with previous frontend shape
    private String ip;
    private String description;
    private IpWhitelist.IpType type = IpWhitelist.IpType.SINGLE;
}

