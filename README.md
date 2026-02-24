# temporal-partially-encrypted-payload-wip

  * references
    * https://docs.temporal.io/
    * https://docs.temporal.io/develop/java
    * https://docs.temporal.io/default-custom-data-converters
    * https://docs.temporal.io/encryption
    * https://docs.quarkiverse.io/quarkus-temporal/dev/index.html
    * https://quarkus.io/guides/rest
    * https://developers.google.com/tink
    * https://developers.google.com/tink/aead
    * https://github.com/google/tink

# quote

> Security is a process, not a product.
>
> Bruce Schneier

# preface

  * goal of this workshop:
    * show how to protect selected Temporal payload fields without encrypting the whole payload
    * preserve ordinary JSON serialization for non-secret workflow data
    * bind encrypted values to Temporal workflow context using authenticated additional data
    * reduce accidental plaintext access to secrets in application code
    * run the same workflow through a Quarkus REST endpoint and Temporal workers
  * exemplary domain:
    * endpoint receives a name, api key, and optional parameters
    * workflow calls one activity that changes the name
    * workflow calls another activity that rotates the api key
    * api keys and selected parameters are represented as `SecureString`
  * this is a workshop project, not production-ready key management:
    * the development and test encryption key is configured in `application.properties`
    * `ApiKeyActivityImpl` returns a hardcoded replacement key
    * the response intentionally exposes plaintext keys so the example is easy to verify

# project description

This project is a Quarkus application using Temporal Java SDK through Quarkus Temporal.

The important part is a custom Temporal `DataConverter`: all values of type `SecureString`
are serialized as encrypted tokens, while ordinary strings, maps, numbers, booleans, and
workflow metadata remain readable JSON.

The encryption format is:

```text
enc:v1:<base64-aes-gcm-ciphertext>
```

The encrypted token is produced by `PartialPayloadCrypto` using Tink AEAD backed by
AES-GCM. The additional authenticated data is derived from the Temporal serialization
context:

```text
ns=<namespace>
wid=<workflowId>
```

Consequences:

  1. the same secret serialized in different workflows produces context-bound ciphertext
  2. copying an encrypted field from one workflow payload to another workflow should not decrypt
  3. non-secret fields remain readable in Temporal payload JSON

# modules

  * `TemporalWorkflowResource`
    * exposes `POST /temporal/example`
    * converts request `apiKey` into `SecureString`
    * converts top-level textual parameters whose keys start with `secret` into `SecureString`
    * starts `ExampleWorkflow`
  * `ExampleWorkflowImpl`
    * runs on worker `<default>`
    * calls `NameActivity`
    * calls `ApiKeyActivity`
  * `TemporalDataConverterProducer`
    * creates the Temporal `DataConverter`
    * registers custom Jackson serializer and deserializer for `SecureString`
    * installs `ContextAwareJacksonPayloadConverter`
  * `ContextAwareJacksonPayloadConverter`
    * keeps Temporal `SerializationContext` available while Jackson serializes/deserializes
  * `TemporalSerializationContextHolder`
    * stores serialization context in a thread-local for the current conversion
  * `PartialPayloadCrypto`
    * encrypts and decrypts `SecureString` values
    * validates key length and non-empty AAD
  * `SecureString`
    * stores secret text as copied `char[]`
    * redacts `toString`
    * requires `@AllowUnsafeChars` where code intentionally extracts plaintext chars

# request model

Example request:

```json
{
  "name": "Temporal",
  "apiKey": "sk_test_1234567890",
  "parameters": {
    "source": "curl",
    "secretToken": "param_secret"
  }
}
```

Rules:

  1. `apiKey` is always treated as secret
  2. top-level textual `parameters` entries whose key starts with `secret` are treated as secret
  3. non-textual `secret...` parameters are left unchanged
  4. nested `secret...` fields are left unchanged
  5. `parameters` may be `null`, but if present it must be a JSON object

# workflow

  1. REST receives JSON request
  2. request deserialization wraps selected values in `SecureString`
  3. resource starts `ExampleWorkflow` with workflow id `example-<uuid>`
  4. Temporal serializes workflow input using the custom data converter
  5. every `SecureString` field is encrypted before it enters Temporal payload storage
  6. workflow deserialization decrypts `SecureString` values using workflow context as AAD
  7. `ApiKeyActivity` receives old api key and returns old/new key pair plus timestamp
  8. REST response converts secure values back to plaintext for demo verification

# security notes

  1. partial encryption is type-based
     * only `SecureString` is encrypted
     * ordinary `String` values are not encrypted
     * callers must model secrets explicitly
  2. authenticated additional data protects context
     * encryption uses namespace and workflow id
     * decryption requires the same serialization context
  3. plaintext access is explicit
     * `SecureString.unsafeChars()` is annotated with Error Prone `@RestrictedApi`
     * callers must add `@AllowUnsafeChars` with a reason
  4. char arrays are cleared after temporary plaintext use
     * this limits accidental retention
     * it does not guarantee complete JVM memory erasure
  5. development key is not a production key strategy
     * use external key management for real deployments
     * plan rotation and versioning beyond the `enc:v1:` prefix

