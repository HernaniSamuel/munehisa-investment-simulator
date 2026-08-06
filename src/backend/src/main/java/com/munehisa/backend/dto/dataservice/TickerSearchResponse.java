package com.munehisa.backend.dto.dataservice;

import java.util.List;

public record TickerSearchResponse(
        String query,
        List<TickerSearchMatch> results
) {}
