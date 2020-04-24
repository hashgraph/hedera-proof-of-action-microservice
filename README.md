# Hedera™ Hashgraph Proof-of-Action API

## Requirements

 * PostgreSQL 10+
 
 * Java 12+
 
## Usage

### Build

```
$ ./gradlew build
```

### Configure

Copy [`.env.sample`](.env.sample) to `.env` and fill out. You will need an operator to submit 
transactions and a Topic ID to submit transactions to. 

### Run

```
$ java -jar build/libs/hedera-proof-of-action.jar
```

## Example

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
