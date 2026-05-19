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

# google tink

## overview

  * Google Tink is a cryptographic library created to make common cryptographic
    tasks safer to use from application code
  * main idea:
    * application should work with high-level cryptographic primitives
    * application should not manually combine low-level cryptographic blocks
    * application should not decide details like:
      * how to generate nonces / IVs
      * how to append authentication tags
      * how to encode ciphertext
      * how to safely rotate keys
      * how to choose algorithm parameters for common cases
  * Tink exposes cryptography through primitives
    * primitive = interface with a concrete security purpose
    * examples:
      * `Aead` - authenticated encryption with associated data
      * `DeterministicAead` - deterministic authenticated encryption
      * `Mac` - message authentication code
      * `HybridEncrypt` / `HybridDecrypt` - public key encryption
      * `StreamingAead` - authenticated encryption for large streams
  * Tink tries to reduce cryptographic misuse
    * it gives small APIs
    * it hides dangerous low-level details
    * it encourages keysets and rotation instead of one hardcoded forever-key
    * it separates the application problem from cryptographic construction details

## why Tink

  1. cryptography is easy to use incorrectly
     * AES alone is not enough information
     * we also need mode, nonce handling, authentication, encoding, key lifecycle,
       rotation, limits
     * many failures come from using a correct algorithm in an incorrect way
  2. application usually needs a security property, not a low-level algorithm
     * "encrypt this field and detect tampering"
     * "sign this data"
     * "verify that this data was not modified"
     * "encrypt with public key and decrypt with private key"
  3. Tink maps those needs to primitives
     * `Aead` for normal authenticated encryption
     * `Mac` for integrity without confidentiality
     * `HybridEncrypt` for public-key encryption
  4. Tink supports key management concepts
     * keysets
     * primary keys
     * old keys kept for decryption
     * encrypted keysets protected by KMS
     * key rotation

## basic structures

  1. primitive
     * main interface used by application code
     * answers the question: what operation do we need?
     * example:

       ```java
       Aead aead = ...;

       byte[] ciphertext = aead.encrypt(plaintext, associatedData);
       byte[] decrypted = aead.decrypt(ciphertext, associatedData);
       ```

     * application code does not manually:
       * generate IV
       * append tag
       * verify tag
       * split encrypted bytes into internal parts
  2. key
     * cryptographic secret material used by a primitive
     * example:
       * AES-GCM key for `Aead`
       * HMAC key for `Mac`
     * should not be logged
     * should not be committed to git
     * should not be copied around as normal configuration in production
  3. keyset
     * collection of keys for one primitive
     * usually contains:
       * one primary key
       * zero or more older keys
     * primary key is used for new encryption
     * older keys may still be used for decryption
     * this is useful for rotation:
       * new payloads are encrypted with a new key
       * old payloads remain decryptable
  4. keyset handle
     * object that gives application access to primitives
     * application normally asks the keyset handle for a primitive:

       ```java
       Aead aead = keysetHandle.getPrimitive(Aead.class);
       ```

     * code works with `Aead`, not directly with raw key bytes
  5. key status
     * keys can have status such as enabled or disabled
     * disabled keys should not be used
     * this helps with rotation and key retirement
  6. output prefix / key id
     * Tink ciphertext may contain information that helps choose the right key from
       a keyset
     * useful when many keys are available for decryption
     * application usually should not parse cryptographic wire format by hand
  7. KMS
     * key management system
     * used to protect Tink keysets
     * examples:
       * Google Cloud KMS
       * AWS KMS
       * HashiCorp Vault
     * common pattern:
       * data encryption key is managed by Tink
       * Tink keyset is encrypted by a KMS key
       * application loads encrypted keyset
       * KMS unwraps it at runtime

## primitive: AEAD

  * AEAD = authenticated encryption with associated data
  * it provides:
    * confidentiality
      * plaintext is hidden
    * authenticity
      * ciphertext cannot be changed without detection
    * symmetric encryption
      * same key is used for encryption and decryption
    * randomized encryption
      * encrypting the same plaintext twice usually gives different ciphertexts

Example:

```java
byte[] plaintext = "sk_test_1234567890".getBytes(StandardCharsets.UTF_8);
byte[] aad = "workflow-context".getBytes(StandardCharsets.UTF_8);

byte[] ciphertext = aead.encrypt(plaintext, aad);
byte[] decrypted = aead.decrypt(ciphertext, aad);
```

Important consequence:

  * same plaintext + same key does not mean same ciphertext
  * AEAD encryption is randomized
  * this hides equality between encrypted values

Example:

```text
plaintext: sk_test_1234567890
ciphertext 1: 8Mf3...
ciphertext 2: Yw21...
ciphertext 3: I9aQ...
```

All three ciphertexts can decrypt to the same plaintext, but an observer cannot
simply compare ciphertext strings to know that.

## AEAD inputs

  * encryption input:

    ```text
    plaintext + key + associated data -> ciphertext
    ```

  * decryption input:

    ```text
    ciphertext + same key + same associated data -> plaintext
    ```

  * decryption fails when:
    * ciphertext was modified
    * wrong key is used
    * wrong associated data is used
    * ciphertext format is invalid

## associated data

  * associated data is extra context authenticated together with ciphertext
  * associated data is not encrypted
  * associated data can be public
  * associated data must be exactly the same during encryption and decryption
  * if associated data changes, authentication fails

