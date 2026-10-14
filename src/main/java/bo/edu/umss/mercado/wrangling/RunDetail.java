package bo.edu.umss.mercado.wrangling;

import java.util.List;
import java.util.Map;

/** Un producto congelado; los originales privados se consultan por separado. */
public record RunDetail(RunSummary summary, Map<String, Object> manifest,
                        List<Map<String, Object>> dictionary, List<Map<String, Object>> graduates,
                        List<Map<String, Object>> employers, List<Map<String, Object>> selections,
                        List<Map<String, Object>> coding, List<Map<String, Object>> issues,
                        List<Map<String, Object>> quality, List<Map<String, Object>> members) {}
