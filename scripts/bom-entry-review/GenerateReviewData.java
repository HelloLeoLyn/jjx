import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jjx.production.domain.model.BomConsumptionCalculator;
import com.jjx.production.domain.model.ExactQuantity;
import com.jjx.production.domain.dto.PitchRequirementPreviewRequest;
import com.jjx.production.service.PitchRequirementCalculator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** dev-20261112-007：仅离线生成评审结果，直接调用既有权威计算器；不访问数据库或服务。 */
public class GenerateReviewData {
    private static BigDecimal dec(String value) { return new BigDecimal(value); }
    private static String text(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }
    private static String display(ExactQuantity value) { return text(value.toBigDecimal(4, RoundingMode.HALF_UP)); }
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("snapshot.json output.json");
        ObjectMapper json = new ObjectMapper();
        ObjectNode root = (ObjectNode) json.readTree(Files.readString(Path.of(args[0])));
        Map<String, Object> quotas = new LinkedHashMap<>();
        String[] bases = {"1", "0.5", "0.0156", "0.0195", "0.08", "0.078", "0.1"};
        String[] modules = {"1", "3", "6", "0.5", "100"};
        String[] losses = {"0", "2.5", "5", "10"};
        String[] minima = {"0", "1", "10"};
        int[] counts = {1, 100, 160, 300};
        for (String unit : new String[]{"PCS", "M", "M2"})
        for (String base : bases) for (String module : modules)
        for (String loss : losses) for (String minimum : minima) for (int count : counts) {
            String key = String.join("|", unit, base, module, loss, minimum, "" + count);
            ExactQuantity each = BomConsumptionCalculator.theoretical(BigDecimal.ONE, dec(base), dec(module), null);
            ExactQuantity total = BomConsumptionCalculator.appliedDemand(BigDecimal.valueOf(count), dec(base), dec(module),
                    null, null, dec(loss), dec(minimum));
            Map<String, Object> quote = new LinkedHashMap<>();
            quote.put("each", display(each));
            quote.put("total", text(BomConsumptionCalculator.roundStock(total, unit)));
            quote.put("exact", total.exactText());
            Map<String, Object> lengths = new LinkedHashMap<>();
            if (!unit.equals("PCS")) {
                for (String width : new String[]{"195", "205"}) {
                    ExactQuantity metres = unit.equals("M") ? total
                            : total.divide(ExactQuantity.fromDecimal(dec(width)).divide(ExactQuantity.of(1000)));
                    Map<String, Object> value = new LinkedHashMap<>();
                    value.put("metres", display(metres));
                    Map<String, String> rolls = new LinkedHashMap<>();
                    for (String roll : new String[]{"25", "37.2", "50"})
                        rolls.put(roll, metres.divide(ExactQuantity.fromDecimal(dec(roll))).ceilToInteger().toString());
                    value.put("rolls", rolls);
                    lengths.put(width, value);
                }
            }
            quote.put("lengths", lengths);
            quotas.put(key, quote);
        }
        Map<String, Object> pitches = new LinkedHashMap<>();
        for (String unit : new String[]{"M", "M2"})
        for (String pitch : new String[]{"78", "80", "80.5", "100"})
        for (String perJump : new String[]{"1", "2", "6"}) for (int count : counts) {
            var request = new PitchRequirementPreviewRequest();
            request.setWidthMm(dec("195")); request.setPitchMm(dec(pitch)); request.setPcsPerJump(dec(perJump));
            request.setInputUnit(unit); request.setOutputPcs(BigDecimal.valueOf(count));
            pitches.put(String.join("|", unit, "195", pitch, perJump, "" + count), PitchRequirementCalculator.preview(request));
        }
        root.put("calculationSource", "BomConsumptionCalculator + PitchRequirementCalculator (offline review only)");
        root.set("quotes", json.valueToTree(quotas));
        root.set("pitchQuotes", json.valueToTree(pitches));
        Files.writeString(Path.of(args[1]), json.writeValueAsString(root) + "\n");
        System.out.println("Generated " + quotas.size() + " quota samples + " + pitches.size() + " pitch samples from existing Java calculators");
    }
}
