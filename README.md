# Kotlin Coroutines Playground & Practical Use Cases

A comprehensive, production-oriented playground designed to experiment with core and advanced patterns in Kotlin Coroutines and Reactive Flows.

---

## Overview of Use Cases

This project demonstrates 6 real-world architectural patterns using Kotlin Coroutines:

### 1. Parallel Data Aggregation (`usecases.aggregation`)
* **Concepts**: Concurrent decomposition with `async` / `await`, structured failure cascading in `coroutineScope`, error isolation with `supervisorScope`, and graceful degradation with fallback values.
* **Problem Solved**: Efficiently aggregating independent upstream I/O calls (user profile, orders, recommendations) without sequential blocking while preventing non-critical service outages from failing entire user requests.

### 2. Cooperative Cancellation, Timeouts & Safe Cleanup (`usecases.cancellation`)
* **Concepts**: `isActive`, `ensureActive()`, `yield()`, `withTimeout` vs `withTimeoutOrNull`, and `withContext(NonCancellable)`.
* **Problem Solved**: Managing long-running batch processing jobs that cleanly terminate when cancelled without leaking resources, guaranteeing critical cleanup/rollback code executes even during coroutine cancellation.

### 3. Concurrency Control & Shared State (`usecases.concurrency`)
* **Concepts**: Thread-safety comparison (Unsafe vs `Mutex.withLock` vs single-thread dispatcher confinement via `limitedParallelism(1)`), and rate-limiting / bounded concurrency with `Semaphore`.
* **Problem Solved**: Protecting shared mutable counters without blocking OS threads, understanding coroutine interleaving pitfalls during suspension points, and capping concurrent outbound requests (e.g., maximum 3 parallel requests).

### 4. Producer-Consumer Worker Pipeline (`usecases.channels`)
* **Concepts**: Coroutine `Channel`s, channel capacities, buffering, Fan-out (distributing tasks to multiple worker coroutines), and Fan-in (aggregating results into a central collection).
* **Problem Solved**: Decoupling task ingestion from task processing with backpressure handling and worker pool concurrency.

### 5. Reactive Streams & State Management (`usecases.flows`)
* **Concepts**: Cold Flows (`flow { ... }`), context preservation with `flowOn`, backpressure management with `conflate()`, stream combination (`zip`), and Hot Flows (`StateFlow` for state observation and `SharedFlow` for one-off event broadcasting).
* **Problem Solved**: Handling streaming data (e.g., market quotes) efficiently with fast producers and slow consumers, modeling reactive UI/application state machines.

### 6. Structured Resilience & Retry Policies (`usecases.resilience`)
* **Concepts**: Exponential backoff with retry multiplier, preserving cancellation hierarchy (ensuring `CancellationException` is never caught as a retryable error), and child error isolation with `SupervisorJob`.
* **Problem Solved**: Building fault-tolerant operations for flaky network dependencies while respecting coroutine lifecycle cancellation.

---

## Running the Application

To run the complete demonstration suite:

```bash
./gradlew run
```

---

## Running the Tests

To execute all unit test suites (utilizing `kotlinx-coroutines-test`, `runTest`, and virtual time control):

```bash
./gradlew test
```
