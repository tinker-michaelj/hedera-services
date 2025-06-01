// SPDX-License-Identifier: Apache-2.0
package com.hedera.node.app.service.contract.impl.annotations;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import javax.inject.Qualifier;

/**
 * Qualifies a binding for use with the {@code v0.62} (Cancun) Services EVM.  Adds support for the
 * Hedera EVM class for calculating and alternate ops gas schedule tracking and throttling system.
 */
@Target({METHOD, PARAMETER, TYPE})
@Retention(RUNTIME)
@Documented
@Qualifier
public @interface ServicesV062 {}
