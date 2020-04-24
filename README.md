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

## Contributing to this Project

We welcome participation from all developers!
For instructions on how to contribute to this repo, please
review the [Contributing Guide](CONTRIBUTING.md).

## License Information

Licensed under Apache License,
Version 2.0 – see [LICENSE](LICENSE) in this repo
or [apache.org/licenses/LICENSE-2.0](http://www.apache.org/licenses/LICENSE-2.0).
