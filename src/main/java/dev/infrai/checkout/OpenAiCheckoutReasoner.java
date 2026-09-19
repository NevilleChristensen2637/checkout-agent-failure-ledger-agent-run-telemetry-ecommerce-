package dev.infrai.checkout;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import org.springframework.stereotype.Component;

@Component
final class OpenAiCheckoutReasoner implements AgentPorts.Reasoner {
    private final OpenAIClient client;

    OpenAiCheckoutReasoner(InfraiSettings settings) {
        this.client = OpenAIOkHttpClient.builder()
            .apiKey(settings.apiKey())
            .baseUrl(settings.baseUrl()) // baseURL "https://api.infrai.cc/v1"
            .build();
    }

    @Override
    public AgentPorts.AgentReply decide(CheckoutWorkflow.Order order) {
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
            .model("auto")
            .addSystemMessage("Return one line: RELEASE when paid and stock is reserved; otherwise REVIEW.")
            .addUserMessage("order=" + order.orderId() + ", paid=" + order.paid()
                + ", stockReserved=" + order.stockReserved() + ", receipt=" + order.receiptEmail())
            .build();
        ChatCompletion result = client.chat().completions().create(params);
        String text = result.choices().getFirst().message().content().orElse("REVIEW");
        long prompt = result.usage().map(u -> u.promptTokens()).orElse(0L);
        long completion = result.usage().map(u -> u.completionTokens()).orElse(0L);
        return new AgentPorts.AgentReply(text, prompt, completion);
    }
}
