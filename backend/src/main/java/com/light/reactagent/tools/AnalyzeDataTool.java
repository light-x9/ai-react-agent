package com.light.reactagent.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.light.reactagent.tools.file.FileContextHolder;
import com.light.reactagent.tools.file.FileMetadataManager;
import com.light.reactagent.tools.file.FileStorageException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.extractor.ExtractorFactory;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据分析工具：读取工作区内的 CSV / Excel / JSON 表格，解析后写入一份 echarts JSON 配置文件。
 * <p>
 * 触发时机：用户在对话中上传了 CSV/Excel 文件时，由 system prompt 提示 LLM 自动调用。
 * 返回的 echarts json 会通过已有文件下载链路把 fileId 吐给前端，前端据此渲染交互式图表。
 */
@Slf4j
public class AnalyzeDataTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** 第一行采样字数上限（避免单 cell 过长污染样本统计） */
    private static final int SAMPLE_CELL_MAX = 80;
    /** 可用于聚合的数值列最大 distinct 值，超过则认为是 ID 类数值而非度量 */
    private static final int MEASURE_MAX_DISTINCT = 100;
    /** 分类维度的最大 distinct 值，超过则跳过为维度 */
    private static final int DIM_MAX_DISTINCT = 50;

    private final FileMetadataManager fileMetadataManager;

    public AnalyzeDataTool(FileMetadataManager fileMetadataManager) {
        this.fileMetadataManager = fileMetadataManager;
    }

    @Tool(description = "分析工作区内的数据文件（CSV/Excel/JSON）并生成一个 echarts JSON 配置文件用于前端图表渲染。"
            + "filePath 为文件相对路径，chartTitle 为图表标题（可选），chartType 可选：auto（默认自动判断）/ bar / line / pie。"
            + "返回写入的 chart JSON 文件路径及分析摘要。")
    public String analyze(@ToolParam(description = "要分析的数据文件在 sandbox 内的路径，例如 'file/uploaded.csv'") String filePath,
                          @ToolParam(required = false, description = "图表标题（可选）") String chartTitle,
                          @ToolParam(required = false, description = "图表类型：auto / bar / line / pie（可选，默认 auto）") String chartType) {
        if (StrUtil.isBlank(filePath)) {
            return "filePath 不能为空";
        }
        // 路径安全：只读沙箱内
        File file = resolveSafe(filePath);
        if (file == null) {
            return "拒绝访问：文件路径越界，只能分析工作区内的文件。";
        }
        if (!file.exists() || !file.isFile()) {
            return "文件不存在：" + filePath;
        }

        try {
            String lower = filePath.toLowerCase();
            TableData data;
            if (lower.endsWith(".csv") || lower.endsWith(".txt")) {
                data = parseCsv(file);
            } else if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
                data = parseExcel(file);
            } else if (lower.endsWith(".json")) {
                data = parseJson(file);
            } else {
                return "不支持的数据文件格式，仅接受：.csv / .txt / .xlsx / .xls / .json";
            }

            if (data.rows.isEmpty()) {
                return "未从文件中解析到数据行：" + filePath;
            }
            if (data.headers.size() < 2) {
                return "至少需要 2 列数据才能生成图表，当前仅 " + data.headers.size() + " 列：" + filePath;
            }

            // 推断图表类型 & 选维度/度量列
            boolean auto = chartType == null || "auto".equalsIgnoreCase(chartType);
            ChartSpec spec = auto
                    ? inferAndBuild(data, chartTitle)
                    : buildWith(data, chartTitle, chartType);

            // 写入 echarts json 文件
            String outName = "charts/chart_" + System.currentTimeMillis() + ".json";
            File outFile = resolveSafeWrite(outName);
            if (outFile == null) {
                return "无法写入 chart 文件：路径越界";
            }
            FileUtil.mkdir(outFile.getParentFile());
            String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(spec.options);
            FileUtil.writeString(json, outFile, StandardCharsets.UTF_8);

            // 注册文件元数据 → 后续 frontend file cards 自动显示下载按钮
            String fileId = null;
            try {
                String chatId = FileContextHolder.getChatId();
                if (StrUtil.isNotBlank(chatId) && outFile.exists()) {
                    fileId = fileMetadataManager.registerFile(
                            chatId,
                            FileContextHolder.getUserId(),
                            outName,
                            "charts/",
                            "application/json",
                            outFile.length()
                    );
                }
            } catch (FileStorageException e) {
                log.warn("[AnalyzeDataTool] chart 文件注册失败：{}", e.getMessage());
            }

            StringBuilder sb = new StringBuilder();
            sb.append("已生成图表配置文件：").append(outName).append("\n");
            sb.append("图表类型：").append(spec.type).append("\n");
            sb.append("维度列（X 轴）：").append(spec.dimension).append("\n");
            sb.append("度量列（Y 轴）：").append(String.join(", ", spec.measures)).append("\n");
            sb.append("数据行数：").append(data.rows.size()).append("\n");
            sb.append("列数：").append(data.headers.size());
            if (fileId != null) {
                sb.append("\n[CHART_FILE=").append(fileId).append("]");
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("[AnalyzeDataTool] 分析文件 {} 失败: {}", filePath, e.getMessage(), e);
            return "分析数据文件失败：" + e.getMessage();
        }
    }

    /**
     * 自动推断维度列 + 度量列 + 图表类型，构建 echarts option
     */
    private ChartSpec inferAndBuild(TableData data, String chartTitle) {
        // 1) 区分数值列 vs 分类列（按采样）
        int colCount = data.headers.size();
        boolean[] isNumeric = new boolean[colCount];
        boolean[] isDate = new boolean[colCount];
        int[] distinctCount = new int[colCount];
        for (int c = 0; c < colCount; c++) {
            isNumeric[c] = detectNumericColumn(data, c);
            isDate[c] = detectDateColumn(data, c);
        }
        // distinct count
        for (int c = 0; c < colCount; c++) {
            java.util.Set<String> set = new java.util.LinkedHashSet<>();
            for (List<String> row : data.rows) {
                if (c < row.size()) set.add(row.get(c));
            }
            distinctCount[c] = set.size();
        }

        // 2) 选维度列：优先日期，其次是 distinct 值少的非数值列
        int dimCol = -1;
        for (int c = 0; c < colCount; c++) {
            if (isDate[c] && distinctCount[c] >= 2) {
                dimCol = c;
                break;
            }
        }
        if (dimCol == -1) {
            // 选 distinct 值最少且非数值（或虽然数值但 distinct 够多说明是度量）的列
            int bestDistinct = Integer.MAX_VALUE;
            for (int c = 0; c < colCount; c++) {
                if (isNumeric[c]) continue;
                if (distinctCount[c] >= 2 && distinctCount[c] <= DIM_MAX_DISTINCT && distinctCount[c] < bestDistinct) {
                    bestDistinct = distinctCount[c];
                    dimCol = c;
                }
            }
        }
        if (dimCol == -1) {
            // fallback: 列0
            dimCol = 0;
        }

        // 3) 选度量列：数值列且 distinct > 1
        List<Integer> measureCols = new ArrayList<>();
        for (int c = 0; c < colCount; c++) {
            if (c == dimCol) continue;
            if (isNumeric[c] && distinctCount[c] >= 2) {
                measureCols.add(c);
            }
        }
        if (measureCols.isEmpty()) {
            // 把维度列外的第一列当度量（强制）
            for (int c = 0; c < colCount; c++) {
                if (c != dimCol) {
                    measureCols.add(c);
                    break;
                }
            }
        }

        // 4) 确定图表类型
        String type;
        if (measureCols.size() == 1 && distinctCount[dimCol] <= 8 && !isDate[dimCol]) {
            type = "pie";
        } else if (isDate[dimCol]) {
            type = "line";
        } else {
            type = "bar";
        }

        return buildOption(data, dimCol, measureCols, type, chartTitle);
    }

    private ChartSpec buildWith(TableData data, String chartTitle, String chartType) {
        int dimCol = 0;
        List<Integer> measureCols = new ArrayList<>();
        for (int c = 1; c < data.headers.size(); c++) {
            measureCols.add(c);
        }
        return buildOption(data, dimCol, measureCols, chartType, chartTitle);
    }

    /**
     * 组装 echarts option（JSON 直接可喂给 echarts.setOption()）
     */
    private ChartSpec buildOption(TableData data, int dimCol, List<Integer> measureCols, String type, String title) {
        ObjectNode root = MAPPER.createObjectNode();

        // title
        root.putObject("title").put("text", StrUtil.blankToDefault(title, "数据分析图表"));

        // tooltip
        root.putObject("tooltip").put("trigger", "pie".equals(type) ? "item" : "axis");

        // legend (多系列时有用)
        if (measureCols.size() > 1 && !"pie".equals(type)) {
            root.putObject("legend");
        }

        // grid
        root.putObject("grid").putArray("containLabel").add(true);
        ObjectNode grid = (ObjectNode) root.get("grid");
        grid.put("left", "3%").put("right", "4%").put("bottom", "3%");

        // toolbox：保存图片
        ObjectNode toolbox = root.putObject("toolbox").putObject("feature");
        toolbox.putObject("saveAsImage").put("title", "保存为图片");

        // dimensional data
        java.util.LinkedHashSet<String> xSet = new java.util.LinkedHashSet<>();
        for (List<String> row : data.rows) {
            if (dimCol < row.size()) xSet.add(row.get(dimCol));
        }
        List<String> xData = new ArrayList<>(xSet);

        if ("pie".equals(type)) {
            // pie: xData => legend, 单 measure
            ObjectNode series = root.putObject("series");
            series.put("name", "数据").put("type", "pie").put("radius", "55%");
            ArrayNode dataArr = series.putArray("data");
            String measure = data.headers.get(measureCols.get(0));
            int idx = 0;
            for (List<String> row : data.rows) {
                String name = dimCol < row.size() ? row.get(dimCol) : "未知";
                ObjectNode item = dataArr.addObject();
                item.put("value", numOrZero(measureCols.get(0) < row.size() ? row.get(measureCols.get(0)) : "0"));
                item.put("name", name);
                if (idx++ >= xData.size()) break;
            }
            ChartSpec spec = new ChartSpec();
            spec.type = "pie";
            spec.dimension = data.headers.get(dimCol);
            spec.measures = List.of(measure);
            spec.options = root;
            return spec;
        }

        // xAxis
        ObjectNode xAxis = root.putObject("xAxis");
        xAxis.put("type", "category").put("boundaryGap", !"line".equals(type));
        ArrayNode xArr = xAxis.putArray("data");
        xData.forEach(xArr::add);
        // 标签旋转防重叠
        xAxis.putObject("axisLabel").put("rotate", xData.size() > 6 ? 30 : 0);

        // yAxis
        root.putObject("yAxis").put("type", "value");

        // series
        ArrayNode seriesArr = root.putArray("series");
        for (int mc : measureCols) {
            String name = data.headers.get(mc);
            ObjectNode s = seriesArr.addObject();
            s.put("name", name).put("type", type).put("data", buildSeriesData(data, dimCol, mc, xData));
            if ("line".equals(type)) {
                s.put("smooth", true);
            }
        }

        ChartSpec spec = new ChartSpec();
        spec.type = type;
        spec.dimension = data.headers.get(dimCol);
        spec.measures = measureCols.stream().map(data.headers::get).toList();
        spec.options = root;
        return spec;
    }

    private ArrayNode buildSeriesData(TableData data, int dimCol, int measureCol, List<String> xData) {
        ArrayNode arr = MAPPER.createArrayNode();
        Map<String, Double> dim2val = new LinkedHashMap<>();
        // 同维度合并（取最后一个值 or 求和）——多数场景取最后一个值即可
        for (List<String> row : data.rows) {
            String d = dimCol < row.size() ? row.get(dimCol) : "未知";
            double v = numOrZero(measureCol < row.size() ? row.get(measureCol) : "0");
            dim2val.merge(d, v, Double::sum);
        }
        for (String x : xData) {
            Double v = dim2val.get(x);
            if (v == null || v == 0.0) {
                arr.addNull();
            } else {
                arr.add(Math.round(v * 100.0) / 100.0);
            }
        }
        return arr;
    }

    private boolean detectNumericColumn(TableData data, int col) {
        int check = Math.min(data.rows.size(), 20);
        int numCount = 0, total = 0;
        for (int r = 0; r < check; r++) {
            if (col >= data.rows.get(r).size()) continue;
            String v = data.rows.get(r).get(col);
            if (v == null || v.isBlank()) continue;
            total++;
            try {
                Double.parseDouble(v.trim().replace(",", ""));
                numCount++;
            } catch (NumberFormatException ignored) {
            }
        }
        return total > 0 && (numCount * 1.0 / total) >= 0.7;
    }

    private boolean detectDateColumn(TableData data, int col) {
        int check = Math.min(data.rows.size(), 20);
        int dateCount = 0, total = 0;
        for (int r = 0; r < check; r++) {
            if (col >= data.rows.get(r).size()) continue;
            String v = data.rows.get(r).get(col);
            if (v == null || v.isBlank()) continue;
            total++;
            if (cn.hutool.core.date.DateUtil.parse(v.trim()) != null) dateCount++;
        }
        return total > 0 && (dateCount * 1.0 / total) >= 0.7;
    }

    private double numOrZero(String v) {
        if (v == null || v.isBlank()) return 0;
        try {
            return Double.parseDouble(v.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ===== 文件格式解析 =====

    private TableData parseCsv(File file) {
        TableData td = new TableData();
        List<String> lines = FileUtil.readUtf8Lines(file);
        if (lines.isEmpty()) return td;
        td.headers = splitCsv(lines.get(0));
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line == null || line.isBlank()) continue;
            td.rows.add(splitCsv(line));
        }
        return td;
    }

    /** 简易 CSV 切分（忽略引号内逗号——够用了） */
    private List<String> splitCsv(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuote = false;
        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuote = !inQuote;
            } else if (c == ',' && !inQuote) {
                out.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        out.add(sb.toString().trim());
        return out;
    }

    private TableData parseExcel(File file) throws Exception {
        TableData td = new TableData();
        try (Workbook wb = WorkbookFactory.create(file)) {
            Sheet sheet = wb.getSheetAt(0);
            int maxCols = 0;
            for (Row row : sheet) {
                maxCols = Math.max(maxCols, row.getLastCellNum());
            }
            for (Row row : sheet) {
                List<String> cells = new ArrayList<>();
                for (int c = 0; c < maxCols; c++) {
                    Cell cell = row.getCell(c);
                    cells.add(cell == null ? "" : cellToString(cell));
                }
                if (td.headers == null) {
                    td.headers = cells;
                } else {
                    td.rows.add(cells);
                }
            }
        }
        return td;
    }

    private String cellToString(Cell cell) {
        CellType type = cell.getCellType();
        if (type == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(cell)) {
                return cn.hutool.core.date.DateUtil.formatDateTime(cell.getDateCellValue());
            }
            double v = cell.getNumericCellValue();
            return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
        } else if (type == CellType.BOOLEAN) {
            return String.valueOf(cell.getBooleanCellValue());
        } else if (type == CellType.FORMULA) {
            try {
                return String.valueOf(cell.getNumericCellValue());
            } catch (Exception e) {
                return cell.getStringCellValue();
            }
        } else {
            String s = cell.getStringCellValue();
            return s == null ? "" : s;
        }
    }

    private TableData parseJson(File file) throws Exception {
        // 简单支持：JSON 对象数组，每行是一个对象
        TableData td = new TableData();
        String content = FileUtil.readUtf8String(file).trim();
        if (content.startsWith("[")) {
            ArrayNode arr = (ArrayNode) MAPPER.readTree(content);
            for (var node : arr) {
                if (node.isObject()) {
                    ObjectNode obj = (ObjectNode) node;
                    if (td.headers == null) {
                        List<String> headers = new ArrayList<>();
                        obj.fieldNames().forEachRemaining(headers::add);
                        td.headers = headers;
                    }
                    List<String> row = new ArrayList<>();
                    for (String h : td.headers) {
                        row.add(obj.has(h) && !obj.get(h).isNull() ? obj.get(h).asText() : "");
                    }
                    td.rows.add(row);
                }
            }
        } else if (content.startsWith("{")) {
            // 单对象 → 1 列 key, 1 列 value
            td.headers = List.of("键", "值");
            ObjectNode obj = (ObjectNode) MAPPER.readTree(content);
            obj.fields().forEachRemaining(e -> td.rows.add(List.of(e.getKey(), e.getValue().asText())));
        }
        return td;
    }

    // ===== 沙箱路径 =====

    private File resolveSafe(String relPath) {
        if (relPath == null || relPath.isBlank()) return null;
        File baseDir = baseDir();
        baseDir.mkdirs();
        File target = new File(baseDir, relPath).getAbsoluteFile();
        if (!target.toPath().normalize().startsWith(baseDir.toPath().normalize())) {
            return null;
        }
        return target;
    }

    private File resolveSafeWrite(String relPath) {
        File f = resolveSafe(relPath);
        return f;
    }

    private File baseDir() {
        // 复用 FileToolSupport 的沙箱根路径
        return com.light.reactagent.tools.file.FileToolSupport.resolveBaseDir("file");
    }

    // ===== 内部数据结构 =====

    /** 简化后的表格：等长列为目标 */
    private static class TableData {
        List<String> headers;
        List<List<String>> rows = new ArrayList<>();
    }

    /** 组装结果 */
    private static class ChartSpec {
        String type;
        String dimension;
        List<String> measures;
        ObjectNode options;
    }
}
