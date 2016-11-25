[![Build Status](https://travis-ci.com/RackSec/alertlogic-lib.svg?token=SsdaNZWmAMhuouKpWNZa&branch=master)](https://travis-ci.com/RackSec/alertlogic-lib)
[![codecov](https://codecov.io/gh/RackSec/alertlogic-lib/branch/master/graph/badge.svg?token=PILVJJwrfX)](https://codecov.io/gh/RackSec/alertlogic-lib)

# alertlogic-lib

Clojure library used to fetch information from the Alert Logic APIs.

## Usage

### Fetching all Log Manager devices for a given customer

```clojure
=> (use 'alertlogic.core)
=> (get-lm-devices-for-customer! "11111" sekret-api-key)
16-11-25 19:39:43 INFO [alertlogic-lib.core] - fetching /api/lm/v1/11111/hosts
({:name "some-machine.rackspace.security",
  :status "ok",
  :ips ["192.168.0.129"],
  :type "host"}
 {:name "another.rackspace.security",
  :status "ok",
  :ips
  ["192.168.10.50"
   "192.168.10.51"],
  :type "host"}
 {:name "machineeee.rackspace.security",
  :status "ok",
  :ips ["192.168.0.130"],
  :type "host"})
```

## License

Copyright © 2016 Rackspace

This software is proprietary.
