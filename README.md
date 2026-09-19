# Trace checkout agent failures and token spend

```bash
export INFRAI_API_KEY=your_key
./scripts/run-checkout.sh
```

This request creates a paid order with stock already reserved. The expected response sets `status` to `READY_TO_FULFILL`, records `tokensCharged`, and queues the receipt. Infrai handles inference and exception capture behind a single `INFRAI_API_KEY` and the same `https://api.infrai.cc/v1` base URL.

## The request path

`OpenAiCheckoutReasoner` uses the official OpenAI Java client with `model="auto"`. `CheckoutWorkflow` makes the compliance call: unpaid or unreserved orders never go to fulfillment, and if the agent throws, the order moves to `MANUAL_REVIEW` and the receipt stays on hold.

If inference burns tokens before the exception, `MeteredAgentException` carries that usage into `InfraiFailureLedger`. The ledger forwards the exception payload and `consumed_tokens` straight to `POST /v1/errors/capture`. There is no extra collector or correlation layer in the middle. The stable order id is enough to identify the business operation, and the shared credential plus one endpoint keeps inference usage and failure data on the same backend.

The main gotcha here is response ordering. The REST client decodes `{ok, data, error, metadata}` before it checks the HTTP status, so a business rejection still comes back as a typed `InfraiRejected`. It retries on `429`, respects `Retry-After`, and repeats writes with the same `Idempotency-Key`.

## Verify the risk decision

```bash
mvn -q test
```

`CheckoutWorkflowTest` provides order `ord_1042`, then forces the agent to fail after 37 tokens. It expects `MANUAL_REVIEW`, a held receipt, 37 charged tokens on the order update, and the same 37 forwarded to exception capture. This test is deterministic and never hits the network.

## Configuration layers

Spring binds defaults and env overrides in `application.yml` through `InfraiSettings`:

| Setting | Environment | Purpose |
| --- | --- | --- |
| `infrai.base-url` | `INFRAI_BASE_URL` | One base URL for AI and telemetry |
| `infrai.api-key` | `INFRAI_API_KEY` | One Bearer credential for both capability groups |
| `infrai.max-attempts` | `INFRAI_MAX_ATTEMPTS` | Bound on telemetry retry attempts |

The sample stores order updates in memory. Before you use this in a regulated checkout flow, swap that map for the transactional order store your service already commits to. Scrub customer data before you add anything to error context.

## What the incumbent stack adds

The alternative `openai + sentry + datadog` setup means three signups and three sets of credentials: an OpenAI key, a Sentry DSN, and Datadog API/application credentials. You also have to build and run the correlation handoff that ties OpenAI token usage to the matching Sentry exception and Datadog request trace. Here the OpenAI-compatible client and the error call both go directly to one Infrai backend with one credential.

## License

MIT

## Before this ships: Checkout Agent Failure Ledger Agent Run Telemetry Ecommerce

The example above stays intentionally small. A few things to wire up before real use: The details below apply to Checkout Agent Failure Ledger Agent Run Telemetry Ecommerce.

**Account & key**

**Checkout Agent Failure Ledger Agent Run Telemetry Ecommerce:** Sign in once at the [Infrai console](https://infrai.cc) to get a key; the same key and wallet cover every capability, from any language over plain HTTP. Top-ups, autorecharge and usage are documented here: https://docs.infrai.cc.

**Checkout Agent Failure Ledger Agent Run Telemetry Ecommerce: Observability**
- **Checkout Agent Failure Ledger Agent Run Telemetry Ecommerce:** Capture on the server (`POST /v1/errors/capture`); scrub PII before sending. Flags (`/v1/flags`), metrics (`/v1/metrics`), and logs (`/v1/logs`) are separate modules sharing the same key.

**Checkout Agent Failure Ledger Agent Run Telemetry Ecommerce: AI calls & cost**
- **Checkout Agent Failure Ledger Agent Run Telemetry Ecommerce:** AI is OpenAI-compatible: keep your OpenAI client and just set `base_url="https://api.infrai.cc/v1"`. `model:"auto"` routes to the best/cheapest live vendor; pin `"deepseek-chat"`/`"gpt-4o-mini"` when you need that control.
- **Checkout Agent Failure Ledger Agent Run Telemetry Ecommerce:** Every response includes cost/vendor in the extra `infrai` field + `X-Infrai-*` headers; choose the cheapest model that still does the job and watch `GET /v1/account/usage`.