# Hedera™ Hashgraph Proof-of-Action Microservice

## What is the proof-of-action microservice?
The Hedera Proof of Action (HPoA) microservice aims to make it easy for organizations to build a capability to record and subsequently prove the existence of business events using the Hedera Consensus Service.

Examples of these types of recorded events are proof of deletion of personally sensitive data for regulatory compliance, proof of usage of data for a particular purpose for record keeping, or proof of redeeming a coupon for business process automation.

## How does the proof-of-action microservice work?
First, an application logs important business events (serialized business objects) into the HPoA microservice at the time they occur. The business logic specifies whether the business event should be stored in open, encrypted, or in the hashed form. The HPoA microservice stores these events in a specific HCS topic, configured for this purpose. Creation of this topic is done as part of initialization.

When the business or third party needs to prove the existence of an event – say for creating a report, for an audit, or in a court of law — it simply invokes another API with either the business object or an identifier to that object, and the HPoA microservice provides a mathematically provable record of that business object being stored on to the public ledger along with the timestamp.

## Microservice architecture diagram
First, a business application creates a business object that needs to be recorded for the purposes of compliance, auditability, public verifiability, automation, or other regulatory reasons. The business logic simply calls a /record_action API with a serialized representation of that business object. The following options are available to the business logic:

#### Storage in the public ledger:
* Store the object in unencrypted form
* Store the object in an encrypted form
* Store just the hash of the object

#### Storage in a database within the microservice:
* Option to store the business object in a database within the microservice

When the business application invokes this API, the appropriate data (original object, encrypted object, or a hash) is sent to Hedera Consensus Service using the SDK. If required, the SDK handles fragmentation of messages.  The microservice returns the Transaction-ID that the business logic stores for future references.

When the business application wants to prove that a particular event was memorialized on the public DLT, it invokes the prove_action API – either with the original business object, or with the Transction-ID. The HPoA microservice queries a mirror node (mirror nodes are run by Hedera as well as several third parties), and obtains the record of the transaction. It also creates an equivalent representation of the original object (encryption or hashed), and compares it with the representation obtained from the mirror node record. If the two match, it returns a successful response with the business object that was memorialized and appropriate details of the proof from the Hedera’s public ledger. In future, this proof will also contain the state proof obtained from Hedera.

![Image of HPoA microservice architecture diagram](https://s3.amazonaws.com/hedera-com/Screen-Shot-2020-05-07-at-11.14.00-AM.png)


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

An optional, client-generated, non-unique `clientId` can be submitted and later used to lookup actions. 

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

An action can be proven by requesting by the original payload, the returned transaction ID, or a client-generated ID.

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
