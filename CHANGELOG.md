# 0.1.1

Bugfix: get log manager device type at top-level of the returned JSON (instead
of looking inside the status info).

# 0.1.0

Initial release.

## Customers

- Adds `get-customers!` for fetching Alert Logic customer accounts and
  sub-accounts
- Adds `get-customers-map!` which maps known customer IDs (based on a naming
  convention) to Alert Logic customer IDs

## Log Manager

- Adds `get-lm-devices-for-customer!` for fetching all devices available under
  the Alert Logic Log Manager for a given customer
