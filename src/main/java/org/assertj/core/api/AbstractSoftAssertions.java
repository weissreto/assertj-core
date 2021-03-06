/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * Copyright 2012-2020 the original author or authors.
 */
package org.assertj.core.api;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

import java.lang.reflect.Constructor;
import java.util.List;

import org.assertj.core.error.AssertionErrorCreator;
import org.assertj.core.internal.Failures;

public abstract class AbstractSoftAssertions extends DefaultAssertionErrorCollector
    implements SoftAssertionsProvider, InstanceOfAssertFactories {

  protected final SoftProxies proxies;

  protected AbstractSoftAssertions() {
    // pass itself as an AssertionErrorCollector instance
    proxies = new SoftProxies(this);
  }

  private static final AssertionErrorCreator ASSERTION_ERROR_CREATOR = new AssertionErrorCreator();

  public static void assertAll(AssertionErrorCollector collector) {
    List<AssertionError> errors = collector.assertionErrorsCollected();
    if (!errors.isEmpty()) throw ASSERTION_ERROR_CREATOR.multipleSoftAssertionsError(errors);
  }

  @Override
  public void assertAll() {
    assertAll(this);
  }

  @Override
  public <SELF extends Assert<? extends SELF, ? extends ACTUAL>, ACTUAL> SELF proxy(Class<SELF> assertClass,
                                                                                    Class<ACTUAL> actualClass, ACTUAL actual) {
    return proxies.createSoftAssertionProxy(assertClass, actualClass, actual);
  }

  /**
   * Fails with the given message.
   *
   * @param failureMessage error message.
   * @since 2.6.0 / 3.6.0
   */
  public void fail(String failureMessage) {
    AssertionError error = Failures.instance().failure(failureMessage);
    collectAssertionError(error);
  }

  /**
   * Fails with the given message built like {@link String#format(String, Object...)}.
   *
   * @param failureMessage error message.
   * @param args Arguments referenced by the format specifiers in the format string.
   * @since 2.6.0 / 3.6.0
   */
  public void fail(String failureMessage, Object... args) {
    AssertionError error = Failures.instance().failure(format(failureMessage, args));
    collectAssertionError(error);
  }

  /**
   * Fails with the given message and with the {@link Throwable} that caused the failure.
   *
   * @param failureMessage error message.
   * @param realCause cause of the error.
   * @since 2.6.0 / 3.6.0
   */
  public void fail(String failureMessage, Throwable realCause) {
    AssertionError error = Failures.instance().failure(failureMessage);
    error.initCause(realCause);
    collectAssertionError(error);
  }

  /**
   * Fails with a message explaining that a {@link Throwable} of given class was expected to be thrown
   * but had not been.
   *
   * @param throwableClass the Throwable class that was expected to be thrown.
   * @throws AssertionError with a message explaining that a {@link Throwable} of given class was expected to be thrown but had
   *           not been.
   * @since 2.6.0 / 3.6.0
   *
   * {@link Fail#shouldHaveThrown(Class)} can be used as a replacement.
   */
  public void failBecauseExceptionWasNotThrown(Class<? extends Throwable> throwableClass) {
    shouldHaveThrown(throwableClass);
  }

  /**
   * Fails with a message explaining that a {@link Throwable} of given class was expected to be thrown
   * but had not been.
   *
   * @param throwableClass the Throwable class that was expected to be thrown.
   * @throws AssertionError with a message explaining that a {@link Throwable} of given class was expected to be thrown but had
   *           not been.
   * @since 2.6.0 / 3.6.0
   */
  public void shouldHaveThrown(Class<? extends Throwable> throwableClass) {
    AssertionError error = Failures.instance().expectedThrowableNotThrown(throwableClass);
    collectAssertionError(error);
  }

  @Override
  public List<AssertionError> assertionErrorsCollected() {
    return decorateErrorsCollected(super.assertionErrorsCollected());
  }

  /**
   * Returns a copy of list of soft assertions collected errors.
   * @return a copy of list of soft assertions collected errors.
   */
  public List<Throwable> errorsCollected() {
    return decorateErrorsCollected(super.assertionErrorsCollected());
  }

  /**
   * Modifies collected errors. Override to customize modification.
   * @param <T> the supertype to use in the list return value
   * @param errors list of errors to decorate
   * @return decorated list
  */
  protected <T extends Throwable> List<T> decorateErrorsCollected(List<? extends T> errors) {
    return addLineNumberToErrorMessages(errors);
  }

  private <T extends Throwable> List<T> addLineNumberToErrorMessages(List<? extends T> errors) {
    return errors.stream()
                 .map(this::addLineNumberToErrorMessage)
                 .collect(toList());
  }

  private <T extends Throwable> T addLineNumberToErrorMessage(T error) {
    StackTraceElement testStackTraceElement = getFirstStackTraceElementFromTest(error.getStackTrace());
    if (testStackTraceElement != null) {
      try {
        return createNewInstanceWithLineNumberInErrorMessage(error, testStackTraceElement);
      } catch (@SuppressWarnings("unused") SecurityException | ReflectiveOperationException ignored) {}
    }
    return error;
  }

  private <T extends Throwable> T createNewInstanceWithLineNumberInErrorMessage(T error,
                                                                                StackTraceElement testStackTraceElement) throws ReflectiveOperationException {
    @SuppressWarnings("unchecked")
    Constructor<? extends T> constructor = (Constructor<? extends T>) error.getClass().getConstructor(String.class,
                                                                                                      Throwable.class);
    T errorWithLineNumber = constructor.newInstance(buildErrorMessageWithLineNumber(error.getMessage(),
                                                                                    testStackTraceElement),
                                                    error.getCause());
    errorWithLineNumber.setStackTrace(error.getStackTrace());
    for (Throwable suppressed : error.getSuppressed()) {
      errorWithLineNumber.addSuppressed(suppressed);
    }
    return errorWithLineNumber;
  }

  private String buildErrorMessageWithLineNumber(String originalErrorMessage, StackTraceElement testStackTraceElement) {
    String testClassName = simpleClassNameOf(testStackTraceElement);
    String testName = testStackTraceElement.getMethodName();
    int lineNumber = testStackTraceElement.getLineNumber();
    return format("%s%nat %s.%s(%s.java:%s)", originalErrorMessage, testClassName, testName, testClassName, lineNumber);
  }

  private String simpleClassNameOf(StackTraceElement testStackTraceElement) {
    String className = testStackTraceElement.getClassName();
    return className.substring(className.lastIndexOf('.') + 1);
  }

  private StackTraceElement getFirstStackTraceElementFromTest(StackTraceElement[] stacktrace) {
    for (StackTraceElement element : stacktrace) {
      String className = element.getClassName();
      if (isProxiedAssertionClass(className)
          || className.startsWith("sun.reflect")
          || className.startsWith("jdk.internal.reflect")
          || className.startsWith("java.")
          || className.startsWith("javax.")
          || className.startsWith("org.junit.")
          || className.startsWith("org.eclipse.jdt.internal.junit.")
          || className.startsWith("org.eclipse.jdt.internal.junit4.")
          || className.startsWith("org.eclipse.jdt.internal.junit5.")
          || className.startsWith("com.intellij.junit5.")
          || className.startsWith("com.intellij.rt.execution.junit.")
          || className.startsWith("com.intellij.rt.junit.") // since IntelliJ IDEA build 193.2956.37
          || className.startsWith("org.apache.maven.surefire")
          || className.startsWith("org.assertj")) {
        continue;
      }
      return element;
    }
    return null;
  }

  private boolean isProxiedAssertionClass(String className) {
    return className.contains("$ByteBuddy$");
  }
}
