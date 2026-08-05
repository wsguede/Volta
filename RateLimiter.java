import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Standalone rate limiter supporting three strategies: fixed window, sliding
 * window, and token bucket. Not part of the Volta Android app — this is a
 * self-contained utility with no dependencies on the rest of the repo.
 */
public class RateLimiter {

    private enum Strategy {
        FIXED_WINDOW,
        SLIDING_WINDOW,
        TOKEN_BUCKET
    }

    private final Strategy strategy;
    private final int limit;
    private final long windowSizeMillis;
    private final Map<String, FixedWindowState> fixedWindowClients = new ConcurrentHashMap<>();
    private final Map<String, SlidingWindowState> slidingWindowClients = new ConcurrentHashMap<>();
    private final Map<String, TokenBucketState> tokenBucketClients = new ConcurrentHashMap<>();

    public RateLimiter(String strategy, int limit, int windowSizeSeconds) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        if (windowSizeSeconds <= 0) {
            throw new IllegalArgumentException("windowSizeSeconds must be positive");
        }
        this.strategy = parseStrategy(strategy);
        this.limit = limit;
        this.windowSizeMillis = windowSizeSeconds * 1000L;
    }

    private static Strategy parseStrategy(String strategy) {
        if (strategy == null) {
            throw new IllegalArgumentException("strategy must not be null");
        }
        String normalized = strategy.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        switch (normalized) {
            case "fixed_window":
                return Strategy.FIXED_WINDOW;
            case "sliding_window":
                return Strategy.SLIDING_WINDOW;
            case "token_bucket":
                return Strategy.TOKEN_BUCKET;
            default:
                throw new IllegalArgumentException("Unknown strategy: " + strategy);
        }
    }

    public boolean allowRequest(String clientId) {
        switch (strategy) {
            case FIXED_WINDOW:
                return allowFixedWindow(clientId);
            case SLIDING_WINDOW:
                return allowSlidingWindow(clientId);
            case TOKEN_BUCKET:
                return allowTokenBucket(clientId);
            default:
                throw new IllegalStateException("Unhandled strategy: " + strategy);
        }
    }

    public int getRemainingRequests(String clientId) {
        switch (strategy) {
            case FIXED_WINDOW:
                return remainingFixedWindow(clientId);
            case SLIDING_WINDOW:
                return remainingSlidingWindow(clientId);
            case TOKEN_BUCKET:
                return remainingTokenBucket(clientId);
            default:
                throw new IllegalStateException("Unhandled strategy: " + strategy);
        }
    }

    public void resetClient(String clientId) {
        fixedWindowClients.remove(clientId);
        slidingWindowClients.remove(clientId);
        tokenBucketClients.remove(clientId);
    }

    // ---- Fixed window ----

    private static final class FixedWindowState {
        long windowStart;
        int count;
    }

    private boolean allowFixedWindow(String clientId) {
        FixedWindowState state = fixedWindowClients.computeIfAbsent(clientId, id -> newFixedWindowState());
        synchronized (state) {
            refreshFixedWindow(state);
            if (state.count < limit) {
                state.count++;
                return true;
            }
            return false;
        }
    }

    private int remainingFixedWindow(String clientId) {
        FixedWindowState state = fixedWindowClients.computeIfAbsent(clientId, id -> newFixedWindowState());
        synchronized (state) {
            refreshFixedWindow(state);
            return Math.max(0, limit - state.count);
        }
    }

    private FixedWindowState newFixedWindowState() {
        FixedWindowState state = new FixedWindowState();
        state.windowStart = currentWindowStart();
        state.count = 0;
        return state;
    }

    private void refreshFixedWindow(FixedWindowState state) {
        long currentWindowStart = currentWindowStart();
        if (state.windowStart != currentWindowStart) {
            state.windowStart = currentWindowStart;
            state.count = 0;
        }
    }

    private long currentWindowStart() {
        long now = System.currentTimeMillis();
        return now - (now % windowSizeMillis);
    }

    // ---- Sliding window (log of request timestamps) ----

    private static final class SlidingWindowState {
        final Deque<Long> timestamps = new ArrayDeque<>();
    }

    private boolean allowSlidingWindow(String clientId) {
        SlidingWindowState state = slidingWindowClients.computeIfAbsent(clientId, id -> new SlidingWindowState());
        synchronized (state) {
            long now = System.currentTimeMillis();
            evictExpired(state, now);
            if (state.timestamps.size() < limit) {
                state.timestamps.addLast(now);
                return true;
            }
            return false;
        }
    }

    private int remainingSlidingWindow(String clientId) {
        SlidingWindowState state = slidingWindowClients.computeIfAbsent(clientId, id -> new SlidingWindowState());
        synchronized (state) {
            evictExpired(state, System.currentTimeMillis());
            return Math.max(0, limit - state.timestamps.size());
        }
    }

    private void evictExpired(SlidingWindowState state, long now) {
        long cutoff = now - windowSizeMillis;
        while (!state.timestamps.isEmpty() && state.timestamps.peekFirst() <= cutoff) {
            state.timestamps.pollFirst();
        }
    }

    // ---- Token bucket ----

    private static final class TokenBucketState {
        double tokens;
        long lastRefillMillis;
    }

    private boolean allowTokenBucket(String clientId) {
        TokenBucketState state = tokenBucketClients.computeIfAbsent(clientId, id -> newTokenBucketState());
        synchronized (state) {
            refillTokens(state);
            if (state.tokens >= 1.0) {
                state.tokens -= 1.0;
                return true;
            }
            return false;
        }
    }

    private int remainingTokenBucket(String clientId) {
        TokenBucketState state = tokenBucketClients.computeIfAbsent(clientId, id -> newTokenBucketState());
        synchronized (state) {
            refillTokens(state);
            return (int) Math.floor(state.tokens);
        }
    }

    private TokenBucketState newTokenBucketState() {
        TokenBucketState state = new TokenBucketState();
        state.tokens = limit;
        state.lastRefillMillis = System.currentTimeMillis();
        return state;
    }

    private void refillTokens(TokenBucketState state) {
        long now = System.currentTimeMillis();
        long elapsedMillis = now - state.lastRefillMillis;
        if (elapsedMillis <= 0) {
            return;
        }
        double refillRatePerMillis = (double) limit / windowSizeMillis;
        double refill = elapsedMillis * refillRatePerMillis;
        state.tokens = Math.min(limit, state.tokens + refill);
        state.lastRefillMillis = now;
    }
}
