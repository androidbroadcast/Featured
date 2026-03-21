import XCTest
import Combine
@testable import iosApp

/// Tests for the Combine publisher bridge on FeatureFlags.
///
/// These tests use a custom stream provider so they do not require a live
/// KMP runtime. Each test exercises one observable behavior of
/// `FeatureFlags.publisher(for:)`.
final class FeatureFlagsCombineTests: XCTestCase {

    private var cancellables: Set<AnyCancellable> = []

    override func tearDown() {
        cancellables.removeAll()
        super.tearDown()
    }

    // MARK: - Helpers

    /// Builds a FeatureFlags whose stream immediately emits the given values and finishes.
    private func makeFlags<T>(emitting values: [T]) -> FeatureFlags {
        FeatureFlags(streamProvider: { _ in
            AsyncStream<Any> { continuation in
                for value in values {
                    continuation.yield(value as Any)
                }
                continuation.finish()
            }
        })
    }

    // MARK: - publisher(for:) exists and returns AnyPublisher

    /// RED: `FeatureFlags` must expose a `publisher(for:)` method that
    /// returns `AnyPublisher<T, Never>`.
    ///
    /// Without the implementation this file does not compile — that is the
    /// expected red state.
    func test_publisher_returnsAnyPublisher() {
        let flags = makeFlags(emitting: [true] as [Bool])
        let flag = FeatureFlag<Bool>(key: "test_flag", defaultValue: false)

        // The type annotation is the assertion: this line fails to compile
        // if publisher(for:) does not exist or returns the wrong type.
        let publisher: AnyPublisher<Bool, Never> = flags.publisher(for: flag)

        XCTAssertNotNil(publisher)
    }

    // MARK: - Values are forwarded from AsyncStream to publisher

    /// RED: values emitted by the underlying stream must arrive on the publisher.
    func test_publisher_forwardsValueFromStream() {
        let exp = expectation(description: "receives value from stream")
        let flags = makeFlags(emitting: [true] as [Bool])
        let flag = FeatureFlag<Bool>(key: "dark_mode", defaultValue: false)

        var received: [Bool] = []

        flags.publisher(for: flag)
            .sink { value in
                received.append(value)
                exp.fulfill()
            }
            .store(in: &cancellables)

        waitForExpectations(timeout: 1.0)
        XCTAssertEqual(received, [true])
    }

    // MARK: - Multiple values are forwarded in order

    func test_publisher_forwardsMultipleValuesInOrder() {
        let exp = expectation(description: "receives all values")
        exp.expectedFulfillmentCount = 3

        let flags = makeFlags(emitting: [false, true, false] as [Bool])
        let flag = FeatureFlag<Bool>(key: "toggle", defaultValue: false)

        var received: [Bool] = []

        flags.publisher(for: flag)
            .sink { value in
                received.append(value)
                exp.fulfill()
            }
            .store(in: &cancellables)

        waitForExpectations(timeout: 1.0)
        XCTAssertEqual(received, [false, true, false])
    }

    // MARK: - Default value is used when cast fails

    func test_publisher_usesDefaultValueOnCastFailure() {
        let exp = expectation(description: "receives default value")

        // Stream emits a String but the flag expects Bool — cast fails, default is used.
        let flags = FeatureFlags(streamProvider: { _ in
            AsyncStream<Any> { continuation in
                continuation.yield("not a bool" as Any)
                continuation.finish()
            }
        })
        let flag = FeatureFlag<Bool>(key: "flag", defaultValue: true)

        var received: [Bool] = []

        flags.publisher(for: flag)
            .sink { value in
                received.append(value)
                exp.fulfill()
            }
            .store(in: &cancellables)

        waitForExpectations(timeout: 1.0)
        // cast returns defaultValue (true) when the raw value is not Bool
        XCTAssertEqual(received, [true])
    }

    // MARK: - Cancellation stops the underlying Task

    /// Cancelling the subscription must stop the bridging Task, preventing leaks.
    func test_publisher_cancellationStopsTask() {
        let finishExp = expectation(description: "stream terminates after cancel")

        let flags = FeatureFlags(streamProvider: { _ in
            AsyncStream<Any> { continuation in
                continuation.onTermination = { _ in
                    finishExp.fulfill()
                }
                // Stream stays open; values arrive only after cancel in this test.
            }
        })

        let flag = FeatureFlag<Bool>(key: "persistent", defaultValue: false)

        // Subscribe then immediately cancel by not retaining the AnyCancellable.
        flags.publisher(for: flag)
            .sink { _ in }
            .cancel()

        waitForExpectations(timeout: 1.0)
    }
}
