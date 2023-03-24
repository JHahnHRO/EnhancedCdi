package io.github.jhahnhro.enhancedcdi.messaging;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

class RetryTest {


    static final Duration THREE_SECONDS = Duration.ofSeconds(3);
    static final Duration SIX_SECONDS = Duration.ofSeconds(6);
    static final Duration TWELVE_SECONDS = Duration.ofSeconds(12);
    static final Duration TWENTY_ONE_SECONDS = Duration.ofSeconds(3 + 6 + 12);
    static final Duration TWENTY_FOUR_SECONDS = Duration.ofSeconds(24);
    static final Duration UNLIMITED_DURATION = Duration.ofSeconds(Long.MAX_VALUE);

    @Nested
    class TestConstructor {
        @Test
        void givenInitialDelayNull_whenCtor_throwNPE() {
            assertThatNullPointerException().isThrownBy(() -> new Retry(null, Duration.ZERO, Duration.ZERO, 10));
        }

        @Test
        void givenMaxDelayNull_whenCtor_throwNPE() {
            assertThatNullPointerException().isThrownBy(() -> new Retry(Duration.ZERO, null, Duration.ZERO, 10));
        }

        @Test
        void givenMaxTimeNull_whenCtor_throwNPE() {
            assertThatNullPointerException().isThrownBy(() -> new Retry(Duration.ZERO, Duration.ZERO, null, 10));
        }

        @Test
        void givenInitialDelayNegative_whenCtor_throwIAE() {
            assertThatIllegalArgumentException().isThrownBy(
                    () -> new Retry(Duration.of(-1, ChronoUnit.SECONDS), Duration.ZERO, Duration.ZERO, 10));
        }

        @Test
        void givenMaxDelaySmallerThanInitialDelay_whenCtor_throwIAE() {
            assertThatIllegalArgumentException().isThrownBy(
                    () -> new Retry(Duration.ZERO, Duration.of(-1, ChronoUnit.SECONDS), Duration.ZERO, 10));
        }

        @Test
        void givenMaxWaitingTimeNegative_whenCtor_throwIAE() {
            assertThatIllegalArgumentException().isThrownBy(
                    () -> new Retry(Duration.ZERO, Duration.ZERO, Duration.of(-1, ChronoUnit.SECONDS), 10));
        }

        @Test
        void givenMaxAttemptsZero_whenCtor_throwIAE() {
            assertThatIllegalArgumentException().isThrownBy(
                    () -> new Retry(Duration.ZERO, Duration.ZERO, Duration.ZERO, 0));
        }

        @Test
        void givenMaxAttemptsNegative_whenCtor_throwIAE() {
            assertThatIllegalArgumentException().isThrownBy(
                    () -> new Retry(Duration.ZERO, Duration.ZERO, Duration.ZERO, -1));
        }
    }

    @Nested
    class TestNextDelay {

        @Test
        void givenInitialDelayEqualMaxDelay_thenNextDelayConstant() {
            final Duration delay = Duration.ofSeconds(1);
            final Retry retry = Retry.after(delay);

            final Duration delay0 = retry.calcNextDelay(Duration.ZERO);
            final Duration delay1 = retry.calcNextDelay(delay0);
            final Duration delay2 = retry.calcNextDelay(delay1);
            final Duration delay3 = retry.calcNextDelay(delay2);

            assertThat(delay0).isEqualTo(delay);
            assertThat(delay1).isEqualTo(delay);
            assertThat(delay2).isEqualTo(delay);
            assertThat(delay3).isEqualTo(delay);
        }

