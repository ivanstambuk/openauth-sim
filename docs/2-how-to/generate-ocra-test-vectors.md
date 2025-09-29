# How-To: Generate OCRA Test Vectors

_Status: Draft_
_Last updated: 2025-09-29_

Consistently derive new OCRA test vectors with the reference Java implementation from Appendix B of the "draft-mraihi-mutual-oath-hotp-variants" specification. This workflow is mandatory for every suite we exercise in domain, REST, or UI tests so fixtures remain reproducible and traceable.

## Prerequisites
- Java 17 toolchain available on the PATH (the repository already standardises on JDK 17).
- Appendix A/B reference code embedded below (original source: <https://www.potaroo.net/ietf/all-ids/draft-mraihi-mutual-oath-hotp-variants-11.txt>).
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




## Embedded Reference Code

The following classes are copied from Appendix A and Appendix B of draft-mraihi-mutual-oath-hotp-variants-11
(available at <https://www.potaroo.net/ietf/all-ids/draft-mraihi-mutual-oath-hotp-variants-11.txt>). They are
provided verbatim aside from whitespace normalization so future agents can generate vectors without fetching the draft.

### Appendix A – `OCRA.java`

```java
import java.lang.reflect.UndeclaredThrowableException;
import java.security.GeneralSecurityException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;

/**
 * This an example implementation of the OATH OCRA algorithm.
 * Visit www.openauthentication.org for more information.
 *
 *
 * @author Johan Rydell, PortWise
 */
public class OCRA {

    private OCRA() {}

    /**
    * This method uses the JCE to provide the crypto
    * algorithm.
    * HMAC computes a Hashed Message Authentication Code with the
    * crypto hash algorithm as a parameter.
    *
    * @param crypto     the crypto algorithm (HmacSHA1,
    *                   HmacSHA256,
    *                   HmacSHA512)
    * @param keyBytes   the bytes to use for the HMAC key
    * @param text       the message or text to be authenticated.
    */

    private static byte[] hmac_sha1(String crypto,
    byte[] keyBytes,
    byte[] text)
    {
        try {
            Mac hmac;
            hmac = Mac.getInstance(crypto);
            SecretKeySpec macKey =
            new SecretKeySpec(keyBytes, "RAW");
            hmac.init(macKey);
            return hmac.doFinal(text);
        } catch (GeneralSecurityException gse) {
            throw new UndeclaredThrowableException(gse);
        }
    }

    private static final int[] DIGITS_POWER
    // 0 1  2   3    4     5      6       7        8
    = {1,10,100,1000,10000,100000,1000000,10000000,100000000 };

    /**
    * This method converts HEX string to Byte[]
    *
    * @param hex   the HEX string
    *
    * @return      A byte array
    */

    private static byte[] hexStr2Bytes(String hex){
        // Adding one byte to get the right conversion
        // values starting with "0" can be converted
        byte[] bArray = new BigInteger("10" + hex,16).toByteArray();

        // Copy all the REAL bytes, not the "first"
        byte[] ret = new byte[bArray.length - 1];
        for (int i = 0; i < ret.length ; i++)
        ret[i] = bArray[i+1];
        return ret;
    }

    /**
    * This method generates an OCRA HOTP value for the given
    * set of parameters.
    *
    * @param ocraSuite    the OCRA Suite
    * @param key          the shared secret, HEX encoded
    * @param counter      the counter that changes
    *                     on a per use basis,
    *                     HEX encoded
    * @param question     the challenge question, HEX encoded
    * @param password     a password that can be used,
    *                     HEX encoded
    * @param sessionInformation
    *                     Static information that identifies the
    *                     current session, Hex encoded
    * @param timeStamp    a value that reflects a time
    *
    * @return A numeric String in base 10 that includes
    * {@link truncationDigits} digits
    */
    static public String generateOCRA(String ocraSuite,
    String key,
    String counter,
    String question,
    String password,
    String sessionInformation,
    String timeStamp)
    {
        int codeDigits = 0;
        String crypto = "";
        String result = null;
        int ocraSuiteLength = (ocraSuite.getBytes()).length;
        int counterLength = 0;
        int questionLength = 0;
        int passwordLength = 0;

        int sessionInformationLength = 0;
        int timeStampLength = 0;

        if(ocraSuite.toLowerCase().indexOf("sha1") > 1)
        crypto = "HmacSHA1";
        if(ocraSuite.toLowerCase().indexOf("sha256") > 1)
        crypto = "HmacSHA256";
        if(ocraSuite.toLowerCase().indexOf("sha512") > 1)
        crypto = "HmacSHA512";

        // How many digits should we return
        String oS = ocraSuite.substring(ocraSuite.indexOf(":"),
        ocraSuite.indexOf(":", ocraSuite.indexOf(":") + 1));
        codeDigits = Integer.decode(oS.substring
        (oS.lastIndexOf("-")+1,
        oS.length()));

        // The size of the byte array message to be encrypted
        // Counter
        if(ocraSuite.toLowerCase().indexOf(":c") > 1) {
            // Fix the length of the HEX string
            while(counter.length() < 16)
            counter = "0" + counter;
            counterLength=8;
        }
        // Question
        if((ocraSuite.toLowerCase().indexOf(":q") > 1) ||
        (ocraSuite.toLowerCase().indexOf("-q") > 1)) {
            while(question.length() < 256)
            question = question + "0";
            questionLength=128;
        }

        // Password
        if((ocraSuite.toLowerCase().indexOf(":p") > 1) ||
        (ocraSuite.toLowerCase().indexOf("-p") > 1)){
            while(password.length() < 40)
            password = "0" + password;
            passwordLength=20;
        }

        // sessionInformation
        if((ocraSuite.toLowerCase().indexOf(":s") > 1) ||
        (ocraSuite.toLowerCase().indexOf("-s",
        ocraSuite.indexOf(":",
        ocraSuite.indexOf(":") + 1)) > 1)){
            while(sessionInformation.length() < 128)
            sessionInformation = "0" + sessionInformation;

            sessionInformationLength=64;
        }
        // TimeStamp
        if((ocraSuite.toLowerCase().indexOf(":t") > 1) ||
        (ocraSuite.toLowerCase().indexOf("-t") > 1)){
            while(timeStamp.length() < 16)
            timeStamp = "0" + timeStamp;
            timeStampLength=8;
        }

        // Remember to add "1" for the "00" byte delimiter
        byte[] msg = new byte[ocraSuiteLength +
        counterLength +
        questionLength +
        passwordLength +
        sessionInformationLength +
        timeStampLength +
        1];

        // Put the bytes of "ocraSuite" parameters into the message
        byte[] bArray = ocraSuite.getBytes();
        for(int i = 0; i < bArray.length; i++){
            msg[i] = bArray[i];
        }

        // Delimiter
        msg[bArray.length] = 0x00;

        // Put the bytes of "Counter" to the message
        // Input is HEX encoded
        if(counterLength > 0 ){
            bArray = hexStr2Bytes(counter);
            for (int i = 0; i < bArray.length ; i++) {
                msg[i + ocraSuiteLength + 1] = bArray[i];
            }
        }

        // Put the bytes of "question" to the message
        // Input is text encoded
        if(question.length() > 0 ){
            bArray = hexStr2Bytes(question);
            for (int i = 0; i < bArray.length ; i++) {
                msg[i + ocraSuiteLength + 1 + counterLength] =
                bArray[i];
            }
        }

        // Put the bytes of "password" to the message
        // Input is HEX encoded
        if(password.length() > 0){
            bArray = hexStr2Bytes(password);
            for (int i = 0; i < bArray.length ; i++) {
                msg[i + ocraSuiteLength + 1 + counterLength
                + questionLength] = bArray[i];
            }
        }

        // Put the bytes of "sessionInformation" to the message
        // Input is text encoded
        if(sessionInformation.length() > 0 ){
            bArray = hexStr2Bytes(sessionInformation);
            for (int i = 0; i < 128 && i < bArray.length ; i++) {
                msg[i + ocraSuiteLength
                + 1 + counterLength
                + questionLength
                + passwordLength] = bArray[i];
            }
        }

        // Put the bytes of "time" to the message
        // Input is text value of minutes
        if(timeStamp.length() > 0){
            bArray = hexStr2Bytes(timeStamp);
            for (int i = 0; i < 8 && i < bArray.length ; i++) {
                msg[i + ocraSuiteLength + 1 + counterLength +
                questionLength + passwordLength +
                sessionInformationLength] = bArray[i];
            }
        }

        byte[] hash;
        bArray = hexStr2Bytes(key);

        hash = hmac_sha1(crypto, bArray, msg);

        // put selected bytes into result int
        int offset = hash[hash.length - 1] & 0xf;

        int binary =
        ((hash[offset] & 0x7f) << 24) |
        ((hash[offset + 1] & 0xff) << 16) |
        ((hash[offset + 2] & 0xff) << 8) |
        (hash[offset + 3] & 0xff);

        int otp = binary % DIGITS_POWER[codeDigits];

        result = Integer.toString(otp);
        while (result.length() < codeDigits) {
            result = "0" + result;
        }
        return result;
    }
}
```

### Appendix B – `TestOCRA.java`

```java
import java.math.BigInteger;
import java.util.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

public class TestOCRA {

public static String asHex (byte buf[]) {
    StringBuffer strbuf = new StringBuffer(buf.length * 2);
    int i;

    for (i = 0; i < buf.length; i++) {
        if (((int) buf[i] & 0xff) < 0x10)
            strbuf.append("0");
        strbuf.append(Long.toString((int) buf[i] & 0xff, 16));
    }
    return strbuf.toString();
}

/**
 * @param args
 */
public static void main(String[] args) {

    String ocra = "";
    String seed = "";
    String ocraSuite = "";
    String counter = "";
    String password = "";
    String sessionInformation = "";
    String question = "";
    String qHex = "";
    String timeStamp = "";

    String PASS1234 = "7110eda4d09e062aa5e4a390b0a572ac0d2c0220";
    String SEED = "3132333435363738393031323334353637383930";
    String SEED32 = "31323334353637383930313233343536373839" +
        "30313233343536373839303132";
    String SEED64 = "31323334353637383930313233343536373839" +
        "3031323334353637383930313233343536373839" +
        "3031323334353637383930313233343536373839" +
        "3031323334";
    int STOP = 5;

    Date myDate = Calendar.getInstance().getTime();
    BigInteger b = new BigInteger("0");
    String sDate = "Mar 25 2008, 12:06:30 GMT";

    try{
        DateFormat df =
            new SimpleDateFormat("MMM dd yyyy, HH:mm:ss zzz");
        myDate = df.parse(sDate);
        b = new BigInteger("0" + myDate.getTime());
        b = b.divide(new BigInteger("60000"));

        System.out.println("Time of \"" + sDate + "\" is in");
        System.out.println("milli sec: " + myDate.getTime());
        System.out.println("minutes: " + b.toString());
        System.out.println("minutes (HEX encoded): "
            + b.toString(16).toUpperCase());
        System.out.println("Time of \"" + sDate
            + "\" is the same as this localized");
        System.out.println("time, \""
            + new Date(myDate.getTime()) + "\"");

        System.out.println();
        System.out.println("Standard 20Byte key: " +
            "3132333435363738393031323334353637383930");
        System.out.println("Standard 32Byte key: " +
            "3132333435363738393031323334353637383930");
        System.out.println("            " +
            "313233343536373839303132");
        System.out.println("Standard 64Byte key: 313233343536373839" +
            "3031323334353637383930");
        System.out.println("                     313233343536373839" +
            "3031323334353637383930");
        System.out.println("                     313233343536373839" +
            "3031323334353637383930");
        System.out.println("                     31323334");

        System.out.println();
        System.out.println("Plain challenge response");
        System.out.println("========================");
        System.out.println();

        ocraSuite = "OCRA-1:HOTP-SHA1-6:QN08";
        System.out.println(ocraSuite);
        System.out.println("=======================");
        seed = SEED;
        counter = "";
        question = "";

        password = "";
        sessionInformation = "";
        timeStamp = "";
        for(int i=0; i < 10; i++){
            question = "" + i + i + i + i + i + i + i + i;
            qHex = new String((new BigInteger(question,10))
                .toString(16)).toUpperCase();
            ocra = OCRA.generateOCRA(ocraSuite,seed,counter,
                qHex,password,
                sessionInformation,timeStamp);
            System.out.println("Key: Standard 20Byte  Q: "
                + question + "  OCRA: " + ocra);
        }
        System.out.println();

        ocraSuite = "OCRA-1:HOTP-SHA256-8:C-QN08-PSHA1";
        System.out.println(ocraSuite);
        System.out.println("=================================");
        seed = SEED32;
        counter = "";
        question = "12345678";
        password = PASS1234;
        sessionInformation = "";
        timeStamp = "";
        for(int i=0; i < 10; i++){
            counter = "" + i;
            qHex = new String((new BigInteger(question,10))
                .toString(16)).toUpperCase();
            ocra = OCRA.generateOCRA(ocraSuite,seed,counter,
                qHex,password,sessionInformation,timeStamp);
            System.out.println("Key: Standard 32Byte  C: "
                + counter + "  Q: "
                + question + "  PIN(1234): ");
            System.out.println(password + "  OCRA: " + ocra);
        }
        System.out.println();

        ocraSuite = "OCRA-1:HOTP-SHA256-8:QN08-PSHA1";
        System.out.println(ocraSuite);
        System.out.println("===============================");
        seed = SEED32;
        counter = "";
        question = "";
        password = PASS1234;
        sessionInformation = "";
        timeStamp = "";
        for(int i=0; i < STOP; i++){
            question = "" + i + i + i + i + i + i + i + i;

            qHex = new String((new BigInteger(question,10))
                .toString(16)).toUpperCase();
            ocra = OCRA.generateOCRA(ocraSuite,seed,counter,
                qHex,password,sessionInformation,timeStamp);
            System.out.println("Key: Standard 32Byte  Q: "
                + question + "  PIN(1234): ");
            System.out.println(password + "  OCRA: " + ocra);
        }
        System.out.println();

        ocraSuite = "OCRA-1:HOTP-SHA512-8:C-QN08";
        System.out.println(ocraSuite);
        System.out.println("===========================");
        seed = SEED64;
        counter = "";
        question = "";
        password = "";
        sessionInformation = "";
        timeStamp = "";
        for(int i=0; i < 10; i++){
            question = "" + i + i + i + i + i + i + i + i;
            qHex = new String((new BigInteger(question,10))
                .toString(16)).toUpperCase();
            counter = "0000" + i;
            ocra = OCRA.generateOCRA(ocraSuite,seed,counter,
                qHex,password,sessionInformation,timeStamp);
            System.out.println("Key: Standard 64Byte  C: "
                + counter + "  Q: "
                + question + "  OCRA: " + ocra);
        }
        System.out.println();

        ocraSuite = "OCRA-1:HOTP-SHA512-8:QN08-T1M";
        System.out.println(ocraSuite);
        System.out.println("=============================");
        seed = SEED64;
        counter = "";
        question = "";
        password = "";
        sessionInformation = "";
        timeStamp = b.toString(16);
        for(int i=0; i < STOP; i++){
            question = "" + i + i + i + i + i + i + i + i;
            counter = "";
            qHex = new String((new BigInteger(question,10))
                .toString(16)).toUpperCase();
            ocra = OCRA.generateOCRA(ocraSuite,seed,counter,
                qHex,password,sessionInformation,timeStamp);

            System.out.println("Key: Standard 64Byte  Q: "
                                + question +"  T: "
                                + timeStamp.toUpperCase()
                                + "  OCRA: " + ocra);
        }
        System.out.println();

        System.out.println();
        System.out.println("Mutual Challenge Response");
        System.out.println("=========================");
        System.out.println();

        ocraSuite = "OCRA-1:HOTP-SHA256-8:QA08";
        System.out.println("OCRASuite (server computation) = "
            + ocraSuite);
        System.out.println("OCRASuite (client computation) = "
            + ocraSuite);
        System.out.println("===============================" +
            "===========================");
        seed = SEED32;
        counter = "";
        question = "";
        password = "";
        sessionInformation = "";
        timeStamp = "";
        for(int i=0; i < STOP; i++){
            question = "CLI2222" + i + "SRV1111" + i;
            qHex = asHex(question.getBytes());
            ocra = OCRA.generateOCRA(ocraSuite,seed,counter,qHex,
                password,sessionInformation,timeStamp);
            System.out.println(
                "(server)Key: Standard 32Byte  Q: "
                + question + "  OCRA: "
                + ocra);
            question = "SRV1111" + i + "CLI2222" + i;
            qHex = asHex(question.getBytes());
            ocra = OCRA.generateOCRA(ocraSuite,seed,counter,qHex,
                password,sessionInformation,timeStamp);
            System.out.println(
                "(client)Key: Standard 32Byte  Q: "
                + question + "  OCRA: "
                + ocra);
        }
        System.out.println();

        String ocraSuite1 = "OCRA-1:HOTP-SHA512-8:QA08";
        String ocraSuite2 = "OCRA-1:HOTP-SHA512-8:QA08-PSHA1";
        System.out.println("OCRASuite (server computation) = "

            + ocraSuite1);
        System.out.println("OCRASuite (client computation) = "
            + ocraSuite2);
        System.out.println("===============================" +
            "=================================");
        ocraSuite = "";
        seed = SEED64;
        counter = "";
        question = "";
        password = "";
        sessionInformation = "";
        timeStamp = "";
        for(int i=0; i < STOP; i++){
            ocraSuite = ocraSuite1;
            question = "CLI2222" + i + "SRV1111" + i;
            qHex = asHex(question.getBytes());
            password = "";
            ocra = OCRA.generateOCRA(ocraSuite,seed,counter,qHex,
                password,sessionInformation,timeStamp);
            System.out.println(
                "(server)Key: Standard 64Byte  Q: "
                + question + "  OCRA: "
                + ocra);
            ocraSuite = ocraSuite2;
            question = "SRV1111" + i + "CLI2222" + i;
            qHex = asHex(question.getBytes());
            password = PASS1234;
            ocra = OCRA.generateOCRA(ocraSuite,seed,counter,qHex,
                password,sessionInformation,timeStamp);
            System.out.println("(client)Key: Standard 64Byte  Q: "
                + question);
            System.out.println("P: " + password.toUpperCase()
                + "  OCRA: " + ocra);
        }
        System.out.println();

        System.out.println();
        System.out.println("Plain Signature");
        System.out.println("===============");
        System.out.println();
        ocraSuite = "OCRA-1:HOTP-SHA256-8:QA08";
        System.out.println(ocraSuite);
        System.out.println("=========================");
        seed = SEED32;
        counter = "";
        question = "";
        password = "";
        sessionInformation = "";

        timeStamp = "";
        for(int i=0; i < STOP; i++){
            question = "SIG1" + i + "000";
            qHex = asHex(question.getBytes());
            ocra = OCRA.generateOCRA(ocraSuite,seed,counter,qHex,
                password,sessionInformation,timeStamp);
            System.out.println(
                "Key: Standard 32Byte  Q(Signature challenge): "
                + question);
            System.out.println("   OCRA: " + ocra);
        }
        System.out.println();

        ocraSuite = "OCRA-1:HOTP-SHA512-8:QA10-T1M";
        System.out.println(ocraSuite);
        System.out.println("=============================");
        seed = SEED64;
        counter = "";
        question = "";
        password = "";
        sessionInformation = "";
        timeStamp = b.toString(16);
        for(int i=0; i < STOP; i++){
            question = "SIG1" + i + "00000";
            qHex = asHex(question.getBytes());
            ocra = OCRA.generateOCRA(ocraSuite,seed,counter,
                qHex,password,sessionInformation,timeStamp);
            System.out.println(
                "Key: Standard 64Byte  Q(Signature challenge): "
                + question);
            System.out.println("   T: "
                + timeStamp.toUpperCase() + "  OCRA: "
                + ocra);
        }

    }catch (Exception e){
        System.out.println("Error : " + e);
    }
}
}
```

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
