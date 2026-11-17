// 模型展示层文案：中文名称、类型名、结果标签、以及每个模型的「能力说明」。
//
// 数据边界：
//   - 模型元数据（准确率、上下线状态、算法、官方描述）以后端 /api/models 为唯一来源。
//   - 这里只放「面向用户的中文解释」——它比数据库字段更适合承载较长文案，
//     也避免把 UI 文案硬塞进业务表。新增模型时在此补一条 MODEL_GUIDES 即可。

// ── 中文产品名称映射 ──────────────────────────────────────────────

export const CHINESE_DISPLAY_NAMES: Record<string, string> = {
  "ResNet-50": "智能图像分类",
  "YOLOv8": "智能目标检测",
  "BERT-Base": "文本情感分析",
  "BERT": "文本情感分析",
  "XGBoost": "鸢尾花品种分类",
  "U2Net": "智能抠图（背景移除）",
  "U2-Net": "智能抠图（背景移除）",
  "ChestCT": "肺部 CT 影像识别",
  "WeatherGBM": "天气类型预测",
  "StockRegression": "股票收盘价预测",
  "FlowerCNN": "花卉品种识别",
  "ChineseSentiment": "中文评论情感分析",
};

export function getDisplayName(englishName: string): string {
  return CHINESE_DISPLAY_NAMES[englishName] || englishName;
}

// ── 模型类型中文名 ────────────────────────────────────────────────

export const MODEL_TYPE_LABELS: Record<string, string> = {
  image: "图像分类",
  object: "目标检测",
  image_advanced: "图像处理",
  text: "文本分析",
  ml: "结构化预测",
  audio: "语音分析",
  video: "视频分析",
};

export function getTypeLabel(type?: string | null): string {
  if (!type) return "智能分析";
  return MODEL_TYPE_LABELS[type] || type;
}

// ── 结果标签中文映射 ──────────────────────────────────────────────
//
// 后端返回的标签是模型训练时的原始类别名（英文），这里统一翻译。
// 未命中时原样返回，保证新模型不会显示成空白。

export const LABEL_DISPLAY_MAP: Record<string, string> = {
  // 情感分析（二分类：英文 POSITIVE/NEGATIVE，中文 正面/负面）
  POSITIVE: "正面",
  NEGATIVE: "负面",
  NEUTRAL: "中性",
  positive: "正面",
  negative: "负面",
  neutral: "中性",

  // 肺部 CT 四分类
  "normal": "正常",
  "large.cell.carcinoma": "大细胞癌",
  "squamous.cell.carcinoma": "鳞状细胞癌",
  "adenocarcinoma": "腺癌",

  // 花卉五分类
  "daisy": "雏菊",
  "dandelion": "蒲公英",
  "roses": "玫瑰",
  "sunflowers": "向日葵",
  "tulips": "郁金香",

  // 鸢尾花三分类
  "setosa": "山鸢尾",
  "versicolor": "变色鸢尾",
  "virginica": "维吉尼亚鸢尾",

  // 天气五分类（西雅图气象数据）
  "rain": "雨",
  "sun": "晴",
  "fog": "雾",
  "drizzle": "毛毛雨",
  "snow": "雪",
};

export function getLabelDisplayName(label: string): string {
  return LABEL_DISPLAY_MAP[label] || LABEL_DISPLAY_MAP[label?.trim()] || label;
}

// ── 服务分类 ──────────────────────────────────────────────────────
//
// 分类决定卡片配色、图标与分组，是「视觉区分度」的来源。
// 每一条 Tailwind class 都写成完整字面量，避免运行时拼接被裁剪。

export type ModelCategory = "medical" | "vision" | "text" | "data";

export interface CategoryMeta {
  key: ModelCategory;
  label: string;
  short: string;
  description: string;
  icon: string;
  /** 渐变底（图标块 / 强调条） */
  gradient: string;
  /** 浅色标签 */
  soft: string;
  /** 文字主色 */
  text: string;
  /** 细边框 */
  border: string;
  /** 分隔线 / 进度条 */
  bar: string;
  /** 分区背景 */
  surface: string;
}

