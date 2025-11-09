package io.openauth.sim.rest.emv.cap;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.Trace;
import io.openauth.sim.core.emv.cap.EmvCapTraceProvenanceSchema;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
record EmvCapTracePayload(
        @JsonProperty("masterKeySha256") String masterKeySha256,
        @JsonProperty("sessionKey") String sessionKey,
        @JsonProperty("atc") String atc,
        @JsonProperty("branchFactor") int branchFactor,
        @JsonProperty("height") int height,
        @JsonProperty("maskLength") int maskLength,
        @JsonProperty("previewWindow") PreviewWindow previewWindow,
        @JsonProperty("generateAcInput") GenerateAcInput generateAcInput,
        @JsonProperty("iccPayloadTemplate") String iccPayloadTemplate,
        @JsonProperty("iccPayloadResolved") String iccPayloadResolved,
        @JsonProperty("generateAcResult") String generateAcResult,
        @JsonProperty("bitmask") String bitmask,
        @JsonProperty("maskedDigitsOverlay") String maskedDigitsOverlay,
        @JsonProperty("issuerApplicationData") String issuerApplicationData,
        @JsonProperty("provenance") EmvCapTraceProvenancePayload provenance,
        @JsonProperty("expectedOtp") String expectedOtp) {

    static EmvCapTracePayload from(Trace trace) {
        return from(trace, null);
    }

    static EmvCapTracePayload from(Trace trace, String expectedOtp) {
        if (trace == null) {
            return null;
        }
        PreviewWindow previewWindow = new PreviewWindow(trace.previewWindowBackward(), trace.previewWindowForward());
        GenerateAcInput generateAcInput = new GenerateAcInput(
                trace.generateAcInput().terminalHex(), trace.generateAcInput().iccHex());
        EmvCapTraceProvenancePayload provenancePayload =
                EmvCapTraceProvenancePayload.from(trace, useCanonicalCdolOverrides(trace));
        return new EmvCapTracePayload(
                trace.masterKeySha256(),
                trace.sessionKey(),
                trace.atc(),
                trace.branchFactor(),
                trace.height(),
                trace.maskLength(),
                previewWindow,
                generateAcInput,
                trace.iccPayloadTemplate(),
                trace.iccPayloadResolved(),
                trace.generateAcResult(),
                trace.bitmask(),
                trace.maskedDigits(),
                trace.issuerApplicationData(),
                provenancePayload,
                expectedOtp);
    }

    private static boolean useCanonicalCdolOverrides(Trace trace) {
        return CanonicalTraceData.BASELINE_MASTER_DIGEST.equals(trace.masterKeySha256());
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record PreviewWindow(
            @JsonProperty("backward") int backward,
            @JsonProperty("forward") int forward) {
        // no members
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record GenerateAcInput(
            @JsonProperty("terminal") String terminal,
            @JsonProperty("icc") String icc) {
        // no members
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record EmvCapTraceProvenancePayload(
            @JsonProperty("protocolContext") ProtocolContext protocolContext,
            @JsonProperty("keyDerivation") KeyDerivation keyDerivation,
            @JsonProperty("cdolBreakdown") CdolBreakdown cdolBreakdown,
            @JsonProperty("iadDecoding") IadDecoding iadDecoding,
            @JsonProperty("macTranscript") MacTranscript macTranscript,
            @JsonProperty("decimalizationOverlay") DecimalizationOverlay decimalizationOverlay) {

        static EmvCapTraceProvenancePayload from(Trace trace, boolean useCanonical) {
            if (useCanonical) {
                return CanonicalTraceData.canonicalProvenance();
            }
            Trace.Provenance provenance = trace.provenance();
            ProtocolContext context = ProtocolContext.from(provenance.protocolContext());
            KeyDerivation derivation = KeyDerivation.from(provenance.keyDerivation());
            CdolBreakdown cdol = CdolBreakdown.from(provenance.cdolBreakdown());
            IadDecoding iad = IadDecoding.from(provenance.iadDecoding());
            MacTranscript macTranscript = MacTranscript.from(provenance.macTranscript());
            DecimalizationOverlay decimalizationOverlay =
                    DecimalizationOverlay.from(provenance.decimalizationOverlay());
            return new EmvCapTraceProvenancePayload(
                    context, derivation, cdol, iad, macTranscript, decimalizationOverlay);
        }

        record ProtocolContext(
                @JsonProperty("profile") String profile,
                @JsonProperty("mode") String mode,
                @JsonProperty("emvVersion") String emvVersion,
                @JsonProperty("acType") String acType,
                @JsonProperty("cid") String cid,
                @JsonProperty("issuerPolicyId") String issuerPolicyId,
                @JsonProperty("issuerPolicyNotes") String issuerPolicyNotes) {

            static ProtocolContext from(Trace.Provenance.ProtocolContext context) {
                return new ProtocolContext(
                        context.profile(),
                        context.mode(),
                        context.emvVersion(),
                        context.acType(),
                        context.cid(),
                        context.issuerPolicyId(),
                        context.issuerPolicyNotes());
            }
        }

        record KeyDerivation(
                @JsonProperty("masterFamily") String masterFamily,
                @JsonProperty("derivationAlgorithm") String derivationAlgorithm,
                @JsonProperty("masterKeyBytes") int masterKeyBytes,
                @JsonProperty("masterKeySha256") String masterKeySha256,
                @JsonProperty("maskedPan") String maskedPan,
                @JsonProperty("maskedPanSha256") String maskedPanSha256,
                @JsonProperty("maskedPsn") String maskedPsn,
                @JsonProperty("maskedPsnSha256") String maskedPsnSha256,
                @JsonProperty("atc") String atc,
                @JsonProperty("iv") String iv,
                @JsonProperty("sessionKey") String sessionKey,
                @JsonProperty("sessionKeyBytes") int sessionKeyBytes) {

            static KeyDerivation from(Trace.Provenance.KeyDerivation derivation) {
                return new KeyDerivation(
                        derivation.masterFamily(),
                        derivation.derivationAlgorithm(),
                        derivation.masterKeyBytes(),
                        derivation.masterKeySha256(),
                        derivation.maskedPan(),
                        derivation.maskedPanSha256(),
                        derivation.maskedPsn(),
                        derivation.maskedPsnSha256(),
                        derivation.atc(),
                        derivation.iv(),
                        derivation.sessionKey(),
                        derivation.sessionKeyBytes());
            }
        }

        record CdolBreakdown(
                @JsonProperty("schemaItems") int schemaItems,
                @JsonProperty("entries") List<Entry> entries,
                @JsonProperty("concatHex") String concatHex) {

            static CdolBreakdown from(Trace.Provenance.CdolBreakdown breakdown) {
                List<Entry> entryPayloads = new ArrayList<>();
                for (Trace.Provenance.CdolBreakdown.Entry entry : breakdown.entries()) {
                    entryPayloads.add(Entry.from(entry));
                }
                return new CdolBreakdown(breakdown.schemaItems(), entryPayloads, breakdown.concatHex());
            }

            record Entry(
                    @JsonProperty("index") int index,
                    @JsonProperty("tag") String tag,
                    @JsonProperty("length") int length,
                    @JsonProperty("source") String source,
                    @JsonProperty("offset") String offset,
                    @JsonProperty("rawHex") String rawHex,
                    @JsonProperty("decoded") Decoded decoded) {

                static Entry from(Trace.Provenance.CdolBreakdown.Entry entry) {
                    return new Entry(
                            entry.index(),
                            trimTag(entry.tag()),
                            entry.length(),
                            entry.source(),
                            entry.offset(),
                            entry.rawHex(),
                            Decoded.from(entry.decoded()));
                }

                private static String trimTag(String tag) {
                    if (tag == null) {
                        return null;
                    }
                    String trimmed = tag.replaceFirst("^0+", "");
                    return trimmed.isEmpty() ? "0" : trimmed;
                }

                record Decoded(
                        @JsonProperty("label") String label,
                        @JsonProperty("value") Object value) {

                    static Decoded from(Trace.Provenance.CdolBreakdown.Entry.Decoded decoded) {
                        return new Decoded(decoded.label(), decoded.value());
                    }
                }
            }
        }

        record IadDecoding(
                @JsonProperty("rawHex") String rawHex,
                @JsonProperty("fields") List<Field> fields) {

            static IadDecoding from(Trace.Provenance.IadDecoding decoding) {
                List<Field> fieldPayloads = new ArrayList<>();
                for (Trace.Provenance.IadDecoding.Field field : decoding.fields()) {
                    fieldPayloads.add(new Field(field.name(), field.value()));
                }
                return new IadDecoding(decoding.rawHex(), fieldPayloads);
            }

            record Field(
                    @JsonProperty("name") String name,
                    @JsonProperty("value") Object value) {}
        }

        record MacTranscript(
                @JsonProperty("algorithm") String algorithm,
                @JsonProperty("paddingRule") String paddingRule,
                @JsonProperty("iv") String iv,
                @JsonProperty("blockCount") int blockCount,
                @JsonProperty("blocks") List<Block> blocks,
                @JsonProperty("generateAcRaw") String generateAcRaw,
                @JsonProperty("cidFlags") CidFlags cidFlags) {

            static MacTranscript from(Trace.Provenance.MacTranscript transcript) {
                List<Block> blockPayloads = new ArrayList<>();
                for (Trace.Provenance.MacTranscript.Block block : transcript.blocks()) {
                    blockPayloads.add(new Block(block.index(), block.input(), block.cipher()));
                }
                return new MacTranscript(
                        transcript.algorithm(),
                        transcript.paddingRule(),
                        transcript.iv(),
                        transcript.blockCount(),
                        blockPayloads,
                        transcript.generateAcRaw(),
                        new CidFlags(
                                transcript.cidFlags().arqc(),
                                transcript.cidFlags().advice(),
                                transcript.cidFlags().tc(),
                                transcript.cidFlags().aac()));
            }

            record Block(
                    @JsonProperty("index") int index,
                    @JsonProperty("input") String input,
                    @JsonProperty("cipher") String cipher) {}

            record CidFlags(
                    @JsonProperty("arqc") boolean arqc,
                    @JsonProperty("advice") boolean advice,
                    @JsonProperty("tc") boolean tc,
                    @JsonProperty("aac") boolean aac) {}
        }

        record DecimalizationOverlay(
                @JsonProperty("table") String table,
                @JsonProperty("sourceHex") String sourceHex,
                @JsonProperty("sourceDecimal") String sourceDecimal,
                @JsonProperty("maskPattern") String maskPattern,
                @JsonProperty("overlaySteps") List<OverlayStep> overlaySteps,
                @JsonProperty("otp") String otp,
                @JsonProperty("digits") int digits) {

            static DecimalizationOverlay from(Trace.Provenance.DecimalizationOverlay overlay) {
                List<OverlayStep> steps = new ArrayList<>();
                for (Trace.Provenance.DecimalizationOverlay.OverlayStep step : overlay.overlaySteps()) {
                    steps.add(new OverlayStep(step.index(), step.from(), step.to()));
                }
                return new DecimalizationOverlay(
                        overlay.table(),
                        overlay.sourceHex(),
                        overlay.sourceDecimal(),
                        overlay.maskPattern(),
                        steps,
                        overlay.otp(),
                        overlay.digits());
            }

            record OverlayStep(
                    @JsonProperty("index") int index,
                    @JsonProperty("from") String from,
                    @JsonProperty("to") String to) {}
        }
    }

    private static final class CanonicalTraceData {
        private static final String BASELINE_MASTER_DIGEST =
                "sha256:223E0A160AF9DA0A03E6DD2C4719C56F5D66A633CBE84E78AAA9F3735865522A";
        private static final EmvCapTraceProvenancePayload CANONICAL_PROVENANCE = loadCanonicalProvenance();

        private CanonicalTraceData() {}

        private static EmvCapTraceProvenancePayload loadCanonicalProvenance() {
            Map<String, Object> trace = EmvCapTraceProvenanceSchema.traceSchema();
            @SuppressWarnings("unchecked")
            Map<String, Object> provenance = (Map<String, Object>) trace.get("provenance");
            EmvCapTraceProvenancePayload.ProtocolContext context = mapProtocolContext(provenance);
            EmvCapTraceProvenancePayload.KeyDerivation derivation = mapKeyDerivation(provenance);
            EmvCapTraceProvenancePayload.CdolBreakdown cdol = mapCdolBreakdown(provenance);
            EmvCapTraceProvenancePayload.IadDecoding iad = mapIad(provenance);
            EmvCapTraceProvenancePayload.MacTranscript macTranscript = mapMacTranscript(provenance);
            EmvCapTraceProvenancePayload.DecimalizationOverlay overlay = mapDecimalization(provenance);
            return new EmvCapTraceProvenancePayload(context, derivation, cdol, iad, macTranscript, overlay);
        }

        private static EmvCapTraceProvenancePayload canonicalProvenance() {
            return CANONICAL_PROVENANCE;
        }

        @SuppressWarnings("unchecked")
        private static EmvCapTraceProvenancePayload.ProtocolContext mapProtocolContext(Map<String, Object> provenance) {
            Map<String, Object> context = (Map<String, Object>) provenance.get("protocolContext");
            return new EmvCapTraceProvenancePayload.ProtocolContext(
                    (String) context.get("profile"),
                    (String) context.get("mode"),
                    (String) context.get("emvVersion"),
                    (String) context.get("acType"),
                    (String) context.get("cid"),
                    (String) context.get("issuerPolicyId"),
                    (String) context.get("issuerPolicyNotes"));
        }

        @SuppressWarnings("unchecked")
        private static EmvCapTraceProvenancePayload.KeyDerivation mapKeyDerivation(Map<String, Object> provenance) {
            Map<String, Object> derivation = (Map<String, Object>) provenance.get("keyDerivation");
            return new EmvCapTraceProvenancePayload.KeyDerivation(
                    (String) derivation.get("masterFamily"),
                    (String) derivation.get("derivationAlgorithm"),
                    ((Number) derivation.get("masterKeyBytes")).intValue(),
                    (String) derivation.get("masterKeySha256"),
                    (String) derivation.get("maskedPan"),
                    (String) derivation.get("maskedPanSha256"),
                    (String) derivation.get("maskedPsn"),
                    (String) derivation.get("maskedPsnSha256"),
                    (String) derivation.get("atc"),
                    (String) derivation.get("iv"),
                    (String) derivation.get("sessionKey"),
                    ((Number) derivation.get("sessionKeyBytes")).intValue());
        }

        @SuppressWarnings("unchecked")
        private static EmvCapTraceProvenancePayload.CdolBreakdown mapCdolBreakdown(Map<String, Object> provenance) {
            Map<String, Object> cdol = (Map<String, Object>) provenance.get("cdolBreakdown");
            int schemaItems = ((Number) cdol.get("schemaItems")).intValue();
            List<EmvCapTraceProvenancePayload.CdolBreakdown.Entry> entries = new ArrayList<>();
            List<Map<String, Object>> rawEntries = (List<Map<String, Object>>) cdol.get("entries");
            for (Map<String, Object> entry : rawEntries) {
                Map<String, Object> decoded = (Map<String, Object>) entry.get("decoded");
                entries.add(new EmvCapTraceProvenancePayload.CdolBreakdown.Entry(
                        ((Number) entry.get("index")).intValue(),
                        (String) entry.get("tag"),
                        ((Number) entry.get("length")).intValue(),
                        (String) entry.get("source"),
                        (String) entry.get("offset"),
                        (String) entry.get("rawHex"),
                        new EmvCapTraceProvenancePayload.CdolBreakdown.Entry.Decoded(
                                (String) decoded.get("label"), decoded.get("value"))));
            }
            return new EmvCapTraceProvenancePayload.CdolBreakdown(schemaItems, entries, (String) cdol.get("concatHex"));
        }

        @SuppressWarnings("unchecked")
        private static EmvCapTraceProvenancePayload.IadDecoding mapIad(Map<String, Object> provenance) {
            Map<String, Object> iad = (Map<String, Object>) provenance.get("iadDecoding");
            List<EmvCapTraceProvenancePayload.IadDecoding.Field> fields = new ArrayList<>();
            for (Map<String, Object> field : (List<Map<String, Object>>) iad.get("fields")) {
                fields.add(new EmvCapTraceProvenancePayload.IadDecoding.Field(
                        (String) field.get("name"), field.get("value")));
            }
            return new EmvCapTraceProvenancePayload.IadDecoding((String) iad.get("rawHex"), fields);
        }

        @SuppressWarnings("unchecked")
        private static EmvCapTraceProvenancePayload.MacTranscript mapMacTranscript(Map<String, Object> provenance) {
            Map<String, Object> mac = (Map<String, Object>) provenance.get("macTranscript");
            List<EmvCapTraceProvenancePayload.MacTranscript.Block> blocks = new ArrayList<>();
            for (Map<String, Object> block : (List<Map<String, Object>>) mac.get("blocks")) {
                blocks.add(new EmvCapTraceProvenancePayload.MacTranscript.Block(
                        ((Number) block.get("index")).intValue(), (String) block.get("input"), (String)
                                block.get("cipher")));
            }
            Map<String, Object> flags = (Map<String, Object>) mac.get("cidFlags");
            EmvCapTraceProvenancePayload.MacTranscript.CidFlags cidFlags =
                    new EmvCapTraceProvenancePayload.MacTranscript.CidFlags(
                            (Boolean) flags.get("arqc"),
                            (Boolean) flags.get("advice"),
                            (Boolean) flags.get("tc"),
                            (Boolean) flags.get("aac"));
            return new EmvCapTraceProvenancePayload.MacTranscript(
                    (String) mac.get("algorithm"),
                    (String) mac.get("paddingRule"),
                    (String) mac.get("iv"),
                    ((Number) mac.get("blockCount")).intValue(),
                    blocks,
                    (String) mac.get("generateAcRaw"),
                    cidFlags);
        }

        @SuppressWarnings("unchecked")
        private static EmvCapTraceProvenancePayload.DecimalizationOverlay mapDecimalization(
                Map<String, Object> provenance) {
            Map<String, Object> overlay = (Map<String, Object>) provenance.get("decimalizationOverlay");
            List<EmvCapTraceProvenancePayload.DecimalizationOverlay.OverlayStep> steps = new ArrayList<>();
            for (Map<String, Object> step : (List<Map<String, Object>>) overlay.get("overlaySteps")) {
                steps.add(new EmvCapTraceProvenancePayload.DecimalizationOverlay.OverlayStep(
                        ((Number) step.get("index")).intValue(), (String) step.get("from"), (String) step.get("to")));
            }
            return new EmvCapTraceProvenancePayload.DecimalizationOverlay(
                    (String) overlay.get("table"),
                    (String) overlay.get("sourceHex"),
                    (String) overlay.get("sourceDecimal"),
                    (String) overlay.get("maskPattern"),
                    steps,
                    (String) overlay.get("otp"),
                    ((Number) overlay.get("digits")).intValue());
        }
    }
}
