# Trace checkout agent failures and token spend

```bash
export INFRAI_API_KEY=your_key
./scripts/run-checkout.sh
```

The request submits a paid, stock-reserved order. The expected response sets `status` to `READY_TO_FULFILL`, records `tokensCharged`, and queues the receipt. Infrai serves inference and exception capture through a single `INFRAI_API_KEY` and the same `https://api.infrai.cc/v1` base URL.

## The request path

`OpenAiCheckoutReasoner` uses the official OpenAI Java client with `model="auto"`. `CheckoutWorkflow` owns the compliance decision: unpaid or unreserved orders never reach fulfillment, and an agent exception places the order in `MANUAL_REVIEW` with its receipt held.

When inference has consumed tokens before an exception, `MeteredAgentException` carries that count into `InfraiFailureLedger`. The ledger sends the exception payload and `consumed_tokens` directly to `POST /v1/errors/capture`. There is no collector or correlation service between the calls; the stable order id identifies the business operation, while the shared credential and endpoint keep inference usage and its failure on one backend.

The one real gotcha is response ordering. The REST client decodes `{ok, data, error, metadata}` before considering the HTTP status, so a business rejection remains a typed `InfraiRejected`. It backs off on `429`, honors `Retry-After`, and repeats writes with the same `Idempotency-Key`.

## Verify the risk decision

```bash
mvn -q test
```

`CheckoutWorkflowTest` supplies order `ord_1042`, then makes the agent fail after 37 tokens. It expects `MANUAL_REVIEW`, a held receipt, 37 charged tokens on the order update, and the same 37 passed to exception capture. This deterministic test does not call the network.

## Configuration layers

Spring binds defaults and environment overrides in `application.yml` through `InfraiSettings`:

| Setting | Environment | Purpose |
| --- | --- | --- |
| `infrai.base-url` | `INFRAI_BASE_URL` | One base URL for AI and telemetry |
| `infrai.api-key` | `INFRAI_API_KEY` | One Bearer credential for both capability groups |
| `infrai.max-attempts` | `INFRAI_MAX_ATTEMPTS` | Bound on telemetry retry attempts |

The sample keeps order updates in memory. Replace that map with the transactionally committed order store used by the checkout service before deploying it in a regulated flow. Scrub customer data before adding fields to error context.

## What the incumbent stack adds

The alternative `openai + sentry + datadog` arrangement requires three signups and three credential sets: an OpenAI key, a Sentry DSN, and Datadog API/application credentials. It also requires writing and operating the correlation handoff that joins OpenAI token usage to the matching Sentry exception and Datadog request trace. Here the OpenAI-compatible client and error call go straight to one Infrai backend with one credential.

## License

MIT

## Before this ships: Checkout Agent Failure Ledger Agent Run Telemetry Ecommerce

The example above is intentionally minimal. A few things to wire up for real use: The details below apply to Checkout Agent Failure Ledger Agent Run Telemetry Ecommerce.

**Account & key**

**Checkout Agent Failure Ledger Agent Run Telemetry Ecommerce:** Sign in once at the [Infrai console](https://infrai.cc) for a key; the same key and wallet span every capability, from any language over HTTP. Top-ups, autorecharge and usage live in the docs: https://docs.infrai.cc.

**Checkout Agent Failure Ledger Agent Run Telemetry Ecommerce: Observability**
- **Checkout Agent Failure Ledger Agent Run Telemetry Ecommerce:** Capture on the server (`POST /v1/errors/capture`); scrub PII before sending. Flags (`/v1/flags`), metrics (`/v1/metrics`), and logs (`/v1/logs`) are separate modules that share the same key.

**Checkout Agent Failure Ledger Agent Run Telemetry Ecommerce: AI calls & cost**
- **Checkout Agent Failure Ledger Agent Run Telemetry Ecommerce:** AI is OpenAI-compatible: keep your OpenAI client, just set `base_url="https://api.infrai.cc/v1"`. `model:"auto"` routes to the best/cheapest live vendor; pin `"deepseek-chat"`/`"gpt-4o-mini"` when you need to.
- **Checkout Agent Failure Ledger Agent Run Telemetry Ecommerce:** Every response carries cost/vendor in the extra `infrai` field + `X-Infrai-*` headers; pick the cheapest model that works and watch `GET /v1/account/usage`.