# manual

## tests

```shell
./gradlew test
```

Tests use mocked Temporal support:

```properties
%test.quarkus.temporal.enable-mock=true
%test.quarkus.compose.devservices.enabled=false
```

Important tests:

  * `TemporalWorkflowResourceIT`
    * verifies the REST endpoint starts the workflow and returns expected output
  * `TemporalWorkflowResourceRequestTest`
    * verifies request deserialization rules for secret parameters

## application

Run the application in Quarkus dev mode:

```shell
./gradlew quarkusDev
```

In dev mode this project uses Docker Compose dev services:

```yaml
services:
  temporal:
    image: temporalio/temporal:latest
    command: ["server", "start-dev", "--ip", "0.0.0.0", "--namespace", "default"]
    ports:
      - "7233:7233"
      - "8088:8233"
```

Useful local URLs:

  * application: http://localhost:8080
  * Quarkus Dev UI: http://localhost:8080/q/dev/
  * Temporal UI: http://localhost:8088

## example call

```shell
curl -X POST 'http://localhost:8080/temporal/example' \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "Temporal",
    "apiKey": "sk_test_1234567890",
    "parameters": {
      "source": "curl",
      "secretToken": "param_secret"
    }
  }'
```

Expected shape:

```json
{
  "oldName": "Temporal",
  "newName": "new_name_hardcoded",
  "oldApiKey": "sk_test_1234567890",
  "newApiKey": "sk_new_hardcoded_123",
  "date": "2026-05-16T00:00:00.000Z"
}
```

The exact `date` is generated at runtime.

# implementation overview

## secure string

`SecureString` is the marker type that makes partial encryption possible:

```java
public final class SecureString {
    private final char[] value;

    public SecureString(char[] value) {
        this.value = Arrays.copyOf(value, value.length);
    }

    @RestrictedApi(
            explanation = "Access to secret chars must be explicitly acknowledged via @AllowUnsafeChars",
            link = "",
            allowlistAnnotations = {AllowUnsafeChars.class}
    )
    public char[] unsafeChars() {
        return Arrays.copyOf(value, value.length);
    }

    @Override
    public String toString() {
        return "SecureString(**redacted**)";
    }
}
```

Key points:

  1. constructor copies input chars
  2. `unsafeChars` returns a defensive copy
  3. `toString` never prints the secret
  4. Error Prone requires explicit local acknowledgement before plaintext extraction

## custom temporal data converter

Temporal uses a `DataConverter` to transform workflow/activity inputs and outputs into
payloads. This project overrides the Jackson payload converter:

```java
return DefaultDataConverter.newDefaultInstance()
        .withPayloadConverterOverrides(
                new ContextAwareJacksonPayloadConverter(mapper)
        );
```

The mapper registers custom handlers:

  * serializer:
    * receives `SecureString`
    * builds workflow-bound AAD
    * writes encrypted token as a JSON string
  * deserializer:
    * rejects missing values
    * rejects non-encrypted `SecureString` payloads
    * decrypts encrypted token into a new `SecureString`

## serialization context

Temporal supplies context to payload converters through `withContext`.

This project preserves that context around Jackson conversion:

```java
@Override
public Optional<Payload> toData(Object value) {
    return TemporalSerializationContextHolder.withContext(context, () -> super.toData(value));
}
```

Then encryption can use workflow-specific metadata:

```java
String namespace = workflowContext.getNamespace();
String workflowId = workflowContext.getWorkflowId();
return ("ns=" + namespace + "\nwid=" + workflowId).getBytes(StandardCharsets.UTF_8);
```

# exercises

  1. inspect Temporal UI after running the curl request
     * verify which fields stay readable
     * verify which fields become `enc:v1:...`
  2. add another secret field to `ExampleWorkflowInput`
     * model it as `SecureString`
     * confirm it is encrypted automatically
  3. add a non-secret string field
     * confirm it remains readable in payload JSON
  4. replace the hardcoded key in `ApiKeyActivityImpl`
     * call a fake external key provider
     * keep the result as `SecureString`
  5. add nested secret parameter handling
     * recursively convert textual keys starting with `secret`
     * update request tests
  6. introduce key versions
     * extend token prefix from `enc:v1:` to a versioned key id format
     * keep backward-compatible decryption
  7. add a negative test for workflow context binding
     * encrypt with one workflow id
     * attempt to decrypt with another workflow id
     * expect decryption failure

# build

Package the application:

```shell
./gradlew build
```

Run the packaged Quarkus application:

```shell
java -jar build/quarkus-app/quarkus-run.jar
```

Build an uber jar:

```shell
./gradlew build -Dquarkus.package.jar.type=uber-jar
```

Build a native executable:

```shell
./gradlew build -Dquarkus.native.enabled=true
```
