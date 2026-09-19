package dev.infrai.checkout;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;

class CheckoutWorkflowTest {
    @Test
    void paidOrderMovesToManualReviewAndRecordsWastedTokensWhenAgentFails() {
        AtomicReference<Long> capturedTokens = new AtomicReference<>();
        AgentPorts.Reasoner reasoner = order -> { throw new CheckoutWorkflow.MeteredAgentException("provider rejected output", 37); };
        AgentPorts.FailureLedger ledger = (key, failure, order, tokens) -> capturedTokens.set(tokens);
        CheckoutWorkflow workflow = new CheckoutWorkflow(reasoner, ledger);

        CheckoutWorkflow.OrderUpdate update = workflow.process(
            new CheckoutWorkflow.Order("ord_1042", true, true, "buyer@example.test"));

        assertThat(update.status()).isEqualTo(CheckoutWorkflow.Status.MANUAL_REVIEW);
        assertThat(update.receiptState()).isEqualTo("HELD");
        assertThat(update.tokensCharged()).isEqualTo(37);
        assertThat(capturedTokens.get()).isEqualTo(37);
    }
}
