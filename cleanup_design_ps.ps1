$path = 'F:\PROJECTS\git\distributed-order-system\design.md'
$text = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
$replacements = @(
  @('payment_idempotency', 'business-natural idempotency key'),
  @('MANUAL_IMMEDIATE', 'RECORD'),
  @('dlq_message', 'DLQ table'),
  @('INVENTORY_RESERVED', 'reservation'),
  @('confirm fails, so refund', 'the valid refund path is triggered only when a payment success arrives after cancellation or compensation has already begun'),
  @('`payment_idempotency`', '`business-natural idempotency key`'),
  @('`MANUAL_IMMEDIATE`', '`RECORD`'),
  @('`INVENTORY_RESERVED`', '`reservation`'),
  @('`dlq_message`', '`DLQ table`'),
  @('No public-order `FAILED` or `reservation` state is kept; those are internal or transient saga details, not reliable client-facing status.', 'No public-order `FAILED` state is kept; those are internal or transient saga details, not reliable client-facing status.'),
  @('No public-order `FAILED` or `reservation` state is kept; those are internal or transient saga details, not reliable client-facing status.', 'No public-order `FAILED` state is kept; those are internal or transient saga details, not reliable client-facing status.'),
  @('Alternative considered: a separate `DLQ table` was rejected because the repo already has a clear Kafka-topic-based DLQ contract and a replay script is simpler to operate and reason about.', 'Alternative considered: a separate DLQ table was rejected because the repo already has a clear Kafka-topic-based DLQ contract and a replay script is simpler to operate and reason about.'),
  @('No separate `DLQ table` is required. The DLQ topic is the durable source of truth for replay.', 'No separate DLQ table is required. The DLQ topic is the durable source of truth for replay.')
)
foreach ($pair in $replacements) {
  $text = $text.Replace($pair[0], $pair[1])
}
$special = @(
  @('â€™', "'"),
  @('â€', '"'),
  @('â€œ', '"'),
  @('â€”', '-'),
  @('â€“', '-'),
  @('â€', '"'),
  @('’', "'"),
  @('“', '"'),
  @('”', '"'),
  @('—', '-'),
  @('–', '-')
)
foreach ($pair in $special) {
  $text = $text.Replace($pair[0], $pair[1])
}
[System.IO.File]::WriteAllText($path, $text, [System.Text.UTF8Encoding]::new($false))
Write-Host 'cleanup done'
