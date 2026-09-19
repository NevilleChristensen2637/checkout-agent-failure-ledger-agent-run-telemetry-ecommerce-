package dev.infrai.checkout;

import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

@Service
public final class CheckoutWorkflow {
    public enum Status { READY_TO_FULFILL, MANUAL_REVIEW }
    public record Order(String orderId, boolean paid, boolean stockReserved, String receiptEmail) {}
    public record OrderUpdate(String orderId, Status status, long tokensCharged, String receiptState) {}

    private final AgentPorts.Reasoner reasoner;
    private final AgentPorts.FailureLedger failures;
    private final ConcurrentHashMap<String, OrderUpdate> updates = new ConcurrentHashMap<>();

    CheckoutWorkflow(AgentPorts.Reasoner reasoner, AgentPorts.FailureLedger failures) {
        this.reasoner = reasoner;
        this.failures = failures;
    }

    public OrderUpdate process(Order order) {
        if (!order.paid() || !order.stockReserved()) return store(order, Status.MANUAL_REVIEW, 0);
        long consumedTokens = 0;
        try {
            AgentPorts.AgentReply reply = reasoner.decide(order);
            consumedTokens = reply.promptTokens() + reply.completionTokens();
            Status status = reply.instruction().strip().equalsIgnoreCase("RELEASE")
                ? Status.READY_TO_FULFILL : Status.MANUAL_REVIEW;
            return store(order, status, consumedTokens);
        } catch (MeteredAgentException failure) {
            consumedTokens = failure.consumedTokens();
            failures.capture("checkout-agent-" + order.orderId(), failure, order, consumedTokens);
            return store(order, Status.MANUAL_REVIEW, consumedTokens);
        } catch (RuntimeException failure) {
            failures.capture("checkout-agent-" + order.orderId(), failure, order, consumedTokens);
            return store(order, Status.MANUAL_REVIEW, consumedTokens);
        }
    }

    public OrderUpdate find(String orderId) { return updates.get(orderId); }

    private OrderUpdate store(Order order, Status status, long tokens) {
        OrderUpdate update = new OrderUpdate(order.orderId(), status, tokens,
            status == Status.READY_TO_FULFILL ? "QUEUED" : "HELD");
        updates.put(order.orderId(), update);
        return update;
    }

    public static final class MeteredAgentException extends RuntimeException {
        private final long consumedTokens;
        public MeteredAgentException(String message, long consumedTokens) { super(message); this.consumedTokens = consumedTokens; }
        public long consumedTokens() { return consumedTokens; }
    }
}
