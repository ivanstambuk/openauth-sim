# How to Drive EMV/CAP Evaluations from JMeter via the Native Java API

This guide shows how to call the Native Java EMV/CAP facade from Apache JMeter using a JSR223 Sampler with Groovy. The
snippet below constructs an `EmvCapEvaluationApplicationService`, builds an inline Identify-mode request, and calculates
EMV/CAP OTP values (no replay flows).

## Prerequisites

- Java 17 JDK to build and run OpenAuth Simulator itself (per the project constitution).
- JMeter must also run on a Java 17 (or newer) runtime, because the OpenAuth Simulator JARs are compiled with
  `targetCompatibility = 17`. Check the `java -version` used by `jmeter`/`jmeter.bat` rather than relying only on
  `JAVA_HOME`.
- OpenAuth Simulator JARs on JMeter’s classpath (at minimum: `core`, `core-shared`, `core-ocra`, and `application`).
- JMeter configured with a JSR223 Sampler using `groovy`.

## 1. JSR223 Sampler for inline EMV/CAP Identify mode

The Groovy script below can be pasted directly into a JSR223 Sampler. It uses the Native Java
`EmvCapEvaluationApplicationService` and an inline Identify-mode request:

```groovy
import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService
import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.EvaluationRequest
import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.CustomerInputs
import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.TransactionData
import io.openauth.sim.core.emv.cap.EmvCapMode

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
def masterKeyHex = requirePresent(vars.get('emvMasterKeyHex'), "JMeter variable 'emvMasterKeyHex'")
if (masterKeyHex == null) return
def atcHex = requirePresent(vars.get('emvAtcHex'), "JMeter variable 'emvAtcHex'")
if (atcHex == null) return
def challenge = requirePresent(vars.get('emvChallenge'), "JMeter variable 'emvChallenge'")
if (challenge == null) return
def reference = requirePresent(vars.get('emvReference'), "JMeter variable 'emvReference'")
if (reference == null) return
def amount = requirePresent(vars.get('emvAmount'), "JMeter variable 'emvAmount'")
if (amount == null) return

// Build Identify-mode request
EvaluationRequest request = new EvaluationRequest(
        EmvCapMode.IDENTIFY,
        masterKeyHex,
        atcHex,
        4,   // branchFactor
        8,   // height
        0,   // previewWindowBackward
        0,   // previewWindowForward
        '0000000000000000',          // ivHex
        '000000000000',              // cdol1Hex (example)
        '00001F0000000000000000000000000000000000', // issuerProprietaryBitmapHex
        new CustomerInputs(challenge, reference, amount),
        TransactionData.empty(),
        '',                          // iccDataTemplateHex
        ''                           // issuerApplicationDataHex
)

def service = new EmvCapEvaluationApplicationService()
def result = service.evaluate(request)

def otp = result.otp()
vars.put('emvOtp', otp)
log.info("EMV/CAP Identify OTP: ${otp}, maskLength=${result.maskLength()}")
```

You can adapt this pattern for Respond/Sign modes by changing `EmvCapMode` and the CDOL/customer-input fields while
keeping the script self-contained (no helper classes).

## 2. JSR223 Sampler for EMV/CAP with stored presets

While EMV/CAP does not use a `CredentialStore`, many scenarios rely on a fixed set of master keys and CDOL templates
identified by name. The snippet below demonstrates how to treat those as “stored” presets keyed by an identifier,
keeping the calculation behaviour identical while separating configuration from inputs.

```groovy
import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService
import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.EvaluationRequest
import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.CustomerInputs
import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.TransactionData
import io.openauth.sim.core.emv.cap.EmvCapMode

// Registry of EMV/CAP presets keyed by presetId
def presets = [
        "identify-demo": [
                masterKeyHex: "00112233445566778899AABBCCDDEEFF",
                atcHex       : "0001",
                branchFactor : 4,
                height       : 8,
                ivHex        : "0000000000000000",
                cdol1Hex     : "000000000000",
                issuerBitmap : "00001F0000000000000000000000000000000000"
        ]
]

// Guard: fail fast if required inputs are missing
def requirePresentPreset = { String value, String label ->
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

def presetId = requirePresentPreset(vars.get('emvPresetId'), "JMeter variable 'emvPresetId'")
if (presetId == null) return
def preset = presets[presetId]
if (preset == null) {
    throw new IllegalArgumentException("Unknown EMV/CAP presetId: " + presetId)
}

// Customer inputs (must be provided via JMeter variables / CSV)
def challenge = requirePresentPreset(vars.get('emvStoredChallenge'), "JMeter variable 'emvStoredChallenge'")
if (challenge == null) return
def reference = requirePresentPreset(vars.get('emvStoredReference'), "JMeter variable 'emvStoredReference'")
if (reference == null) return
def amount = requirePresentPreset(vars.get('emvStoredAmount'), "JMeter variable 'emvStoredAmount'")
if (amount == null) return

EvaluationRequest storedRequest = new EvaluationRequest(
        EmvCapMode.IDENTIFY,
        preset.masterKeyHex,
        preset.atcHex,
        preset.branchFactor as int,
        preset.height as int,
        0,
        0,
        preset.ivHex,
        preset.cdol1Hex,
        preset.issuerBitmap,
        new CustomerInputs(challenge, reference, amount),
        TransactionData.empty(),
        "",
        ""
)

def storedService = new EmvCapEvaluationApplicationService()
def storedResult = storedService.evaluate(storedRequest)

def storedOtp = storedResult.otp()
vars.put('emvStoredOtp', storedOtp)
log.info("EMV/CAP Identify OTP (preset=${presetId}): ${storedOtp}")
```

This mirrors the EMV/CAP inline calculation path but lets you distinguish between inline (ad-hoc) requests and stored
presets keyed by name. Both flows remain generation-only with no replay semantics.