Example:

```java
byte[] aad1 = "user-id=100".getBytes(StandardCharsets.UTF_8);
byte[] aad2 = "user-id=200".getBytes(StandardCharsets.UTF_8);

byte[] ciphertext = aead.encrypt(secretBytes, aad1);

aead.decrypt(ciphertext, aad1); // works
aead.decrypt(ciphertext, aad2); // fails
```

This is useful when ciphertext should belong to a specific context.

Example:

  * encrypted medical history should belong to one user id
  * encrypted api key should belong to one workflow id
  * encrypted tenant secret should belong to one tenant id

The associated data is not secret. Its job is not to hide the namespace or workflow
id. Its job is to authenticate the context. If someone copies an encrypted api key
from one workflow payload into another workflow payload, the ciphertext is now paired
with different associated data, so Tink rejects it during decryption.

## Tink in this project

  * this project uses Tink for partial Temporal payload encryption
  * important class:
    * `PartialPayloadCrypto`
  * important primitive:
    * `Aead`
  * current implementation:
    * reads base64 key from `application.properties`
    * validates that decoded key has 32 bytes
    * creates AES-GCM based AEAD
    * encrypts only values represented as `SecureString`
  * token format:

    ```text
    enc:v1:<base64-aes-gcm-ciphertext>
    ```

  * example payload before encryption:

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

  * example payload shape after encryption:

    ```json
    {
      "name": "Temporal",
      "apiKey": "enc:v1:...",
      "parameters": {
        "source": "curl",
        "secretToken": "enc:v1:..."
      }
    }
    ```

  * fields left readable:
    * `name`
    * `parameters.source`
    * workflow metadata
  * fields encrypted:
    * `apiKey`
    * textual top-level parameters whose key starts with `secret`

## Temporal context as associated data

  * this project does not encrypt `SecureString` values in isolation
  * encrypted values are bound to Temporal serialization context
  * associated data is built from:
    * namespace
    * workflow id

Format:

```text
ns=<namespace>
wid=<workflowId>
```

Example:

```text
ns=default
wid=example-8f5f0e2a-4a42-43d8-a9a6-8bb5d0c5c412
```

Consequences:

  1. same secret in two workflows gets different ciphertext
     * AEAD is randomized
     * workflow id is also different
  2. copied ciphertext should not decrypt in another workflow
     * associated data differs
     * authentication check fails
  3. namespace becomes part of the authenticated boundary
     * ciphertext from another namespace should not decrypt
  4. Temporal UI can still show non-secret JSON
     * only selected fields become `enc:v1:...`

## encryption path

  1. REST request is deserialized
     * `apiKey` becomes `SecureString`
     * selected parameters become `SecureString`
  2. workflow is started
  3. Temporal serializes workflow input
  4. custom Jackson serializer sees `SecureString`
  5. serializer asks for current Temporal serialization context
  6. namespace and workflow id are converted to associated data
  7. Tink encrypts secret bytes using AEAD
  8. encrypted bytes are base64 encoded
  9. JSON field is written as `enc:v1:...`

Simplified code:

```java
String aadText = "ns=" + namespace + "\nwid=" + workflowId;
byte[] aad = aadText.getBytes(StandardCharsets.UTF_8);

byte[] ciphertext = aead.encrypt(plainBytes, aad);

String token = "enc:v1:" + Base64.getEncoder().encodeToString(ciphertext);
```

## decryption path

  1. Temporal deserializes workflow input / output / activity argument
  2. custom Jackson deserializer sees target type `SecureString`
  3. value must start with `enc:v1:`
  4. ciphertext is base64 decoded
  5. namespace and workflow id are rebuilt from Temporal context
  6. Tink tries to decrypt using the same associated data
  7. if authentication succeeds, plaintext becomes a new `SecureString`
  8. if authentication fails, deserialization fails

Simplified code:

```java
if (!token.startsWith("enc:v1:")) {
    throw new IllegalArgumentException("Encrypted field has unexpected format");
}

byte[] ciphertext = Base64.getDecoder().decode(token.substring("enc:v1:".length()));

String aadText = "ns=" + namespace + "\nwid=" + workflowId;
byte[] aad = aadText.getBytes(StandardCharsets.UTF_8);

byte[] plainBytes = aead.decrypt(ciphertext, aad);
```

## why partial encryption

  * encrypting the whole Temporal payload is simple but inconvenient
    * Temporal UI becomes less useful
    * debugging becomes harder
    * non-secret workflow state is hidden
  * leaving everything as JSON is convenient but unsafe
    * api keys are stored in workflow history
    * secrets may appear in logs or UI
  * partial encryption is a compromise
    * secret fields are encrypted
    * ordinary fields stay readable
    * encryption is explicit through type modeling
    * `SecureString` marks values that need protection

## production notes

  * current project is workshop-oriented
  * current key management is intentionally simple
  * development key is stored in `application.properties`
  * production version should use stronger key management:
    * encrypted Tink keysets
    * KMS-protected key encryption keys
    * explicit key ids
    * key rotation
    * old-key support for Temporal history replay

Possible future token format:

```text
enc:v2:<key-id>:<base64-ciphertext>
```

Why key id matters:

  * Temporal histories may live longer than one key version
  * worker may need to replay old workflow history
  * old payloads must remain decryptable after rotation
  * new payloads should use the newest key

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
