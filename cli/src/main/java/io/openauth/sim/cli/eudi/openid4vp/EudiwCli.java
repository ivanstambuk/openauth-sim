package io.openauth.sim.cli.eudi.openid4vp;

import io.openauth.sim.application.eudi.openid4vp.Oid4vpProblemDetails;
import io.openauth.sim.application.eudi.openid4vp.Oid4vpValidationException;
import io.openauth.sim.application.eudi.openid4vp.Oid4vpVerboseTraceBuilder;
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpAuthorizationRequestService;
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpValidationService;
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpWalletSimulationService;
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpWalletSimulationService.InlineSdJwt;
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpWalletSimulationService.ResponseMode;
import io.openauth.sim.cli.VerboseTracePrinter;
import io.openauth.sim.core.trace.VerboseTrace;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import picocli.CommandLine;

/** Picocli facade for Feature 040 – EUDIW OpenID4VP simulator flows. */
@CommandLine.Command(
        name = "eudiw",
        mixinStandardHelpOptions = true,
        description = "Interact with the EUDIW OpenID4VP simulator (requests, wallet simulation, validation).",
        subcommands = {EudiwCli.RequestCommand.class, EudiwCli.WalletCommand.class, EudiwCli.ValidateCommand.class})
public final class EudiwCli implements java.util.concurrent.Callable<Integer> {

    private static final String EVENT_PREFIX = "cli.eudiw.";
    private static final AtomicLong TELEMETRY_SEQUENCE = new AtomicLong();

    private final EudiwCliServices services;

    @CommandLine.Spec
    private CommandLine.Model.CommandSpec spec;

    public EudiwCli() {
        this(EudiwCliServices.createDefault());
    }

    EudiwCli(EudiwCliServices services) {
        this.services = services;
    }

    @Override
    public Integer call() {
        spec.commandLine().usage(out());
        return CommandLine.ExitCode.USAGE;
    }

    PrintWriter out() {
        return spec.commandLine().getOut();
    }

    PrintWriter err() {
        return spec.commandLine().getErr();
    }

    EudiwCliServices services() {
        return services;
    }

    void printPayload(String event, Map<String, Object> payload, boolean outputJson) {
        printPayload(event, payload, outputJson, "success", "success");
    }

    void printPayload(String event, Map<String, Object> payload, boolean outputJson, String status, String reasonCode) {
        if (outputJson) {
            out().println(JsonWriter.toJson(envelope(event, payload, status, reasonCode)));
        } else if ("success".equals(status)) {
            out().println(payload);
        } else {
            err().println(formatErrorLine(event, status, reasonCode, payload));
        }
    }

    private String formatErrorLine(String event, String status, String reasonCode, Map<String, Object> payload) {
        StringBuilder builder =
                new StringBuilder("event=").append(event).append(" status=").append(status);
        if (reasonCode != null && !reasonCode.isBlank()) {
            builder.append(" reasonCode=").append(reasonCode);
        }
        Object reason = payload == null ? null : payload.get("reason");
        if (reason != null) {
            builder.append(" reason=").append(reason);
        }
        return builder.toString();
    }

