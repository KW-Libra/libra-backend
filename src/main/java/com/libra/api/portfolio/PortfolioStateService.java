package com.libra.api.portfolio;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Service;

@Service
public class PortfolioStateService {

    private final AtomicReference<PortfolioSnapshot> current = new AtomicReference<>();

    public PortfolioSnapshot save(PortfolioSnapshot snapshot) {
        current.set(snapshot);
        return snapshot;
    }

    public Optional<PortfolioSnapshot> getCurrent() {
        return Optional.ofNullable(current.get());
    }
}
