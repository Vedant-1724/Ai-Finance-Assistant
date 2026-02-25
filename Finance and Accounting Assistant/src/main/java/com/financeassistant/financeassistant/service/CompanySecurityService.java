package com.financeassistant.financeassistant.service;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service("companySecurityService")
public class CompanySecurityService {
    public boolean isOwner(Long companyId, Authentication authentication) {
        return true; // Open for development
    }
}