    private static Map<String, Object> envelope(
            String event, Map<String, Object> payload, String status, String reasonCode) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("event", event);
        root.put("status", status);
        root.put("reasonCode", reasonCode);
        Object telemetryId = resolveTelemetryId(payload);
        if (telemetryId == null) {
            telemetryId = nextTelemetryId();
        }
        if (telemetryId != null) {
            root.put("telemetryId", telemetryId);
        }
        root.put("sanitized", Boolean.TRUE);
        root.put("data", payload);
        return root;
    }

    private static String nextTelemetryId() {
        return "oid4vp-cli-" + TELEMETRY_SEQUENCE.incrementAndGet();
    }

    @SuppressWarnings("unchecked")
    private static Object resolveTelemetryId(Map<String, Object> payload) {
        Object telemetry = payload.get("telemetry");
        if (!(telemetry instanceof Map<?, ?> telemetryMap)) {
            return null;
        }
        Object fields = telemetryMap.get("fields");
        if (!(fields instanceof Map<?, ?> fieldsMap)) {
            return null;
        }
        return fieldsMap.get("telemetryId");
    }

    private static String cliEvent(String suffix) {
        return EVENT_PREFIX + suffix;
    }

    static OpenId4VpAuthorizationRequestService.Profile parseAuthorizationProfile(String value) {
        if (value == null || value.isBlank()) {
            return OpenId4VpAuthorizationRequestService.Profile.HAIP;
        }
        return OpenId4VpAuthorizationRequestService.Profile.valueOf(value.toUpperCase(Locale.ROOT));
    }

    static OpenId4VpWalletSimulationService.Profile parseWalletProfile(String value) {
        if (value == null || value.isBlank()) {
            return OpenId4VpWalletSimulationService.Profile.HAIP;
        }
        return OpenId4VpWalletSimulationService.Profile.valueOf(value.toUpperCase(Locale.ROOT));
    }

    static OpenId4VpAuthorizationRequestService.ResponseMode parseAuthorizationResponseMode(String value) {
        if (value == null || value.isBlank()) {
            return OpenId4VpAuthorizationRequestService.ResponseMode.DIRECT_POST_JWT;
        }
        return OpenId4VpAuthorizationRequestService.ResponseMode.valueOf(value.toUpperCase(Locale.ROOT));
    }

    static ResponseMode parseResponseMode(String value) {
        if (value == null || value.isBlank()) {
            return ResponseMode.DIRECT_POST_JWT;
        }
        return ResponseMode.valueOf(value.toUpperCase(Locale.ROOT));
    }

    static String readFile(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8).trim();
    }

    static List<String> resolveDisclosures(List<String> inputs) throws IOException {
        if (inputs == null || inputs.isEmpty()) {
            return List.of();
        }
        List<String> values = new ArrayList<>(inputs.size());
        for (String input : inputs) {
            Path candidate = Path.of(input);
            if (Files.exists(candidate)) {
                values.add(readFile(candidate));
            } else {
                values.add(input);
            }
        }
        return List.copyOf(values);
    }

    static Optional<Path> optionalPath(Path path) {
        return path == null ? Optional.empty() : Optional.of(path);
    }

    @CommandLine.Command(
            name = "request",
            description = "Authorization request helpers",
            subcommands = RequestCommand.CreateCommand.class)
    static final class RequestCommand implements java.util.concurrent.Callable<Integer> {

        @CommandLine.ParentCommand
        private EudiwCli parent;

        @CommandLine.Spec
        private CommandLine.Model.CommandSpec spec;

        @Override
        public Integer call() {
            spec.commandLine().usage(parent.out());
            return CommandLine.ExitCode.USAGE;
        }

        @CommandLine.Command(name = "create", description = "Create a HAIP-aligned authorization request")
        static final class CreateCommand implements java.util.concurrent.Callable<Integer> {

            @CommandLine.ParentCommand
            private RequestCommand parent;

            @CommandLine.Option(names = "--profile", defaultValue = "HAIP")
            private String profile;

            @CommandLine.Option(names = "--response-mode", defaultValue = "DIRECT_POST_JWT")
            private String responseMode;

            @CommandLine.Option(names = "--dcql-preset")
            private String dcqlPreset;

            @CommandLine.Option(names = "--dcql-json", description = "Path to inline DCQL JSON definition")
            private Path dcqlOverride;

            @CommandLine.Option(names = "--signed-request", negatable = true, defaultValue = "true")
            private boolean signedRequest;

            @CommandLine.Option(names = "--include-qr", negatable = true, defaultValue = "false")
            private boolean includeQrAscii;

            @CommandLine.Option(names = "--verbose", negatable = true, defaultValue = "false")
            private boolean verbose;

            @CommandLine.Option(
                    names = "--output-json",
                    description = "Pretty-print a REST-equivalent JSON response instead of text output")
            private boolean outputJson;

            @Override
            public Integer call() {
                try {
                    OpenId4VpAuthorizationRequestService.CreateRequest request =
                            new OpenId4VpAuthorizationRequestService.CreateRequest(
                                    EudiwCli.parseAuthorizationProfile(profile),
                                    EudiwCli.parseAuthorizationResponseMode(responseMode),
                                    Optional.ofNullable(dcqlPreset),
                                    optionalPath(dcqlOverride).map(path -> uncheckedRead(path)),
                                    signedRequest,
                                    includeQrAscii,
                                    verbose);
                    var result = parent.parent.services().authorizationService().create(request);
                    Optional<VerboseTrace> verboseTrace =
                            verbose ? Oid4vpVerboseTraceBuilder.authorization(result) : Optional.empty();
                    parent.parent.printPayload(
                            cliEvent("request.create"),
                            Oid4vpCliMapper.authorization(
                                    result,
                                    verboseTrace
                                            .map(Oid4vpCliMapper::traceToMap)
                                            .orElse(null)),
                            outputJson);
                    if (verboseTrace.isPresent() && !outputJson) {
                        VerboseTracePrinter.print(parent.parent.out(), verboseTrace.get());
                    }
                    return CommandLine.ExitCode.OK;
                } catch (IllegalArgumentException ex) {
                    parent.parent.printPayload(
                            cliEvent("request.create"),
                            Map.of("reason", ex.getMessage()),
                            outputJson,
                            "invalid",
                            "invalid_request");
                    return CommandLine.ExitCode.USAGE;
                }
            }

            private static String uncheckedRead(Path path) {
                try {
                    return EudiwCli.readFile(path);
                } catch (IOException ex) {
                    throw new IllegalArgumentException("Unable to read DCQL override " + path, ex);
                }
            }
        }
    }

    @CommandLine.Command(
            name = "wallet",
            description = "Wallet simulation helpers",
            subcommands = WalletCommand.SimulateCommand.class)
    static final class WalletCommand implements java.util.concurrent.Callable<Integer> {

        @CommandLine.ParentCommand
        private EudiwCli parent;

        @CommandLine.Spec
        private CommandLine.Model.CommandSpec spec;

        @Override
        public Integer call() {
            spec.commandLine().usage(parent.out());
            return CommandLine.ExitCode.USAGE;
        }

        @CommandLine.Command(name = "simulate", description = "Generate deterministic wallet responses")
        static final class SimulateCommand implements java.util.concurrent.Callable<Integer> {

            @CommandLine.ParentCommand
            private WalletCommand parent;

            @CommandLine.Option(names = "--request-id", required = true)
            private String requestId;

            @CommandLine.Option(names = "--wallet-preset")
            private String walletPreset;

            @CommandLine.Option(names = "--profile", defaultValue = "HAIP")
            private String profile;

            @CommandLine.Option(names = "--inline-sdjwt", description = "Path to compact SD-JWT input")
            private Path inlineSdJwt;

            @CommandLine.Option(names = "--disclosure", arity = "0..*", description = "Disclosure JSON files")
            private List<String> disclosures;

            @CommandLine.Option(names = "--kb-jwt", description = "Path to KB-JWT payload")
            private Path kbJwt;

            @CommandLine.Option(names = "--trusted-authority")
            private String trustedAuthorityPolicy;

            @CommandLine.Option(names = "--response-mode", defaultValue = "DIRECT_POST_JWT")
            private String responseMode;

            @CommandLine.Option(names = "--verbose", negatable = true, defaultValue = "false")
            private boolean verbose;

            @CommandLine.Option(names = "--output-json")
            private boolean outputJson;

            @Override
            public Integer call() throws Exception {
                try {
                    Optional<InlineSdJwt> inline = buildInlineSdJwt();
                    var request = new OpenId4VpWalletSimulationService.SimulateRequest(
                            Objects.requireNonNull(requestId, "requestId"),
                            EudiwCli.parseWalletProfile(profile),
                            EudiwCli.parseResponseMode(responseMode),
                            Optional.ofNullable(walletPreset),
                            inline,
                            Optional.ofNullable(trustedAuthorityPolicy));
                    var result =
                            parent.parent.services().walletSimulationService().simulate(request);
                    VerboseTrace verboseTrace = verbose ? Oid4vpVerboseTraceBuilder.wallet(result) : null;
                    parent.parent.printPayload(
                            cliEvent("wallet.simulate"),
                            Oid4vpCliMapper.wallet(
                                    result, verboseTrace == null ? null : Oid4vpCliMapper.traceToMap(verboseTrace)),
                            outputJson);
                    if (verboseTrace != null && !outputJson) {
                        VerboseTracePrinter.print(parent.parent.out(), verboseTrace);
                    }
                    return CommandLine.ExitCode.OK;
                } catch (IllegalArgumentException ex) {
                    parent.parent.printPayload(
                            cliEvent("wallet.simulate"),
                            Map.of("reason", ex.getMessage()),
                            outputJson,
                            "invalid",
                            "invalid_request");
                    return CommandLine.ExitCode.USAGE;
                }
            }

            private Optional<InlineSdJwt> buildInlineSdJwt() throws IOException {
                if (inlineSdJwt == null) {
                    return Optional.empty();
                }
                String compact = EudiwCli.readFile(inlineSdJwt);
                List<String> disclosureValues = EudiwCli.resolveDisclosures(disclosures);
                String format = disclosureValues.isEmpty() ? "dc+sd-jwt" : "dc+sd-jwt";
                return Optional.of(new InlineSdJwt(
                        walletPreset != null ? walletPreset : "inline-credential",
                        format,
                        compact,
                        disclosureValues,
                        optionalPath(kbJwt).map(path -> uncheckedRead(path)),
                        List.of()));
            }

            private static String uncheckedRead(Path path) {
                try {
                    return EudiwCli.readFile(path);
                } catch (IOException ex) {
                    throw new IllegalArgumentException("Unable to read KB-JWT file " + path, ex);
                }
            }
        }
    }

    @CommandLine.Command(name = "validate", description = "Validate VP Tokens against Trusted Authorities")
    static final class ValidateCommand implements java.util.concurrent.Callable<Integer> {

        @CommandLine.ParentCommand
        private EudiwCli parent;

        @CommandLine.Option(names = "--request-id")
        private String requestId;

        @CommandLine.Option(names = "--preset")
        private String presetId;

        @CommandLine.Option(names = "--vp-token", description = "Path to inline VP Token JSON")
        private Path vpToken;

        @CommandLine.Option(names = "--trusted-authority")
        private String trustedAuthorityPolicy;

        @CommandLine.Option(names = "--profile", defaultValue = "HAIP")
        private String profile;

        @CommandLine.Option(names = "--response-mode", defaultValue = "DIRECT_POST_JWT")
        private String responseMode;

        @CommandLine.Option(names = "--verbose", negatable = true, defaultValue = "false")
        private boolean verbose;

        @CommandLine.Option(names = "--output-json")
        private boolean outputJson;

        @Override
        public Integer call() throws Exception {
            try {
                if ((presetId == null && vpToken == null) || (presetId != null && vpToken != null)) {
                    throw new IllegalArgumentException("Provide either --preset or --vp-token");
                }
                String resolvedRequestId = requestId != null
                        ? requestId
                        : parent.services().seedSequence().nextRequestId();
                Optional<String> storedPresentation = Optional.ofNullable(presetId);
                Optional<OpenId4VpValidationService.InlineVpToken> inlineVpToken =
                        optionalPath(vpToken).map(this::readInlineToken);
                var validateRequest = new OpenId4VpValidationService.ValidateRequest(
                        resolvedRequestId,
                        EudiwCli.parseWalletProfile(profile),
                        Optional.of(EudiwCli.parseResponseMode(responseMode)),
                        storedPresentation,
                        inlineVpToken,
                        Optional.ofNullable(trustedAuthorityPolicy),
                        inlineVpToken.map(token -> token.vpToken().toString()));
                var result = parent.services().validationService().validate(validateRequest);
                VerboseTrace verboseTrace = verbose ? Oid4vpVerboseTraceBuilder.validation(result) : null;
                parent.printPayload(
                        cliEvent("validate"),
                        Oid4vpCliMapper.validation(
                                result, verboseTrace == null ? null : Oid4vpCliMapper.traceToMap(verboseTrace)),
                        outputJson);
                if (verboseTrace != null && !outputJson) {
                    VerboseTracePrinter.print(parent.out(), verboseTrace);
                }
                return CommandLine.ExitCode.OK;
            } catch (Oid4vpValidationException ex) {
                Map<String, Object> problemDetails = problemDetails(ex.problemDetails());
                problemDetails.put("reason", ex.problemDetails().detail());
                parent.printPayload(
                        cliEvent("validate"),
                        problemDetails,
                        outputJson,
                        "invalid",
                        ex.problemDetails().type());
                return 2;
            } catch (IllegalArgumentException ex) {
                parent.printPayload(
                        cliEvent("validate"),
                        Map.of("reason", ex.getMessage()),
                        outputJson,
                        "invalid",
                        "invalid_request");
                return CommandLine.ExitCode.USAGE;
            }
        }

        private static Map<String, Object> problemDetails(Oid4vpProblemDetails details) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("type", details.type());
            map.put("title", details.title());
            map.put("status", details.status());
            map.put("detail", details.detail());
            map.put("violations", details.violations());
            return map;
        }

        private OpenId4VpValidationService.InlineVpToken readInlineToken(Path path) {
            try {
                String json = EudiwCli.readFile(path);
                Object parsed = io.openauth.sim.core.json.SimpleJson.parse(json);
                if (!(parsed instanceof Map<?, ?> map)) {
                    throw new IllegalArgumentException("Inline VP token must be a JSON object");
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> cast = (Map<String, Object>) map;
                String credentialId =
                        cast.getOrDefault("credentialId", "inline-credential").toString();
                String format = cast.getOrDefault("format", "dc+sd-jwt").toString();
                Map<String, Object> vpToken = new LinkedHashMap<>();
                vpToken.put("vp_token", cast.get("vp_token"));
                vpToken.put("presentation_submission", cast.get("presentation_submission"));
                @SuppressWarnings("unchecked")
                List<String> disclosures = (List<String>) cast.getOrDefault("disclosures", List.of());
                return new OpenId4VpValidationService.InlineVpToken(
                        credentialId,
                        format,
                        vpToken,
                        Optional.ofNullable((String) cast.get("keyBindingJwt")),
                        disclosures,
                        List.of());
            } catch (IOException ex) {
                throw new IllegalArgumentException("Unable to read VP Token file " + path, ex);
            }
        }
    }
}
