# How to Drive OCRA Evaluations from JMeter via the Native Java API

This guide shows how to call the Native Java OCRA facade from Apache JMeter using a JSR223 Sampler with Groovy. The
snippet below is self-contained: it constructs an OCRA credential descriptor and execution context directly, without any
extra helper classes.

## Prerequisites

- Java 17 JDK to build and run OpenAuth Simulator itself (per the project constitution).
- JMeter must also run on a Java 17 (or newer) runtime, because the OpenAuth Simulator JARs are compiled with
  `targetCompatibility = 17`. Check the `java -version` used by `jmeter`/`jmeter.bat` rather than relying only on
  `JAVA_HOME`.
- OpenAuth Simulator JARs (at minimum: `core`, `core-shared`, `core-ocra`) on JMeter’s classpath.
- JMeter configured with a JSR223 Sampler using `groovy` as the language.

## 1. JSR223 Sampler for inline OCRA

The following Groovy script can be pasted directly into a JSR223 Sampler. It constructs an OCRA credential descriptor
and execution context using the same types as `use-ocra-from-java.md`, then calculates the OTP and stores it in a
JMeter variable.

```groovy
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory.OcraCredentialRequest
import io.openauth.sim.core.credentials.ocra.OcraCredentialDescriptor
import io.openauth.sim.core.credentials.ocra.OcraResponseCalculator
import io.openauth.sim.core.credentials.ocra.OcraResponseCalculator.OcraExecutionContext
import io.openauth.sim.core.model.SecretEncoding

// Guard: fail fast if required inputs are missing
def requirePresent = { String value, String label ->
    if (value == null || value.trim().isEmpty()) {
        def msg = "Missing required input: ${label}"
        log.error(msg)
        prev.setSuccessful(false)
        prev.setResponseMessage(msg)
        prev.setResponseCode("500")
        org.apache.jmeter.engine.StandardJMeterEngine.stopTest()
        return null
    }
    return value
}

// Input parameters (must be provided via JMeter variables)
def credentialId = requirePresent(vars.get('ocraCredentialId'), "JMeter variable 'ocraCredentialId'")
if (credentialId == null) return
def suite = requirePresent(vars.get('ocraSuite'), "JMeter variable 'ocraSuite'")
if (suite == null) return
def secretHex = requirePresent(vars.get('ocraSecretHex'), "JMeter variable 'ocraSecretHex'")
if (secretHex == null) return
def challenge = requirePresent(vars.get('ocraChallenge'), "JMeter variable 'ocraChallenge'") // 8-char alphanumeric for QA08
if (challenge == null) return
def sessionHex = requirePresent(vars.get('ocraSessionHex'), "JMeter variable 'ocraSessionHex'")
if (sessionHex == null) return

// Build a credential descriptor (inline)
def factory = new OcraCredentialFactory()
OcraCredentialDescriptor descriptor = factory.createDescriptor(
        new OcraCredentialRequest(
                credentialId,
                suite,
                secretHex,
                SecretEncoding.HEX,
                null,              // counter (for suites with C)
                null,              // PIN hash (optional)
                null,              // allowed timestamp drift (for T suites)
                java.util.Map.of('owner', 'jmeter-demo')
        )
)

// Build an execution context for QA08-S064
OcraExecutionContext context = new OcraExecutionContext(
        null,         // counter (C)
        challenge,    // combined question (Q)
        sessionHex,   // session data (S064)
        null,         // client challenge (for split Q forms)
        null,         // server challenge (for split Q forms)
        null,         // runtime PIN hash
        null          // timestamp (for T suites)
)

// Calculate the OCRA response
def otp = OcraResponseCalculator.generate(descriptor, context)

// Expose OTP to the rest of the JMeter test plan
vars.put('ocraOtp', otp)
log.info("OCRA OTP for ${credentialId}: ${otp}")
```

This script performs pure OCRA calculation using inline parameters. To model different suites or secrets, override
`ocraSuite`, `ocraSecretHex`, `ocraChallenge`, or `ocraSessionHex` via JMeter variables or CSV Data Set Configs.

## 2. JSR223 Sampler for stored OCRA descriptors

Sometimes you want to treat descriptors as “stored” credentials that operators reference by identifier. The following
script shows how to keep a small in-script registry of OCRA descriptors keyed by `credentialId` and evaluate a stored
entry. You can replace the hard-coded entries with your own configuration source as needed.

```groovy
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory.OcraCredentialRequest
import io.openauth.sim.core.credentials.ocra.OcraCredentialDescriptor
import io.openauth.sim.core.credentials.ocra.OcraResponseCalculator
import io.openauth.sim.core.credentials.ocra.OcraResponseCalculator.OcraExecutionContext
import io.openauth.sim.core.model.SecretEncoding

def factory = new OcraCredentialFactory()

// Simple in-memory registry of descriptors keyed by credentialId
Map<String, OcraCredentialDescriptor> registry = [
        "operator-demo": factory.createDescriptor(new OcraCredentialRequest(
                "operator-demo",
                "OCRA-1:HOTP-SHA256-8:QA08-S064",
                "3132333435363738393031323334353637383930313233343536373839303132",
                SecretEncoding.HEX,
                null,
                null,
                null,
                java.util.Map.of("owner", "registry"))),
        "tps-demo": factory.createDescriptor(new OcraCredentialRequest(
                "tps-demo",
                "OCRA-1:HOTP-SHA1-6:QA08",
                "31323334353637383930313233343536",
                SecretEncoding.HEX,
                null,
                null,
                null,
                java.util.Map.of("owner", "registry")))
]

// Guard: fail fast if required inputs are missing
def requirePresentStored = { String value, String label ->
    if (value == null || value.trim().isEmpty()) {
        def msg = "Missing required input: ${label}"
        log.error(msg)
        prev.setSuccessful(false)
        prev.setResponseMessage(msg)
        prev.setResponseCode("500")
        org.apache.jmeter.engine.StandardJMeterEngine.stopTest()
        return null
    }
    return value
}

// Input parameters (must be provided via JMeter variables)
def credentialId = requirePresentStored(vars.get('ocraStoredCredentialId'), "JMeter variable 'ocraStoredCredentialId'")
if (credentialId == null) return
def challenge = requirePresentStored(vars.get('ocraStoredChallenge'), "JMeter variable 'ocraStoredChallenge'")
if (challenge == null) return
def sessionHex = requirePresentStored(vars.get('ocraStoredSessionHex'), "JMeter variable 'ocraStoredSessionHex'")
if (sessionHex == null) return

OcraCredentialDescriptor storedDescriptor = registry.get(credentialId)
if (storedDescriptor == null) {
    throw new IllegalArgumentException("Unknown OCRA credentialId in registry: " + credentialId)
}

OcraExecutionContext storedContext = new OcraExecutionContext(
        null,
        challenge,
        sessionHex,
        null,
        null,
        null,
        null)

def storedOtp = OcraResponseCalculator.generate(storedDescriptor, storedContext)
vars.put('ocraStoredOtp', storedOtp)
log.info("Stored OCRA OTP for ${credentialId}: ${storedOtp}")
```

This pattern keeps OCRA in the “calculation-only” space while giving you a clear separation between inline requests and
stored descriptors referenced by name.
