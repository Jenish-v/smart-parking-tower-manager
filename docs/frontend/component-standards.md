# Frontend Component Standards

The operator dashboard uses route-level pages, shared presentation components, and a separate typed API access layer.
These rules keep browser concerns outside backend modules and make operator states explicit.

## Boundaries

- `src/pages` composes a route and owns page-level loading, empty, error, and success states.
- `src/components` contains reusable presentation and layout components. Components receive data through typed props.
- `src/api` owns HTTP paths, transport types, and RFC problem response handling. Components do not call `fetch`
  directly.
- Backend domain types are represented by frontend transport types; backend source code is not imported or duplicated as
  executable logic.

## Interaction and content

Use native controls and landmarks before custom semantics. Every workflow must remain usable with a keyboard and expose
an accessible name. Focus indicators must be visible. Motion must respect `prefers-reduced-motion`.

Views must distinguish configured reference values from live API data. Do not render invented occupancy, sessions, or
service-health values. A mutation must expose pending, success, retry-safe failure, and validation states before its
control is enabled for operators.

Time-bound forms use native date and time controls and send UTC instants to the API. A reservation retry keeps its
client-selected identifier until the matching command succeeds. Cancellation updates the returned reservation in place
instead of predicting a terminal state in the browser.

Exit results render monetary totals from the server receipt in currency minor units. The browser does not repeat fee
calculation or infer a total from timestamps. Receipt-history navigation remains a separate workflow.

## Testing

Component tests assert user-observable behaviour through roles, names, and content. API client tests verify URL
encoding, headers, and problem-response mapping. Implementation details and CSS class names are not test contracts.