        @Test
        void givenInitialDelayUnequalMaxDelay_thenNextDelayDoubles() {
            final Retry retry = Retry.after(Duration.ofSeconds(1)).withMaxDelay(Duration.ofSeconds(10));

            final Duration delay0 = retry.calcNextDelay(Duration.ZERO);
            final Duration delay1 = retry.calcNextDelay(delay0);
            final Duration delay2 = retry.calcNextDelay(delay1);
            final Duration delay3 = retry.calcNextDelay(delay2);
            final Duration delay4 = retry.calcNextDelay(delay3);
            final Duration delay5 = retry.calcNextDelay(delay4);

            assertThat(delay0).isEqualTo(Duration.ofSeconds(1));
            assertThat(delay1).isEqualTo(Duration.ofSeconds(2));
            assertThat(delay2).isEqualTo(Duration.ofSeconds(4));
            assertThat(delay3).isEqualTo(Duration.ofSeconds(8));
            assertThat(delay4).isEqualTo(Duration.ofSeconds(10)); // max
            assertThat(delay5).isEqualTo(Duration.ofSeconds(10));
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class TestRetry {

        Instant currentTime = Instant.EPOCH;
        private final Answer<Void> advanceCurrentTime = invocation -> {
            final Duration howLong = invocation.getArgument(0);
            currentTime = currentTime.plus(howLong);
            return null; // void method
        };
        @Captor
        ArgumentCaptor<Duration> durationCaptor;
        @Mock
        Callable<Integer> callable;

        private void verifySleeps(Retry retrySpy, Duration... sleepTimes) throws InterruptedException {
            verify(retrySpy, times(sleepTimes.length)).sleep(durationCaptor.capture());

            final List<Duration> allValues = durationCaptor.getAllValues();
            assertThat(allValues).containsExactly(sleepTimes);
        }

        private Retry mockTime(final Retry retry) throws InterruptedException {
            final Retry spy = spy(retry); // use inline mock-maker to mock final classes
            doAnswer(invocation -> currentTime).when(spy).now();
            lenient().doAnswer(advanceCurrentTime).when(spy).sleep(any());

            return spy;
        }

        @Nested
        class TestNoRetry {

            @Test
            void givenNoRetry_whenCall_thenExecuteOnce() throws Exception {
                when(callable.call()).thenReturn(42);

                final Retry retry = mockTime(Retry.NO_RETRY);

                final Integer actualResult = retry.call(callable);
                assertThat(actualResult).isEqualTo(42);

                verify(callable, times(1)).call();
                verifyNoSleep(retry);
            }

            @Test
            void givenNoRetry_whenCallableThrowsAnException_thenExecuteOnce() throws Exception {
                when(callable.call()).thenThrow(new RuntimeException());

                final Retry retry = mockTime(Retry.NO_RETRY);

                assertThatThrownBy(() -> retry.call(callable)).isInstanceOf(TimeoutException.class)
                        .hasMessageContaining("attempt");
                verify(callable, times(1)).call();
                verifyNoSleep(retry);
            }

            private void verifyNoSleep(Retry retry) throws InterruptedException {
                verifySleeps(retry);
            }
        }

        @Nested
        class TestRetryWithFixedDelay {

            private final Retry retryWithFixedDelay = Retry.after(THREE_SECONDS);

            @Test
            void givenFixedDelayRetryWithUnlimitedAttempts_whenCall_thenExecuteUntilFirstResult() throws Exception {
                when(callable.call()).thenThrow(new RuntimeException())
                        .thenThrow(new RuntimeException())
                        .thenThrow(new RuntimeException())
                        .thenThrow(new RuntimeException())
                        .thenReturn(42);

                Retry retry = mockTime(this.retryWithFixedDelay.indefinitely());
                final Integer actualResult = retry.call(callable);

                assertThat(actualResult).isEqualTo(42);
                verify(callable, times(5)).call();
                verifySleeps(retry, THREE_SECONDS, THREE_SECONDS, THREE_SECONDS, THREE_SECONDS);
            }

            @Test
            void givenFixedDelayRetryWithLimitedAttempts_whenCallableThrowsTooOften_thenThrowTimeoutException()
                    throws Exception {
                when(callable.call()).thenThrow(new RuntimeException())
                        .thenThrow(new RuntimeException())
                        .thenThrow(new RuntimeException())
                        .thenThrow(new RuntimeException())
                        .thenReturn(42);

                Retry retry = mockTime(this.retryWithFixedDelay.giveUpAfterAttemptNr(4));


                assertThatThrownBy(() -> retry.call(callable)).isInstanceOf(TimeoutException.class)
                        .hasMessageContaining("attempts");

                verify(callable, times(4)).call();
                verifySleeps(retry, THREE_SECONDS, THREE_SECONDS, THREE_SECONDS);
            }

            @Test
            void givenFixedDelayRetryWithLimitedAttempts_whenCallableThrowsOnlyAFewTimes_thenExecuteUntilFirstResult()
                    throws Exception {
                when(callable.call()).thenThrow(new RuntimeException())
                        .thenThrow(new RuntimeException())
                        .thenThrow(new RuntimeException())
                        .thenReturn(42);

                Retry retry = mockTime(this.retryWithFixedDelay.giveUpAfterAttemptNr(4));


                Integer actualResult = retry.call(callable);
                assertThat(actualResult).isEqualTo(42);

                verify(callable, times(4)).call();
                verifySleeps(retry, THREE_SECONDS, THREE_SECONDS, THREE_SECONDS);
            }

            @Test
            void givenFixedDelayRetryWithLimitedTime_whenCallableThrowsTooOften_thenThrowTimeoutException()
                    throws Exception {
                when(callable.call()).thenThrow(new RuntimeException())
                        .thenThrow(new RuntimeException())
                        .thenThrow(new RuntimeException())
                        .thenThrow(new RuntimeException())
                        .thenThrow(new RuntimeException())
                        .thenReturn(42);

                Retry retry = mockTime(this.retryWithFixedDelay.giveUpAfter(TWELVE_SECONDS));


                assertThatThrownBy(() -> retry.call(callable)).isInstanceOf(TimeoutException.class)
                        .hasMessageContaining("wait time");

                verify(callable, times(5)).call();
                verifySleeps(retry, THREE_SECONDS, THREE_SECONDS, THREE_SECONDS, THREE_SECONDS, THREE_SECONDS);
            }

            @Test
            void givenFixedDelayRetryWithLimitedTime_whenCallableThrowsOnlyAFewTimes_thenExecuteUntilFirstResult()
                    throws Exception {
                when(callable.call()).thenThrow(new RuntimeException())
                        .thenThrow(new RuntimeException())
                        .thenThrow(new RuntimeException())
                        .thenReturn(42);

                Retry retry = mockTime(this.retryWithFixedDelay.giveUpAfter(TWELVE_SECONDS));


                Integer actualResult = retry.call(callable);
                assertThat(actualResult).isEqualTo(42);

                verify(callable, times(4)).call();
                verifySleeps(retry, THREE_SECONDS, THREE_SECONDS, THREE_SECONDS);
            }
        }

        @Nested
        class TestRetryWithExponentialBackoff {

            private final Retry retryWithExpBackOff = Retry.after(THREE_SECONDS).withMaxDelay(UNLIMITED_DURATION);

            @Test
            void givenRetryWithExponentialBackoffAndUnlimitedRetries_whenCall_thenExecuteUntilFirstResult()
                    throws Exception {
                when(callable.call()).thenThrow(new RuntimeException())
                        .thenThrow(new RuntimeException())
                        .thenThrow(new RuntimeException())
                        .thenReturn(42);

                Retry retry = mockTime(this.retryWithExpBackOff.indefinitely());
                final Integer actualResult = retry.call(callable);

                assertThat(actualResult).isEqualTo(42);
                verify(callable, times(4)).call();
                verifySleeps(retry, THREE_SECONDS, SIX_SECONDS, TWELVE_SECONDS);
            }

            @Test
            void givenRetryWithExponentialBackoffAndLimitedAttempts_whenCallableThrowsTooOften_thenThrowTimeoutException()
                    throws Exception {
                when(callable.call()).thenThrow(new RuntimeException())
                        .thenThrow(new RuntimeException())
                        .thenThrow(new RuntimeException())
                        .thenThrow(new RuntimeException())
                        .thenReturn(42);

                Retry retry = mockTime(this.retryWithExpBackOff.giveUpAfterAttemptNr(4));


                assertThatThrownBy(() -> retry.call(callable)).isInstanceOf(TimeoutException.class)
                        .hasMessageContaining("attempts");

                verify(callable, times(4)).call();
                verifySleeps(retry, THREE_SECONDS, SIX_SECONDS, TWELVE_SECONDS);
            }

            @Test
            void givenRetryWithExponentialBackoffAndLimitedAttempts_whenCallableThrowsOnlyAFewTimes_thenExecuteUntilFirstResult()
                    throws Exception {
                when(callable.call()).thenThrow(new RuntimeException())
                        .thenThrow(new RuntimeException())
                        .thenThrow(new RuntimeException())
                        .thenReturn(42);

                Retry retry = mockTime(this.retryWithExpBackOff.giveUpAfterAttemptNr(4));


                Integer actualResult = retry.call(callable);
                assertThat(actualResult).isEqualTo(42);

                verify(callable, times(4)).call();
                verifySleeps(retry, THREE_SECONDS, SIX_SECONDS, TWELVE_SECONDS);
            }

            @Test
            void givenRetryWithExponentialBackoffAndLimitedTime_whenCallableThrowsTooOften_thenThrowTimeoutException()
                    throws Exception {
                when(callable.call()).thenThrow(new RuntimeException())
                        .thenThrow(new RuntimeException())
                        .thenThrow(new RuntimeException())
                        .thenThrow(new RuntimeException())
                        .thenThrow(new RuntimeException())
                        .thenReturn(42);

                Retry retry = mockTime(this.retryWithExpBackOff.giveUpAfter(TWENTY_ONE_SECONDS));


                assertThatThrownBy(() -> retry.call(callable)).isInstanceOf(TimeoutException.class)
                        .hasMessageContaining("wait time");

                verify(callable, times(4)).call();
                verifySleeps(retry, THREE_SECONDS, SIX_SECONDS, TWELVE_SECONDS, TWENTY_FOUR_SECONDS);
            }

            @Test
            void givenRetryWithExponentialBackoffAndLimitedTime_whenCallableThrowsOnlyAFewTimes_thenExecuteUntilFirstResult()
                    throws Exception {
                when(callable.call()).thenThrow(new RuntimeException())
                        .thenThrow(new RuntimeException())
                        .thenThrow(new RuntimeException())
                        .thenReturn(42);

                Retry retry = mockTime(this.retryWithExpBackOff.giveUpAfter(TWENTY_ONE_SECONDS));


                Integer actualResult = retry.call(callable);
                assertThat(actualResult).isEqualTo(42);

                verify(callable, times(4)).call();
                verifySleeps(retry, THREE_SECONDS, SIX_SECONDS, TWELVE_SECONDS);
            }
        }

        @Nested
        class TestExceptionHandling {

            @Mock
            BiConsumer<Integer, Exception> exceptionHandler;

            @Test
            void givenExceptionHandler_whenCallableIsRetried_thenCallExceptionHandler() throws Exception {
                final RuntimeException exception1 = new RuntimeException();
                final RuntimeException exception2 = new RuntimeException();
                when(callable.call()).thenThrow(exception1).thenThrow(exception2).thenReturn(42);

                Retry retry = mockTime(Retry.after(THREE_SECONDS).indefinitely());
                retry.call(callable, exceptionHandler);


                verify(callable, times(3)).call();

                ArgumentCaptor<Integer> attemptNumber = ArgumentCaptor.forClass(Integer.class);
                ArgumentCaptor<Exception> exceptions = ArgumentCaptor.forClass(Exception.class);
                verify(exceptionHandler, times(2)).accept(attemptNumber.capture(), exceptions.capture());

                assertThat(attemptNumber.getAllValues()).containsExactly(1, 2);
                assertThat(exceptions.getAllValues()).containsExactly(exception1, exception2);
            }

            @Test
            void givenSleepIsInterrupted_whenRetry_thenThrowInterruptedException() throws Exception {
                final Retry retrySpy = mockTime(Retry.after(THREE_SECONDS));

                when(callable.call()).thenThrow(new RuntimeException());
                final InterruptedException interruptedException = new InterruptedException();
                doThrow(interruptedException).when(retrySpy).sleep(any());

                assertThatThrownBy(() -> retrySpy.call(callable)).isSameAs(interruptedException);
            }
        }
    }
}