export const CATEGORY_META: Record<ModelCategory, CategoryMeta> = {
  medical: {
    key: "medical",
    label: "医学影像",
    short: "医疗",
    description: "面向医学影像的辅助识别，输出结构化类别与概率",
    icon: "🩻",
    gradient: "from-teal-500 to-emerald-500",
    soft: "bg-teal-50 text-teal-700 border-teal-100",
    text: "text-teal-700",
    border: "border-teal-100",
    bar: "from-teal-500 to-emerald-400",
    surface: "bg-teal-50/40",
  },
  vision: {
    key: "vision",
    label: "视觉理解",
    short: "视觉",
    description: "识别图片里有什么、在哪里，或直接把主体抠出来",
    icon: "👁️",
    gradient: "from-indigo-500 to-violet-500",
    soft: "bg-indigo-50 text-indigo-700 border-indigo-100",
    text: "text-indigo-700",
    border: "border-indigo-100",
    bar: "from-indigo-500 to-violet-400",
    surface: "bg-indigo-50/40",
  },
  text: {
    key: "text",
    label: "文本语言",
    short: "文本",
    description: "判断一段文字的情绪倾向，输出正面 / 负面",
    icon: "✍️",
    gradient: "from-amber-500 to-orange-500",
    soft: "bg-amber-50 text-amber-700 border-amber-100",
    text: "text-amber-700",
    border: "border-amber-100",
    bar: "from-amber-500 to-orange-400",
    surface: "bg-amber-50/40",
  },
  data: {
    key: "data",
    label: "数据预测",
    short: "数据",
    description: "用结构化字段做分类或回归，输出可直接使用的数值与概率",
    icon: "📊",
    gradient: "from-cyan-500 to-sky-500",
    soft: "bg-cyan-50 text-cyan-700 border-cyan-100",
    text: "text-cyan-700",
    border: "border-cyan-100",
    bar: "from-cyan-500 to-sky-400",
    surface: "bg-cyan-50/40",
  },
};

// 显式归属，避免依赖对描述文本做关键词匹配。
const API_CATEGORY: Record<string, ModelCategory> = {
  chest_ct: "medical",
  resnet50: "vision",
  yolov8: "vision",
  u2net: "vision",
  flower: "vision",
  bert: "text",
  chinese_sentiment: "text",
  xgboost: "data",
  weather: "data",
  stock: "data",
};

export function getCategoryByApi(api?: string | null, type?: string | null): ModelCategory {
  if (api && API_CATEGORY[api]) return API_CATEGORY[api];
  if (type === "text") return "text";
  if (type === "ml") return "data";
  return "vision";
}

// ── 每个模型的能力说明 ────────────────────────────────────────────
//
// 这是「模型介绍」的唯一来源：卡片、详情页、体验页提示都读它。
// tagline  → 一句话定位（卡片主文案）
// summary  → 两三句完整介绍（详情页）
// outputs  → 这个模型到底输出什么（用户最关心）
// classes  → 可识别的具体类别（分类模型必填）
// input    → 输入要求
// scenario → 适用场景
// notice   → 使用限制 / 免责声明

export interface ModelGuide {
  category: ModelCategory;
  icon: string;
  tagline: string;
  summary: string;
  outputs: string[];
  classes?: string[];
  classNote?: string;
  input: string;
  output: string;
  scenario: string[];
  algorithmCn: string;
  notice?: string;
}

