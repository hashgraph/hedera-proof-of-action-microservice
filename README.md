# Hedera™ Hashgraph Proof-of-Action Microservice

## Requirements

 * PostgreSQL 10+

## Install

Ensure that the database exists. On the first run, the necessary tables will be created.

```sh
$ docker run -p 8080:8080 -d \
    -e DATABASE_URL="postgres://postgres:password@127.0.0.1/hedera_proof_of_action__dev" \
    -e HEDERA_OPERATOR_ID="0.0.xxx" \
    -e HEDERA_OPERATOR_KEY="302..." \
    -e HEDERA_TOPIC_ID="0.0.yyy" \
    -e SECRET_KEY="..."
    docker.pkg.github.com/hashgraph/hedera-proof-of-action-microservice/hedera-proof-of-action:897
```

 * `DATABASE_URL` – URL to the postgres database server to use for persistence.
 
 * `HEDERA_OPERATOR_ID` – The Account ID on Hedera™ that will pay for the transactions.
 
 * `HEDERA_OEPRATOR_KEY` – The matching private key for the `HEDERA_OPERATOR_ID`.
 
 * `HEDERA_TOPIC_ID` – The topic ID to use for consensus. A new one will be created if not provided.
 
 * `SECRET_KEY` – The secret key to use to optionally encrypt messages to Hedera. A new one will be generated if not provided.  

## Usage

### Submit an action

An action can be submitted to HCS as a `hash`, `encrypted`, or `direct` (
`direct` is the default).

```json
POST /v1/action/
content-type: application/json

{
    "payload": "anything goes here",
    "submit": "hash"
}
```

```json
202 Accepted
content-length: 49
content-type: application/json

{
    "transactionId": "0.0.1035@1587742118.141000000"
}
```

### Prove an action

An action can be proven by requesting by the original payload
or the returned transaction ID.

```json
GET /v1/action/?payload=anything%20goes%20here
```

```json
GET /v1/action/?transactionId=0.0.1035@1587742118.141000000
```

```json
200 OK
content-length: 235
content-type: application/json

[
    {
        "consensusTimestamp": "2020-04-24T15:28:48.595593Z",
        "runningHash": "2dc9abcfea672d0d6047504dada83e4540f3a28601af7b6e9eaaf071e570b3624d5f22d7d0caa7944e00ee6fb11f9392",
        "sequenceNumber": 19,
        "transactionId": "0.0.1035@1587742118.141000000"
    }
]
```

## Contributing to this Project

We welcome participation from all developers!
For instructions on how to contribute to this repo, please
review the [Contributing Guide](CONTRIBUTING.md).

## License Information

Licensed under Apache License,
Version 2.0 – see [LICENSE](LICENSE) in this repo
or [apache.org/licenses/LICENSE-2.0](http://www.apache.org/licenses/LICENSE-2.0).
