# How-To: Generate OCRA Test Vectors

_Status: Draft_
_Last updated: 2025-09-29_

Consistently derive new OCRA test vectors with the reference Java implementation from Appendix B of the "draft-mraihi-mutual-oath-hotp-variants" specification. This workflow is mandatory for every suite we exercise in domain, REST, or UI tests so fixtures remain reproducible and traceable.

## Prerequisites
- Java 17 toolchain available on the PATH (the repository already standardises on JDK 17).
- Local copy of the Appendix A/B reference code from <https://www.potaroo.net/ietf/all-ids/draft-mraihi-mutual-oath-hotp-variants-11.txt>.
- Deterministic inputs you plan to exercise (suite string, shared secret hex, challenge/counter/session/timestamp values).
- Workspace cloned and clean so you can commit new fixtures immediately after generation.

## Steps
1. **Extract the reference classes.** Copy the `OCRA` class from Appendix A and the helper harness from Appendix B (only the portions you need) into a local scratch file or an interactive `jshell` session. Do not modify the logic beyond trimming unused loops.
2. **Prepare inputs.** Decide on explicit sample values for the new suite. Use the standard seeds defined in Appendix B unless the feature requires different material. Record each parameter (suite, key, counter, question, password, session, timestamp) in your notes before execution.
3. **Execute the generator.** Run the code using `jshell` or a temporary `javac`/`java` pair. Example `jshell` snippet:
   ```shell
   jshell <<'EOF'
   /open /path/to/OCRA.java
   String suite = "OCRA-1:HOTP-SHA256-6:C-QH64";
   String key = "3132333435363738393031323334353637383930313233343536373839303132";
   String counter = "1";
   String question = "00112233445566778899AABBCCDDEEFF00112233445566778899AABBCCDDEEFF";
   String password = "";
   String sessionInformation = "";
   String timeStamp = "";
   System.out.println(OCRA.generateOCRA(suite, key, counter, question, password, sessionInformation, timeStamp));
   EOF
   ```
   Adjust the variables as required, but keep the generator unchanged.
4. **Capture outputs.** Record each OTP alongside its inputs in the relevant fixture class. Include at least one counter/example per suite so regressions can detect drift. Store artefacts under version control (e.g., `OcraDraftHotpVariantsVectorFixtures`).
5. **Document provenance.** Cross-link the fixture or test with this how-to and note the generation date in commit messages or doc updates.

## Verification
- Re-run the generator with the same inputs and confirm the OTP matches the value committed to the repository.
- Execute `./gradlew spotlessApply check` to ensure new tests pass and formatting remains consistent.

## Rollback
- Remove any fixture or documentation changes you introduced, or revert the commit that added the vector.
- If you mistakenly generated values with a modified generator, discard them and repeat the procedure with the unaltered Appendix B code.

## Time to complete
- 5–10 minutes per suite once inputs are selected.

## Common failures
- **Edited generator logic:** Leads to mismatched OTPs later. Always re-copy the Appendix code rather than editing a cached copy.
- **Incorrect padding:** Ensure counter and challenge values follow the draft’s padding rules (lengths are zero-padded within the generator).
- **Secret encoding mismatch:** Verify secrets are supplied in hex and correspond to the suite’s expected key length (20/32/64 bytes).