export const MODEL_GUIDES: Record<string, ModelGuide> = {
  resnet50: {
    category: "vision",
    icon: "🖼️",
    tagline: "看出图片里的主体是什么",
    summary:
      "基于 ResNet-50 残差网络，在 ImageNet 1000 类物体上预训练。上传一张图片，模型判断画面主体最可能属于哪一类，并给出前 5 个候选类别及概率。",
    outputs: ["Top-5 中文类别", "每个类别的置信度", "按概率降序排列"],
    classes: ["动物", "交通工具", "水果蔬菜", "乐器", "家具", "电子产品", "运动器材", "自然景物"],
    classNote: "覆盖 ImageNet 1000 类常见物体，结果优先返回中文名称",
    input: "单张 JPG / PNG 图片，不超过 5MB，建议分辨率 224×224 以上，主体占画面比例较高",
    output: "返回前 5 个类别及对应概率（0–100%），第一项为模型判定结果",
    scenario: ["相册自动分类与打标", "电商商品图归类", "内容审核初筛", "教学演示与科普"],
    algorithmCn: "残差网络 ResNet-50，ImageNet 预训练权重",
  },

  yolov8: {
    category: "vision",
    icon: "🎯",
    tagline: "找出画面里有什么、各自在什么位置",
    summary:
      "YOLOv8 单阶段目标检测模型，在 COCO 80 类上预训练。一次推理即可同时定位多个目标，输出每个目标的类别、置信度与边界框，适合需要「位置信息」的场景。",
    outputs: ["目标类别（中文）", "检测置信度", "目标所在的边界框坐标"],
    classes: ["人", "汽车", "自行车", "狗", "猫", "椅子", "餐桌", "电视", "公交车", "背包"],
    classNote: "覆盖 COCO 80 类常见目标，可同时框出画面中的多个对象",
    input: "单张 JPG / PNG 图片，不超过 5MB，支持一张图内多个目标",
    output: "返回检测到的目标列表：类别 + 置信度（+ 边界框坐标），按置信度降序",
    scenario: ["安防监控画面分析", "工业缺陷与异物检测", "零售货架盘点", "自动驾驶感知演示"],
    algorithmCn: "YOLOv8n 单阶段检测器，COCO 预训练权重",
  },

  bert: {
    category: "text",
    icon: "💬",
    tagline: "按语种判定一句话是正面还是负面",
    summary:
      "二分类情感分析，按语种自动分流：英文走 DistilBERT（SST-2 微调），中文走平台自训的中文评论情感模型（MLP + 词袋）。两条链路都只输出正负两类，没有「中性」——SST-2 本身就是二分类，不会给出中性结论。长句、反讽、双重否定容易误判。",
    outputs: ["正面概率", "负面概率"],
    classes: ["正面", "负面"],
    classNote: "二分类，不输出中性。英文原始标签 POSITIVE / NEGATIVE，中文标签为「正面 / 负面」",
    input: "中英文文本，建议 10–500 字。中文占两成以上会自动走中文模型，否则走英文模型；中英混排时以中文模型为准更容易判对",
    output: "返回两类概率（合计 100%），概率最高者即为判定结论",
    scenario: ["商品评论情感归类", "舆情正负面监测", "客服对话情绪识别", "问卷开放题分析"],
    algorithmCn: "英文 DistilBERT（SST-2）+ 中文 MLP（ChnSentiCorp 酒店评论）分流",
  },

  xgboost: {
    category: "data",
    icon: "🌺",
    tagline: "根据花萼与花瓣尺寸判断鸢尾花品种",
    summary:
      "在经典 Iris 数据集（150 个样本、4 个数值特征、3 个品种）上训练的梯度提升树模型。输入四组测量值，输出三个品种各自的概率。",
    outputs: ["山鸢尾 / 变色鸢尾 / 维吉尼亚鸢尾 的概率", "概率最高的品种"],
    classes: ["山鸢尾", "变色鸢尾", "维吉尼亚鸢尾"],
    classNote: "三分类，原始标签 setosa / versicolor / virginica；特征是 4 个连续数值，不是文本或图片",
    input: "4 个数值（单位 cm）：花萼长度、花萼宽度、花瓣长度、花瓣宽度",
    output: "返回三个品种的概率分布，最高项为预测品种",
    scenario: ["结构化分类任务演示", "教学中的特征工程示例", "表格类模型接入范式参考"],
    algorithmCn: "XGBoost 梯度提升树，sklearn Iris 数据集训练",
  },

  u2net: {
    category: "vision",
    icon: "✂️",
    tagline: "一键去掉背景，得到透明底图片",
    summary:
      "U²-Net 显著目标分割模型，自动识别画面主体并生成 alpha 通道，把背景处理为透明。适合人像、商品、宠物等主体清晰的图片。",
    outputs: ["透明背景 PNG", "可直接预览与下载", "处理耗时"],
    input: "单张 JPG / PNG 图片，不超过 5MB；主体与背景对比越明显，边缘越干净",
    output: "返回去背后的透明 PNG 图像（在线预览，可一键下载）",
    scenario: ["电商商品白底图制作", "人像抠图与证件照", "海报素材去背景", "批量图片预处理"],
    algorithmCn: "U²-Net 显著目标分割（ONNX Runtime 推理）",
  },

  chest_ct: {
    category: "medical",
    icon: "🔬",
    tagline: "区分正常肺组织与三类肺癌亚型",
    summary:
      "基于 ResNet-50 迁移学习的胸部 CT 切片四分类模型，输出正常、腺癌、大细胞癌、鳞状细胞癌四类的概率，用于影像辅助筛查的教学与研究演示。",
    outputs: ["四类病变各自的概率", "概率最高的类别", "置信度"],
    classes: ["正常", "腺癌", "大细胞癌", "鳞状细胞癌"],
    classNote: "四分类，输入是单张 CT 切片而非整份序列；原始标签 normal / adenocarcinoma / large.cell.carcinoma / squamous.cell.carcinoma",
    input: "胸部 CT 切片图像（JPG / PNG），不超过 5MB，建议为轴位肺窗图像",
    output: "返回四类概率分布与判定类别，概率越高代表模型越倾向该诊断",
    scenario: ["医学影像 AI 教学演示", "肺部病变筛查流程验证", "影像算法对比实验"],
    algorithmCn: "ResNet-50 迁移学习（TensorFlow / Keras）",
    notice: "本模型仅用于科研与教学演示，输出结果不构成医学诊断依据，请以执业医师意见为准。",
  },

  weather: {
    category: "data",
    icon: "🌦️",
    tagline: "用气象观测数据判断当天天气类型",
    summary:
      "基于西雅图历史气象数据训练的梯度提升分类器。输入降水量、最高/最低气温、风速与日期，判断当天属于雨、晴、雾、毛毛雨、雪中的哪一种。",
    outputs: ["五类天气各自的概率", "概率最高的天气类型"],
    classes: ["雨", "晴", "雾", "毛毛雨", "雪"],
    classNote: "五分类，原始标签 rain / sun / fog / drizzle / snow；输出天气类型，不是温度数值",
    input: "7 个字段：降水量(mm)、最高温(°C)、最低温(°C)、风速(m/s)、年、月、日",
    output: "返回五类天气的概率分布，最高项为预测天气",
    scenario: ["气象数据分类教学", "历史天气数据回判", "结构化特征建模示例"],
    algorithmCn: "GradientBoostingClassifier，西雅图气象数据集训练",
  },

  stock: {
    category: "data",
    icon: "📈",
    tagline: "用开盘、最高、最低与成交量预测收盘价",
    summary:
      "线性回归模型，根据当日开盘价、最高价、最低价与成交量四个交易特征，回归预测当日收盘价。注意这是回归任务，输出的是一个价格数值，而不是涨跌方向。",
    outputs: ["预测收盘价（数值）", "输入特征与耗时"],
    input: "4 个数值字段：开盘价 Open、最高价 High、最低价 Low、成交量 Volume",
    output: "返回预测的收盘价数值（与输入价格同量纲），不做涨跌分类",
    scenario: ["回归任务接入演示", "量化特征建模教学", "价格区间预估"],
    algorithmCn: "线性回归 LinearRegression，Twitter 股价数据集训练",
    notice: "预测结果仅为模型拟合值，不构成任何投资建议。",
  },

  flower: {
    category: "vision",
    icon: "🌸",
    tagline: "识别照片里是哪一种花",
    summary:
      "从零训练的卷积神经网络，覆盖五种常见花卉。上传一张花卉照片，模型给出五种花的概率分布，适合作为图像分类的入门示例。",
    outputs: ["五种花卉各自的概率", "概率最高的花卉名称"],
    classes: ["雏菊", "蒲公英", "玫瑰", "向日葵", "郁金香"],
    classNote: "五分类，原始标签 daisy / dandelion / roses / sunflowers / tulips；只认识这五类，其它品种会被归到最接近的一类",
    input: "单张花卉照片（JPG / PNG），不超过 5MB，花朵主体清晰、背景简单效果更好",
    output: "返回五种花卉的概率分布，最高项为识别结果",
    scenario: ["植物识别科普", "拍照识花应用原型", "图像分类教学案例"],
    algorithmCn: "自建卷积神经网络（CNN），五类花卉数据集训练",
  },

  chinese_sentiment: {
    category: "text",
    icon: "🏨",
    tagline: "判断中文评论是好评还是差评",
    summary:
      "基于中文酒店评论训练的文本分类模型（MLP + CountVectorizer 词袋特征）。输出正面 / 负面两类概率，适合短评、弹幕、客服对话这类口语化文本。",
    outputs: ["正面概率", "负面概率", "概率最高的倾向"],
    classes: ["正面", "负面"],
    classNote: "二分类，原始标签为中文「正面 / 负面」，与「文本情感分析」走的是同一个中文模型",
    input: "中文短文本，建议 10–200 字，口语化评论效果最好",
    output: "返回正面 / 负面两类概率，最高项为情感倾向",
    scenario: ["酒店与餐饮评论分析", "客服工单情绪分级", "问卷简答归类"],
    algorithmCn: "MLP 多层感知机 + CountVectorizer，中文酒店评论数据集训练",
  },
};

/** 未登记的模型用类型兜底，保证详情页永远有内容可展示。 */
const FALLBACK_GUIDE: ModelGuide = {
  category: "vision",
  icon: "📦",
  tagline: "在线智能分析服务",
  summary: "该模型已接入统一推理服务，上传对应格式的数据即可获得分析结果。",
  outputs: ["分析结果", "置信度"],
  input: "根据模型类型上传对应格式的数据",
  output: "返回分析结果与置信度",
  scenario: ["在线分析体验"],
  algorithmCn: "—",
};

export function getModelGuide(api?: string | null, type?: string | null): ModelGuide {
  if (api && MODEL_GUIDES[api]) return MODEL_GUIDES[api];
  return { ...FALLBACK_GUIDE, category: getCategoryByApi(api, type) };
}

/** 卡片上展示的输出标签（最多 3 个，避免撑破版式）。 */
export function getOutputChips(api?: string | null, type?: string | null): string[] {
  const guide = getModelGuide(api, type);
  const source = guide.classes && guide.classes.length > 0 ? guide.classes : guide.outputs;
  return source.slice(0, 3);
}
