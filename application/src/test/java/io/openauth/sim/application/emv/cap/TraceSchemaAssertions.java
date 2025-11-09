package io.openauth.sim.application.emv.cap;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.Trace;
import io.openauth.sim.core.emv.cap.EmvCapTraceProvenanceSchema;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class TraceSchemaAssertions {

    private TraceSchemaAssertions() {
        // no instances
    }

    static void assertMatchesSchema(Trace trace) {
        Map<String, Object> traceMap = new LinkedHashMap<>();
        traceMap.put("masterKeySha256", trace.masterKeySha256());
        traceMap.put("sessionKey", trace.sessionKey());
        traceMap.put("atc", trace.atc());
        traceMap.put("branchFactor", trace.branchFactor());
        traceMap.put("height", trace.height());
        traceMap.put("maskLength", trace.maskLength());
        Map<String, Object> previewWindow = new LinkedHashMap<>();
        previewWindow.put("backward", trace.previewWindowBackward());
        previewWindow.put("forward", trace.previewWindowForward());
        traceMap.put("previewWindow", previewWindow);

        Map<String, Object> generateAcInput = new LinkedHashMap<>();
        generateAcInput.put("terminal", trace.generateAcInput().terminalHex());
        generateAcInput.put("icc", trace.generateAcInput().iccHex());
        traceMap.put("generateAcInput", generateAcInput);

        traceMap.put("iccPayloadTemplate", trace.iccPayloadTemplate());
        traceMap.put("iccPayloadResolved", trace.iccPayloadResolved());
        traceMap.put("generateAcResult", trace.generateAcResult());
        traceMap.put("bitmask", trace.bitmask());
        traceMap.put("maskedDigitsOverlay", trace.maskedDigits());
        traceMap.put("issuerApplicationData", trace.issuerApplicationData());
        traceMap.put("provenance", provenanceMap(trace.provenance()));

        List<String> missing = EmvCapTraceProvenanceSchema.missingFields(traceMap);
        assertTrue(missing.isEmpty(), () -> "Trace schema mismatch: missing " + missing);
    }

    private static Map<String, Object> provenanceMap(Trace.Provenance provenance) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("protocolContext", protocolContextMap(provenance.protocolContext()));
        map.put("keyDerivation", keyDerivationMap(provenance.keyDerivation()));
        map.put("cdolBreakdown", cdolBreakdownMap(provenance.cdolBreakdown()));
        map.put("iadDecoding", iadMap(provenance.iadDecoding()));
        map.put("macTranscript", macTranscriptMap(provenance.macTranscript()));
        map.put("decimalizationOverlay", decimalizationMap(provenance.decimalizationOverlay()));
        return map;
    }

    private static Map<String, Object> protocolContextMap(Trace.Provenance.ProtocolContext context) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("profile", context.profile());
        map.put("mode", context.mode());
        map.put("emvVersion", context.emvVersion());
        map.put("acType", context.acType());
        map.put("cid", context.cid());
        map.put("issuerPolicyId", context.issuerPolicyId());
        map.put("issuerPolicyNotes", context.issuerPolicyNotes());
        return map;
    }

    private static Map<String, Object> keyDerivationMap(Trace.Provenance.KeyDerivation derivation) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("masterFamily", derivation.masterFamily());
        map.put("derivationAlgorithm", derivation.derivationAlgorithm());
        map.put("masterKeyBytes", derivation.masterKeyBytes());
        map.put("masterKeySha256", derivation.masterKeySha256());
        map.put("maskedPan", derivation.maskedPan());
        map.put("maskedPanSha256", derivation.maskedPanSha256());
        map.put("maskedPsn", derivation.maskedPsn());
        map.put("maskedPsnSha256", derivation.maskedPsnSha256());
        map.put("atc", derivation.atc());
        map.put("iv", derivation.iv());
        map.put("sessionKey", derivation.sessionKey());
        map.put("sessionKeyBytes", derivation.sessionKeyBytes());
        return map;
    }

    private static Map<String, Object> cdolBreakdownMap(Trace.Provenance.CdolBreakdown breakdown) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("schemaItems", breakdown.schemaItems());
        List<Map<String, Object>> entries = new ArrayList<>();
        for (Trace.Provenance.CdolBreakdown.Entry entry : breakdown.entries()) {
            Map<String, Object> entryMap = new LinkedHashMap<>();
            entryMap.put("index", entry.index());
            entryMap.put("tag", entry.tag());
            entryMap.put("length", entry.length());
            entryMap.put("source", entry.source());
            entryMap.put("offset", entry.offset());
            entryMap.put("rawHex", entry.rawHex());
            Trace.Provenance.CdolBreakdown.Entry.Decoded decoded = entry.decoded();
            Map<String, Object> decodedMap = new LinkedHashMap<>();
            decodedMap.put("label", decoded.label());
            decodedMap.put("value", decoded.value());
            entryMap.put("decoded", decodedMap);
            entries.add(entryMap);
        }
        map.put("entries", entries);
        map.put("concatHex", breakdown.concatHex());
        return map;
    }

    private static Map<String, Object> iadMap(Trace.Provenance.IadDecoding iadDecoding) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("rawHex", iadDecoding.rawHex());
        List<Map<String, Object>> fields = new ArrayList<>();
        for (Trace.Provenance.IadDecoding.Field field : iadDecoding.fields()) {
            Map<String, Object> fieldMap = new LinkedHashMap<>();
            fieldMap.put("name", field.name());
            fieldMap.put("value", field.value());
            fields.add(fieldMap);
        }
        map.put("fields", fields);
        return map;
    }

    private static Map<String, Object> macTranscriptMap(Trace.Provenance.MacTranscript transcript) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("algorithm", transcript.algorithm());
        map.put("paddingRule", transcript.paddingRule());
        map.put("iv", transcript.iv());
        map.put("blockCount", transcript.blockCount());
        List<Map<String, Object>> blocks = new ArrayList<>();
        for (Trace.Provenance.MacTranscript.Block block : transcript.blocks()) {
            Map<String, Object> blockMap = new LinkedHashMap<>();
            blockMap.put("index", block.index());
            blockMap.put("input", block.input());
            blockMap.put("cipher", block.cipher());
            blocks.add(blockMap);
        }
        map.put("blocks", blocks);
        map.put("generateAcRaw", transcript.generateAcRaw());
        Trace.Provenance.MacTranscript.CidFlags flags = transcript.cidFlags();
        Map<String, Object> flagMap = new LinkedHashMap<>();
        flagMap.put("arqc", flags.arqc());
        flagMap.put("advice", flags.advice());
        flagMap.put("tc", flags.tc());
        flagMap.put("aac", flags.aac());
        map.put("cidFlags", flagMap);
        return map;
    }

    private static Map<String, Object> decimalizationMap(Trace.Provenance.DecimalizationOverlay overlay) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("table", overlay.table());
        map.put("sourceHex", overlay.sourceHex());
        map.put("sourceDecimal", overlay.sourceDecimal());
        map.put("maskPattern", overlay.maskPattern());
        List<Map<String, Object>> steps = new ArrayList<>();
        for (Trace.Provenance.DecimalizationOverlay.OverlayStep step : overlay.overlaySteps()) {
            Map<String, Object> stepMap = new LinkedHashMap<>();
            stepMap.put("index", step.index());
            stepMap.put("from", step.from());
            stepMap.put("to", step.to());
            steps.add(stepMap);
        }
        map.put("overlaySteps", steps);
        map.put("otp", overlay.otp());
        map.put("digits", overlay.digits());
        return map;
    }
}
