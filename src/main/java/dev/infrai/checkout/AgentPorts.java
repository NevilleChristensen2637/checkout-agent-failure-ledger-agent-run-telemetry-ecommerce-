package dev.infrai.checkout;

interface AgentPorts {
    record AgentReply(String instruction, long promptTokens, long completionTokens) {}

    interface Reasoner {
        AgentReply decide(CheckoutWorkflow.Order order);
    }

    interface FailureLedger {
        void capture(String operationKey, RuntimeException failure, CheckoutWorkflow.Order order, long consumedTokens);
    }
}
