// SPDX-License-Identifier: Apache-2.0
package com.hedera.node.app.grpc.impl.usage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class UserAgentTest {

    @ParameterizedTest
    @MethodSource("testUserAgentArgs")
    void testUserAgent(
            final String userAgentStr, final UserAgentType expectedAgentType, final String expectedAgentVersion) {
        final UserAgent userAgent = UserAgent.from(userAgentStr);
        assertThat(userAgent.agentType()).isEqualTo(expectedAgentType);
        assertThat(userAgent.version()).isEqualTo(expectedAgentVersion);
    }

    static List<Arguments> testUserAgentArgs() {
        return List.of(
                Arguments.of("hiero-sdk-cpp/1.1.1", UserAgentType.HIERO_SDK_CPP, "1.1.1"),
                Arguments.of("hiero-sdk-go/1.1.2", UserAgentType.HIERO_SDK_GO, "1.1.2"),
                Arguments.of("hiero-sdk-java/1.1.3", UserAgentType.HIERO_SDK_JAVA, "1.1.3"),
                Arguments.of("hiero-sdk-js/1.1.4", UserAgentType.HIERO_SDK_JS, "1.1.4"),
                Arguments.of("hiero-sdk-python/1.1.5", UserAgentType.HIERO_SDK_PYTHON, "1.1.5"),
                Arguments.of("hiero-sdk-rust/1.1.6", UserAgentType.HIERO_SDK_RUST, "1.1.6"),
                Arguments.of("hiero-sdk-swift/1.1.7", UserAgentType.HIERO_SDK_SWIFT, "1.1.7"),
                Arguments.of("hiero-sdk-lua/1.1.8", UserAgentType.UNKNOWN, "Unknown"),
                Arguments.of("hiero-sdk-lua", UserAgentType.UNKNOWN, "Unknown"),
                Arguments.of("hiero-sdk-java", UserAgentType.HIERO_SDK_JAVA, "Unknown"),
                Arguments.of(null, UserAgentType.UNSPECIFIED, "Unknown"),
                Arguments.of("", UserAgentType.UNSPECIFIED, "Unknown"),
                Arguments.of("/1.1.3", UserAgentType.UNSPECIFIED, "Unknown"),
                Arguments.of("hiero-sdk-lua/1.1.3 foo-bar/42 baz", UserAgentType.UNKNOWN, "Unknown"),
                Arguments.of("/", UserAgentType.UNSPECIFIED, "Unknown"),
                Arguments.of("foo-bar/2 hiero-sdk-java/1.2.3", UserAgentType.HIERO_SDK_JAVA, "1.2.3"),
                Arguments.of("hiero-sdk-java/1.2.3/foo", UserAgentType.UNKNOWN, "Unknown"),
                Arguments.of("hiero-sdk-java/dev", UserAgentType.HIERO_SDK_JAVA, "dev"),
                Arguments.of("hiero-sdk-java/3.0.0-beta1", UserAgentType.HIERO_SDK_JAVA, "3.0.0-beta1"),
                Arguments.of("hiero-sdk-java/2.1.0 hiero-sdk-java/2.1.0", UserAgentType.UNKNOWN, "Unknown"));
    }

    @ParameterizedTest
    @MethodSource("testUserAgentTypeArgs")
    void testUserAgentType(final String userAgentType, final UserAgentType expectedType) {
        final UserAgentType actualType = UserAgentType.fromString(userAgentType);
        assertThat(actualType).isEqualTo(expectedType);
    }

    static List<Arguments> testUserAgentTypeArgs() {
        return List.of(
                Arguments.of(null, UserAgentType.UNSPECIFIED),
                Arguments.of("", UserAgentType.UNSPECIFIED),
                Arguments.of("  ", UserAgentType.UNSPECIFIED),
                Arguments.of(" hiero-sdk-java   ", UserAgentType.HIERO_SDK_JAVA),
                Arguments.of("Hiero-Sdk-Java", UserAgentType.HIERO_SDK_JAVA),
                Arguments.of("grpc-sdk-java", UserAgentType.UNKNOWN));
    }
}
